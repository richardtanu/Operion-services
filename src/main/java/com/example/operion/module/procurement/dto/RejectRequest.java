package com.example.operion.module.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectRequest {

    @NotBlank
    private String reason;
}
