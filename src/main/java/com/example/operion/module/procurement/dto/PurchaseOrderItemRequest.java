package com.example.operion.module.procurement.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseOrderItemRequest {

    @NotNull
    private UUID partId;

    @Positive
    private Integer quantity;

    @NotNull
    @Positive
    private BigDecimal price;
}
