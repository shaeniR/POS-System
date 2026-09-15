package com.pos.inventory.service;

import com.pos.inventory.model.Order;
import com.pos.inventory.model.OrderItem;
import com.pos.inventory.model.OrderStatus;
import com.pos.inventory.model.PaymentStatus;
import com.pos.inventory.repository.OrderRepository;
import com.pos.inventory.repository.PaymentRepository;
import com.pos.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Manages the lifecycle of stock reservations.
 * <p>
 * Stock is deducted from {@code Product.stockCount} the moment a cart enters checkout, and the resulting
 * order is held in {@link OrderStatus#RESERVED} until {@code expiresAt}. If checkout is not completed
 * by then, the reservation is expired and the reserved quantities are returned to available inventory.
 * <p>
 * Every release happens under a pessimistic row lock on the order and re-checks the order status, so a
 * reservation is released at most once even when the scheduler, an on-read expiry check and (later) a
 * payment confirmation race against each other or run on multiple application instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;

    /** A PROCESSING payment older than the gateway timeout plus this margin is treated as abandoned. */
    private static final long PAYMENT_IN_FLIGHT_GRACE_MS = 30_000;

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
     * is no longer RESERVED (already paid, expired, cancelled...) or has not reached its expiry time yet.
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

        releaseReservedStock(order, OrderStatus.EXPIRED);
        log.info("Reservation for order {} expired at {}; stock released", order.getOrderNumber(), order.getExpiresAt());
        return true;
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

    /**
     * Moves the order to {@code targetStatus} and returns all of its reserved quantities to inventory.
     * <p>
     * Must be called inside a transaction that already holds the order's row lock (see
     * {@link OrderRepository#findByIdForUpdate}). Products are restored in ascending ID order, matching
     * the lock order used at checkout, to avoid deadlocks.
     */
    void releaseReservedStock(Order order, OrderStatus targetStatus) {
        // Capture quantities before the bulk stock updates clear the persistence context
        Map<Long, Integer> quantityByProductId = new TreeMap<>();
        for (OrderItem item : order.getItems()) {
            quantityByProductId.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
        }

        order.setStatus(targetStatus);
        orderRepository.saveAndFlush(order);

        quantityByProductId.forEach((productId, quantity) -> {
            if (productRepository.restoreStock(productId, quantity) == 0) {
                log.warn("Could not restore {} units to product ID {} for order {}: product no longer exists",
                        quantity, productId, order.getOrderNumber());
            }
        });
    }
}
