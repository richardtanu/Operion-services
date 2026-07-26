package com.example.operion.module.partinstance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExhaustRequest {

    @NotBlank
    private String barcode;

    private String notes;
}
