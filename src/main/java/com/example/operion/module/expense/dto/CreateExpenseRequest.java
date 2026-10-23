package com.example.operion.module.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.example.operion.module.expense.enums.ExpenseCategory;
import com.example.operion.module.expense.enums.ExpensePaidBy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateExpenseRequest {

    @NotBlank(message = "What was bought is required")
    @Size(max = 255)
    private String description;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be more than 0")
    private BigDecimal amount;

    /** Defaults to today. */
    private LocalDate expenseDate;

    @NotNull(message = "Category is required")
    private ExpenseCategory category;

    @NotNull(message = "Paid by is required")
    private ExpensePaidBy paidBy;

    /** BE-23 uploads, purpose EXPENSE_RECEIPT. At least one when paid personally. */
    private List<UUID> receiptUploadIds;
}
