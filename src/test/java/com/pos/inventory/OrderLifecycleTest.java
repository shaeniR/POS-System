package com.pos.inventory;

import com.pos.inventory.dto.AddToCartRequest;
import com.pos.inventory.dto.CartResponse;
import com.pos.inventory.dto.OrderResponse;
import com.pos.inventory.dto.PaymentRequest;
import com.pos.inventory.exception.InsufficientStockException;
import com.pos.inventory.exception.InvalidOrderStateException;
import com.pos.inventory.model.Order;
import com.pos.inventory.model.OrderStatus;
import com.pos.inventory.model.PaymentStatus;
import com.pos.inventory.model.Product;
import com.pos.inventory.payment.PaymentOutcome;
import com.pos.inventory.repository.OrderRepository;
import com.pos.inventory.repository.PaymentRepository;
import com.pos.inventory.repository.ProductRepository;
import com.pos.inventory.service.CartService;
import com.pos.inventory.service.OrderService;
import com.pos.inventory.service.PaymentService;
import com.pos.inventory.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        // Long scheduler interval so the background job does not race these tests
        "pos.reservation.expiry-check-interval-ms=3600000",
        "pos.payment.gateway-timeout-ms=500",
        "pos.payment.mock-latency-ms=50",
        "pos.payment.mock-timeout-hang-ms=5000"
})
public class OrderLifecycleTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReservationService reservationService;

    @Test
    @DisplayName("Status transitions follow the lifecycle; final statuses allow none")
    void transitionRules() {
        assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.RESERVED));
        assertTrue(OrderStatus.RESERVED.canTransitionTo(OrderStatus.PAID));
        assertTrue(OrderStatus.RESERVED.canTransitionTo(OrderStatus.EXPIRED));
        assertTrue(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED));

        assertFalse(OrderStatus.PAID.canTransitionTo(OrderStatus.RESERVED));
        assertFalse(OrderStatus.PAID.canTransitionTo(OrderStatus.EXPIRED));
        assertFalse(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAID));
        assertFalse(OrderStatus.RESERVED.canTransitionTo(OrderStatus.RESERVED));

        for (OrderStatus terminal : List.of(OrderStatus.CANCELLED, OrderStatus.EXPIRED, OrderStatus.FAILED)) {
            assertTrue(terminal.isTerminal());
            for (OrderStatus target : OrderStatus.values()) {
                assertFalse(terminal.canTransitionTo(target), terminal + " must not move to " + target);
            }
        }

        Order order = Order.builder().orderNumber("ORD-TEST").status(OrderStatus.EXPIRED).build();
        assertThrows(InvalidOrderStateException.class, () -> order.transitionTo(OrderStatus.PAID));
        assertEquals(OrderStatus.EXPIRED, order.getStatus());
    }

    @Test
    @DisplayName("Cancelling a reserved order restores its stock")
    void cancelReservedOrder() {
        Long productId = createProduct(5);
        OrderResponse order = checkout(productId, 3);
        assertEquals(2, stockOf(productId));

        orderService.cancelOrder(order.getId());

        OrderResponse cancelled = orderService.getOrderById(order.getId());
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertTrue(cancelled.getAllowedTransitions().isEmpty());
        assertEquals(5, stockOf(productId));
    }

    @Test
    @DisplayName("Cancelling a paid order restores its stock and refunds the payment")
    void cancelPaidOrder() {
        Long productId = createProduct(5);
        OrderResponse order = checkout(productId, 2);
        paymentService.processPayment(order.getId(), new PaymentRequest(PaymentOutcome.SUCCESS), null);
        assertEquals(3, stockOf(productId));

        orderService.cancelOrder(order.getId());

        assertEquals(OrderStatus.CANCELLED, statusOf(order.getId()));
        assertEquals(5, stockOf(productId));
        assertEquals(PaymentStatus.REFUNDED, paymentRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).get(0).getStatus());
    }

    @Test
    @DisplayName("Cancelling twice is rejected and never restores stock twice")
    void cancelTwiceRejected() {
        Long productId = createProduct(5);
        OrderResponse order = checkout(productId, 2);

        orderService.cancelOrder(order.getId());
        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(order.getId()));

        assertEquals(5, stockOf(productId));
    }

    @Test
    @DisplayName("Failed and expired orders cannot be cancelled, and their already-restored stock is not restored again")
    void finalOrdersCannotBeCancelled() {
        Long productId = createProduct(10);

        OrderResponse failed = checkout(productId, 2);
        paymentService.processPayment(failed.getId(), new PaymentRequest(PaymentOutcome.FAILURE), null);
        OrderResponse expired = checkout(productId, 3);
        backdateExpiry(expired.getId());
        reservationService.expireReservation(expired.getId());
        assertEquals(10, stockOf(productId));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(failed.getId()));
        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(expired.getId()));

        assertEquals(OrderStatus.FAILED, statusOf(failed.getId()));
        assertEquals(OrderStatus.EXPIRED, statusOf(expired.getId()));
        assertEquals(10, stockOf(productId));
    }

    @Test
    @DisplayName("A cancelled order cannot be paid")
    void cancelledOrderCannotBePaid() {
        Long productId = createProduct(5);
        OrderResponse order = checkout(productId, 1);
        orderService.cancelOrder(order.getId());

        assertThrows(InvalidOrderStateException.class, () ->
                paymentService.processPayment(order.getId(), new PaymentRequest(PaymentOutcome.SUCCESS), null));
        assertEquals(5, stockOf(productId));
    }

    @Test
    @DisplayName("Checkout that fails part-way rolls back every stock deduction and creates no order")
    void failedCheckoutRollsBackCompletely() {
        Long plentiful = createProduct(5);
        Long scarce = createProduct(1);
        CartResponse cart = cartService.addItemToCart(null, AddToCartRequest.builder().productId(plentiful).quantity(2).build());
        cartService.addItemToCart(cart.getCartId(), AddToCartRequest.builder().productId(scarce).quantity(1).build());
        // Another customer takes the last unit after it was added to this cart
        Product scarceProduct = productRepository.findById(scarce).orElseThrow();
        scarceProduct.setStockCount(0);
        productRepository.save(scarceProduct);

        assertThrows(InsufficientStockException.class, () -> orderService.checkoutCart(cart.getCartId()));

        assertEquals(5, stockOf(plentiful), "Deduction of the first item must be rolled back");
        assertEquals(0, stockOf(scarce));
        assertTrue(orderRepository.findAll().stream().noneMatch(o -> cart.getCartId().equals(o.getCartId())));
        assertEquals(2, cartService.getCart(cart.getCartId()).getItems().size(), "Cart must be left intact");
    }

    @Test
    @DisplayName("Cancellation racing a payment leaves the order and stock consistent")
    void cancelRacingPayment() throws InterruptedException {
        for (int attempt = 0; attempt < 5; attempt++) {
            Long productId = createProduct(5);
            OrderResponse order = checkout(productId, 2);

            runTogether(
                    () -> orderService.cancelOrder(order.getId()),
                    () -> paymentService.processPayment(order.getId(), new PaymentRequest(PaymentOutcome.SUCCESS), null));

            OrderStatus status = statusOf(order.getId());
            if (status == OrderStatus.PAID) {
                assertEquals(3, stockOf(productId));
            } else {
                assertEquals(OrderStatus.CANCELLED, status);
                assertEquals(5, stockOf(productId));
                assertTrue(paymentRepository.findByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS).isEmpty(),
                        "A cancelled order must not keep a successful charge");
            }
        }
    }

    @Test
    @DisplayName("Cancellation racing reservation expiry restores stock exactly once")
    void cancelRacingExpiry() throws InterruptedException {
        for (int attempt = 0; attempt < 5; attempt++) {
            Long productId = createProduct(5);
            OrderResponse order = checkout(productId, 4);
            backdateExpiry(order.getId());

            runTogether(
                    () -> orderService.cancelOrder(order.getId()),
                    () -> reservationService.expireReservation(order.getId()));

            assertTrue(statusOf(order.getId()) == OrderStatus.CANCELLED || statusOf(order.getId()) == OrderStatus.EXPIRED);
            assertEquals(5, stockOf(productId), "Stock must be restored exactly once");
        }
    }

    /** Starts both tasks at the same instant and waits for both; expected business exceptions are ignored. */
    private void runTogether(Runnable first, Runnable second) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        for (Runnable task : List.of(first, second)) {
            executor.submit(() -> {
                try {
                    start.await();
                    task.run();
                } catch (RuntimeException | InterruptedException ignored) {
                    // Losing the race is expected to be rejected
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        executor.shutdown();
    }

    private Long createProduct(int stock) {
        return productRepository.save(Product.builder()
                .name("Lifecycle Test Product")
                .price(BigDecimal.valueOf(15))
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
