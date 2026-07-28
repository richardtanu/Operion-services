package com.example.operion.module.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectRealisasiRequest {

    @NotBlank(message = "Reason is required")
    private String reason;
}
