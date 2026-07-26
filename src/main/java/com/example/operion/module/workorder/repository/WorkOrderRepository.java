package com.example.operion.module.workorder.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.workorder.entity.WorkOrder;
import com.example.operion.module.workorder.enums.WorkOrderPriority;
import com.example.operion.module.workorder.enums.WorkOrderStatus;

public interface WorkOrderRepository
                extends JpaRepository<WorkOrder, UUID> {

        Optional<WorkOrder> findByIdAndTenantId(
                        UUID id,
                        UUID tenantId);

        Page<WorkOrder> findByTenantId(
                        UUID tenantId,
                        Pageable pageable);

        @Query("select w from WorkOrder w where w.tenant.id in :tenantIds")
        Page<WorkOrder> findByTenantIdIn(
                        @Param("tenantIds") List<UUID> tenantIds,
                        Pageable pageable);

        List<WorkOrder> findByAirsoftUnitId(
                        UUID unitId);

        Long countByTenantIdAndStatus(
                        UUID tenantId,
                        WorkOrderStatus status);

        @Query("select count(w) from WorkOrder w where w.tenant.id in :tenantIds and w.status = :status")
        Long countByTenantIdInAndStatus(
                        @Param("tenantIds") List<UUID> tenantIds,
                        @Param("status") WorkOrderStatus status);

        boolean existsByAirsoftUnitIdAndStatusIn(
                        UUID airsoftUnitId,
                        List<WorkOrderStatus> statuses);

        Long countByTenantIdAndPriority(
                        UUID tenantId,
                        WorkOrderPriority priority);

        Long countByTenantIdAndPriorityAndStatusNot(
                        UUID tenantId,
                        WorkOrderPriority priority,
                        WorkOrderStatus status);

        @Query("select count(w) from WorkOrder w where w.tenant.id in :tenantIds and w.priority = :priority and w.status <> :status")
        Long countByTenantIdInAndPriorityAndStatusNot(
                        @Param("tenantIds") List<UUID> tenantIds,
                        @Param("priority") WorkOrderPriority priority,
                        @Param("status") WorkOrderStatus status);

        List<WorkOrder> findByAirsoftUnitIdOrderByCreatedAtDesc(
                        UUID airsoftUnitId);

        Long countByTenantIdAndStatusNotAndTargetDateBefore(
                        UUID tenantId,
                        WorkOrderStatus status,
                        LocalDate targetDate);

        @Query("select count(w) from WorkOrder w where w.tenant.id in :tenantIds and w.status <> :status and w.targetDate < :date")
        Long countByTenantIdInAndStatusNotAndTargetDateBefore(
                        @Param("tenantIds") List<UUID> tenantIds,
                        @Param("status") WorkOrderStatus status,
                        @Param("date") LocalDate date);

        List<WorkOrder> findByAirsoftUnitIdAndStatusIn(
                        UUID unitId,
                        List<WorkOrderStatus> statuses);

        List<WorkOrder> findByAirsoftUnitPartIdAndStatusIn(
                        UUID unitPartId,
                        List<WorkOrderStatus> statuses);

        long countByTenantIdAndStatusAndCompletedAtBetween(
                        UUID tenantId,
                        WorkOrderStatus status,
                        LocalDateTime start,
                        LocalDateTime end);

        @Query("select count(w) from WorkOrder w where w.tenant.id in :tenantIds and w.status = :status and w.completedAt between :start and :end")
        long countByTenantIdInAndStatusAndCompletedAtBetween(
                        @Param("tenantIds") List<UUID> tenantIds,
                        @Param("status") WorkOrderStatus status,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        boolean existsByMaintenanceScheduleIdAndStatusIn(
                        UUID maintenanceScheduleId,
                        List<WorkOrderStatus> statuses);
}
