package com.example.operion.module.airsoft.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAirsoftUnitRequest {

    @NotBlank(message = "Serial number is required")
    private String serialNumber;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Brand is required")
    private String brand;

    @NotBlank(message = "Model is required")
    private String model;

    @NotBlank(message = "Type is required")
    private String type;

    private LocalDate purchaseDate;
    private String notes;
}
