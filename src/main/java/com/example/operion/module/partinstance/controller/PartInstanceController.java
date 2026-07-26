package com.example.operion.module.partinstance.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.partinstance.dto.ExhaustRequest;
import com.example.operion.module.partinstance.dto.PartInstanceResponse;
import com.example.operion.module.partinstance.dto.ScanInRequest;
import com.example.operion.module.partinstance.dto.TakeRequest;
import com.example.operion.module.partinstance.service.PartInstanceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/part-instances")
@RequiredArgsConstructor
public class PartInstanceController {

    private final PartInstanceService service;

    @PostMapping("/scan-in")
    public ApiResponse<PartInstanceResponse> scanIn(
            @Valid @RequestBody ScanInRequest request) {

        return ApiResponse.<PartInstanceResponse>builder()
                .success(true)
                .message("Part instance scanned in")
                .data(service.scanIn(request))
                .build();
    }

    @PostMapping("/take")
    public ApiResponse<PartInstanceResponse> take(
            @Valid @RequestBody TakeRequest request) {

        return ApiResponse.<PartInstanceResponse>builder()
                .success(true)
                .message("Part instance taken")
                .data(service.take(request))
                .build();
    }

    @PostMapping("/exhaust")
    public ApiResponse<PartInstanceResponse> exhaust(
            @Valid @RequestBody ExhaustRequest request) {

        return ApiResponse.<PartInstanceResponse>builder()
                .success(true)
                .message("Part instance exhausted")
                .data(service.exhaust(request))
                .build();
    }

    @GetMapping("/part/{partId}")
    public ApiResponse<List<PartInstanceResponse>> getByPart(
            @PathVariable UUID partId) {

        return ApiResponse.<List<PartInstanceResponse>>builder()
                .success(true)
                .message("Part instances fetched")
                .data(service.getByPart(partId))
                .build();
    }
}
