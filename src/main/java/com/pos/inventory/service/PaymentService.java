package com.pos.inventory.service;

import com.pos.inventory.dto.PaymentRequest;
import com.pos.inventory.dto.PaymentResponse;
import com.pos.inventory.model.Payment;
import com.pos.inventory.model.PaymentStatus;
import com.pos.inventory.payment.GatewayResult;
import com.pos.inventory.payment.MockPaymentGateway;
import com.pos.inventory.payment.PaymentOutcome;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;

/**
 * Orchestrates a payment: record it (transaction 1), call the gateway with a timeout (no transaction,
 * no locks held), then apply the outcome (transaction 2).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final PaymentTransactionService paymentTransactionService;
    private final MockPaymentGateway paymentGateway;

    private final ExecutorService gatewayExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${pos.payment.gateway-timeout-ms:3000}")
    private long gatewayTimeoutMs;

    public PaymentResponse processPayment(Long orderId, PaymentRequest request, String idempotencyKey) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        Payment payment = paymentTransactionService.startPayment(orderId, key);

        PaymentOutcome simulatedOutcome = (request != null && request.getSimulatedOutcome() != null)
                ? request.getSimulatedOutcome()
                : randomOutcome();
        GatewayResult result = chargeWithTimeout(payment, simulatedOutcome);

        PaymentResponse response = paymentTransactionService.completePayment(orderId, payment.getId(), result);
        if (response.getPaymentStatus() == PaymentStatus.REFUNDED) {
            paymentGateway.refund(result.transactionId());
        }
        return response;
    }

    public List<PaymentResponse> getPaymentsForOrder(Long orderId) {
        return paymentTransactionService.getPaymentsForOrder(orderId);
    }

    private GatewayResult chargeWithTimeout(Payment payment, PaymentOutcome simulatedOutcome) {
        Future<GatewayResult> future = gatewayExecutor.submit(() ->
                paymentGateway.charge("PAY-" + payment.getId(), payment.getAmount(), simulatedOutcome));
        try {
            return future.get(gatewayTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Payment {} timed out after {} ms", payment.getId(), gatewayTimeoutMs);
            return GatewayResult.timeout(gatewayTimeoutMs);
        } catch (ExecutionException e) {
            log.error("Payment {} failed with a gateway error", payment.getId(), e.getCause());
            return new GatewayResult(PaymentOutcome.FAILURE, null, "Payment gateway error: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return new GatewayResult(PaymentOutcome.FAILURE, null, "Payment processing was interrupted");
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String key = idempotencyKey.trim();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalStateException("Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return key;
    }

    private PaymentOutcome randomOutcome() {
        PaymentOutcome[] outcomes = PaymentOutcome.values();
        return outcomes[ThreadLocalRandom.current().nextInt(outcomes.length)];
    }

    @PreDestroy
    void shutdownExecutor() {
        gatewayExecutor.shutdownNow();
    }
}
