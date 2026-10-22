package com.example.operion.module.part.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.part.dto.CreatePartRequest;
import com.example.operion.module.part.dto.LinkCatalogPartRequest;
import com.example.operion.module.part.dto.PartResponse;
import com.example.operion.module.part.dto.PartsByCategoryResponse;
import com.example.operion.module.part.dto.RetirePartRequest;
import com.example.operion.module.part.dto.UpdateHqFlagsRequest;
import com.example.operion.module.part.dto.UpdatePartRequest;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.entity.PartType;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.part.repository.PartTypeRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;
import com.example.operion.module.unitpart.enums.UnitPartStatus;
import com.example.operion.module.unitpart.repository.AirsoftUnitPartRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PartService {

        private final PartRepository repository;

        private final TenantRepository tenantRepository;

        private final PartCategoryRepository categoryRepository;

        private final PartTypeRepository partTypeRepository;

        private final AirsoftUnitPartRepository unitPartRepository;

        private final TenantHierarchyService tenantHierarchyService;

        private final HqCatalogService hqCatalogService;

        public PartResponse create(CreatePartRequest request) {

                if (request.getCategoryId() == null) {
                        throw new RuntimeException("Category is required");
                }

                if (request.getPartTypeId() == null) {
                        throw new RuntimeException("Part Type is required");
                }

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                Tenant tenant = tenantRepository
                                .findById(tenantId)
                                .orElseThrow();

                // PartCategory category;

                // if (request.getCategoryId() != null) {
                PartCategory category = categoryRepository
                                .findByIdAndTenantId(
                                                request.getCategoryId(),
                                                tenantId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Category not found"));
                // System.out.println(category);
                // }

                PartType partType = partTypeRepository
                                .findByIdAndTenantId(
                                                request.getPartTypeId(),
                                                tenantId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Part Type not found"));

                if (!partType.getCategory().getId().equals(category.getId())) {

                        throw new RuntimeException(
                                        "Selected Part Type does not belong to selected Category.");

                }
                // else if (request.getCategory() != null
                // && !request.getCategory().isBlank()) {
                // category = categoryRepository
                // .findByTenantIdAndName(
                // tenantId,
                // request.getCategory())
                // .orElseGet(() -> categoryRepository.save(
                // PartCategory.builder()
                // .tenant(tenant)
                // .name(request.getCategory())
                // .build()));
                // // System.out.println(category);
                // }
                // else {
                // throw new RuntimeException("Category is required");
                // }

                Part part = Part.builder()
                                .tenant(tenant)
                                .name(request.getName())
                                .brand(request.getBrand())
                                .category(category)
                                .partType(partType)
                                .legacyCategory(category.getName())
                                .minimumStock(request.getMinimumStock())
                                .reorderQuantity(request.getReorderQuantity())
                                .manualDailyUsage(request.getManualDailyUsage())
                                .manualReorderPoint(request.getManualReorderPoint())
                                .expectedLifespanDays(request.getExpectedLifespanDays())
                                .notes(request.getNotes())
                                .build();

                Part saved = repository.save(part);

                return map(saved);
        }

        public List<PartResponse> getAll() {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

                return repository.findByTenantIdInAndActiveTrue(tenantIds)
                                .stream()
                                .map(this::map)
                                .toList();
        }

        public List<PartsByCategoryResponse> getAllGroupByCategory() {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

                List<Part> parts = repository.findByTenantIdInAndActiveTrue(tenantIds);

                Map<UUID, List<Part>> byCategoryId = parts.stream()
                                .collect(Collectors.groupingBy(
                                                p -> p.getCategory().getId(),
                                                LinkedHashMap::new,
                                                Collectors.toList()));

                return byCategoryId.values()
                                .stream()
                                .map(group -> {

                                        PartCategory category = group.get(0).getCategory();

                                        return PartsByCategoryResponse.builder()
                                                        .categoryId(category.getId())
                                                        .categoryName(category.getName())
                                                        .parts(group.stream().map(this::map).toList())
                                                        .build();
                                })
                                .sorted(Comparator.comparing(
                                                PartsByCategoryResponse::getCategoryName,
                                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                                .toList();
        }

        private PartResponse map(Part part) {

                PartCategory category = part.getCategory();
                PartType partType = part.getPartType();

                return PartResponse.builder()
                                .id(part.getId())
                                .name(part.getName())
                                .brand(part.getBrand())

                                .category(category != null ? category.getName() : null)
                                .categoryId(category != null ? category.getId() : null)
                                .categoryName(category != null ? category.getName() : null)

                                .partTypeId(partType != null ? partType.getId() : null)
                                .partTypeName(partType != null ? partType.getName() : null)
                                .expectedLifespanDays(part.getExpectedLifespanDays())
                                .currentStock(part.getCurrentStock())
                                .minimumStock(part.getMinimumStock())
                                .reorderQuantity(part.getReorderQuantity())
                                .manualDailyUsage(part.getManualDailyUsage())
                                .manualReorderPoint(part.getManualReorderPoint())
                                .notes(part.getNotes())
                                .retired(part.getRetired())
                                .retiredAt(part.getRetiredAt())
                                .catalogPartId(part.getCatalogPart() != null ? part.getCatalogPart().getId() : null)
                                .soldByHq(hqCatalogService.isSoldByHq(part))
                                .mandatoryFromHq(hqCatalogService.isMandatoryFromHq(part))
                                .build();
        }

        /**
         * BE-21: HQ marks one of its own items as sold to stores and/or
         * mandatory (stores must buy it from HQ). HQ admins only; a store's
         * part isn't found here, because the flags live on HQ rows.
         */
        @Transactional
        public PartResponse updateHqFlags(UUID partId, UpdateHqFlagsRequest request) {

                hqCatalogService.requireHqCatalogAdmin();

                UUID tenantId = UUID.fromString(TenantContext.getTenantId());

                Part part = repository
                                .findByIdAndTenantId(partId, tenantId)
                                .orElseThrow(() -> new RuntimeException("Part not found"));

                boolean sold = request.getSoldByHq() != null ? request.getSoldByHq() : part.isSoldByHq();
                boolean mandatory = request.getMandatoryFromHq() != null
                                ? request.getMandatoryFromHq()
                                : part.isMandatoryFromHq();

                if (mandatory && !sold) {
                        throw new RuntimeException(
                                        "An item stores must buy from HQ has to be sold by HQ too");
                }

                part.setSoldByHq(sold);
                part.setMandatoryFromHq(mandatory);

                return map(repository.save(part));
        }

        /**
         * BE-21: say which HQ catalog item an existing store part is, so the
         * store's first order of it doesn't create a second "6mm BBs".
         * HQ admins only; the store part must be in their tree, the item one
         * of HQ's own, and a store links each HQ item at most once.
         */
        @Transactional
        public PartResponse linkToCatalog(UUID partId, LinkCatalogPartRequest request) {

                hqCatalogService.requireHqCatalogAdmin();

                UUID rootId = UUID.fromString(TenantContext.getTenantId());
                List<UUID> tree = tenantHierarchyService.getEffectiveTenantIds(rootId);

                Part part = repository.findById(partId)
                                .filter(p -> tree.contains(p.getTenant().getId()))
                                .filter(p -> !p.getTenant().getId().equals(rootId))
                                .orElseThrow(() -> new RuntimeException("Part not found"));

                if (request.getCatalogPartId() == null) {
                        part.setCatalogPart(null);
                        return map(repository.save(part));
                }

                Part item = repository.findByIdAndTenantId(request.getCatalogPartId(), rootId)
                                .orElseThrow(() -> new RuntimeException("Catalog item not found"));

                repository.findByTenantIdAndCatalogPartId(part.getTenant().getId(), item.getId())
                                .filter(other -> !other.getId().equals(part.getId()))
                                .ifPresent(other -> {
                                        throw new RuntimeException("This store already has an item linked to "
                                                        + item.getName() + ": " + other.getName());
                                });

                part.setCatalogPart(item);

                return map(repository.save(part));
        }

        @Transactional
        public PartResponse update(
                        UUID partId,
                        UpdatePartRequest request) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                Part part = repository
                                .findByIdAndTenantId(
                                                partId,
                                                tenantId)
                                .orElseThrow(() -> new RuntimeException("Part not found"));

                /*
                 * Determine final category
                 */

                PartCategory finalCategory = part.getCategory();

                if (request.getCategoryId() != null) {

                        finalCategory = categoryRepository
                                        .findByIdAndTenantId(
                                                        request.getCategoryId(),
                                                        tenantId)
                                        .orElseThrow(() -> new RuntimeException("Category not found"));
                }

                /*
                 * Determine final part type
                 */

                PartType finalPartType = part.getPartType();

                if (request.getPartTypeId() != null) {

                        finalPartType = partTypeRepository
                                        .findByIdAndTenantId(
                                                        request.getPartTypeId(),
                                                        tenantId)
                                        .orElseThrow(() -> new RuntimeException("Part Type not found"));
                }

                /*
                 * Category changed?
                 * Then PartType MUST also be supplied.
                 */

                if (request.getCategoryId() != null &&
                                request.getPartTypeId() == null) {

                        throw new RuntimeException(
                                        "Changing category requires selecting a compatible Part Type.");
                }

                /*
                 * Validate relationship
                 */

                if (!finalPartType.getCategory()
                                .getId()
                                .equals(finalCategory.getId())) {

                        throw new RuntimeException(
                                        "Selected Part Type does not belong to selected Category.");
                }

                /*
                 * Apply changes
                 */

                part.setCategory(finalCategory);
                part.setLegacyCategory(finalCategory.getName());
                part.setPartType(finalPartType);

                if (request.getName() != null && !request.getName().isBlank())

                {
                        part.setName(request.getName().trim());
                }

                if (request.getManufacturer() != null && !request.getManufacturer().isBlank()) {

                        part.setBrand(request.getManufacturer().trim());
                }

                if (request.getExpectedLifespan() != null) {
                        part.setExpectedLifespanDays(
                                        request.getExpectedLifespan());
                }

                if (request.getMinimumStock() != null) {
                        part.setMinimumStock(
                                        request.getMinimumStock());
                }

                if (request.getReorderQuantity() != null) {
                        part.setReorderQuantity(
                                        request.getReorderQuantity());
                }

                if (request.getManualDailyUsage() != null) {
                        part.setManualDailyUsage(
                                        request.getManualDailyUsage());
                }

                if (request.getManualReorderPoint() != null) {
                        part.setManualReorderPoint(
                                        request.getManualReorderPoint());
                }

                if (request.getNotes() != null) {
                        part.setNotes(request.getNotes().trim());
                }

                return

                map(repository.save(part));
        }

        @Transactional
        public PartResponse retire(
                        UUID partId,
                        RetirePartRequest request) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                Part part = repository
                                .findByIdAndTenantId(
                                                partId,
                                                tenantId)
                                .orElseThrow(() -> new RuntimeException("Part not found"));

                if (part.getCurrentStock() > 0) {

                        throw new RuntimeException(
                                        "Cannot retire part while stock still exists.");
                }

                if (unitPartRepository.existsByPartIdAndStatus(
                                partId,
                                UnitPartStatus.INSTALLED)) {

                        throw new RuntimeException(
                                        "Cannot retire because this part is currently installed.");
                }

                part.setRetired(true);
                part.setRetiredAt(LocalDateTime.now());
                part.setRetirementReason(request.getReason());

                return map(repository.save(part));
        }

        @Transactional
        public PartResponse restore(UUID partId) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                Part part = repository
                                .findByIdAndTenantId(
                                                partId,
                                                tenantId)
                                .orElseThrow(() -> new RuntimeException("Part not found"));

                if (!Boolean.TRUE.equals(part.getRetired())) {

                        throw new RuntimeException("Part is not retired.");
                }

                part.setActive(true);
                part.setRetired(false);
                part.setRetiredAt(null);
                part.setRetirementReason(null);

                return map(repository.save(part));
        }

}
