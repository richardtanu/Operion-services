package com.example.operion.module.workorder.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.workorder.dto.AssignWorkOrderRequest;
import com.example.operion.module.workorder.dto.CreateWorkOrderRequest;
import com.example.operion.module.workorder.dto.UpdateWorkOrderStatusRequest;
import com.example.operion.module.workorder.dto.WorkOrderDashboardResponse;
import com.example.operion.module.workorder.dto.WorkOrderResponse;
import com.example.operion.module.workorder.service.WorkOrderService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

        private final WorkOrderService service;

        @PostMapping
        public ApiResponse<WorkOrderResponse> create(
                        @RequestBody CreateWorkOrderRequest request) {

                return ApiResponse
                                .<WorkOrderResponse>builder()
                                .success(true)
                                .message("Work order created")
                                .data(service.create(request))
                                .build();
        }

        @GetMapping
        public ApiResponse<Page<WorkOrderResponse>> getAll(

                        @RequestParam(defaultValue = "0") int page,

                        @RequestParam(defaultValue = "10") int size) {

                return ApiResponse
                                .<Page<WorkOrderResponse>>builder()
                                .success(true)
                                .message("Work orders fetched")
                                .data(service.getAll(
                                                page,
                                                size))
                                .build();
        }

        @PatchMapping("/{id}/status")
        public ApiResponse<WorkOrderResponse> updateStatus(

                        @PathVariable UUID id,

                        @RequestBody UpdateWorkOrderStatusRequest request) {

                return ApiResponse
                                .<WorkOrderResponse>builder()
                                .success(true)
                                .message("Work order updated")
                                .data(service.updateStatus(
                                                id,
                                                request))
                                .build();
        }

        @PatchMapping("/{id}/complete")
        public ApiResponse<WorkOrderResponse> complete(
                        @PathVariable UUID id) {

                return ApiResponse
                                .<WorkOrderResponse>builder()
                                .success(true)
                                .message("Work order completed")
                                .data(service.complete(id))
                                .build();
        }

        @GetMapping("/unit/{unitId}")
        public ApiResponse<List<WorkOrderResponse>> getByUnit(
                        @PathVariable UUID unitId) {

                return ApiResponse
                                .<List<WorkOrderResponse>>builder()
                                .success(true)
                                .message("Unit work orders fetched")
                                .data(service.getByUnit(
                                                unitId))
                                .build();
        }

        @PatchMapping("/{id}/assign")
        public ApiResponse<WorkOrderResponse> assign(
                        @PathVariable UUID id,
                        @RequestBody AssignWorkOrderRequest request) {

                return ApiResponse
                                .<WorkOrderResponse>builder()
                                .success(true)
                                .message("Work order assigned")
                                .data(
                                                service.assign(
                                                                id,
                                                                request))
                                .build();
        }

        @PatchMapping("/{id}/start")
        public ApiResponse<WorkOrderResponse> start(
                        @PathVariable UUID id) {

                return ApiResponse
                                .<WorkOrderResponse>builder()
                                .success(true)
                                .message("Work order started")
                                .data(
                                                service.start(id))
                                .build();
        }

        @PatchMapping("/{id}/waiting-parts")
        public ApiResponse<WorkOrderResponse> waitingParts(
                        @PathVariable UUID id) {

                return ApiResponse
                                .<WorkOrderResponse>builder()
                                .success(true)
                                .message("Waiting for parts")
                                .data(
                                                service.waitingParts(id))
                                .build();
        }

        @GetMapping("/dashboard")
        public ApiResponse<WorkOrderDashboardResponse> dashboard() {

                return ApiResponse
                                .<WorkOrderDashboardResponse>builder()
                                .success(true)
                                .message("Dashboard fetched")
                                .data(service.dashboard())
                                .build();
        }
}
