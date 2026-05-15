package com.example.operion.module.unitpart.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.unitpart.dto.AirsoftUnitPartResponse;
import com.example.operion.module.unitpart.dto.InstallPartRequest;
import com.example.operion.module.unitpart.service.AirsoftUnitPartService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/unit-parts")
@RequiredArgsConstructor
public class AirsoftUnitPartController {

  private final AirsoftUnitPartService service;

  @PostMapping("/install")
  public ApiResponse<AirsoftUnitPartResponse> installPart(
      @RequestBody InstallPartRequest request) {

    return ApiResponse
        .<AirsoftUnitPartResponse>builder()
        .success(true)
        .message("Part installed")
        .data(service.installPart(request))
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
}