package com.example.operion.module.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSupplierRequest {

    @NotBlank
    private String name;

    private String contact;
}
