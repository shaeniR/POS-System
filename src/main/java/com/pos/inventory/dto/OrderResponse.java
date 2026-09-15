package com.pos.inventory.dto;

import com.pos.inventory.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private Long id;
    private String orderNumber;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    /** Seconds left before the stock reservation expires; null unless the order is RESERVED. */
    private Long reservationSecondsRemaining;
    @Builder.Default
    private List<OrderItemResponse> items = new ArrayList<>();
}
