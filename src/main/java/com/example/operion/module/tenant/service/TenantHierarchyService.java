package com.example.operion.module.tenant.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantHierarchyService {

    private final TenantRepository repository;

    public List<UUID> getEffectiveTenantIds(UUID tenantId) {

        List<UUID> result = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();

        collect(tenantId, result, visited);

        return result;
    }

    private void collect(UUID tenantId, List<UUID> acc, Set<UUID> visited) {

        if (!visited.add(tenantId)) {
            return;
        }

        acc.add(tenantId);

        for (Tenant child : repository.findByParentId(tenantId)) {
            collect(child.getId(), acc, visited);
        }
    }

    /**
     * Self + every ancestor, walking up via {@code parent}. Opposite direction
     * from {@link #getEffectiveTenantIds}, which walks down to descendants.
     */
    public List<UUID> getAncestorTenantIds(UUID tenantId) {

        List<UUID> result = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();

        UUID current = tenantId;

        while (current != null && visited.add(current)) {

            result.add(current);
            current = repository.findParentIdById(current).orElse(null);
        }

        return result;
    }

    /**
     * BE-12: throws {@link AccessDeniedException} (→ 403) unless the target
     * tenant is the caller's own tenant or one of its descendants. Ancestors
     * are deliberately excluded — an outlet admin must not reach upward into
     * HQ. Reads the caller's tenant from the JWT-populated
     * {@link TenantContext}.
     */
    public void requireWithinCallerTenantTree(UUID targetTenantId) {

        UUID callerTenantId = UUID.fromString(TenantContext.getTenantId());

        if (!getEffectiveTenantIds(callerTenantId).contains(targetTenantId)) {
            throw new AccessDeniedException("Tenant is outside your tenant hierarchy");
        }
    }

    /**
     * BE-14 / BE-17: which store a record is for, when HQ staff may act for a
     * store in their tree (file a PR, log a purchase).
     * <ul>
     * <li>{@code requested} null or the caller's own tenant → the caller's tenant.</li>
     * <li>Another tenant and {@code callerMayActForAnotherStore} false → 403.</li>
     * <li>Another tenant outside the caller's tree (own + descendants), or one
     *     that doesn't exist → 400 "Store not found", the same answer either way,
     *     so other roots' ids can't be probed.</li>
     * </ul>
     * Who may act for another store differs per action, so the caller decides.
     */
    public UUID resolveTargetTenant(UUID requested, boolean callerMayActForAnotherStore) {

        UUID callerTenantId = UUID.fromString(TenantContext.getTenantId());

        if (requested == null || requested.equals(callerTenantId)) {
            return callerTenantId;
        }

        if (!callerMayActForAnotherStore) {
            throw new AccessDeniedException("You can only do this for your own store");
        }

        if (!getEffectiveTenantIds(callerTenantId).contains(requested)) {
            throw new RuntimeException("Store not found");
        }

        return requested;
    }

    /**
     * BE-26: the tenants a list read covers. No {@code tenantId}: the caller's
     * tree, as before. With one: that store's subtree, which must be inside the
     * caller's tree (else 400 "Store not found", as {@link #resolveTargetTenant}).
     */
    public List<UUID> readScope(UUID tenantId) {

        return getEffectiveTenantIds(resolveTargetTenant(tenantId, true));
    }

    /**
     * The top-level (no-parent) tenant above (or equal to) the given tenant.
     */
    public UUID getRootTenantId(UUID tenantId) {

        List<UUID> ancestors = getAncestorTenantIds(tenantId);

        return ancestors.get(ancestors.size() - 1);
    }
}
