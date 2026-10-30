package com.example.operion.module.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** BE-33: {@code PATCH /users/{id}/active}. A reason is required to switch someone off. */
@Data
public class UpdateUserActiveRequest {

    @NotNull(message = "active is required")
    private Boolean active;

    private String reason;
}
