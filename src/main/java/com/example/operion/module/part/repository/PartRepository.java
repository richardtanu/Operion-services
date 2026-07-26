package com.example.operion.module.part.repository;

import java.util.List;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.part.entity.Part;

public interface PartRepository
        extends JpaRepository<Part, UUID> {

    Optional<Part> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("select p from Part p where p.tenant.id in :tenantIds and p.active = true")
    List<Part> findByTenantIdInAndActiveTrue(@Param("tenantIds") List<UUID> tenantIds);

    @Query("select p from Part p where p.tenant.id in :tenantIds")
    List<Part> findByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);

    @Query("select p from Part p where p.tenant.id in :tenantIds order by p.currentStock asc")
    List<Part> findByTenantIdInOrderByCurrentStockAsc(@Param("tenantIds") List<UUID> tenantIds);

    long countByTenantId(UUID tenantId);

    List<Part> findByTenantId(UUID tenantId);

    List<Part> findByTenantIdOrderByCurrentStockAsc(UUID tenantId);

    boolean existsByPartTypeId(UUID partTypeId);

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