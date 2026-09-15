package com.pos.inventory;

import com.pos.inventory.dto.AddToCartRequest;
import com.pos.inventory.dto.CartResponse;
import com.pos.inventory.dto.OrderResponse;
import com.pos.inventory.exception.InsufficientStockException;
import com.pos.inventory.model.Product;
import com.pos.inventory.repository.ProductRepository;
import com.pos.inventory.service.CartService;
import com.pos.inventory.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class OrderConcurrencyTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Test
    @DisplayName("Verify that simultaneous checkouts for a limited stock item NEVER cause overselling")
    void testConcurrentOrdersPreventOverselling() throws InterruptedException {
        // 1. Create a product with ONLY 1 item in stock
        Product product = Product.builder()
                .name("Limited Edition Gadget")
                .price(BigDecimal.valueOf(99.99))
                .stockCount(1)
                .build();
        product = productRepository.save(product);
        Long productId = product.getId();

        // 2. Prepare 10 separate user carts, each containing this 1 item
        int numberOfConcurrentUsers = 10;
        List<String> cartIds = new ArrayList<>();

        for (int i = 0; i < numberOfConcurrentUsers; i++) {
            CartResponse cart = cartService.addItemToCart(null, AddToCartRequest.builder()
                    .productId(productId)
                    .quantity(1)
                    .build());
            cartIds.add(cart.getCartId());
        }

        // 3. Setup multi-threaded execution to trigger checkout simultaneously
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfConcurrentUsers);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(numberOfConcurrentUsers);

        AtomicInteger successfulOrders = new AtomicInteger(0);
        AtomicInteger failedOrders = new AtomicInteger(0);
        List<OrderResponse> createdOrders = Collections.synchronizedList(new ArrayList<>());

        for (String cartId : cartIds) {
            executorService.submit(() -> {
                try {
                    // Wait for all threads to align at the starting gate
                    startSignal.await();
                    OrderResponse order = orderService.checkoutCart(cartId);
                    successfulOrders.incrementAndGet();
                    createdOrders.add(order);
                } catch (InsufficientStockException e) {
                    failedOrders.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Unexpected exception: " + e.getMessage());
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        // Release the latch to start all threads simultaneously!
        startSignal.countDown();
        doneSignal.await();
        executorService.shutdown();

        // 4. Assert concurrency guarantees
        Product finalProductState = productRepository.findById(productId).orElseThrow();

        System.out.println("=== Concurrency Test Results ===");
        System.out.println("Successful Orders: " + successfulOrders.get());
        System.out.println("Failed Orders (Prevented Overselling): " + failedOrders.get());
        System.out.println("Final Stock Count in DB: " + finalProductState.getStockCount());

        // EXACTLY 1 order must have succeeded
        assertEquals(1, successfulOrders.get(), "Only 1 order should succeed for stock of 1");
        // EXACTLY 9 orders must have failed
        assertEquals(9, failedOrders.get(), "9 orders should fail due to insufficient stock");
        // Remaining stock MUST be exactly 0 (never negative)
        assertEquals(0, finalProductState.getStockCount(), "Remaining stock in DB must be 0");
    }
}
