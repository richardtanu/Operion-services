package com.example.operion.module.maintenance.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.maintenance.entity.MaintenancePolicy;
import com.example.operion.module.maintenance.repository.MaintenancePolicyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MaintenancePolicyService {

  private final MaintenancePolicyRepository repository;

  public MaintenancePolicy getCurrentPolicy() {

    UUID tenantId = UUID.fromString(
        TenantContext.getTenantId());

    return repository.findByTenantId(tenantId)
        .orElseThrow(() -> new RuntimeException(
            "Maintenance Policy not configured."));
  }

}