package com.example.operion.module.stockadjustment.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class StockAdjustmentResponse {

    private UUID id;

    private UUID partId;

    private String partName;

    private Integer quantity;

    private String reason;

    private String status;

    private UUID requestedById;

    private String requestedByName;

    private LocalDateTime createdAt;
}
