package com.example.operion.module.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.tenant.Tenant;

public interface TenantRepository
        extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByCode(String code);
}