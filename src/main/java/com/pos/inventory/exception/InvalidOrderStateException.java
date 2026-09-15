package com.pos.inventory.exception;

/** The requested operation is not allowed for the order's current status. */
public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
