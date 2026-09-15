package com.pos.inventory.service;

import com.pos.inventory.exception.InvalidOrderStateException;
import com.pos.inventory.exception.ResourceNotFoundException;
import com.pos.inventory.model.Order;
import com.pos.inventory.model.OrderStatus;
import com.pos.inventory.model.Payment;
import com.pos.inventory.model.PaymentStatus;
import com.pos.inventory.repository.OrderRepository;
import com.pos.inventory.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCancellationService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ReservationService reservationService;
    private final OrderLifecycleService orderLifecycleService;

    /**
     * Cancels a RESERVED or PAID order in one transaction: restores its stock and marks successful payments REFUNDED.
     *
     * @return gateway transaction IDs to refund once this transaction has committed
     * @throws InvalidOrderStateException if the order is already final (CANCELLED, EXPIRED, FAILED)
     *                                    or a payment for it is still in progress
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<String> cancel(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new InvalidOrderStateException("Order " + order.getOrderNumber() + " cannot be cancelled: it is already " +
                    order.getStatus() + (order.getStatus().holdsStock() ? "" : " and its stock has already been restored"));
        }
        if (reservationService.hasPaymentInFlight(orderId)) {
            throw new InvalidOrderStateException("Order " + order.getOrderNumber() +
                    " has a payment in progress; retry cancellation once it completes");
        }

        reservationService.closeAbandonedPayments(orderId);

        List<Payment> chargedPayments = paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS);
        for (Payment payment : chargedPayments) {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setMessage("Refunded on order cancellation");
            payment.setCompletedAt(LocalDateTime.now());
        }
        paymentRepository.saveAllAndFlush(chargedPayments);

        orderLifecycleService.transition(order, OrderStatus.CANCELLED);

        return chargedPayments.stream().map(Payment::getTransactionId).toList();
    }
}
