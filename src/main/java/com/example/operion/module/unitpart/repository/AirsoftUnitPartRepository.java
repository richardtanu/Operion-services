package com.example.operion.module.unitpart.repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.unitpart.enums.UnitPartStatus;

public interface AirsoftUnitPartRepository
                extends JpaRepository<AirsoftUnitPart, UUID> {

        Long countByTenantId(UUID tenantId);

        Long countByTenantIdAndStatus(
                        UUID tenantId,
                        UnitPartStatus status);

        @Query("select count(a) from AirsoftUnitPart a where a.tenant.id in :tenantIds and a.status = :status")
        Long countByTenantIdInAndStatus(
                        @Param("tenantIds") List<UUID> tenantIds,
                        @Param("status") UnitPartStatus status);

        List<AirsoftUnitPart> findByAirsoftUnitIdAndStatus(
                        UUID airsoftUnitId,
                        UnitPartStatus status);

        List<AirsoftUnitPart> findByAirsoftUnitIdOrderByInstalledDateDesc(
                        UUID airsoftUnitId);

        List<AirsoftUnitPart> findByTenantId(UUID tenantId);

        @Query("select a from AirsoftUnitPart a where a.tenant.id in :tenantIds")
        List<AirsoftUnitPart> findByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);

        List<AirsoftUnitPart> findByAirsoftUnitIdAndTenantId(
                        UUID airsoftUnitId,
                        UUID tenantId);

        List<AirsoftUnitPart> findByAirsoftUnitIdAndTenantIdAndStatus(
                        UUID airsoftUnitId,
                        UUID tenantId,
                        UnitPartStatus status);

        Optional<AirsoftUnitPart> findByIdAndTenantId(
                        UUID id,
                        UUID tenantId);

        boolean existsByAirsoftUnitIdAndPart_Category_NameAndStatus(
                        UUID airsoftUnitId,
                        String categoryName,
                        UnitPartStatus status);

        boolean existsByPartIdAndStatus(
                        UUID partId,
                        UnitPartStatus status);

        boolean existsByAirsoftUnitIdAndPart_PartType_IdAndStatus(
                        UUID unitId,
                        UUID partTypeId,
                        UnitPartStatus status);

        Optional<AirsoftUnitPart> findByPartIdAndTenantIdAndStatus(
                        UUID partId,
                        UUID tenantId,
                        UnitPartStatus status);

        boolean existsByPartIdAndTenantIdAndStatus(
                        UUID partId,
                        UUID tenantId,
                        UnitPartStatus status);
}