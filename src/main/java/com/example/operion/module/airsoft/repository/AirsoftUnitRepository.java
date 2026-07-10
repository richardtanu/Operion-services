package com.example.operion.module.airsoft.repository;

import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.enums.UnitStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

public interface AirsoftUnitRepository extends JpaRepository<AirsoftUnit, UUID> {

        Long countByTenantId(UUID tenantId);

        Long countByTenantIdAndStatus(
                        UUID tenantId,
                        UnitStatus status);

        List<AirsoftUnit> findAllByTenant_Id(
                        UUID tenantId);

        // long countByTenantIdAndStatus(
        // java.util.UUID tenantId,
        // com.example.operion.module.airsoft.enums.UnitStatus status);

        Optional<AirsoftUnit> findByIdAndTenantId(
                        UUID id,
                        UUID tenantId);
}