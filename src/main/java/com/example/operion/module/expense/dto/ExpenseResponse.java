package com.example.operion.module.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.example.operion.module.expense.enums.ExpenseCategory;
import com.example.operion.module.expense.enums.ExpensePaidBy;
import com.example.operion.module.expense.enums.ExpenseStatus;
import com.example.operion.module.procurement.enums.ReimbursementMethod;
import com.example.operion.module.procurement.enums.ReimbursementStatus;

import lombok.Builder;
import lombok.Getter;

/**
 * BE-19 (PROC-10). {@code amount} is null below OWNER, except for the
 * claimant's own PERSONAL expense (BE-18's exception).
 */
@Getter
@Builder
public class ExpenseResponse {

    private UUID id;

    private UUID tenantId;

    private String tenantName;

    private String description;

    private BigDecimal amount;

    private LocalDate expenseDate;

    private ExpenseCategory category;

    private ExpensePaidBy paidBy;

    private ExpenseStatus status;

    private UUID createdById;

    private String createdByName;

    private LocalDateTime createdAt;

    private List<UUID> receiptUploadIds;

    private String voidedByName;

    private LocalDateTime voidedAt;

    private String voidReason;

    private ReimbursementStatus reimbursementStatus;

    private String reimbursedByName;

    private LocalDateTime reimbursedAt;

    private ReimbursementMethod reimbursementMethod;

    private String reimbursementReference;

    private String reimbursementUndoReason;
}
