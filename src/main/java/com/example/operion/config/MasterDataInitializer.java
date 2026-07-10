package com.example.operion.config;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.module.part.service.PartCategoryService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MasterDataInitializer {

  private final PartCategoryService partCategoryService;

  @Transactional
  public void initialize(UUID tenantId) {

    partCategoryService.seedDefaultCategories(tenantId);

    // future
    // roleService.seedDefaultRoles(tenantId);
    // settingService.seedDefaultSettings(tenantId);
  }
}
