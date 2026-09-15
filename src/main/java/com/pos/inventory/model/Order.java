package com.pos.inventory.model;

import com.pos.inventory.exception.InvalidOrderStateException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = @Index(name = "idx_orders_status_expires_at", columnList = "status, expiresAt"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String orderNumber;

    /** Cart this order was created from; used to detect duplicate checkout submissions. */
    @Column(length = 64)
    private String cartId;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    /** Set on creation via the builder; afterwards changed only through {@link #transitionTo}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Setter(AccessLevel.NONE)
    private OrderStatus status;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    /**
     * Changes the status, enforcing the lifecycle defined in {@link OrderStatus}.
     * Stock side effects are applied by {@code OrderLifecycleService}, which should be used instead of calling this directly
     * on persisted orders.
     *
     * @throws InvalidOrderStateException if the transition is not allowed
     */
    public void transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidOrderStateException("Order " + orderNumber + " cannot move from " + status + " to " + target +
                    (status.isTerminal() ? " (" + status + " is a final status)" : "; allowed: " + status.allowedTransitions()));
        }
        this.status = target;
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.expiresAt == null) {
            this.expiresAt = this.createdAt.plusMinutes(5);
        }
    }
}
