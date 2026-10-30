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

  /**
   * The signed-in store's settings. BE-38: a store that never saved any (every
   * store but BLITZ_BDG, and every new one) gets the defaults, not an error;
   * before, its nightly unit checks all failed with "not configured".
   */
  public MaintenancePolicy getCurrentPolicy() {

    UUID tenantId = UUID.fromString(
        TenantContext.getTenantId());

    return repository.findByTenantId(tenantId)
        .orElseGet(() -> MaintenancePolicy.builder().build());
  }

}