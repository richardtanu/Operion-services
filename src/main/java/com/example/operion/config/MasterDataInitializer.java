package com.example.operion.config;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.module.part.service.PartCategoryService;
import com.example.operion.module.part.service.PartTypeService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MasterDataInitializer {

  private final PartCategoryService partCategoryService;

  private final PartTypeService partTypeService;

  @Transactional
  public void initialize(UUID tenantId) {

    partCategoryService.seedDefaultCategories(tenantId);

    partTypeService.seedDefaultPartTypes(tenantId);

    // future
    // roleService.seedDefaultRoles(tenantId);
    // settingService.seedDefaultSettings(tenantId);
  }
}
