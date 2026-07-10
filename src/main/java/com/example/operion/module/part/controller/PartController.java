package com.example.operion.module.part.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.part.dto.CreatePartRequest;
import com.example.operion.module.part.dto.PartResponse;
import com.example.operion.module.part.dto.RetirePartRequest;
import com.example.operion.module.part.dto.UpdatePartRequest;
import com.example.operion.module.part.service.PartService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/parts")
@RequiredArgsConstructor
public class PartController {

    private final PartService service;

    @PostMapping
    public ApiResponse<PartResponse> create(
            @Valid @RequestBody CreatePartRequest request) {

        return ApiResponse
                .<PartResponse>builder()
                .success(true)
                .message("Part created")
                .data(service.create(request))
                .build();
    }

    @GetMapping
    public ApiResponse<List<PartResponse>> getAll() {

        return ApiResponse
                .<List<PartResponse>>builder()
                .success(true)
                .message("Parts fetched")
                .data(service.getAll())
                .build();
    }

    @PutMapping("/{partId}")
    public ApiResponse<PartResponse> update(
            @PathVariable UUID partId,
            @RequestBody UpdatePartRequest request) {

        return ApiResponse
                .<PartResponse>builder()
                .success(true)
                .message("Part updated")
                .data(
                        service.update(
                                partId,
                                request))
                .build();
    }

    @PatchMapping("/{partId}/retire")
    public ApiResponse<PartResponse> retire(
            @PathVariable UUID partId,
            @Valid @RequestBody RetirePartRequest request) {

        return ApiResponse
                .<PartResponse>builder()
                .success(true)
                .message("Part retired")
                .data(service.retire(partId, request))
                .build();
    }

}
