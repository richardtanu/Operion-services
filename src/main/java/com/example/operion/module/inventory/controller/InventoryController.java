package com.example.operion.module.inventory.controller;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.inventory.dto.AdjustStockRequest;
import com.example.operion.module.inventory.dto.InventoryDashboardResponse;
import com.example.operion.module.inventory.dto.LowStockResponse;
import com.example.operion.module.inventory.repository.ReceiveStockRequest;
import com.example.operion.module.inventory.services.PartStockService;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

  private final PartStockService stockService;

  @PostMapping("/adjust")
  public ApiResponse<String> adjust(
      @RequestBody AdjustStockRequest request) {

    stockService.manualAdjustment(request);

    return ApiResponse.<String>builder()
        .success(true)
        .message("Stock adjusted")
        .data("OK")
        .build();
  }

  @PostMapping("/receive")
  public ApiResponse<String> receive(

      @RequestBody ReceiveStockRequest request) {

    stockService.receiveStock(request);

    return ApiResponse.<String>builder()
        .success(true)
        .message("Stock received")
        .data("OK")
        .build();
  }

  @GetMapping("/dashboard")
  public ApiResponse<InventoryDashboardResponse> dashboard() {

    return ApiResponse
        .<InventoryDashboardResponse>builder()
        .success(true)
        .message("Inventory dashboard fetched")
        .data(stockService.dashboard())
        .build();
  }

  @GetMapping("/low-stock")
  public ApiResponse<List<LowStockResponse>> lowStock() {

    return ApiResponse
        .<List<LowStockResponse>>builder()
        .success(true)
        .message("Low stock fetched")
        .data(stockService.lowStock())
        .build();
  }

  @GetMapping("/stock-summary")
  public ApiResponse<List<LowStockResponse>> stockSummary() {

    return ApiResponse
        .<List<LowStockResponse>>builder()
        .success(true)
        .message("Stock summary fetched")
        .data(stockService.stockSummary())
        .build();
  }
}