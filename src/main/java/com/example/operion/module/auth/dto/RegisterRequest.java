package com.example.operion.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Email is required")
    private String email;

    // BE-36: the same rule as every new password (BCrypt reads at most 72 bytes)
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    /**
     * Optional: which tenant to register the new user under, by code.
     * Defaults to the calling admin's own tenant if omitted. Must be the
     * admin's tenant or one of its descendants.
     */
    private String tenantCode;

    // BE-12: deliberately no `scope` field. Scope is privilege and is only
    // granted by an ADMIN via PATCH /users/{id}/scope, never self-assigned at
    // registration. A `scope` key in the JSON body is silently ignored.
}

