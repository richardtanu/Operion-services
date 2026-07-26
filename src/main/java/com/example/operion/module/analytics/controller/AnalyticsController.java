package com.example.operion.module.analytics.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.analytics.dto.ConsumablePerformanceResponse;
import com.example.operion.module.analytics.dto.PartDurabilityResponse;
import com.example.operion.module.analytics.service.AnalyticsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

  private final AnalyticsService service;

  @GetMapping("/parts/durability")
  public ApiResponse<List<PartDurabilityResponse>> getPartDurability() {

    return ApiResponse
        .<List<PartDurabilityResponse>>builder()
        .success(true)
        .message("Part durability analytics")
        .data(service.getPartDurability())
        .build();
  }

  @GetMapping("/consumables/performance")
  public ApiResponse<List<ConsumablePerformanceResponse>> getConsumablePerformance() {

    return ApiResponse
        .<List<ConsumablePerformanceResponse>>builder()
        .success(true)
        .message("Consumable performance analytics")
        .data(service.getConsumablePerformance())
        .build();
  }
}