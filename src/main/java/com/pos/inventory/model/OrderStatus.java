package com.pos.inventory.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


public enum OrderStatus {
    /** Being created; stock not yet reserved. Only exists inside the checkout transaction. */
    PENDING,
    /** Stock reserved until {@code expiresAt}, awaiting payment. */
    RESERVED,
    /** Payment succeeded; stock permanently allocated. */
    PAID,
    /** Cancelled by the user; stock restored (and any payment refunded). */
    CANCELLED,
    /** Reservation ran out before payment completed, or the gateway timed out; stock restored. */
    EXPIRED,
    /** Payment was declined; stock restored. */
    FAILED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PENDING, EnumSet.of(RESERVED, FAILED, CANCELLED));
        ALLOWED_TRANSITIONS.put(RESERVED, EnumSet.of(PAID, CANCELLED, EXPIRED, FAILED));
        ALLOWED_TRANSITIONS.put(PAID, EnumSet.of(CANCELLED));
        ALLOWED_TRANSITIONS.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(EXPIRED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(FAILED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public Set<OrderStatus> allowedTransitions() {
        return Collections.unmodifiableSet(ALLOWED_TRANSITIONS.get(this));
    }

    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }

    /** Whether an order in this status has its items' quantities deducted from product stock. */
    public boolean holdsStock() {
        return this == RESERVED || this == PAID;
    }
}
