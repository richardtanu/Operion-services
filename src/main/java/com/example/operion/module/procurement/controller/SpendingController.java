package com.example.operion.module.procurement.controller;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.procurement.dto.StoreSpendingResponse;
import com.example.operion.module.procurement.service.SpendingService;

import lombok.RequiredArgsConstructor;

/**
 * BE-16: store spending summary. OWNER+ only (checked in the service, since
 * scope isn't a granted authority). {@code month} defaults to this month;
 * {@code tenantId} narrows HQ's one-row-per-store answer to one store.
 */
@RestController
@RequestMapping("/procurement/spending")
@RequiredArgsConstructor
public class SpendingController {

    private final SpendingService service;

    @GetMapping
    public ApiResponse<List<StoreSpendingResponse>> getSpending(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) UUID tenantId) {

        return ApiResponse.<List<StoreSpendingResponse>>builder()
                .success(true)
                .message("Spending fetched")
                .data(service.getSpending(month, tenantId))
                .build();
    }
}
