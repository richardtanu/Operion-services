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

import lombok.RequiredArgsConstructor;
import jakarta.transaction.Transactional;

@Service
@RequiredArgsConstructor
public class PartTypeService {

    private final PartTypeRepository repository;

    private final PartCategoryRepository categoryRepository;

    private final TenantRepository tenantRepository;

    private final PartRepository partRepository;

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
     * GET ALL
     */

    public List<PartTypeResponse> getAll() {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        return repository
                .findByTenantId(tenantId)

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

        categoryRepository
                .findByIdAndTenantId(
                        categoryId,
                        tenantId)
                .orElseThrow(() -> new RuntimeException(
                        "Category not found"));

        return repository
                .findByCategoryIdAndTenantId(
                        categoryId,
                        tenantId)

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
