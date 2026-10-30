package com.example.operion.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** BE-36: {@code PATCH /users/{id}/password}, a temporary password for someone who forgot theirs. */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 72, message = "New password must be 8 to 72 characters")
    private String newPassword;
}
