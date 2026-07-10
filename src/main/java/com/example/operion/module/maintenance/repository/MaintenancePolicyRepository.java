package com.example.operion.module.maintenance.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.operion.module.maintenance.entity.MaintenancePolicy;

@Repository
public interface MaintenancePolicyRepository extends JpaRepository<MaintenancePolicy, UUID> {

  Optional<MaintenancePolicy> findByTenantId(UUID tenantId);
}
