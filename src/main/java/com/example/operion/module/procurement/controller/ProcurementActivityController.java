package com.example.operion.module.procurement.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.procurement.dto.ProcurementActivityResponse;
import com.example.operion.module.procurement.enums.ProcurementEntityType;
import com.example.operion.module.procurement.service.ProcurementActivityService;

import lombok.RequiredArgsConstructor;

/**
 * BE-07: who requested, approved, authorized, bought and ordered what, across
 * the caller's tenant tree. Access is checked in the service (role OR scope,
 * which {@code @PreAuthorize} can't express — scope isn't a granted authority).
 */
@RestController
@RequestMapping("/procurement/activity")
@RequiredArgsConstructor
public class ProcurementActivityController {

    private final ProcurementActivityService service;

    @GetMapping
    public ApiResponse<List<ProcurementActivityResponse>> getActivity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) ProcurementEntityType type,
            @RequestParam(required = false) Integer limit) {

        return ApiResponse.<List<ProcurementActivityResponse>>builder()
                .success(true)
                .message("Procurement activity fetched")
                .data(service.getActivity(from, to, tenantId, actorId, type, limit))
                .build();
    }
}
