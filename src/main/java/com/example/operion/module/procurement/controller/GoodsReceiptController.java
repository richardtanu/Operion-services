package com.example.operion.module.procurement.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.procurement.dto.CreateGoodsReceiptRequest;
import com.example.operion.module.procurement.dto.GoodsReceiptResponse;
import com.example.operion.module.procurement.service.GoodsReceiptService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/goods-receipts")
@RequiredArgsConstructor
public class GoodsReceiptController {

    private final GoodsReceiptService service;

    @PostMapping
    public ApiResponse<GoodsReceiptResponse> receive(
            @Valid @RequestBody CreateGoodsReceiptRequest request) {

        return ApiResponse.<GoodsReceiptResponse>builder()
                .success(true)
                .message("Goods receipt recorded")
                .data(service.receive(request))
                .build();
    }

    @GetMapping("/purchase-order/{poId}")
    public ApiResponse<List<GoodsReceiptResponse>> getByPurchaseOrder(
            @PathVariable UUID poId) {

        return ApiResponse.<List<GoodsReceiptResponse>>builder()
                .success(true)
                .message("Goods receipts fetched")
                .data(service.getByPurchaseOrder(poId))
                .build();
    }

    @GetMapping("/realisasi/{realisasiId}")
    public ApiResponse<List<GoodsReceiptResponse>> getByRealisasi(
            @PathVariable UUID realisasiId) {

        return ApiResponse.<List<GoodsReceiptResponse>>builder()
                .success(true)
                .message("Goods receipts fetched")
                .data(service.getByRealisasi(realisasiId))
                .build();
    }
}
