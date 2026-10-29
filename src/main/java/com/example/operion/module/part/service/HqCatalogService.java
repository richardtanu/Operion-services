package com.example.operion.module.part.service;

import java.math.BigDecimal;
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
import com.example.operion.module.part.repository.PartRepository;
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

    /** BE-28: HQ's price for the HQ item a part is, or null. */
    public BigDecimal hqPriceOf(Part part) {

        Part entry = catalogEntryOf(part);
        return entry != null ? entry.getHqPrice() : null;
    }

    /** BE-28: a price, so Owner+ (BE-11's rule), plus HQ admins on their own catalog. */
    public boolean canSeeHqPrice() {

        return ScopeContext.hasAtLeast(Scope.OWNER) || isHqCatalogAdmin();
    }

    /**
     * Flags and links are HQ's: ADMIN role or PRINCIPAL scope, signed in at
     * the root tenant. A store's ADMIN, Owner or Manager is refused (403).
     */
    public void requireHqCatalogAdmin() {

        if (!isHqCatalogAdmin()) {
            throw new AccessDeniedException("Only HQ admins can manage the HQ catalog");
        }
    }

    public boolean isHqCatalogAdmin() {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        boolean atRoot = tenantHierarchyService.getRootTenantId(tenantId).equals(tenantId);

        return atRoot && (CallerRoles.hasAny(Role.ADMIN) || ScopeContext.hasAtLeast(Scope.PRINCIPAL));
    }

    /** {@code GET /catalog}: what HQ sells, for anyone in the tree. */
    public List<CatalogItemResponse> catalog() {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID rootId = tenantHierarchyService.getRootTenantId(tenantId);
        boolean atRoot = rootId.equals(tenantId);
        boolean canSeePrice = canSeeHqPrice();

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
                            .price(canSeePrice ? item.getHqPrice() : null)
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
     * quantity copied. BE-29: category and type are the HQ item's own (one
     * list for the whole tree, so the store counts it exactly as HQ does); no
     * name matching, no copies. Stock starts at 0.
     */
    private Part newStorePart(Part item, UUID storeTenantId) {

        Tenant store = tenantRepository.findById(storeTenantId).orElseThrow();

        PartCategory category = item.getCategory();

        return Part.builder()
                .tenant(store)
                .name(item.getName())
                .brand(item.getBrand())
                .category(category)
                .legacyCategory(category != null ? category.getName() : null)
                .partType(item.getPartType())
                .expectedLifespanDays(item.getExpectedLifespanDays())
                .reorderQuantity(item.getReorderQuantity())
                .minimumStock(0)
                .currentStock(0)
                .catalogPart(item)
                .build();
    }
}
