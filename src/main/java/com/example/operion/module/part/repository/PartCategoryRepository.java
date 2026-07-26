package com.example.operion.module.part.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.part.entity.PartCategory;

public interface PartCategoryRepository extends JpaRepository<PartCategory, UUID> {
  List<PartCategory> findByTenantId(UUID tenantId);

  @Query("select c from PartCategory c where c.tenant.id in :tenantIds")
  List<PartCategory> findByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);

  @Query("select c from PartCategory c where c.id = :id and c.tenant.id in :tenantIds")
  Optional<PartCategory> findByIdAndTenantIdIn(@Param("id") UUID id, @Param("tenantIds") List<UUID> tenantIds);

  Optional<PartCategory> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<PartCategory> findByTenantIdAndName(UUID tenantId, String name);

}
