package com.example.operion.module.procurement.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.procurement.dto.CreateSupplierRequest;
import com.example.operion.module.procurement.dto.SupplierResponse;
import com.example.operion.module.procurement.service.SupplierService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService service;

    @PostMapping
    public ApiResponse<SupplierResponse> create(
            @Valid @RequestBody CreateSupplierRequest request) {

        return ApiResponse.<SupplierResponse>builder()
                .success(true)
                .message("Supplier created")
                .data(service.create(request))
                .build();
    }

    @GetMapping
    public ApiResponse<List<SupplierResponse>> getAll() {

        return ApiResponse.<List<SupplierResponse>>builder()
                .success(true)
                .message("Suppliers fetched")
                .data(service.getAll())
                .build();
    }
}
