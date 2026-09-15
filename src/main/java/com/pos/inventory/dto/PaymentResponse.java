package com.pos.inventory.dto;

import com.pos.inventory.model.OrderStatus;
import com.pos.inventory.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private Long paymentId;
    private Long orderId;
    private String orderNumber;
    private PaymentStatus paymentStatus;
    private OrderStatus orderStatus;
    private BigDecimal amount;
    private String transactionId;
    private String idempotencyKey;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
