package com.example.operion.module.procurement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;

public interface PurchaseRequestAuthorizationRepository
        extends JpaRepository<PurchaseRequestAuthorization, UUID> {

    Optional<PurchaseRequestAuthorization> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("select pra from PurchaseRequestAuthorization pra where pra.tenant.id in :tenantIds")
    List<PurchaseRequestAuthorization> findByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);
}
