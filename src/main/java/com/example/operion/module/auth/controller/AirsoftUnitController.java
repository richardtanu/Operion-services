package com.example.operion.module.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.auth.dto.CreateAirsoftUnitRequest;
import com.example.operion.module.auth.entity.AirsoftUnit;
import com.example.operion.module.auth.service.AirsoftUnitService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/airsoft-units")
@RequiredArgsConstructor
public class AirsoftUnitController {

    private final AirsoftUnitService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public ResponseEntity<ApiResponse<AirsoftUnit>> create(
            @RequestBody CreateAirsoftUnitRequest request
    ) {

        AirsoftUnit unit = service.create(request);

        ApiResponse<AirsoftUnit> response =
                ApiResponse.<AirsoftUnit>builder()
                        .success(true)
                        .message("Airsoft unit created successfully")
                        .data(unit)
                        .build();

        return ResponseEntity.ok(response);
    }
}