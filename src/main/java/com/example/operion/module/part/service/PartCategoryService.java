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
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.part.repository.PartTypeRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * BE-29: one category list, owned by HQ. Categories exist only on the root
 * tenant; every store reads that list, and only HQ admins change it
 * ({@link HqCatalogService#requireHqCatalogAdmin}). A copy per store drifted:
 * a renamed or re-flagged "consumable" made two stores count the same gas
 * differently.
 */
@Service
@RequiredArgsConstructor
public class PartCategoryService {

    private final TenantRepository tenantRepository;

    private final PartCategoryRepository categoryRepository;

    private final PartTypeRepository partTypeRepository;

    private final PartRepository partRepository;

    private final TenantHierarchyService tenantHierarchyService;

    private final HqCatalogService hqCatalogService;

    @Transactional
    public PartCategoryResponse create(
            CreatePartCategoryRequest request) {

        hqCatalogService.requireHqCatalogAdmin();

        UUID tenantId = rootTenantId();

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
                .consumable(Boolean.TRUE.equals(request.getConsumable()))
                .build();

        return map(categoryRepository.save(category));
    }

    @Transactional
    public PartCategoryResponse update(
            UUID categoryId,
            UpdatePartCategoryRequest request) {

        hqCatalogService.requireHqCatalogAdmin();

        UUID tenantId = rootTenantId();

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

        if (request.getConsumable() != null) {

            category.setConsumable(request.getConsumable());
        }

        return map(categoryRepository.save(category));
    }

    /** HQ's list, whoever asks: a store reads its root's categories, never its own. */
    public List<PartCategoryResponse> getAll() {

        return categoryRepository
                .findByTenantId(rootTenantId())
                .stream()
                .map(this::map)
                .toList();
    }

    /** The caller's root tenant: where the one list lives. */
    private UUID rootTenantId() {

        return tenantHierarchyService.getRootTenantId(
                UUID.fromString(TenantContext.getTenantId()));
    }

    private PartCategoryResponse map(
            PartCategory category) {

        return PartCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .consumable(category.getConsumable())
                .build();
    }

    @Transactional
    public void delete(UUID categoryId) {

        hqCatalogService.requireHqCatalogAdmin();

        UUID tenantId = rootTenantId();

        PartCategory category = categoryRepository
                .findByIdAndTenantId(
                        categoryId,
                        tenantId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        if (partTypeRepository.existsByCategoryId(categoryId)) {

            throw new RuntimeException(
                    "Cannot delete category because it still contains Part Types.");
        }

        // BE-29: every store's items point at HQ's categories now
        if (partRepository.existsByCategoryId(categoryId)) {

            throw new RuntimeException(
                    "Cannot delete category because items still use it.");
        }

        categoryRepository.delete(category);
    }

    /** {@code POST /part-categories/seed}: HQ admins, on HQ's list. */
    @Transactional
    public void seedForCaller() {

        hqCatalogService.requireHqCatalogAdmin();

        seedDefaultCategories(rootTenantId());
    }

    /**
     * Idempotent, incremental: only creates categories from the canonical
     * default list that this tenant doesn't already have (by name). Safe to
     * call repeatedly — on a brand-new tenant, on an already-seeded one, or
     * after the default list itself has grown — it only ever adds what's
     * missing, never duplicates or touches unrelated/custom categories.
     * BE-29: called for root tenants only ({@code MasterDataInitializer}).
     */
    @Transactional
    public void seedDefaultCategories(
            UUID tenantId) {

        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow();

        List<DefaultCategory> defaults = List.of(

                new DefaultCategory("Internal Mechanical",
                        "Gearbox, compression, hop-up, motor and trigger group components", false),

                new DefaultCategory("External Mechanical",
                        "Barrel, receiver, handguard, stock and other body components", false),

                new DefaultCategory("Electrical & Electronic",
                        "MOSFET, ETU, wiring and other electrical components", false),

                new DefaultCategory("Magazine",
                        "Magazine body and internal components", false),

                // The only default category flows through Part.isConsumable() — this is
                // the one place "Consumable" as a name and isConsumable as a fact are
                // still linked. Every other reader goes through the stored flag, not
                // this string (BE-04, OPERION_BE_CHANGE_QUEUE.md).
                new DefaultCategory("Consumable",
                        "Gas, BB and other consumable items", true),

                new DefaultCategory("Optics & Accessories",
                        "Optics, rails, sling, flashlight and other accessories", false));

        for (DefaultCategory entry : defaults) {

            if (categoryRepository.findByTenantIdAndName(tenantId, entry.name()).isEmpty()) {
                categoryRepository.save(category(tenant, entry.name(), entry.description(), entry.consumable()));
            }
        }
    }

    private record DefaultCategory(String name, String description, boolean consumable) {
    }

    private PartCategory category(
            Tenant tenant,
            String name,
            String description,
            boolean consumable) {

        return PartCategory.builder()
                .tenant(tenant)
                .name(name)
                .description(description)
                .consumable(consumable)
                .build();
    }

}