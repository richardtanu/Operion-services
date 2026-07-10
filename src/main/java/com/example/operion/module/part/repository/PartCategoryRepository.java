package com.example.operion.module.part.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.part.entity.PartCategory;

public interface PartCategoryRepository extends JpaRepository<PartCategory, UUID> {
  List<PartCategory> findByTenantId(UUID tenantId);

  Optional<PartCategory> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<PartCategory> findByTenantIdAndName(UUID tenantId, String name);

}
