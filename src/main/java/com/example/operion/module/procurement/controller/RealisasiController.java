package com.example.operion.module.procurement.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.procurement.dto.CreateRealisasiRequest;
import com.example.operion.module.procurement.dto.RealisasiResponse;
import com.example.operion.module.procurement.dto.RejectRealisasiRequest;
import com.example.operion.module.procurement.dto.StatusHistoryResponse;
import com.example.operion.module.procurement.dto.SupersedeRealisasiRequest;
import com.example.operion.module.procurement.service.RealisasiService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/realisasi")
@RequiredArgsConstructor
public class RealisasiController {

    private final RealisasiService service;

    @PostMapping
    public ApiResponse<RealisasiResponse> create(
            @Valid @RequestBody CreateRealisasiRequest request) {

        return ApiResponse.<RealisasiResponse>builder()
                .success(true)
                .message("Realisasi created")
                .data(service.create(request))
                .build();
    }

    @PatchMapping("/{id}/approve")
    public ApiResponse<RealisasiResponse> approveEscalated(@PathVariable UUID id) {

        return ApiResponse.<RealisasiResponse>builder()
                .success(true)
                .message("Realisasi approved")
                .data(service.approveEscalated(id))
                .build();
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<RealisasiResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectRealisasiRequest request) {

        return ApiResponse.<RealisasiResponse>builder()
                .success(true)
                .message("Realisasi marked as failed")
                .data(service.reject(id, request))
                .build();
    }

    @PostMapping("/{id}/supersede")
    public ApiResponse<RealisasiResponse> supersede(
            @PathVariable UUID id,
            @Valid @RequestBody SupersedeRealisasiRequest request) {

        return ApiResponse.<RealisasiResponse>builder()
                .success(true)
                .message("Realisasi superseded")
                .data(service.supersede(id, request))
                .build();
    }

    @GetMapping
    public ApiResponse<List<RealisasiResponse>> getAll() {

        return ApiResponse.<List<RealisasiResponse>>builder()
                .success(true)
                .message("Realisasi records fetched")
                .data(service.getAll())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<RealisasiResponse> getById(@PathVariable UUID id) {

        return ApiResponse.<RealisasiResponse>builder()
                .success(true)
                .message("Realisasi fetched")
                .data(service.getById(id))
                .build();
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<StatusHistoryResponse>> getHistory(@PathVariable UUID id) {

        return ApiResponse.<List<StatusHistoryResponse>>builder()
                .success(true)
                .message("Realisasi history fetched")
                .data(service.getHistory(id))
                .build();
    }
}
