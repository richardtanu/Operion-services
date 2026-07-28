package com.example.operion.module.procurement.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RealisasiItemResponse {

    private UUID id;

    private UUID purchaseRequestAuthorizationItemId;

    private UUID partId;

    private String partName;

    private String purchaseUom;

    private BigDecimal conversionFactor;

    private Integer purchasedQty;

    private BigDecimal actualUnitPrice;

    private BigDecimal allocatedLandedCost;
}
