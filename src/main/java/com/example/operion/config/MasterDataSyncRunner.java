package com.example.operion.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Re-runs master-data seeding for every existing tenant on each app start.
 * seedDefaultCategories/seedDefaultPartTypes are idempotent (insert-only-if-
 * missing, by name), so this is how an already-provisioned tenant picks up
 * new defaults added to those lists later, without anyone re-triggering it
 * per tenant by hand.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterDataSyncRunner implements ApplicationRunner {

  private final TenantRepository tenantRepository;

  private final MasterDataInitializer masterDataInitializer;

  @Override
  public void run(ApplicationArguments args) {

    for (Tenant tenant : tenantRepository.findAll()) {

      try {
        masterDataInitializer.initialize(tenant.getId());
      } catch (Exception e) {
        log.warn("Master data sync failed for tenant {}: {}", tenant.getId(), e.getMessage());
      }
    }
  }
}
