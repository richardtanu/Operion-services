package com.example.operion.module.barcodeblock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IssueBarcodeBlockRequest {

    @NotBlank(message = "Device ID is required")
    private String deviceId;

    @Min(value = 1, message = "Block size must be at least 1")
    @Max(value = 5000, message = "Block size cannot exceed 5000")
    private Integer blockSize;
}
