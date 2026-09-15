package com.pos.inventory.dto;

import com.pos.inventory.payment.PaymentOutcome;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {
    /** Outcome for the mock gateway to simulate. If omitted, one is picked at random. */
    private PaymentOutcome simulatedOutcome;
}
