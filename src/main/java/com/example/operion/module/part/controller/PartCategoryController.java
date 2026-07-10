package com.example.operion.module.part.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.inventory.services.PartStockService;
import com.example.operion.module.part.dto.CreatePartCategoryRequest;
import com.example.operion.module.part.dto.PartCategoryResponse;
import com.example.operion.module.part.dto.UpdatePartCategoryRequest;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.part.service.PartCategoryService;
import com.example.operion.module.part.service.PartService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/part-categories")
@RequiredArgsConstructor
public class PartCategoryController {

        private final PartCategoryService service;

        // private final PartService partService;

        private final PartCategoryRepository partCategoryRepository;

        private final PartRepository partRepository;

        @PostMapping
        public ApiResponse<PartCategoryResponse> create(@RequestBody CreatePartCategoryRequest request) {
                return ApiResponse.<PartCategoryResponse>builder()
                                .success(true)
                                .message("Category created")
                                .data(service.create(request))
                                .build();
        }

        @GetMapping
        public ApiResponse<List<PartCategoryResponse>> getAll() {

                return ApiResponse
                                .<List<PartCategoryResponse>>builder()
                                .success(true)
                                .message("Categories fetched")
                                .data(service.getAll())
                                .build();
        }

        @PutMapping("/{id}")
        public PartCategoryResponse update(
                        @PathVariable UUID id,
                        @RequestBody UpdatePartCategoryRequest request) {

                return service.update(id, request);
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<Void> delete(
                        @PathVariable UUID id) {

                service.delete(id);

                return ResponseEntity.noContent().build();
        }

        @PostMapping("/seed")
        public ApiResponse<Void> seed() {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                service.seedDefaultCategories(tenantId);

                return ApiResponse.<Void>builder()
                                .success(true)
                                .message("Default categories created")
                                .build();
        }

        Map<String, String> CATEGORY_MAPPING = Map.ofEntries(

                        Map.entry("Gearbox Shell", "Gearbox"),
                        Map.entry("Gearset", "Gearbox"),
                        Map.entry("Spring", "Gearbox"),
                        Map.entry("Spring Guide", "Gearbox"),
                        Map.entry("Tappet Plate", "Gearbox"),
                        Map.entry("Bushing", "Gearbox"),

                        Map.entry("Cylinder", "Compression"),
                        Map.entry("Cylinder Head", "Compression"),
                        Map.entry("Piston", "Compression"),
                        Map.entry("Piston Head", "Compression"),
                        Map.entry("Nozzle", "Compression"),

                        Map.entry("Hop-Up Chamber", "Hop Up"),
                        Map.entry("Hop-Up Rubber", "Hop Up"),
                        Map.entry("Hopup", "Hop Up"),

                        Map.entry("Inner Barrel", "Barrel"),
                        Map.entry("Outer Barrel", "Barrel"),

                        Map.entry("Trigger", "Trigger"),

                        Map.entry("Motor", "Motor"),
                        Map.entry("Motor Cage", "Motor"),
                        Map.entry("Pinion Gear", "Motor"),

                        Map.entry("MOSFET", "Electronics")

        );
}