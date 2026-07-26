package com.example.operion.module.tenant.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.config.MasterDataInitializer;
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

    public TenantResponse create(CreateTenantRequest request) {

        Tenant parent = null;

        if (request.getParentId() != null) {

            parent = repository
                    .findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent tenant not found"));
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

    public TenantResponse getById(UUID id) {

        return map(
                repository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Tenant not found")));
    }

    public List<TenantResponse> getChildren(UUID id) {

        return repository.findByParentId(id)
                .stream()
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
