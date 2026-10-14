package com.example.operion.module.auth.dto;

import com.example.operion.module.auth.enums.Role;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserRoleRequest {

    @NotNull(message = "Role is required")
    private Role role;
}
