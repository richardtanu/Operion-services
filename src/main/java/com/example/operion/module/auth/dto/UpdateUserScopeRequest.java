package com.example.operion.module.auth.dto;

import com.example.operion.module.auth.enums.Scope;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserScopeRequest {

    @NotNull(message = "Scope is required")
    private Scope scope;
}
