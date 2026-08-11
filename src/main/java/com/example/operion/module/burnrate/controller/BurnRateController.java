package com.example.operion.module.burnrate.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.burnrate.dto.BurnRateResponse;
import com.example.operion.module.burnrate.service.BurnRateService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/burn-rate")
@RequiredArgsConstructor
public class BurnRateController {

    private final BurnRateService service;

    @GetMapping
    public ApiResponse<List<BurnRateResponse>> getAll() {

        return ApiResponse.<List<BurnRateResponse>>builder()
                .success(true)
                .message("Burn rate fetched")
                .data(service.getAll())
                .build();
    }

    @GetMapping("/{partId}")
    public ApiResponse<BurnRateResponse> getByPart(@PathVariable UUID partId) {

        return ApiResponse.<BurnRateResponse>builder()
                .success(true)
                .message("Burn rate fetched")
                .data(service.getByPart(partId))
                .build();
    }
}
