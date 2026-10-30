package com.example.operion.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** BE-32: {@code PATCH /auth/password}, always the signed-in user's own. */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    // BCrypt reads at most 72 bytes; a longer password would be cut without a word
    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 72, message = "New password must be 8 to 72 characters")
    private String newPassword;
}
