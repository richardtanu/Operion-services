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
import com.example.operion.module.tenant.service.TenantHierarchyService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PartCategoryService {

    private final TenantRepository tenantRepository;

    private final PartCategoryRepository categoryRepository;

    private final PartTypeRepository partTypeRepository;

    private final TenantHierarchyService tenantHierarchyService;

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

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return categoryRepository
                .findByTenantIdIn(tenantIds)
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

        if (partTypeRepository.existsByCategoryId(categoryId)) {

            throw new RuntimeException(
                    "Cannot delete category because it still contains Part Types.");
        }

        categoryRepository.delete(category);
    }

    /**
     * Idempotent, incremental: only creates categories from the canonical
     * default list that this tenant doesn't already have (by name). Safe to
     * call repeatedly — on a brand-new tenant, on an already-seeded one, or
     * after the default list itself has grown — it only ever adds what's
     * missing, never duplicates or touches unrelated/custom categories.
     */
    @Transactional
    public void seedDefaultCategories(
            UUID tenantId) {

        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow();

        List<String[]> defaults = List.of(

                new String[] { "Internal Mechanical",
                        "Gearbox, compression, hop-up, motor and trigger group components" },

                new String[] { "External Mechanical",
                        "Barrel, receiver, handguard, stock and other body components" },

                new String[] { "Electrical & Electronic",
                        "MOSFET, ETU, wiring and other electrical components" },

                new String[] { "Magazine",
                        "Magazine body and internal components" },

                new String[] { "Consumable",
                        "Gas, BB and other consumable items" },

                new String[] { "Optics & Accessories",
                        "Optics, rails, sling, flashlight and other accessories" });

        for (String[] entry : defaults) {

            String name = entry[0];
            String description = entry[1];

            if (categoryRepository.findByTenantIdAndName(tenantId, name).isEmpty()) {
                categoryRepository.save(category(tenant, name, description));
            }
        }
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