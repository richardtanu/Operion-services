package com.example.operion.module.barcodeblock.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.barcodeblock.dto.BarcodeAllocationResponse;
import com.example.operion.module.barcodeblock.dto.IssueBarcodeBlockRequest;
import com.example.operion.module.barcodeblock.service.BarcodeAllocationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/barcode-blocks")
@RequiredArgsConstructor
public class BarcodeAllocationController {

    private final BarcodeAllocationService service;

    @PostMapping
    public ApiResponse<BarcodeAllocationResponse> issue(
            @Valid @RequestBody IssueBarcodeBlockRequest request) {

        return ApiResponse.<BarcodeAllocationResponse>builder()
                .success(true)
                .message("Barcode code-block issued")
                .data(service.issue(request))
                .build();
    }

    @GetMapping
    public ApiResponse<List<BarcodeAllocationResponse>> getAll() {

        return ApiResponse.<List<BarcodeAllocationResponse>>builder()
                .success(true)
                .message("Barcode allocations fetched")
                .data(service.getAll())
                .build();
    }
}
