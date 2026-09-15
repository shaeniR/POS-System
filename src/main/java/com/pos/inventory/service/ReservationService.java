package com.pos.inventory.service;

import com.pos.inventory.model.Order;
import com.pos.inventory.model.OrderStatus;
import com.pos.inventory.model.PaymentStatus;
import com.pos.inventory.repository.OrderRepository;
import com.pos.inventory.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Manages the lifecycle of stock reservations.
 * <p>
 * Stock is deducted from {@code Product.stockCount} the moment a cart enters checkout, and the resulting
 * order is held in {@link OrderStatus#RESERVED} until {@code expiresAt}. If checkout is not completed
 * by then, the reservation is expired and the reserved quantities are returned to available inventory.
 * <p>
 * Every release happens under a pessimistic row lock on the order and re-checks the order status, so a
 * reservation is released at most once even when the scheduler, an on-read expiry check, a payment or a
 * cancellation race against each other or run on multiple application instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    /** A PROCESSING payment older than the gateway timeout plus this margin is treated as abandoned. */
    private static final long PAYMENT_IN_FLIGHT_GRACE_MS = 30_000;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderLifecycleService orderLifecycleService;

    @Value("${pos.reservation.ttl-minutes:5}")
    private long reservationTtlMinutes;

    @Value("${pos.payment.gateway-timeout-ms:3000}")
    private long gatewayTimeoutMs;

    /** Expiry timestamp for a reservation created now. */
    public LocalDateTime newExpiryTime() {
        return LocalDateTime.now().plusMinutes(reservationTtlMinutes);
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredReservationIds() {
        return orderRepository.findIdsByStatusExpiredBy(OrderStatus.RESERVED, LocalDateTime.now());
    }

    /**
     * Expires a single reservation and releases its stock in a new, independent transaction.
     *
     * @return {@code true} if this call expired the reservation; {@code false} if the order does not exist,
     * is no longer RESERVED (already paid, expired, cancelled...), has not reached its expiry time yet,
     * or has a payment in flight.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean expireReservation(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !isReservationExpired(order, LocalDateTime.now())) {
            return false;
        }
        if (hasPaymentInFlight(orderId)) {
            // Let the in-flight payment decide the order's outcome; retried on the next scheduler run
            log.info("Reservation for order {} is overdue but a payment is in progress; deferring expiry", order.getOrderNumber());
            return false;
        }

        expire(order);
        log.info("Reservation for order {} expired at {}; stock released", order.getOrderNumber(), order.getExpiresAt());
        return true;
    }

    /**
     * Expires a RESERVED order whose row lock the caller already holds, closing out any abandoned payments.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void expire(Order order) {
        closeAbandonedPayments(order.getId());
        orderLifecycleService.transition(order, OrderStatus.EXPIRED);
    }

    /**
     * Marks PROCESSING payments that are past the in-flight window as TIMEOUT, so no payment stays unresolved
     * once its order reaches a final status. Should a late gateway approval still arrive, it is refunded.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeAbandonedPayments(Long orderId) {
        paymentRepository.updateStatusForOrder(orderId, PaymentStatus.PROCESSING, PaymentStatus.TIMEOUT,
                "No gateway response recorded; payment abandoned", LocalDateTime.now());
    }

    /** Whether a payment for this order has been sent to the gateway and could still complete. */
    public boolean hasPaymentInFlight(Long orderId) {
        LocalDateTime inFlightSince = LocalDateTime.now().minusNanos((gatewayTimeoutMs + PAYMENT_IN_FLIGHT_GRACE_MS) * 1_000_000);
        return paymentRepository.existsByOrderIdAndStatusAndCreatedAtAfter(orderId, PaymentStatus.PROCESSING, inFlightSince);
    }

    public boolean isReservationExpired(Order order, LocalDateTime now) {
        return order.getStatus() == OrderStatus.RESERVED
                && order.getExpiresAt() != null
                && !order.getExpiresAt().isAfter(now);
    }
}
