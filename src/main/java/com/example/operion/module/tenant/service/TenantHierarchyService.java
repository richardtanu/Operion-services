package com.example.operion.module.tenant.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

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
     * The top-level (no-parent) tenant above (or equal to) the given tenant.
     */
    public UUID getRootTenantId(UUID tenantId) {

        List<UUID> ancestors = getAncestorTenantIds(tenantId);

        return ancestors.get(ancestors.size() - 1);
    }
}
