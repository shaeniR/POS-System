package com.pos.inventory.controller;

import com.pos.inventory.dto.PaymentRequest;
import com.pos.inventory.dto.PaymentResponse;
import com.pos.inventory.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders/{orderId}/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Pays for a RESERVED order through the mock gateway.
     * Body (optional): {"simulatedOutcome": "SUCCESS" | "FAILURE" | "TIMEOUT"}.
     * Send an Idempotency-Key header to have retries of the same payment rejected as duplicates.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> pay(
            @PathVariable Long orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) PaymentRequest request) {
        PaymentResponse response = paymentService.processPayment(orderId, request, idempotencyKey);
        return new ResponseEntity<>(response, httpStatusFor(response));
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getPayments(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentsForOrder(orderId));
    }

    private HttpStatus httpStatusFor(PaymentResponse response) {
        return switch (response.getPaymentStatus()) {
            case SUCCESS -> HttpStatus.OK;
            case FAILED -> HttpStatus.PAYMENT_REQUIRED;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case REFUNDED, PROCESSING -> HttpStatus.CONFLICT;
        };
    }
}
