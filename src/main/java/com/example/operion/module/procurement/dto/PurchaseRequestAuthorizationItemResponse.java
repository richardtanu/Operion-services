package com.example.operion.module.procurement.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PurchaseRequestAuthorizationItemResponse {

    private UUID id;

    private UUID partId;

    private String partName;

    private Integer authorizedQty;

    private BigDecimal maxValue;

    /** Aggregated from this line's APPROVED Realisasi items only. */
    private Integer purchasedQty;

    private BigDecimal purchasedValue;

    private Integer remainingQty;

    private BigDecimal remainingValue;
}
