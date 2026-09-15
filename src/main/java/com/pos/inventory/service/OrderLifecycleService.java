package com.pos.inventory.service;

import com.pos.inventory.model.Order;
import com.pos.inventory.model.OrderItem;
import com.pos.inventory.model.OrderStatus;
import com.pos.inventory.repository.OrderRepository;
import com.pos.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeMap;


@Service
@RequiredArgsConstructor
@Slf4j
public class OrderLifecycleService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    /**
     * @throws com.pos.inventory.exception.InvalidOrderStateException if the transition is not allowed
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void transition(Order order, OrderStatus target) {
        OrderStatus from = order.getStatus();
        boolean restoresStock = from.holdsStock() && !target.holdsStock();

        // Capture quantities before the bulk stock updates clear the persistence context
        Map<Long, Integer> quantityByProductId = restoresStock ? quantitiesByProductId(order) : Map.of();

        order.transitionTo(target);
        orderRepository.saveAndFlush(order);

        // Ascending product ID order matches the lock order used at checkout, avoiding deadlocks
        quantityByProductId.forEach((productId, quantity) -> {
            if (productRepository.restoreStock(productId, quantity) == 0) {
                log.warn("Could not restore {} units to product ID {} for order {}: product no longer exists",
                        quantity, productId, order.getOrderNumber());
            }
        });

        log.info("Order {}: {} -> {}{}", order.getOrderNumber(), from, target, restoresStock ? " (stock restored)" : "");
    }

    private Map<Long, Integer> quantitiesByProductId(Order order) {
        Map<Long, Integer> quantities = new TreeMap<>();
        for (OrderItem item : order.getItems()) {
            quantities.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
        }
        return quantities;
    }
}
