package com.example.operion.module.procurement.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoodsReceiptItemRequest {

    @NotNull
    private UUID partId;

    @Positive
    private Integer quantity;
}
