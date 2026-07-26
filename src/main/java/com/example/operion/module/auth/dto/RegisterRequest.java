package com.example.operion.module.auth.dto;

import com.example.operion.module.auth.enums.Scope;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    /**
     * Optional: which tenant to register under, by code. Defaults to
     * BLITZ_BDG if omitted.
     */
    private String tenantCode;

    /**
     * Optional RBAC scope (SUPERVISOR/MANAGER/OWNER/PRINCIPAL). Defaults to
     * null (no scope) if omitted.
     */
    private Scope scope;
}

