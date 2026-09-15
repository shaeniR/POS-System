package com.pos.inventory.payment;

/**
 * Result of a gateway charge as seen by this system.
 *
 * @param outcome       SUCCESS, FAILURE, or TIMEOUT when no response arrived within the configured timeout
 * @param transactionId gateway transaction reference; only present on SUCCESS
 * @param message       human-readable explanation
 */
public record GatewayResult(PaymentOutcome outcome, String transactionId, String message) {

    public static GatewayResult timeout(long timeoutMs) {
        return new GatewayResult(PaymentOutcome.TIMEOUT, null, "Payment gateway did not respond within " + timeoutMs + " ms");
    }
}
