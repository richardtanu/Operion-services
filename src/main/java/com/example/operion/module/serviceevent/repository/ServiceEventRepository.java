package com.example.operion.module.serviceevent.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.serviceevent.entity.ServiceEvent;

public interface ServiceEventRepository
                extends JpaRepository<ServiceEvent, UUID> {

        Long countByTenantId(UUID tenantId);

        @Query("select count(s) from ServiceEvent s where s.tenant.id in :tenantIds")
        Long countByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);

        List<ServiceEvent> findByTenantId(UUID tenantId);

        @Query("select s from ServiceEvent s where s.tenant.id in :tenantIds")
        List<ServiceEvent> findByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);

        List<ServiceEvent> findTop5ByTenantIdAndAirsoftUnitIdOrderByServiceDateDesc(
                        UUID tenantId,
                        UUID airsoftUnitId);

        @Query("select s from ServiceEvent s where s.tenant.id in :tenantIds and s.airsoftUnit.id = :unitId order by s.serviceDate desc")
        List<ServiceEvent> findTopByTenantIdInAndAirsoftUnitId(
                        @Param("tenantIds") List<UUID> tenantIds,
                        @Param("unitId") UUID unitId,
                        Pageable pageable);

        List<ServiceEvent> findByAirsoftUnitIdOrderByServiceDateDesc(
                        UUID airsoftUnitId);

        Optional<ServiceEvent> findFirstByTenantIdAndAirsoftUnitIdOrderByServiceDateDesc(
                        UUID tenantId,
                        UUID unitId);

        @Query("select s from ServiceEvent s where s.tenant.id in :tenantIds and s.workOrder.id = :workOrderId order by s.serviceDate desc")
        List<ServiceEvent> findByTenantIdInAndWorkOrderId(
                        @Param("tenantIds") List<UUID> tenantIds,
                        @Param("workOrderId") UUID workOrderId);
}