package com.example.operion.module.stockadjustment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectStockAdjustmentRequest {

    @NotBlank(message = "Reason is required")
    private String reason;
}
