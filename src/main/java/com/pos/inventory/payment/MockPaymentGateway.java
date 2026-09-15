package com.pos.inventory.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simulated external payment gateway.
 * <p>
 * SUCCESS and FAILURE respond after a short network latency. TIMEOUT hangs for longer than the caller's
 * timeout, so the timeout is detected by the caller exactly as it would be with a real, unresponsive gateway.
 */
@Component
@Slf4j
public class MockPaymentGateway {

    @Value("${pos.payment.mock-latency-ms:300}")
    private long latencyMs;

    @Value("${pos.payment.mock-timeout-hang-ms:10000}")
    private long timeoutHangMs;

    public GatewayResult charge(String reference, BigDecimal amount, PaymentOutcome simulatedOutcome) throws InterruptedException {
        log.info("Mock gateway: charging {} for {} (simulating {})", amount, reference, simulatedOutcome);

        return switch (simulatedOutcome) {
            case SUCCESS -> {
                Thread.sleep(latencyMs);
                yield new GatewayResult(PaymentOutcome.SUCCESS, "TXN-" + UUID.randomUUID(), "Payment approved");
            }
            case FAILURE -> {
                Thread.sleep(latencyMs);
                yield new GatewayResult(PaymentOutcome.FAILURE, null, "Payment declined by issuer");
            }
            case TIMEOUT -> {
                // Never answers in time; the caller gives up and interrupts this thread
                Thread.sleep(timeoutHangMs);
                yield new GatewayResult(PaymentOutcome.SUCCESS, "TXN-" + UUID.randomUUID(), "Late approval");
            }
        };
    }

    public void refund(String transactionId) {
        log.info("Mock gateway: refunded transaction {}", transactionId);
    }
}
