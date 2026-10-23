package com.example.operion.module.expense.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.expense.enums.ExpenseCategory;
import com.example.operion.module.expense.enums.ExpensePaidBy;
import com.example.operion.module.expense.enums.ExpenseStatus;
import com.example.operion.module.procurement.enums.ReimbursementMethod;
import com.example.operion.module.procurement.enums.ReimbursementStatus;
import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.*;
import lombok.*;

/**
 * BE-19: branch spending that isn't stock. No request, no limits, no stock;
 * receipts are BE-23 uploads attached as ("EXPENSE", id). A PERSONAL expense
 * is paid back by the store Owner under BE-18's rules.
 */
@Entity
@Table(name = "expenses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "paid_by", nullable = false)
    private ExpensePaidBy paidBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExpenseStatus status = ExpenseStatus.ACTIVE;

    /** The claimant: who recorded it (and, if PERSONAL, who paid and is owed). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voided_by")
    private User voidedBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "reimbursement_status", nullable = false)
    private ReimbursementStatus reimbursementStatus;

    /** Who marked it paid back (the store Owner). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reimbursed_by")
    private User reimbursedBy;

    @Column(name = "reimbursed_at")
    private LocalDateTime reimbursedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reimbursement_method", length = 20)
    private ReimbursementMethod reimbursementMethod;

    @Column(name = "reimbursement_reference")
    private String reimbursementReference;

    /** The last "undo paid back" reason. */
    @Column(name = "reimbursement_undo_reason", columnDefinition = "TEXT")
    private String reimbursementUndoReason;

    @PrePersist
    public void prePersist() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
