package com.example.operion.module.expense.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** BE-19: void an expense, or undo its pay-back. */
@Getter
@Setter
public class ExpenseReasonRequest {

    @NotBlank(message = "Reason is required")
    private String reason;
}
