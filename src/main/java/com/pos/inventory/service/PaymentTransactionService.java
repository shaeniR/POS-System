package com.pos.inventory.service;

import com.pos.inventory.dto.PaymentResponse;
import com.pos.inventory.exception.DuplicateSubmissionException;
import com.pos.inventory.exception.InvalidOrderStateException;
import com.pos.inventory.exception.ReservationExpiredException;
import com.pos.inventory.exception.ResourceNotFoundException;
import com.pos.inventory.model.Order;
import com.pos.inventory.model.OrderStatus;
import com.pos.inventory.model.Payment;
import com.pos.inventory.model.PaymentStatus;
import com.pos.inventory.payment.GatewayResult;
import com.pos.inventory.payment.PaymentOutcome;
import com.pos.inventory.repository.OrderRepository;
import com.pos.inventory.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The two short database transactions around a gateway call. Both lock the order row, so all payment
 * attempts, cancellation and reservation expiry for one order are serialized. No lock is held while the
 * gateway is called.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentTransactionService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ReservationService reservationService;
    private final OrderLifecycleService orderLifecycleService;

    /**
     * Validates that the order can be paid and records a PROCESSING payment.
     *
     * @throws DuplicateSubmissionException if the order is already paid, a payment is already in progress,
     *                                      or the idempotency key was used before
     * @throws ReservationExpiredException  if the reservation has expired (its stock is released before throwing)
     * @throws InvalidOrderStateException   if the order is FAILED, CANCELLED or otherwise not payable
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = ReservationExpiredException.class)
    public Payment startPayment(Long orderId, String idempotencyKey) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (idempotencyKey != null) {
            paymentRepository.findByIdempotencyKey(idempotencyKey).ifPresent(existing -> {
                throw new DuplicateSubmissionException("Duplicate payment: Idempotency-Key '" + idempotencyKey +
                        "' was already used for payment " + existing.getId() + " (" + existing.getStatus() + ")");
            });
        }

        switch (order.getStatus()) {
            case RESERVED -> { /* payable */ }
            case PAID -> throw new DuplicateSubmissionException(
                    "Duplicate payment: order " + order.getOrderNumber() + " has already been paid");
            case EXPIRED -> throw new ReservationExpiredException(
                    "Reservation for order " + order.getOrderNumber() + " has expired; please checkout again");
            default -> throw new InvalidOrderStateException(
                    "Order " + order.getOrderNumber() + " cannot be paid in status " + order.getStatus());
        }

        if (reservationService.hasPaymentInFlight(orderId)) {
            throw new DuplicateSubmissionException(
                    "Duplicate payment: a payment for order " + order.getOrderNumber() + " is already in progress");
        }

        if (reservationService.isReservationExpired(order, LocalDateTime.now())) {
            // Release now rather than waiting for the scheduler; noRollbackFor keeps this change committed
            reservationService.expire(order);
            throw new ReservationExpiredException("Reservation for order " + order.getOrderNumber() +
                    " expired at " + order.getExpiresAt() + "; please checkout again");
        }

        return paymentRepository.save(Payment.builder()
                .order(order)
                .idempotencyKey(idempotencyKey)
                .status(PaymentStatus.PROCESSING)
                .amount(order.getTotalAmount())
                .build());
    }

    /**
     * Applies the gateway outcome to the payment and the order:
     * SUCCESS confirms the order (PAID), FAILURE marks it FAILED and releases stock,
     * TIMEOUT expires the reservation and releases stock.
     * <p>
     * If the order was already resolved without this payment (e.g. the payment was abandoned and the reservation
     * expired), the order and stock are left untouched, and an approved charge is marked REFUNDED so the caller
     * can refund it.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse completePayment(Long orderId, Long paymentId, GatewayResult result) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        boolean paymentStillDecidesOrder = payment.getStatus() == PaymentStatus.PROCESSING
                && order.getStatus() == OrderStatus.RESERVED;

        if (!paymentStillDecidesOrder) {
            if (result.outcome() == PaymentOutcome.SUCCESS) {
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.setTransactionId(result.transactionId());
                payment.setMessage("Order was " + order.getStatus() + " when payment was approved; charge refunded");
            } else if (payment.getStatus() == PaymentStatus.PROCESSING) {
                payment.setStatus(paymentStatusFor(result.outcome()));
                payment.setMessage(result.message());
            }
            payment.setCompletedAt(LocalDateTime.now());
            paymentRepository.saveAndFlush(payment);
            log.warn("Payment {} completed after order {} was already {}; order left unchanged",
                    paymentId, order.getOrderNumber(), order.getStatus());
            return toResponse(payment, order);
        }

        payment.setStatus(paymentStatusFor(result.outcome()));
        payment.setTransactionId(result.transactionId());
        payment.setMessage(result.message());
        payment.setCompletedAt(LocalDateTime.now());
        paymentRepository.saveAndFlush(payment);

        orderLifecycleService.transition(order, switch (result.outcome()) {
            case SUCCESS -> OrderStatus.PAID;
            case FAILURE -> OrderStatus.FAILED;
            case TIMEOUT -> OrderStatus.EXPIRED;
        });

        log.info("Payment {} for order {} completed: payment={}, order={}",
                payment.getId(), order.getOrderNumber(), payment.getStatus(), order.getStatus());
        return toResponse(payment, order);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsForOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(payment -> toResponse(payment, order))
                .toList();
    }

    private PaymentStatus paymentStatusFor(PaymentOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> PaymentStatus.SUCCESS;
            case FAILURE -> PaymentStatus.FAILED;
            case TIMEOUT -> PaymentStatus.TIMEOUT;
        };
    }

    private PaymentResponse toResponse(Payment payment, Order order) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .paymentStatus(payment.getStatus())
                .orderStatus(order.getStatus())
                .amount(payment.getAmount())
                .transactionId(payment.getTransactionId())
                .idempotencyKey(payment.getIdempotencyKey())
                .message(payment.getMessage())
                .createdAt(payment.getCreatedAt())
                .completedAt(payment.getCompletedAt())
                .build();
    }
}
