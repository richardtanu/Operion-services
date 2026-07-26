package com.example.operion.module.partinstance.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScanInRequest {

    @NotNull
    private UUID partId;

    @NotBlank
    private String barcode;

    private String notes;
}
