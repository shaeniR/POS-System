package com.pos.inventory.model;

public enum PaymentStatus {
    /** Sent to the gateway; outcome not yet recorded. */
    PROCESSING,
    /** Gateway approved the charge; the order is PAID. */
    SUCCESS,
    /** Gateway declined the charge; the order is FAILED and its stock released. */
    FAILED,
    /** Gateway did not respond in time; the reservation is EXPIRED and its stock released. */
    TIMEOUT,
    /** Gateway approved the charge, but the order was no longer reserved, so the charge was refunded. */
    REFUNDED
}
