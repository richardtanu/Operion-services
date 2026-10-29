package com.example.operion.config;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.module.part.service.PartCategoryService;
import com.example.operion.module.part.service.PartTypeService;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MasterDataInitializer {

  private final PartCategoryService partCategoryService;

  private final PartTypeService partTypeService;

  private final TenantRepository tenantRepository;

  /**
   * BE-29: categories and part types are one list, owned by the root tenant
   * (HQ), so only a root is seeded. A store gets nothing: it reads its root's
   * list.
   */
  @Transactional
  public void initialize(UUID tenantId) {

    Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

    if (tenant.getParent() != null) {
      return;
    }

    partCategoryService.seedDefaultCategories(tenantId);

    partTypeService.seedDefaultPartTypes(tenantId);

    // future
    // roleService.seedDefaultRoles(tenantId);
    // settingService.seedDefaultSettings(tenantId);
  }
}
