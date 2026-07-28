package com.example.operion.module.procurement.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RealisasiItemRequest {

    @NotNull
    private UUID purchaseRequestAuthorizationItemId;

    @NotNull
    private UUID partId;

    @NotBlank
    private String purchaseUom;

    @NotNull
    @Positive
    private BigDecimal conversionFactor;

    @Positive
    private Integer purchasedQty;

    @NotNull
    @Positive
    private BigDecimal actualUnitPrice;
}
