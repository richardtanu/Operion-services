package com.example.operion.module.tenant.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.tenant.dto.CreateTenantRequest;
import com.example.operion.module.tenant.dto.PurchaseBudgetRequest;
import com.example.operion.module.tenant.dto.TenantResponse;
import com.example.operion.module.tenant.service.TenantService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService service;

    // BE-12: previously no authorization at all — any authenticated user
    // could create tenants. Tree containment / PRINCIPAL-for-root rules live
    // in TenantService; the GETs are tree-scoped there too.
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TenantResponse> create(
            @Valid @RequestBody CreateTenantRequest request) {

        return ApiResponse.<TenantResponse>builder()
                .success(true)
                .message("Tenant created")
                .data(service.create(request))
                .build();
    }

    // BE-14: the store picker: caller's own tenant + every descendant. Declared
    // before /{id}; Spring prefers the literal path either way.
    @GetMapping("/tree")
    public ApiResponse<List<TenantResponse>> getTree() {

        return ApiResponse.<List<TenantResponse>>builder()
                .success(true)
                .message("Tenant tree fetched")
                .data(service.getTree())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<TenantResponse> getById(
            @PathVariable UUID id) {

        return ApiResponse.<TenantResponse>builder()
                .success(true)
                .message("Tenant fetched")
                .data(service.getById(id))
                .build();
    }

    /** BE-16: the store's own Owner sets or clears ({@code amount: null}) its monthly budget. */
    @PatchMapping("/{id}/purchase-budget")
    public ApiResponse<TenantResponse> setPurchaseBudget(
            @PathVariable UUID id,
            @RequestBody PurchaseBudgetRequest request) {

        return ApiResponse.<TenantResponse>builder()
                .success(true)
                .message("Purchase budget updated")
                .data(service.setPurchaseBudget(id, request.getAmount()))
                .build();
    }

    @GetMapping("/{id}/children")
    public ApiResponse<List<TenantResponse>> getChildren(
            @PathVariable UUID id) {

        return ApiResponse.<List<TenantResponse>>builder()
                .success(true)
                .message("Tenant children fetched")
                .data(service.getChildren(id))
                .build();
    }
}
