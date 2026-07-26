package com.example.operion.module.part.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.operion.module.part.entity.PartType;

@Repository
public interface PartTypeRepository
                extends JpaRepository<PartType, UUID> {

        Optional<PartType> findByIdAndTenantId(
                        UUID id,
                        UUID tenantId);

        List<PartType> findByTenantId(
                        UUID tenantId);

        @Query("select t from PartType t where t.tenant.id in :tenantIds")
        List<PartType> findByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);

        List<PartType> findByCategoryIdAndTenantId(
                        UUID categoryId,
                        UUID tenantId);

        @Query("select t from PartType t where t.category.id = :categoryId and t.tenant.id in :tenantIds")
        List<PartType> findByCategoryIdAndTenantIdIn(
                        @Param("categoryId") UUID categoryId,
                        @Param("tenantIds") List<UUID> tenantIds);

        Optional<PartType> findByTenantIdAndCategoryIdAndName(
                        UUID tenantId,
                        UUID categoryId,
                        String name);

        boolean existsByCategoryId(UUID categoryId);
}
