package com.example.operion.module.maintenance.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.maintenance.dto.StoreSettingsRequest;
import com.example.operion.module.maintenance.dto.StoreSettingsResponse;
import com.example.operion.module.maintenance.service.StoreSettingsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** BE-38: a store's settings, next to its budget under {@code /tenants/{id}}. */
@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class StoreSettingsController {

    private final StoreSettingsService service;

    @GetMapping("/{id}/settings")
    public ApiResponse<StoreSettingsResponse> get(@PathVariable UUID id) {

        return ApiResponse.<StoreSettingsResponse>builder()
                .success(true)
                .message("Store settings fetched")
                .data(service.get(id))
                .build();
    }

    // the store's own Owner only, checked in the service
    @PatchMapping("/{id}/settings")
    public ApiResponse<StoreSettingsResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody StoreSettingsRequest request) {

        return ApiResponse.<StoreSettingsResponse>builder()
                .success(true)
                .message("Store settings saved")
                .data(service.update(id, request))
                .build();
    }
}
