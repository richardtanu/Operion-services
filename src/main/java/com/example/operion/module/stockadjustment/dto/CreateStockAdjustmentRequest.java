package com.example.operion.module.stockadjustment.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateStockAdjustmentRequest {

    @NotNull(message = "Part is required")
    private UUID partId;

    @NotNull(message = "Quantity is required")
    private Integer quantity;

    @NotBlank(message = "Reason is required")
    private String reason;
}
