package com.example.operion.module.procurement.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.procurement.dto.CreateSupplierRequest;
import com.example.operion.module.procurement.dto.SupplierResponse;
import com.example.operion.module.procurement.entity.Supplier;
import com.example.operion.module.procurement.repository.SupplierRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository repository;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    public SupplierResponse create(CreateSupplierRequest request) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        UUID rootTenantId = tenantHierarchyService.getRootTenantId(tenantId);

        if (!rootTenantId.equals(tenantId)) {
            throw new RuntimeException(
                    "Suppliers are managed centrally — only the top-level tenant can create one.");
        }

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        Supplier supplier = Supplier.builder()
                .tenant(tenant)
                .name(request.getName())
                .contact(request.getContact())
                .build();

        return map(repository.save(supplier));
    }

    public List<SupplierResponse> getAll() {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        UUID rootTenantId = tenantHierarchyService.getRootTenantId(tenantId);

        return repository.findByTenantIdOrderByNameAsc(rootTenantId)
                .stream()
                .map(this::map)
                .toList();
    }

    private SupplierResponse map(Supplier supplier) {

        return SupplierResponse.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .contact(supplier.getContact())
                .createdAt(supplier.getCreatedAt())
                .build();
    }
}
