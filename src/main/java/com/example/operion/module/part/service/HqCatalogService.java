package com.example.operion.module.part.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.CallerRoles;
import com.example.operion.common.security.ScopeContext;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.part.dto.CatalogItemResponse;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.entity.PartType;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.part.repository.PartTypeRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import lombok.RequiredArgsConstructor;

/**
 * BE-21: the HQ catalog. HQ's catalog is the root tenant's own {@link Part}
 * rows (decision (a), "Link"); a store's part points at the HQ row it is
 * through {@code catalogPart}. Stock and history stay on the store's row;
 * "sold by HQ" and "mandatory from HQ" live once, on the HQ row.
 *
 * The rules every caller reads through here:
 * <ul>
 * <li>{@link #catalogEntryOf}: a store part's HQ item, or an HQ part itself.</li>
 * <li>Buy from HQ: only items HQ sells ({@link #storePartForHqOrder}).</li>
 * <li>Buy elsewhere: mandatory items only with HQ as the seller (checked in
 *     RealisasiService with {@link #isMandatoryFromHq}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class HqCatalogService {

    static final String NOT_SOLD = "Only items HQ sells can be ordered from HQ: ";

    private final PartRepository partRepository;

    private final PartCategoryRepository categoryRepository;

    private final PartTypeRepository partTypeRepository;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    /** The HQ item a part is: its linked HQ row, itself when it is an HQ row, else null. */
    public Part catalogEntryOf(Part part) {

        if (part.getCatalogPart() != null) {
            return part.getCatalogPart();
        }

        return part.getTenant().getParent() == null ? part : null;
    }

    public boolean isSoldByHq(Part part) {

        Part entry = catalogEntryOf(part);
        return entry != null && entry.isSoldByHq();
    }

    public boolean isMandatoryFromHq(Part part) {

        Part entry = catalogEntryOf(part);
        return entry != null && entry.isMandatoryFromHq();
    }

    /**
     * Flags and links are HQ's: ADMIN role or PRINCIPAL scope, signed in at
     * the root tenant. A store's ADMIN, Owner or Manager is refused (403).
     */
    public void requireHqCatalogAdmin() {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        boolean atRoot = tenantHierarchyService.getRootTenantId(tenantId).equals(tenantId);

        if (!atRoot || !(CallerRoles.hasAny(Role.ADMIN) || ScopeContext.hasAtLeast(Scope.PRINCIPAL))) {
            throw new AccessDeniedException("Only HQ admins can manage the HQ catalog");
        }
    }

    /** {@code GET /catalog}: what HQ sells, for anyone in the tree. */
    public List<CatalogItemResponse> catalog() {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID rootId = tenantHierarchyService.getRootTenantId(tenantId);
        boolean atRoot = rootId.equals(tenantId);

        return partRepository.findHqCatalog(rootId).stream()
                .map(item -> {
                    Part own = atRoot ? item
                            : partRepository.findByTenantIdAndCatalogPartId(tenantId, item.getId()).orElse(null);

                    return CatalogItemResponse.builder()
                            .id(item.getId())
                            .name(item.getName())
                            .brand(item.getBrand())
                            .categoryName(item.getCategory() != null ? item.getCategory().getName() : null)
                            .partTypeName(item.getPartType() != null ? item.getPartType().getName() : null)
                            .consumable(item.isConsumable())
                            .mandatoryFromHq(item.isMandatoryFromHq())
                            .storePartId(own != null ? own.getId() : null)
                            .storeCurrentStock(own != null ? own.getCurrentStock() : null)
                            .build();
                })
                .toList();
    }

    /**
     * For an order to HQ: the part the order line (and later the receipt's
     * stock) goes on, in the ordering store. {@code partId} may be the store's
     * own part or the HQ catalog item; the first order of an HQ item creates
     * the store's part, linked to it. Anything HQ doesn't sell → 400.
     */
    public Part storePartForHqOrder(UUID partId, UUID storeTenantId) {

        Part own = partRepository.findByIdAndTenantId(partId, storeTenantId).orElse(null);

        if (own != null) {
            if (!isSoldByHq(own)) {
                throw new RuntimeException(NOT_SOLD + own.getName());
            }
            return own;
        }

        UUID rootId = tenantHierarchyService.getRootTenantId(storeTenantId);

        Part item = partRepository.findByIdAndTenantId(partId, rootId)
                .orElseThrow(() -> new RuntimeException("Part not found"));

        if (!item.isSoldByHq() || !Boolean.TRUE.equals(item.getActive()) || Boolean.TRUE.equals(item.getRetired())) {
            throw new RuntimeException(NOT_SOLD + item.getName());
        }

        return partRepository.findByTenantIdAndCatalogPartId(storeTenantId, item.getId())
                .orElseGet(() -> partRepository.save(newStorePart(item, storeTenantId)));
    }

    /**
     * The store's copy of an HQ item: name, brand, lifespan and reorder
     * quantity copied; category and type mapped to the store's same-named ones
     * (created if the store doesn't have them). Stock starts at 0.
     */
    private Part newStorePart(Part item, UUID storeTenantId) {

        Tenant store = tenantRepository.findById(storeTenantId).orElseThrow();

        PartCategory hqCategory = item.getCategory();

        PartCategory category = categoryRepository.findByTenantId(storeTenantId).stream()
                .filter(c -> c.getName().equalsIgnoreCase(hqCategory.getName()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(PartCategory.builder()
                        .tenant(store)
                        .name(hqCategory.getName())
                        .description(hqCategory.getDescription())
                        .consumable(hqCategory.getConsumable())
                        .build()));

        PartType partType = null;

        if (item.getPartType() != null) {

            String typeName = item.getPartType().getName();

            partType = partTypeRepository.findByCategoryIdAndTenantId(category.getId(), storeTenantId).stream()
                    .filter(t -> t.getName() != null && t.getName().equalsIgnoreCase(typeName))
                    .findFirst()
                    .orElseGet(() -> partTypeRepository.save(PartType.builder()
                            .tenant(store)
                            .category(category)
                            .name(typeName)
                            .description(item.getPartType().getDescription())
                            .build()));
        }

        return Part.builder()
                .tenant(store)
                .name(item.getName())
                .brand(item.getBrand())
                .category(category)
                .legacyCategory(category.getName())
                .partType(partType)
                .expectedLifespanDays(item.getExpectedLifespanDays())
                .reorderQuantity(item.getReorderQuantity())
                .minimumStock(0)
                .currentStock(0)
                .catalogPart(item)
                .build();
    }
}
