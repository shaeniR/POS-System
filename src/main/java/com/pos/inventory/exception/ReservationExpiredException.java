package com.pos.inventory.exception;

/** The order's stock reservation has expired, so it can no longer be paid. */
public class ReservationExpiredException extends RuntimeException {
    public ReservationExpiredException(String message) {
        super(message);
    }
}
