package com.example.operion.module.expense.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.expense.entity.Expense;
import com.example.operion.module.expense.enums.ExpenseStatus;
import com.example.operion.module.procurement.enums.ReimbursementStatus;

public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    /** BE-16/19: a store's expenses dated in [from, to), in one status. */
    @Query("select e from Expense e where e.tenant.id = :tenantId and e.status = :status"
            + " and e.expenseDate >= :from and e.expenseDate < :to")
    List<Expense> findDatedBetween(@Param("tenantId") UUID tenantId, @Param("status") ExpenseStatus status,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** BE-41: a store's own-money expenses paid back to the claimant in [from, to). */
    @Query("select e from Expense e where e.tenant.id = :tenantId and e.reimbursementStatus = :paidBack"
            + " and e.reimbursedAt >= :from and e.reimbursedAt < :to")
    List<Expense> findPaidBackBetween(@Param("tenantId") UUID tenantId,
            @Param("paidBack") ReimbursementStatus paidBack,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
