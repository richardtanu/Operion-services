package com.example.operion.module.maintenance.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.maintenance.dto.CreateMaintenanceScheduleRequest;
import com.example.operion.module.maintenance.dto.MaintenanceEvaluationResult;
import com.example.operion.module.maintenance.dto.MaintenanceRecommendationResponse;
import com.example.operion.module.maintenance.dto.MaintenanceScheduleResponse;
import com.example.operion.module.maintenance.entity.MaintenanceSchedule;
import com.example.operion.module.maintenance.repository.MaintenanceScheduleRepository;
import com.example.operion.module.maintenance.service.MaintenanceRecommendationService;
import com.example.operion.module.maintenance.service.PreventiveMaintenanceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/maintenance")
@RequiredArgsConstructor
public class MaintenanceRecommendationController {

  private final MaintenanceRecommendationService service;
  private final MaintenanceScheduleRepository repository;
  private final PreventiveMaintenanceService preventiveMaintenanceService;

  @GetMapping("/recommendations/{unitId}")
  public ApiResponse<List<MaintenanceRecommendationResponse>> getRecommendations(

      @PathVariable UUID unitId) {

    return ApiResponse
        .<List<MaintenanceRecommendationResponse>>builder()
        .success(true)
        .message(
            "Maintenance recommendations generated")
        .data(service.generate(unitId))
        .build();
  }

  @PostMapping("/create")
  public ApiResponse<MaintenanceScheduleResponse> create(
      @RequestBody CreateMaintenanceScheduleRequest request) {

    return ApiResponse
        .<MaintenanceScheduleResponse>builder()
        .success(true)
        .message("Maintenance schedule created")
        .data(service.create(request))
        .build();
  }

  @GetMapping("/all")
  public ApiResponse<List<MaintenanceScheduleResponse>> getAll() {

    return ApiResponse
        .<List<MaintenanceScheduleResponse>>builder()
        .success(true)
        .message("Maintenance schedules fetched")
        .data(service.getAll())
        .build();
  }

  @GetMapping("/unit/{unitId}")
  public ApiResponse<List<MaintenanceScheduleResponse>> getByUnit(
      @PathVariable UUID unitId) {

    return ApiResponse
        .<List<MaintenanceScheduleResponse>>builder()
        .success(true)
        .message("Maintenance schedules fetched")
        .data(service.getMaintenanceRecommendationByUnit(unitId))
        .build();
  }

  @PostMapping("/evaluate/{unitId}")
  public ApiResponse<MaintenanceEvaluationResult> evaluateUnit(
      @PathVariable UUID unitId) {

    return ApiResponse
        .<MaintenanceEvaluationResult>builder()
        .success(true)
        .message("Unit evaluated")
        .data(preventiveMaintenanceService.evaluateUnit(unitId))
        .build();
  }

  @PostMapping("/process-due")
  public ApiResponse<Integer> processDue() {

    int createdCount = service.processDueSchedules();
    // System.out.println("ini loh: " + createdCount);

    return ApiResponse.<Integer>builder()
        .success(true)
        .message("Schedules processed")
        .data(createdCount)
        .build();
  }
}
