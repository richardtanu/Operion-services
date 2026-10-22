package com.example.operion.module.part.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.part.dto.CatalogItemResponse;
import com.example.operion.module.part.service.HqCatalogService;

import lombok.RequiredArgsConstructor;

/** BE-21: what HQ sells — the "Order from HQ" item picker. Anyone signed in. */
@RestController
@RequestMapping("/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final HqCatalogService service;

    @GetMapping
    public ApiResponse<List<CatalogItemResponse>> getCatalog() {

        return ApiResponse.<List<CatalogItemResponse>>builder()
                .success(true)
                .message("HQ catalog fetched")
                .data(service.catalog())
                .build();
    }
}
