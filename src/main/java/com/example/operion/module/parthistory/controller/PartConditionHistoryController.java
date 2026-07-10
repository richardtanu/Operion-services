package com.example.operion.module.parthistory.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.inventory.dto.StockMovementResponse;
import com.example.operion.module.inventory.services.PartStockService;
import com.example.operion.module.parthistory.dto.PartConditionHistoryResponse;
import com.example.operion.module.parthistory.service.PartConditionHistoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/part-history")
@RequiredArgsConstructor
public class PartConditionHistoryController {

  private final PartConditionHistoryService service;

  private final PartStockService stockService;

  @GetMapping("/test")
  public String test() {
    return "OK";
  }

  @GetMapping("/unit-part/{unitPartId}")
  public ApiResponse<List<PartConditionHistoryResponse>> getHistory(
      @PathVariable UUID unitPartId) {

    return ApiResponse
        .<List<PartConditionHistoryResponse>>builder()
        .success(true)
        .message("Part history fetched")
        .data(
            service.getHistory(
                unitPartId))
        .build();
  }
}