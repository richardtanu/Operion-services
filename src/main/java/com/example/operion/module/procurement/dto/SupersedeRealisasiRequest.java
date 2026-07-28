package com.example.operion.module.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupersedeRealisasiRequest {

    @NotBlank(message = "Reason is required")
    private String reason;

    /** The corrected Realisasi data that replaces the superseded one. */
    @Valid
    private CreateRealisasiRequest correction;
}
