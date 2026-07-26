package com.example.operion.module.tenant.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.tenant.entity.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByCode(String code);

    List<Tenant> findByParentId(UUID parentId);

    @Query("select t.parent.id from Tenant t where t.id = :id")
    Optional<UUID> findParentIdById(@Param("id") UUID id);
}
