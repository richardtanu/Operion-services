package com.example.operion.module.stockadjustment.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.stockadjustment.dto.CreateStockAdjustmentRequest;
import com.example.operion.module.stockadjustment.dto.RejectStockAdjustmentRequest;
import com.example.operion.module.stockadjustment.dto.StatusHistoryResponse;
import com.example.operion.module.stockadjustment.dto.StockAdjustmentResponse;
import com.example.operion.module.stockadjustment.service.StockAdjustmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/stock-adjustments")
@RequiredArgsConstructor
public class StockAdjustmentController {

    private final StockAdjustmentService service;

    @PostMapping
    public ApiResponse<StockAdjustmentResponse> create(
            @Valid @RequestBody CreateStockAdjustmentRequest request) {

        return ApiResponse.<StockAdjustmentResponse>builder()
                .success(true)
                .message("Stock adjustment requested")
                .data(service.create(request))
                .build();
    }

    @GetMapping
    public ApiResponse<List<StockAdjustmentResponse>> getAll() {

        return ApiResponse.<List<StockAdjustmentResponse>>builder()
                .success(true)
                .message("Stock adjustments fetched")
                .data(service.getAll())
                .build();
    }

    @GetMapping("/pending")
    public ApiResponse<List<StockAdjustmentResponse>> getPending() {

        return ApiResponse.<List<StockAdjustmentResponse>>builder()
                .success(true)
                .message("Pending stock adjustments fetched")
                .data(service.getPending())
                .build();
    }

    @PatchMapping("/{id}/approve")
    public ApiResponse<StockAdjustmentResponse> approve(
            @PathVariable UUID id) {

        return ApiResponse.<StockAdjustmentResponse>builder()
                .success(true)
                .message("Stock adjustment approved")
                .data(service.approve(id))
                .build();
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<StockAdjustmentResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectStockAdjustmentRequest request) {

        return ApiResponse.<StockAdjustmentResponse>builder()
                .success(true)
                .message("Stock adjustment rejected")
                .data(service.reject(id, request))
                .build();
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<StatusHistoryResponse>> getHistory(
            @PathVariable UUID id) {

        return ApiResponse.<List<StatusHistoryResponse>>builder()
                .success(true)
                .message("Stock adjustment history fetched")
                .data(service.getHistory(id))
                .build();
    }
}
