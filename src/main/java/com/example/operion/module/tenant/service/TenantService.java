package com.example.operion.module.tenant.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.ScopeContext;
import com.example.operion.common.security.TenantContext;
import com.example.operion.config.MasterDataInitializer;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.tenant.dto.CreateTenantRequest;
import com.example.operion.module.tenant.dto.TenantResponse;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository repository;

    private final MasterDataInitializer masterDataInitializer;

    private final TenantHierarchyService tenantHierarchyService;

    /**
     * BE-12: caller must be ADMIN (see {@code TenantController}). A child
     * tenant may only be created under the caller's own tenant tree. A new
     * root tenant is a new customer business, not something an outlet or HQ
     * admin does — it additionally requires {@code PRINCIPAL} scope. No user
     * holds PRINCIPAL today, so in practice new roots arrive via a Flyway
     * seed (as {@code V2__seed_default_tenant.sql} did).
     */
    public TenantResponse create(CreateTenantRequest request) {

        Tenant parent = null;

        if (request.getParentId() != null) {

            tenantHierarchyService.requireWithinCallerTenantTree(request.getParentId());

            parent = repository
                    .findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent tenant not found"));

        } else if (!ScopeContext.hasAtLeast(Scope.PRINCIPAL)) {

            throw new AccessDeniedException("Only Principal-level users can create a root tenant");
        }

        Tenant tenant = Tenant.builder()
                .name(request.getName())
                .code(request.getCode())
                .active(true)
                .parent(parent)
                .build();

        Tenant saved = repository.save(tenant);

        masterDataInitializer.initialize(saved.getId());

        return map(saved);
    }

    // Containment is checked before the lookup so an out-of-tree id answers
    // 403 whether or not it exists — no probing for other tenants' ids.
    public TenantResponse getById(UUID id) {

        tenantHierarchyService.requireWithinCallerTenantTree(id);

        return map(
                repository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Tenant not found")));
    }

    public List<TenantResponse> getChildren(UUID id) {

        tenantHierarchyService.requireWithinCallerTenantTree(id);

        return repository.findByParentId(id)
                .stream()
                .map(this::map)
                .toList();
    }

    /**
     * BE-14: the store picker. The caller's own tenant first, then every
     * descendant (in tree-walk order), so HQ staff can pick a store and a Store
     * Manager sees just their own. Never ancestors or other roots.
     */
    public List<TenantResponse> getTree() {

        List<UUID> ids = tenantHierarchyService.getEffectiveTenantIds(
                UUID.fromString(TenantContext.getTenantId()));

        Map<UUID, Tenant> byId = repository.findAllById(ids).stream()
                .collect(Collectors.toMap(Tenant::getId, t -> t));

        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(this::map)
                .toList();
    }

    private TenantResponse map(Tenant tenant) {

        return TenantResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .code(tenant.getCode())
                .active(tenant.getActive())
                .parentId(tenant.getParent() != null ? tenant.getParent().getId() : null)
                .parentName(tenant.getParent() != null ? tenant.getParent().getName() : null)
                .build();
    }
}
