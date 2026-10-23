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

    /**
     * BE-28: deprecated and ignored. HQ sets the price; the server copies the
     * item's HQ price into the line when the order is drafted and when it's sent.
     * Still accepted so older clients don't break.
     */
    @Deprecated
    private BigDecimal price;
}
