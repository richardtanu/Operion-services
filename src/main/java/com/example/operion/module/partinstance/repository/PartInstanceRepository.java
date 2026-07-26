package com.example.operion.module.partinstance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.partinstance.entity.PartInstance;

public interface PartInstanceRepository extends JpaRepository<PartInstance, UUID> {

    Optional<PartInstance> findByBarcodeAndTenantId(String barcode, UUID tenantId);

    boolean existsByBarcode(String barcode);

    List<PartInstance> findByPartIdAndTenantId(UUID partId, UUID tenantId);

    @Query("select pi from PartInstance pi where pi.part.id = :partId and pi.tenant.id in :tenantIds")
    List<PartInstance> findByPartIdAndTenantIdIn(@Param("partId") UUID partId, @Param("tenantIds") List<UUID> tenantIds);

    @Query("select pi from PartInstance pi where pi.tenant.id in :tenantIds")
    List<PartInstance> findByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);
}
