package com.pos.inventory;

import com.pos.inventory.dto.AddToCartRequest;
import com.pos.inventory.dto.CartResponse;
import com.pos.inventory.dto.OrderResponse;
import com.pos.inventory.dto.PaymentRequest;
import com.pos.inventory.dto.PaymentResponse;
import com.pos.inventory.exception.DuplicateSubmissionException;
import com.pos.inventory.exception.InvalidOrderStateException;
import com.pos.inventory.exception.ReservationExpiredException;
import com.pos.inventory.model.Order;
import com.pos.inventory.model.OrderStatus;
import com.pos.inventory.model.PaymentStatus;
import com.pos.inventory.model.Product;
import com.pos.inventory.payment.PaymentOutcome;
import com.pos.inventory.repository.OrderRepository;
import com.pos.inventory.repository.ProductRepository;
import com.pos.inventory.scheduler.ReservationExpiryScheduler;
import com.pos.inventory.service.CartService;
import com.pos.inventory.service.OrderService;
import com.pos.inventory.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        // Long scheduler interval so the background job does not race these tests
        "pos.reservation.expiry-check-interval-ms=3600000",
        "pos.payment.gateway-timeout-ms=500",
        "pos.payment.mock-latency-ms=50",
        "pos.payment.mock-timeout-hang-ms=5000"
})
public class PaymentTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReservationExpiryScheduler reservationExpiryScheduler;

    @Test
    @DisplayName("Successful payment confirms the order and keeps the stock deducted, even past the reservation expiry")
    void successConfirmsOrder() {
        Long productId = createProduct(5);
        OrderResponse order = checkout(productId, 2);

        PaymentResponse payment = pay(order, PaymentOutcome.SUCCESS, null);

        assertEquals(PaymentStatus.SUCCESS, payment.getPaymentStatus());
        assertEquals(OrderStatus.PAID, payment.getOrderStatus());
        assertNotNull(payment.getTransactionId());
        assertEquals(3, stockOf(productId));

        backdateExpiry(order.getId());
        reservationExpiryScheduler.releaseExpiredReservations();
        assertEquals(OrderStatus.PAID, statusOf(order.getId()));
        assertEquals(3, stockOf(productId), "A paid order's stock must never be released");
    }

    @Test
    @DisplayName("Failed payment marks the order FAILED and releases its stock")
    void failureReleasesStock() {
        Long productId = createProduct(5);
        OrderResponse order = checkout(productId, 2);

        PaymentResponse payment = pay(order, PaymentOutcome.FAILURE, null);

        assertEquals(PaymentStatus.FAILED, payment.getPaymentStatus());
        assertEquals(OrderStatus.FAILED, payment.getOrderStatus());
        assertEquals(5, stockOf(productId));
        assertThrows(InvalidOrderStateException.class, () -> pay(order, PaymentOutcome.SUCCESS, null),
                "A failed order cannot be paid again");
    }

    @Test
    @DisplayName("Gateway timeout expires the reservation and releases its stock")
    void timeoutExpiresReservation() {
        Long productId = createProduct(5);
        OrderResponse order = checkout(productId, 2);

        long start = System.currentTimeMillis();
        PaymentResponse payment = pay(order, PaymentOutcome.TIMEOUT, null);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(PaymentStatus.TIMEOUT, payment.getPaymentStatus());
        assertEquals(OrderStatus.EXPIRED, payment.getOrderStatus());
        assertEquals(5, stockOf(productId));
        assertTrue(elapsed < 3000, "Caller must give up at the gateway timeout, not wait for the gateway");
        assertThrows(ReservationExpiredException.class, () -> pay(order, PaymentOutcome.SUCCESS, null));
    }

    @Test
    @DisplayName("Paying an already paid order is rejected as a duplicate")
    void duplicatePaymentRejected() {
        Long productId = createProduct(5);
        OrderResponse order = checkout(productId, 1);
        pay(order, PaymentOutcome.SUCCESS, null);

        assertThrows(DuplicateSubmissionException.class, () -> pay(order, PaymentOutcome.SUCCESS, null));
        assertEquals(4, stockOf(productId));
        assertEquals(1, paymentService.getPaymentsForOrder(order.getId()).size());
    }

    @Test
    @DisplayName("Reusing an Idempotency-Key is rejected as a duplicate")
    void reusedIdempotencyKeyRejected() {
        Long productId = createProduct(5);
        OrderResponse firstOrder = checkout(productId, 1);
        OrderResponse secondOrder = checkout(productId, 1);
        String key = "key-" + UUID.randomUUID();

        pay(firstOrder, PaymentOutcome.FAILURE, key);

        assertThrows(DuplicateSubmissionException.class, () -> pay(secondOrder, PaymentOutcome.SUCCESS, key));
        assertEquals(OrderStatus.RESERVED, statusOf(secondOrder.getId()));
    }

    @Test
    @DisplayName("Simultaneous payment submissions for one order charge it exactly once")
    void concurrentPaymentsChargeOnce() throws InterruptedException {
        Long productId = createProduct(5);
        OrderResponse order = checkout(productId, 2);

        AtomicInteger duplicates = new AtomicInteger();
        int successes = runConcurrently(6, () -> {
            try {
                return pay(order, PaymentOutcome.SUCCESS, null).getPaymentStatus() == PaymentStatus.SUCCESS;
            } catch (DuplicateSubmissionException e) {
                duplicates.incrementAndGet();
                return false;
            }
        });

        assertEquals(1, successes, "Exactly one payment should succeed");
        assertEquals(5, duplicates.get(), "All other submissions should be rejected as duplicates");
        assertEquals(1, paymentService.getPaymentsForOrder(order.getId()).size());
        assertEquals(3, stockOf(productId));
    }

    @Test
    @DisplayName("Paying after the reservation expired is rejected and the stock is released immediately")
    void paymentAfterExpiryRejected() {
        Long productId = createProduct(5);
        OrderResponse order = checkout(productId, 2);
        backdateExpiry(order.getId());

        assertThrows(ReservationExpiredException.class, () -> pay(order, PaymentOutcome.SUCCESS, null));

        assertEquals(OrderStatus.EXPIRED, statusOf(order.getId()));
        assertEquals(5, stockOf(productId));
    }

    @Test
    @DisplayName("Simultaneous checkout submissions for one cart create exactly one order")
    void concurrentCheckoutOfSameCartCreatesOneOrder() throws InterruptedException {
        Long productId = createProduct(10);
        CartResponse cart = cartService.addItemToCart(null, AddToCartRequest.builder()
                .productId(productId).quantity(3).build());

        AtomicInteger duplicates = new AtomicInteger();
        int created = runConcurrently(5, () -> {
            try {
                orderService.checkoutCart(cart.getCartId());
                return true;
            } catch (DuplicateSubmissionException e) {
                duplicates.incrementAndGet();
                return false;
            }
        });

        assertEquals(1, created, "Exactly one order should be created");
        assertEquals(4, duplicates.get(), "All other submissions should be rejected as duplicates");
        assertEquals(7, stockOf(productId), "Stock must be deducted only once");
    }

    /** Runs the task on {@code threads} threads released at the same instant; returns how many returned true. */
    private int runConcurrently(int threads, Callable<Boolean> task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger trueCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    if (task.call()) {
                        trueCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("Unexpected exception: " + e);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await();
        executor.shutdown();
        return trueCount.get();
    }

    private PaymentResponse pay(OrderResponse order, PaymentOutcome outcome, String idempotencyKey) {
        return paymentService.processPayment(order.getId(), new PaymentRequest(outcome), idempotencyKey);
    }

    private Long createProduct(int stock) {
        return productRepository.save(Product.builder()
                .name("Payment Test Product")
                .price(BigDecimal.valueOf(25))
                .stockCount(stock)
                .build()).getId();
    }

    private OrderResponse checkout(Long productId, int quantity) {
        CartResponse cart = cartService.addItemToCart(null, AddToCartRequest.builder()
                .productId(productId)
                .quantity(quantity)
                .build());
        return orderService.checkoutCart(cart.getCartId());
    }

    private void backdateExpiry(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        orderRepository.save(order);
    }

    private OrderStatus statusOf(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    private int stockOf(Long productId) {
        return productRepository.findById(productId).orElseThrow().getStockCount();
    }
}
