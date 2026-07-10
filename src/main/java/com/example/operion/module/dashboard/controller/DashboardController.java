package com.example.operion.module.dashboard.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.dashboard.dto.OperationalDashboardResponse;
import com.example.operion.module.dashboard.dto.UnitStatusSummaryResponse;
import com.example.operion.module.dashboard.service.DashboardService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardService dashboardService;

  @GetMapping("/unit-status-summary")
  public ApiResponse<UnitStatusSummaryResponse> getUnitStatusSummary() {

    return ApiResponse
        .<UnitStatusSummaryResponse>builder()
        .success(true)
        .message("Dashboard summary fetched")
        .data(dashboardService.getUnitStatusSummary())
        .build();
  }

  @GetMapping("/overview")
  public ApiResponse<OperationalDashboardResponse> getOverview() {

    return ApiResponse
        .<OperationalDashboardResponse>builder()
        .success(true)
        .message(
            "Dashboard overview fetched")
        .data(dashboardService.getOverview())
        .build();
  }
}