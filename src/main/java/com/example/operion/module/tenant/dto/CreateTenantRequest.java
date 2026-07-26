package com.example.operion.module.tenant.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTenantRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String code;

    private UUID parentId;
}
