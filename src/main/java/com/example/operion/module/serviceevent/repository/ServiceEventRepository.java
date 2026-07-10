package com.example.operion.module.serviceevent.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.serviceevent.entity.ServiceEvent;

public interface ServiceEventRepository
                extends JpaRepository<ServiceEvent, UUID> {

        Long countByTenantId(UUID tenantId);

        List<ServiceEvent> findByTenantId(UUID tenantId);

        List<ServiceEvent> findTop5ByTenantIdAndAirsoftUnitIdOrderByServiceDateDesc(
                        UUID tenantId,
                        UUID airsoftUnitId);

        List<ServiceEvent> findByAirsoftUnitIdOrderByServiceDateDesc(
                        UUID airsoftUnitId);

        Optional<ServiceEvent> findFirstByTenantIdAndAirsoftUnitIdOrderByServiceDateDesc(
                        UUID tenantId,
                        UUID unitId);
}