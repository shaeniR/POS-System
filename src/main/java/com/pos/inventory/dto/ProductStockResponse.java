package com.pos.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductStockResponse {
    private Long productId;
    private String productName;
    /** Units that can still be added to a new checkout. */
    private Integer availableStock;
    /** Units currently held by active (RESERVED) checkouts; returned to available stock on expiry. */
    private Integer reservedStock;
}
