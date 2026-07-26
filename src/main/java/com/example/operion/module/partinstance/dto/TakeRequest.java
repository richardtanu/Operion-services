package com.example.operion.module.partinstance.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TakeRequest {

    @NotBlank
    private String barcode;

    @NotNull
    private UUID takenBy;
}
