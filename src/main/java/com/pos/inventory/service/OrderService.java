package com.pos.inventory.service;

import com.pos.inventory.dto.OrderItemResponse;
import com.pos.inventory.dto.OrderResponse;
import com.pos.inventory.exception.InsufficientStockException;
import com.pos.inventory.exception.ResourceNotFoundException;
import com.pos.inventory.model.*;
import com.pos.inventory.repository.CartRepository;
import com.pos.inventory.repository.OrderRepository;
import com.pos.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ReservationService reservationService;

    /**
     * Converts a Cart into an Order under a strict database transaction with Pessimistic Locking.
     * Prevents race conditions, overselling, and deadlocks by locking products in deterministic ID order.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse checkoutCart(String cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found with id: " + cartId));

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty cart");
        }

        // Sort items by Product ID ascending to guarantee deterministic lock acquisition order and prevent deadlocks
        List<CartItem> sortedItems = new ArrayList<>(cart.getItems());
        sortedItems.sort(Comparator.comparing(item -> item.getProduct().getId()));

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        Order order = Order.builder()
                .orderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .status(OrderStatus.RESERVED)
                .createdAt(LocalDateTime.now())
                .expiresAt(reservationService.newExpiryTime())
                .totalAmount(BigDecimal.ZERO)
                .build();

        for (CartItem item : sortedItems) {
            Long productId = item.getProduct().getId();
            int requestedQuantity = item.getQuantity();

            // Atomic database update with row-level locking ensures strictly zero overselling
            int rowsUpdated = productRepository.deductStock(productId, requestedQuantity);
            if (rowsUpdated == 0) {
                Product p = productRepository.findById(productId).orElse(null);
                String pName = (p != null) ? p.getName() : "ID " + productId;
                int currentAvailable = (p != null) ? p.getStockCount() : 0;
                log.warn("Overselling prevented! Product ID {}: available = {}, requested = {}",
                        productId, currentAvailable, requestedQuantity);
                throw new InsufficientStockException("Insufficient stock for product '" +
                        pName + "'. Available: " + currentAvailable + ", Requested: " + requestedQuantity);
            }

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(requestedQuantity));
            totalAmount = totalAmount.add(subtotal);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(requestedQuantity)
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .build();

            orderItems.add(orderItem);
        }

        order.setTotalAmount(totalAmount);
        order.setItems(orderItems);
        Order savedOrder = orderRepository.save(order);

        // Clear cart upon successful order reservation
        cart.getItems().clear();
        cartRepository.save(cart);

        log.info("Order {} successfully created and stock reserved until {}",
                savedOrder.getOrderNumber(), savedOrder.getExpiresAt());

        return mapToOrderResponse(savedOrder);
    }

    // READ_COMMITTED so the order is re-read after expireReservationIfDue commits in its own transaction
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public OrderResponse getOrderById(Long id) {
        expireReservationIfDue(id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        return mapToOrderResponse(order);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public OrderResponse getOrderByNumber(String orderNumber) {
        Long id = orderRepository.findIdByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with number: " + orderNumber));
        expireReservationIfDue(id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with number: " + orderNumber));
        return mapToOrderResponse(order);
    }

    /**
     * Releases a reservation the moment its expiry is observed, so a client never sees a stale RESERVED
     * order between scheduler runs. Runs before the order entity is loaded into this transaction.
     */
    private void expireReservationIfDue(Long orderId) {
        if (orderRepository.existsByIdAndStatusAndExpiresAtLessThanEqual(orderId, OrderStatus.RESERVED, LocalDateTime.now())) {
            reservationService.expireReservation(orderId);
        }
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    private Long secondsUntilExpiry(Order order) {
        if (order.getStatus() != OrderStatus.RESERVED || order.getExpiresAt() == null) {
            return null;
        }
        return Math.max(0, Duration.between(LocalDateTime.now(), order.getExpiresAt()).getSeconds());
    }

    public OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .expiresAt(order.getExpiresAt())
                .reservationSecondsRemaining(secondsUntilExpiry(order))
                .items(itemResponses)
                .build();
    }
}
