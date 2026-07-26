package com.example.operion.module.airsoft.repository;

import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.enums.UnitStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

public interface AirsoftUnitRepository extends JpaRepository<AirsoftUnit, UUID> {

        Long countByTenantId(UUID tenantId);

        @Query("select count(u) from AirsoftUnit u where u.tenant.id in :tenantIds")
        Long countByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);

        Long countByTenantIdAndStatus(
                        UUID tenantId,
                        UnitStatus status);

        @Query("select count(u) from AirsoftUnit u where u.tenant.id in :tenantIds and u.status = :status")
        Long countByTenantIdInAndStatus(
                        @Param("tenantIds") List<UUID> tenantIds,
                        @Param("status") UnitStatus status);

        List<AirsoftUnit> findAllByTenant_Id(
                        UUID tenantId);

        @Query("select u from AirsoftUnit u where u.tenant.id in :tenantIds")
        List<AirsoftUnit> findAllByTenant_IdIn(@Param("tenantIds") List<UUID> tenantIds);

        // long countByTenantIdAndStatus(
        // java.util.UUID tenantId,
        // com.example.operion.module.airsoft.enums.UnitStatus status);

        Optional<AirsoftUnit> findByIdAndTenantId(
                        UUID id,
                        UUID tenantId);
}