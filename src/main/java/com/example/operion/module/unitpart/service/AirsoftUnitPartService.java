package com.example.operion.module.unitpart.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.unitpart.dto.AirsoftUnitPartResponse;
import com.example.operion.module.unitpart.dto.InstallPartRequest;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.unitpart.enums.UnitPartStatus;
import com.example.operion.module.unitpart.repository.AirsoftUnitPartRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AirsoftUnitPartService {

  private final AirsoftUnitPartRepository repository;

  private final TenantRepository tenantRepository;

  private final AirsoftUnitRepository airsoftUnitRepository;

  private final PartRepository partRepository;

  public AirsoftUnitPartResponse installPart(
      InstallPartRequest request) {

    UUID tenantId = UUID.fromString(
        TenantContext.getTenantId());

    Tenant tenant = tenantRepository
        .findById(tenantId)
        .orElseThrow();

    AirsoftUnit unit = airsoftUnitRepository
        .findById(request.getAirsoftUnitId())
        .orElseThrow();

    Part part = partRepository
        .findById(request.getPartId())
        .orElseThrow();

    AirsoftUnitPart installedPart = AirsoftUnitPart.builder()
        .tenant(tenant)
        .airsoftUnit(unit)
        .part(part)
        .status(UnitPartStatus.INSTALLED)
        .installedDate(
            request.getInstalledDate())
        .notes(request.getNotes())
        .build();

    AirsoftUnitPart saved = repository.save(installedPart);

    return map(saved);
  }

  public List<AirsoftUnitPartResponse> getInstalledParts(UUID unitId) {

    return repository
        .findByAirsoftUnitIdAndStatus(
            unitId,
            UnitPartStatus.INSTALLED)
        .stream()
        .map(this::map)
        .toList();
  }

  private AirsoftUnitPartResponse map(
      AirsoftUnitPart item) {

    return AirsoftUnitPartResponse.builder()
        .id(item.getId())
        .unitName(
            item.getAirsoftUnit().getName())
        .partName(
            item.getPart().getName())
        .brand(
            item.getPart().getBrand())
        .category(
            item.getPart().getCategory())
        .status(item.getStatus().name())
        .installedDate(
            item.getInstalledDate())
        .notes(item.getNotes())
        .build();
  }
}