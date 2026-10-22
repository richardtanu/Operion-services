package com.example.operion.module.procurement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.procurement.entity.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    @Query("select s from Supplier s where s.tenant.id = :tenantId order by s.name asc")
    List<Supplier> findByTenantIdOrderByNameAsc(@Param("tenantId") UUID tenantId);

    @Query("select s from Supplier s where s.id = :id and s.tenant.id = :tenantId")
    Optional<Supplier> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /** BE-21: the supplier that is HQ itself (at most one per tenant, V8). */
    @Query("select s from Supplier s where s.tenant.id = :tenantId and s.franchiseCentre = true")
    Optional<Supplier> findFranchiseCentre(@Param("tenantId") UUID tenantId);
}
