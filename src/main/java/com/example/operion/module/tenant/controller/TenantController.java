package com.example.operion.module.tenant.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.tenant.dto.CreateTenantRequest;
import com.example.operion.module.tenant.dto.TenantResponse;
import com.example.operion.module.tenant.service.TenantService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService service;

    @PostMapping
    public ApiResponse<TenantResponse> create(
            @Valid @RequestBody CreateTenantRequest request) {

        return ApiResponse.<TenantResponse>builder()
                .success(true)
                .message("Tenant created")
                .data(service.create(request))
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
