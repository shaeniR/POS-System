package com.pos.inventory;

import com.pos.inventory.dto.AddToCartRequest;
import com.pos.inventory.dto.CartResponse;
import com.pos.inventory.dto.OrderResponse;
import com.pos.inventory.model.Order;
import com.pos.inventory.model.OrderStatus;
import com.pos.inventory.model.Product;
import com.pos.inventory.repository.OrderRepository;
import com.pos.inventory.repository.ProductRepository;
import com.pos.inventory.scheduler.ReservationExpiryScheduler;
import com.pos.inventory.service.CartService;
import com.pos.inventory.service.OrderService;
import com.pos.inventory.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Long scheduler interval so the background job does not race the tests that expire reservations explicitly
@SpringBootTest(properties = "pos.reservation.expiry-check-interval-ms=3600000")
public class StockReservationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationExpiryScheduler reservationExpiryScheduler;

    @Test
    @DisplayName("Entering checkout reserves stock for 5 minutes")
    void checkoutReservesStock() {
        Long productId = createProduct(5);

        OrderResponse order = checkout(productId, 2);

        assertEquals(OrderStatus.RESERVED, order.getStatus());
        assertEquals(3, stockOf(productId));
        long secondsReserved = java.time.Duration.between(order.getCreatedAt(), order.getExpiresAt()).getSeconds();
        assertTrue(secondsReserved >= 299 && secondsReserved <= 300, "Reservation should last 5 minutes");
        assertTrue(order.getReservationSecondsRemaining() > 0);
    }

    @Test
    @DisplayName("Scheduler expires overdue reservations and releases their stock exactly once")
    void expiredReservationReleasesStock() {
        Long productId = createProduct(5);
        OrderResponse order = checkout(productId, 2);
        backdateExpiry(order.getId());

        reservationExpiryScheduler.releaseExpiredReservations();

        assertEquals(OrderStatus.EXPIRED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(5, stockOf(productId));

        // A second run must not restore the same stock again
        reservationExpiryScheduler.releaseExpiredReservations();
        assertEquals(5, stockOf(productId));
    }

    @Test
    @DisplayName("Reservations that have not reached their expiry time are left untouched")
    void activeReservationIsNotReleased() {
        Long productId = createProduct(5);
        OrderResponse order = checkout(productId, 2);

        assertFalse(reservationService.expireReservation(order.getId()));

        assertEquals(OrderStatus.RESERVED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(3, stockOf(productId));
    }

    @Test
    @DisplayName("Reading an overdue order expires it immediately, without waiting for the scheduler")
    void readingOverdueOrderExpiresIt() {
        Long productId = createProduct(5);
        OrderResponse order = checkout(productId, 4);
        backdateExpiry(order.getId());

        OrderResponse fetched = orderService.getOrderByNumber(order.getOrderNumber());

        assertEquals(OrderStatus.EXPIRED, fetched.getStatus());
        assertNull(fetched.getReservationSecondsRemaining());
        assertEquals(5, stockOf(productId));
    }

    @Test
    @DisplayName("Concurrent expiry attempts on the same reservation restore stock only once")
    void concurrentExpiryReleasesStockOnce() throws InterruptedException {
        Long productId = createProduct(10);
        OrderResponse order = checkout(productId, 3);
        backdateExpiry(order.getId());

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger expiredCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    if (reservationService.expireReservation(order.getId())) {
                        expiredCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("Unexpected exception: " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await();
        executor.shutdown();

        assertEquals(1, expiredCount.get(), "Exactly one thread should expire the reservation");
        assertEquals(10, stockOf(productId), "Stock must be restored exactly once");
    }

    private Long createProduct(int stock) {
        return productRepository.save(Product.builder()
                .name("Reservation Test Product")
                .price(BigDecimal.valueOf(10))
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

    private int stockOf(Long productId) {
        return productRepository.findById(productId).orElseThrow().getStockCount();
    }
}
