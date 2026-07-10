package com.example.operion.module.airsoft.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.airsoft.dto.AirsoftUnitOverviewResponse;
import com.example.operion.module.airsoft.dto.AirsoftUnitResponse;
import com.example.operion.module.airsoft.dto.CreateAirsoftUnitRequest;
import com.example.operion.module.airsoft.service.AirsoftUnitOverviewService;
import com.example.operion.module.airsoft.service.AirsoftUnitService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/airsoft-units")
@RequiredArgsConstructor
public class AirsoftUnitController {

        private final AirsoftUnitOverviewService overviewService;
        private final AirsoftUnitService service;

        @PostMapping
        @PreAuthorize("hasAuthority('ROLE_ADMIN')")
        public ResponseEntity<ApiResponse<AirsoftUnitResponse>> create(
                        @Valid @RequestBody CreateAirsoftUnitRequest request) {

                AirsoftUnitResponse unit = service.create(request);

                ApiResponse<AirsoftUnitResponse> response = ApiResponse.<AirsoftUnitResponse>builder()
                                .success(true)
                                .message("Airsoft unit created successfully")
                                .data(unit)
                                .build();

                return ResponseEntity.ok(response);
        }

        @GetMapping
        public ResponseEntity<ApiResponse<List<AirsoftUnitResponse>>> getAll() {
                List<AirsoftUnitResponse> units = service.getAll();

                ApiResponse<List<AirsoftUnitResponse>> response = ApiResponse.<List<AirsoftUnitResponse>>builder()
                                .success(true)
                                .message("Airsoft units fetched successfully")
                                .data(units)
                                .build();

                return ResponseEntity.ok(response);
        }

        @GetMapping("/{id}/overview")
        public ApiResponse<AirsoftUnitOverviewResponse> getOverview(

                        @PathVariable UUID id) {

                return ApiResponse
                                .<AirsoftUnitOverviewResponse>builder()
                                .success(true)
                                .message(
                                                "Unit overview fetched")
                                .data(
                                                overviewService
                                                                .getOverview(id))
                                .build();
        }
}
