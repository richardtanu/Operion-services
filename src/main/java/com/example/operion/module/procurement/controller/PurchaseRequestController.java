package com.example.operion.module.procurement.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.procurement.dto.CreatePurchaseRequestRequest;
import com.example.operion.module.procurement.dto.PurchaseRequestResponse;
import com.example.operion.module.procurement.dto.RejectRequest;
import com.example.operion.module.procurement.dto.StatusHistoryResponse;
import com.example.operion.module.procurement.service.PurchaseRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/purchase-requests")
@RequiredArgsConstructor
public class PurchaseRequestController {

    private final PurchaseRequestService service;

    @PostMapping
    public ApiResponse<PurchaseRequestResponse> create(
            @Valid @RequestBody CreatePurchaseRequestRequest request) {

        return ApiResponse.<PurchaseRequestResponse>builder()
                .success(true)
                .message("Purchase request created")
                .data(service.create(request))
                .build();
    }

    @GetMapping
    public ApiResponse<List<PurchaseRequestResponse>> getAll() {

        return ApiResponse.<List<PurchaseRequestResponse>>builder()
                .success(true)
                .message("Purchase requests fetched")
                .data(service.getAll())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<PurchaseRequestResponse> getById(
            @PathVariable UUID id) {

        return ApiResponse.<PurchaseRequestResponse>builder()
                .success(true)
                .message("Purchase request fetched")
                .data(service.getById(id))
                .build();
    }

    @PatchMapping("/{id}/approve")
    public ApiResponse<PurchaseRequestResponse> approve(
            @PathVariable UUID id) {

        return ApiResponse.<PurchaseRequestResponse>builder()
                .success(true)
                .message("Purchase request approved")
                .data(service.approve(id))
                .build();
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<PurchaseRequestResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectRequest request) {

        return ApiResponse.<PurchaseRequestResponse>builder()
                .success(true)
                .message("Purchase request rejected")
                .data(service.reject(id, request))
                .build();
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<PurchaseRequestResponse> cancel(
            @PathVariable UUID id) {

        return ApiResponse.<PurchaseRequestResponse>builder()
                .success(true)
                .message("Purchase request cancelled")
                .data(service.cancel(id))
                .build();
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<StatusHistoryResponse>> getHistory(
            @PathVariable UUID id) {

        return ApiResponse.<List<StatusHistoryResponse>>builder()
                .success(true)
                .message("Purchase request history fetched")
                .data(service.getHistory(id))
                .build();
    }
}
