package com.example.operion.module.airsoft.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.airsoft.dto.AirsoftUnitResponse;
import com.example.operion.module.airsoft.dto.CreateAirsoftUnitRequest;
import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.enums.UnitStatus;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AirsoftUnitService {

    private final AirsoftUnitRepository repository;
    private final TenantRepository tenantRepository;

    public AirsoftUnitResponse create(CreateAirsoftUnitRequest request) {

        String tenantId = TenantContext.getTenantId();

        Tenant tenant = tenantRepository.findById(UUID.fromString(tenantId))
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        AirsoftUnit unit = AirsoftUnit.builder()
                .tenant(tenant)
                .serialNumber(request.getSerialNumber())
                .name(request.getName())
                .brand(request.getBrand())
                .model(request.getModel())
                .type(request.getType())
                .purchaseDate(request.getPurchaseDate())
                .notes(request.getNotes())
                .status(UnitStatus.ACTIVE)
                .build();

        AirsoftUnit savedUnit = repository.save(unit);
        return toResponse(savedUnit);
    }

    public List<AirsoftUnitResponse> getAll() {

        String tenantId = TenantContext.getTenantId();

        return repository.findAllByTenant_Id(UUID.fromString(tenantId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AirsoftUnitResponse toResponse(AirsoftUnit unit) {
        return AirsoftUnitResponse.builder()
                .id(unit.getId())
                .serialNumber(unit.getSerialNumber())
                .name(unit.getName())
                .brand(unit.getBrand())
                .model(unit.getModel())
                .type(unit.getType())
                .status(unit.getStatus().name())
                .build();
    }
}
