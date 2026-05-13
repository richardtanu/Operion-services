package com.example.operion.module.airsoft.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class CreateAirsoftUnitRequest {

    private String serialNumber;
    private String name;
    private String brand;
    private String model;
    private String type;
    private LocalDate purchaseDate;
    private String notes;
}
