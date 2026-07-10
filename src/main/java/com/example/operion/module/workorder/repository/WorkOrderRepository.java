package com.example.operion.module.workorder.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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

        List<WorkOrder> findByAirsoftUnitId(
                        UUID unitId);

        Long countByTenantIdAndStatus(
                        UUID tenantId,
                        WorkOrderStatus status);

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

        List<WorkOrder> findByAirsoftUnitIdOrderByCreatedAtDesc(
                        UUID airsoftUnitId);

        Long countByTenantIdAndStatusNotAndTargetDateBefore(
                        UUID tenantId,
                        WorkOrderStatus status,
                        LocalDate targetDate);

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

        boolean existsByMaintenanceScheduleIdAndStatusIn(
                        UUID maintenanceScheduleId,
                        List<WorkOrderStatus> statuses);
}
