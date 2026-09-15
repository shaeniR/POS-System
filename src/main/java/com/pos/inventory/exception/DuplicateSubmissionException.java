package com.pos.inventory.exception;

/** A payment or order was submitted again for a cart or order that has already been processed. */
public class DuplicateSubmissionException extends RuntimeException {
    public DuplicateSubmissionException(String message) {
        super(message);
    }
}
