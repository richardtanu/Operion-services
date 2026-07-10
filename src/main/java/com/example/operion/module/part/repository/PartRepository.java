package com.example.operion.module.part.repository;

import java.util.List;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.part.entity.Part;

public interface PartRepository
        extends JpaRepository<Part, UUID> {

    Optional<Part> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    List<Part> findByTenantId(UUID tenantId);

    List<Part> findByTenantIdOrderByCurrentStockAsc(UUID tenantId);

    boolean existsByPartTypeId(UUID partTypeId);

    boolean existsInstalledByPartId(UUID id);

    List<Part> findByTenantIdAndPartTypeId(
            UUID tenantId,
            UUID partTypeId);

    List<Part> findByTenantIdAndPartTypeIdOrderByBrandAsc(
            UUID tenantId,
            UUID partTypeId);

    List<Part> findByTenantIdAndActiveTrue(UUID tenantId);

    Optional<Part> findByIdAndTenantIdAndActiveTrue(
            UUID id,
            UUID tenantId);

}