package com.example.operion.module.dashboard.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.airsoft.enums.UnitStatus;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.dashboard.dto.UnitStatusSummaryResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardService {

  private final AirsoftUnitRepository repository;

  public UnitStatusSummaryResponse getUnitStatusSummary() {

    UUID tenantId = UUID.fromString(
        TenantContext.getTenantId());

    return UnitStatusSummaryResponse.builder()
        .active(repository.countByTenantIdAndStatus(
            tenantId,
            UnitStatus.ACTIVE))

        .maintenance(repository.countByTenantIdAndStatus(
            tenantId,
            UnitStatus.MAINTENANCE))

        .broken(repository.countByTenantIdAndStatus(
            tenantId,
            UnitStatus.BROKEN))

        .upgrade(repository.countByTenantIdAndStatus(
            tenantId,
            UnitStatus.UPGRADE))

        .retired(repository.countByTenantIdAndStatus(
            tenantId,
            UnitStatus.RETIRED))

        .build();
  }
}