package com.example.operion.module.part.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.part.dto.CreatePartRequest;
import com.example.operion.module.part.dto.CreatePartTypeRequest;
import com.example.operion.module.part.dto.PartTypeResponse;
import com.example.operion.module.part.dto.UpdatePartTypeRequest;
import com.example.operion.module.part.dto.PartResponse;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.entity.PartType;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.part.repository.PartTypeRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import lombok.RequiredArgsConstructor;
import jakarta.transaction.Transactional;

@Service
@RequiredArgsConstructor
public class PartTypeService {

    private final PartTypeRepository repository;

    private final PartCategoryRepository categoryRepository;

    private final TenantRepository tenantRepository;

    private final PartRepository partRepository;

    private final TenantHierarchyService tenantHierarchyService;

    /*
     * CREATE
     */

    @Transactional
    public PartTypeResponse create(
            CreatePartTypeRequest request) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new RuntimeException(
                        "Tenant not found"));

        PartCategory category = categoryRepository
                .findByIdAndTenantId(
                        request.getCategoryId(),
                        tenantId)
                .orElseThrow(() -> new RuntimeException(
                        "Category not found"));

        repository
                .findByTenantIdAndCategoryIdAndName(
                        tenantId,
                        category.getId(),
                        request.getName())
                .ifPresent(existing -> {

                    throw new RuntimeException(
                            "Part Type already exists.");

                });

        PartType partType = PartType.builder()

                .tenant(tenant)

                .category(category)

                .name(request.getName())

                .description(
                        request.getDescription())

                .build();

        PartType saved = repository.save(partType);

        return map(saved);
    }

    /*
     * SEED DEFAULTS
     */

    /**
     * Idempotent, incremental: only creates (category, name) pairs from the
     * canonical default list that this tenant doesn't already have. Safe to
     * call repeatedly — on a brand-new tenant, on an already-seeded one, or
     * after the default list itself has grown — it only ever adds what's
     * missing, never duplicates or touches unrelated/custom part types.
     * Assumes the default categories have already been seeded.
     */
    @Transactional
    public void seedDefaultPartTypes(UUID tenantId) {

        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow();

        PartCategory internalMechanical = requiredCategory(tenantId, "Internal Mechanical");
        PartCategory externalMechanical = requiredCategory(tenantId, "External Mechanical");
        PartCategory electrical = requiredCategory(tenantId, "Electrical & Electronic");
        PartCategory magazine = requiredCategory(tenantId, "Magazine");
        PartCategory consumable = requiredCategory(tenantId, "Consumable");
        PartCategory optics = requiredCategory(tenantId, "Optics & Accessories");

        List<Object[]> defaults = List.of(

                new Object[] { internalMechanical, "Anti-Reversal Latch" },
                new Object[] { internalMechanical, "Bushing" },
                new Object[] { internalMechanical, "Cylinder" },
                new Object[] { internalMechanical, "Cylinder Head" },
                new Object[] { internalMechanical, "Gear Set" },
                new Object[] { internalMechanical, "Gearbox Shell" },
                new Object[] { internalMechanical, "Motor Cage" },
                new Object[] { internalMechanical, "Nozzle" },
                new Object[] { internalMechanical, "Pinion Gear" },
                new Object[] { internalMechanical, "Piston" },
                new Object[] { internalMechanical, "Piston Head" },
                new Object[] { internalMechanical, "Spring" },
                new Object[] { internalMechanical, "Spring Guide" },
                new Object[] { internalMechanical, "Tappet Plate" },
                new Object[] { internalMechanical, "Trigger Assembly" },

                new Object[] { externalMechanical, "Grip" },
                new Object[] { externalMechanical, "Hop-up Bucking" },
                new Object[] { externalMechanical, "Hop-up Unit" },
                new Object[] { externalMechanical, "Inner Barrel" },
                new Object[] { externalMechanical, "Outer Barrel" },
                new Object[] { externalMechanical, "Rail System" },
                new Object[] { externalMechanical, "Receiver (Lower)" },
                new Object[] { externalMechanical, "Receiver (Upper)" },
                new Object[] { externalMechanical, "Stock" },

                new Object[] { electrical, "Battery (LiPo)" },
                new Object[] { electrical, "Battery (NiMH)" },
                new Object[] { electrical, "Charger" },
                new Object[] { electrical, "MOSFET" },
                new Object[] { electrical, "Motor" },
                new Object[] { electrical, "Trigger Switch" },
                new Object[] { electrical, "Wiring Harness" },

                new Object[] { magazine, "Drum Magazine" },
                new Object[] { magazine, "Hi-cap Magazine" },
                new Object[] { magazine, "Low-cap Magazine" },
                new Object[] { magazine, "Mid-cap Magazine" },

                new Object[] { consumable, "BB 0.20g" },
                new Object[] { consumable, "BB 0.25g" },
                new Object[] { consumable, "BB 0.28g" },
                new Object[] { consumable, "CO2 Cartridge 12g" },
                new Object[] { consumable, "Green Gas" },

                new Object[] { optics, "Flashlight" },
                new Object[] { optics, "Foregrip" },
                new Object[] { optics, "Magnified Scope" },
                new Object[] { optics, "Red Dot Sight" },
                new Object[] { optics, "Sling" });

        for (Object[] entry : defaults) {

            PartCategory category = (PartCategory) entry[0];
            String name = (String) entry[1];

            boolean exists = repository
                    .findByTenantIdAndCategoryIdAndName(tenantId, category.getId(), name)
                    .isPresent();

            if (!exists) {
                repository.save(type(tenant, category, name));
            }
        }
    }

    private PartCategory requiredCategory(UUID tenantId, String name) {

        return categoryRepository
                .findByTenantIdAndName(tenantId, name)
                .orElseThrow(() -> new RuntimeException(
                        "Category not found: " + name));
    }

    private PartType type(Tenant tenant, PartCategory category, String name) {

        return PartType.builder()
                .tenant(tenant)
                .category(category)
                .name(name)
                .build();
    }

    /*
     * GET ALL
     */

    public List<PartTypeResponse> getAll() {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return repository
                .findByTenantIdIn(tenantIds)

                .stream()

                .map(this::map)

                .toList();
    }

    /*
     * GET BY CATEGORY
     */

    public List<PartTypeResponse> getByCategory(
            UUID categoryId) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        categoryRepository
                .findByIdAndTenantIdIn(
                        categoryId,
                        tenantIds)
                .orElseThrow(() -> new RuntimeException(
                        "Category not found"));

        return repository
                .findByCategoryIdAndTenantIdIn(
                        categoryId,
                        tenantIds)

                .stream()

                .map(this::map)

                .toList();
    }

    /*
     * UPDATE
     */

    @Transactional
    public PartTypeResponse update(

            UUID id,

            UpdatePartTypeRequest request) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        PartType partType = repository
                .findByIdAndTenantId(
                        id,
                        tenantId)
                .orElseThrow(() -> new RuntimeException(
                        "Part Type not found"));

        PartCategory category = categoryRepository
                .findByIdAndTenantId(
                        request.getCategoryId(),
                        tenantId)
                .orElseThrow(() -> new RuntimeException(
                        "Category not found"));

        repository
                .findByTenantIdAndCategoryIdAndName(
                        tenantId,
                        category.getId(),
                        request.getName())
                .ifPresent(existing -> {

                    if (!existing.getId().equals(id)) {

                        throw new RuntimeException(
                                "Part Type already exists.");
                    }

                });

        partType.setCategory(category);

        partType.setName(request.getName());

        partType.setDescription(
                request.getDescription());

        PartType saved = repository.save(partType);

        return map(saved);
    }

    /*
     * DELETE
     */

    @Transactional
    public void delete(UUID id) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        PartType partType = repository
                .findByIdAndTenantId(
                        id,
                        tenantId)
                .orElseThrow(() -> new RuntimeException("Part Type not found"));

        if (partRepository.existsByPartTypeId(id)) {

            throw new RuntimeException(
                    "Cannot delete Part Type because Parts still exist.");
        }

        repository.delete(partType);
    }

    /*
     * MAPPER
     */

    private PartTypeResponse map(
            PartType partType) {

        return PartTypeResponse

                .builder()

                .id(
                        partType.getId())

                .categoryId(
                        partType
                                .getCategory()
                                .getId())

                .categoryName(
                        partType
                                .getCategory()
                                .getName())

                .name(
                        partType.getName())

                .description(
                        partType.getDescription())

                .build();
    }
}
