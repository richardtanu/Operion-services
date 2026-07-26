package com.example.operion.module.serviceevent.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.serviceevent.dto.CreateServiceEventRequest;
import com.example.operion.module.serviceevent.dto.ServiceEventResponse;
import com.example.operion.module.serviceevent.service.ServiceEventService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/service-events")
@RequiredArgsConstructor
public class ServiceEventController {

  private final ServiceEventService service;

  @PostMapping
  public ApiResponse<ServiceEventResponse> create(
      @RequestBody CreateServiceEventRequest request) {

    return ApiResponse.<ServiceEventResponse>builder()
        .success(true)
        .message("Service event created")
        .data(service.create(request))
        .build();
  }

  @GetMapping
  public ApiResponse<List<ServiceEventResponse>> getAll() {

    return ApiResponse.<List<ServiceEventResponse>>builder()
        .success(true)
        .message("Service events fetched")
        .data(service.getAll())
        .build();
  }

  @GetMapping("/work-order/{workOrderId}")
  public ApiResponse<List<ServiceEventResponse>> getByWorkOrder(
      @PathVariable UUID workOrderId) {

    return ApiResponse.<List<ServiceEventResponse>>builder()
        .success(true)
        .message("Service events fetched")
        .data(service.getByWorkOrder(workOrderId))
        .build();
  }
}