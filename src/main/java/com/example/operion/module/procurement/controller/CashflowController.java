package com.example.operion.module.procurement.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.procurement.dto.CashflowResponse;
import com.example.operion.module.procurement.service.CashflowService;

import lombok.RequiredArgsConstructor;

/**
 * BE-41: every money movement between two dates, for Accounting and OWNER+
 * (checked in the service). {@code from}/{@code to} (yyyy-MM-dd, inclusive)
 * default to this month; {@code tenantId} narrows HQ's answer to one store.
 */
@RestController
@RequestMapping("/procurement/cashflow")
@RequiredArgsConstructor
public class CashflowController {

    private final CashflowService service;

    @GetMapping
    public ApiResponse<CashflowResponse> getCashflow(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID tenantId) {

        return ApiResponse.<CashflowResponse>builder()
                .success(true)
                .message("Cashflow fetched")
                .data(service.getCashflow(from, to, tenantId))
                .build();
    }
}
