package com.example.operion.module.procurement.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.procurement.dto.CreatePurchaseOrderRequest;
import com.example.operion.module.procurement.dto.PurchaseOrderResponse;
import com.example.operion.module.procurement.dto.StatusHistoryResponse;
import com.example.operion.module.procurement.service.PurchaseOrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    @PostMapping
    public ApiResponse<PurchaseOrderResponse> create(
            @Valid @RequestBody CreatePurchaseOrderRequest request) {

        return ApiResponse.<PurchaseOrderResponse>builder()
                .success(true)
                .message("Purchase order created")
                .data(service.create(request))
                .build();
    }

    @GetMapping
    public ApiResponse<List<PurchaseOrderResponse>> getAll() {

        return ApiResponse.<List<PurchaseOrderResponse>>builder()
                .success(true)
                .message("Purchase orders fetched")
                .data(service.getAll())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<PurchaseOrderResponse> getById(
            @PathVariable UUID id) {

        return ApiResponse.<PurchaseOrderResponse>builder()
                .success(true)
                .message("Purchase order fetched")
                .data(service.getById(id))
                .build();
    }

    @PatchMapping("/{id}/send")
    public ApiResponse<PurchaseOrderResponse> send(
            @PathVariable UUID id) {

        return ApiResponse.<PurchaseOrderResponse>builder()
                .success(true)
                .message("Purchase order sent to supplier")
                .data(service.sendToSupplier(id))
                .build();
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<PurchaseOrderResponse> cancel(
            @PathVariable UUID id) {

        return ApiResponse.<PurchaseOrderResponse>builder()
                .success(true)
                .message("Purchase order cancelled")
                .data(service.cancel(id))
                .build();
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<StatusHistoryResponse>> getHistory(
            @PathVariable UUID id) {

        return ApiResponse.<List<StatusHistoryResponse>>builder()
                .success(true)
                .message("Purchase order history fetched")
                .data(service.getHistory(id))
                .build();
    }
}
