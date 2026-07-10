package com.example.operion.module.unitpart.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.unitpart.dto.AirsoftUnitPartResponse;
import com.example.operion.module.unitpart.dto.InstallPartRequest;
import com.example.operion.module.unitpart.dto.ReplacePartRequest;
import com.example.operion.module.unitpart.dto.UpdatePartConditionRequest;
import com.example.operion.module.unitpart.service.AirsoftUnitPartService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/unit-parts")
@RequiredArgsConstructor
public class AirsoftUnitPartController {

    private final AirsoftUnitPartService service;

    @PostMapping("/install")
    public ApiResponse<AirsoftUnitPartResponse> installPart(
            @Valid @RequestBody InstallPartRequest request) {

        return ApiResponse
                .<AirsoftUnitPartResponse>builder()
                .success(true)
                .message("Part installed")
                .data(service.installPart(request))
                .build();
    }

    // @PostMapping("/replace")
    // public ApiResponse<AirsoftUnitPartResponse> replacePart(
    // @Valid @RequestBody ReplacePartRequest request) {

    // return ApiResponse
    // .<AirsoftUnitPartResponse>builder()
    // .success(true)
    // .message("Part replaced")
    // .data(service.replacePart(request))
    // .build();
    // }
    @PostMapping("/{installedPartId}/replace")
    public ApiResponse<AirsoftUnitPartResponse> replacePart(

            @PathVariable UUID installedPartId,

            @Valid @RequestBody ReplacePartRequest request) {

        return ApiResponse
                .<AirsoftUnitPartResponse>builder()
                .success(true)
                .message("Part replaced successfully")
                .data(
                        service.replacePart(
                                installedPartId,
                                request))
                .build();
    }

    @PatchMapping("/{id}/condition")
    public ApiResponse<AirsoftUnitPartResponse> updateCondition(

            @PathVariable UUID id,

            @RequestBody UpdatePartConditionRequest request) {

        return ApiResponse
                .<AirsoftUnitPartResponse>builder()
                .success(true)
                .message("Part condition updated")
                .data(service.updateCondition(
                        id,
                        request))
                .build();
    }

    @GetMapping("/{unitId}")
    public ApiResponse<List<AirsoftUnitPartResponse>> getInstalledParts(
            @PathVariable UUID unitId) {

        return ApiResponse
                .<List<AirsoftUnitPartResponse>>builder()
                .success(true)
                .message("Installed parts fetched")
                .data(service.getInstalledParts(unitId))
                .build();
    }

    @GetMapping("/history/{unitId}")
    public ApiResponse<List<AirsoftUnitPartResponse>> getHistory(
            @PathVariable UUID unitId) {

        return ApiResponse
                .<List<AirsoftUnitPartResponse>>builder()
                .success(true)
                .message("Part history fetched")
                .data(service.getHistory(unitId))
                .build();
    }
}
