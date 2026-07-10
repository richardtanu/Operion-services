package com.example.operion.module.part.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.part.dto.CreatePartCategoryRequest;
import com.example.operion.module.part.dto.PartCategoryResponse;
import com.example.operion.module.part.dto.UpdatePartCategoryRequest;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartTypeRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PartCategoryService {

    private final TenantRepository tenantRepository;

    private final PartCategoryRepository categoryRepository;

    private final PartTypeRepository partTypeRepository;

    @Transactional
    public PartCategoryResponse create(
            CreatePartCategoryRequest request) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow();

        categoryRepository.findByTenantIdAndName(
                tenantId,
                request.getName())
                .ifPresent(c -> {
                    throw new RuntimeException(
                            "Category already exists");
                });

        PartCategory category = PartCategory.builder()
                .tenant(tenant)
                .name(request.getName())
                .description(request.getDescription())
                .build();

        return map(categoryRepository.save(category));
    }

    @Transactional
    public PartCategoryResponse update(
            UUID categoryId,
            UpdatePartCategoryRequest request) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        PartCategory category = categoryRepository
                .findByIdAndTenantId(
                        categoryId,
                        tenantId)
                .orElseThrow(() -> new RuntimeException(
                        "Category not found"));

        if (request.getName() != null &&
                !request.getName().equalsIgnoreCase(category.getName())) {

            categoryRepository.findByTenantIdAndName(
                    tenantId,
                    request.getName())
                    .ifPresent(existing -> {
                        throw new RuntimeException(
                                "Category already exists");
                    });

            category.setName(request.getName());
        }

        if (request.getDescription() != null) {

            category.setDescription(
                    request.getDescription());
        }

        return map(categoryRepository.save(category));
    }

    public List<PartCategoryResponse> getAll() {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        return categoryRepository
                .findByTenantId(tenantId)
                .stream()
                .map(this::map)
                .toList();
    }

    private PartCategoryResponse map(
            PartCategory category) {

        return PartCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .build();
    }

    @Transactional
    public void delete(UUID categoryId) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        PartCategory category = categoryRepository
                .findByIdAndTenantId(
                        categoryId,
                        tenantId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        if (partTypeRepository.existsById(categoryId)) {

            throw new RuntimeException(
                    "Cannot delete category because it still contains Part Types.");
        }

        categoryRepository.delete(category);
    }

    @Transactional
    public void seedDefaultCategories(
            UUID tenantId) {

        if (!categoryRepository.findByTenantId(tenantId).isEmpty()) {
            return;
        }

        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow();

        categoryRepository.saveAll(List.of(

                category(
                        tenant,
                        "Gearbox",
                        "Gearbox shell, gears, bushings, tappet plate, spring guide"),

                category(
                        tenant,
                        "Compression",
                        "Cylinder, piston, cylinder head, nozzle and compression parts"),

                category(
                        tenant,
                        "Hop Up",
                        "Hop-up chamber, bucking, nub and related parts"),

                category(
                        tenant,
                        "Barrel",
                        "Inner barrel, outer barrel and muzzle components"),

                category(
                        tenant,
                        "Trigger",
                        "Trigger assembly and selector components"),

                category(
                        tenant,
                        "Motor",
                        "Motor, motor cage and pinion gear"),

                category(
                        tenant,
                        "Electronics",
                        "MOSFET, ETU, wiring and electrical components"),

                category(
                        tenant,
                        "Magazine",
                        "Magazine body and internal components"),

                category(
                        tenant,
                        "External",
                        "Receiver, handguard, stock, grip and body components"),

                category(
                        tenant,
                        "Accessories",
                        "Optics, rails, sling, flashlight and other accessories")

        ));
    }

    private PartCategory category(
            Tenant tenant,
            String name,
            String description) {

        return PartCategory.builder()
                .tenant(tenant)
                .name(name)
                .description(description)
                .build();
    }

}