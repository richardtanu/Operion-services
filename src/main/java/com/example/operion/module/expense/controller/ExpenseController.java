package com.example.operion.module.expense.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.expense.dto.CreateExpenseRequest;
import com.example.operion.module.expense.dto.ExpenseReasonRequest;
import com.example.operion.module.expense.dto.ExpenseResponse;
import com.example.operion.module.expense.enums.ExpenseCategory;
import com.example.operion.module.expense.enums.ExpensePaidBy;
import com.example.operion.module.expense.enums.ExpenseStatus;
import com.example.operion.module.expense.service.ExpenseService;
import com.example.operion.module.procurement.dto.MarkReimbursedRequest;
import com.example.operion.module.procurement.enums.ReimbursementStatus;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** BE-19 (PROC-10): expenses. Rules live in {@link ExpenseService}. */
@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService service;

    @PostMapping
    public ApiResponse<ExpenseResponse> create(@Valid @RequestBody CreateExpenseRequest request) {

        return ApiResponse.<ExpenseResponse>builder()
                .success(true)
                .message("Expense recorded")
                .data(service.create(request))
                .build();
    }

    /** PROC-10 tabs: {@code category=HQ|BRANCH_SPECIFIC}; Owed = {@code reimbursementStatus=PENDING}. */
    @GetMapping
    public ApiResponse<List<ExpenseResponse>> getAll(
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(required = false) ExpensePaidBy paidBy,
            @RequestParam(required = false) ReimbursementStatus reimbursementStatus,
            @RequestParam(required = false) ExpenseStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ApiResponse.<List<ExpenseResponse>>builder()
                .success(true)
                .message("Expenses fetched")
                .data(service.getAll(category, paidBy, reimbursementStatus, status, from, to))
                .build();
    }

    // literal paths before /{id}
    @PatchMapping("/reimbursements/mark-paid")
    public ApiResponse<List<ExpenseResponse>> markReimbursed(@Valid @RequestBody MarkReimbursedRequest request) {

        return ApiResponse.<List<ExpenseResponse>>builder()
                .success(true)
                .message("Expenses marked paid back")
                .data(service.markReimbursed(request))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<ExpenseResponse> getById(@PathVariable UUID id) {

        return ApiResponse.<ExpenseResponse>builder()
                .success(true)
                .message("Expense fetched")
                .data(service.getById(id))
                .build();
    }

    @PatchMapping("/{id}/void")
    public ApiResponse<ExpenseResponse> voidExpense(
            @PathVariable UUID id,
            @Valid @RequestBody ExpenseReasonRequest request) {

        return ApiResponse.<ExpenseResponse>builder()
                .success(true)
                .message("Expense voided")
                .data(service.voidExpense(id, request.getReason()))
                .build();
    }

    @PatchMapping("/{id}/reimbursement/undo")
    public ApiResponse<ExpenseResponse> undoReimbursement(
            @PathVariable UUID id,
            @Valid @RequestBody ExpenseReasonRequest request) {

        return ApiResponse.<ExpenseResponse>builder()
                .success(true)
                .message("Pay-back undone")
                .data(service.undoReimbursement(id, request.getReason()))
                .build();
    }
}
