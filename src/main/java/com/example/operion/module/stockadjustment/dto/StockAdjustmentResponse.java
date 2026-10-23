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

    /** BE-26: which store the adjustment belongs to. */
    private UUID tenantId;

    private String tenantName;

    /** BE-25: approved by its own requester, the store's only Owner+. */
    private boolean selfApproved;

    private LocalDateTime createdAt;
}
