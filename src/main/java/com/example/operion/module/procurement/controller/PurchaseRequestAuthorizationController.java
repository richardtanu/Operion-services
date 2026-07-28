package com.example.operion.module.procurement.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.procurement.dto.CreatePurchaseRequestAuthorizationRequest;
import com.example.operion.module.procurement.dto.PurchaseRequestAuthorizationResponse;
import com.example.operion.module.procurement.dto.StatusHistoryResponse;
import com.example.operion.module.procurement.service.PurchaseRequestAuthorizationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/purchase-authorizations")
@RequiredArgsConstructor
public class PurchaseRequestAuthorizationController {

    private final PurchaseRequestAuthorizationService service;

    @PostMapping
    public ApiResponse<PurchaseRequestAuthorizationResponse> create(
            @Valid @RequestBody CreatePurchaseRequestAuthorizationRequest request) {

        return ApiResponse.<PurchaseRequestAuthorizationResponse>builder()
                .success(true)
                .message("Purchase request authorization created")
                .data(service.create(request))
                .build();
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<PurchaseRequestAuthorizationResponse> cancel(@PathVariable UUID id) {

        return ApiResponse.<PurchaseRequestAuthorizationResponse>builder()
                .success(true)
                .message("Purchase request authorization cancelled")
                .data(service.cancel(id))
                .build();
    }

    @GetMapping
    public ApiResponse<List<PurchaseRequestAuthorizationResponse>> getAll() {

        return ApiResponse.<List<PurchaseRequestAuthorizationResponse>>builder()
                .success(true)
                .message("Purchase request authorizations fetched")
                .data(service.getAll())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<PurchaseRequestAuthorizationResponse> getById(@PathVariable UUID id) {

        return ApiResponse.<PurchaseRequestAuthorizationResponse>builder()
                .success(true)
                .message("Purchase request authorization fetched")
                .data(service.getById(id))
                .build();
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<StatusHistoryResponse>> getHistory(@PathVariable UUID id) {

        return ApiResponse.<List<StatusHistoryResponse>>builder()
                .success(true)
                .message("Purchase request authorization history fetched")
                .data(service.getHistory(id))
                .build();
    }
}
