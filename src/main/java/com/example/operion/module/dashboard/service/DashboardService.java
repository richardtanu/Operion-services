package com.example.operion.module.dashboard.service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.airsoft.enums.UnitStatus;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.dashboard.dto.OperationalDashboardResponse;
import com.example.operion.module.dashboard.dto.UnitStatusSummaryResponse;
import com.example.operion.module.serviceevent.repository.ServiceEventRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;
import com.example.operion.module.unitpart.enums.UnitPartStatus;
import com.example.operion.module.unitpart.repository.AirsoftUnitPartRepository;
import com.example.operion.module.workorder.enums.WorkOrderPriority;
import com.example.operion.module.workorder.enums.WorkOrderStatus;
import com.example.operion.module.workorder.repository.WorkOrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardService {

        private final AirsoftUnitRepository airsoftUnitRepository;

        private final AirsoftUnitPartRepository unitPartRepository;

        private final ServiceEventRepository serviceEventRepository;

        private final WorkOrderRepository workOrderRepository;

        private final TenantHierarchyService tenantHierarchyService;

        public OperationalDashboardResponse getOverview() {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);
                Set<UUID> tenantIdSet = new HashSet<>(tenantIds);

                Long totalUnits = airsoftUnitRepository
                                .countByTenantIdIn(
                                                tenantIds);

                Long activeUnits = airsoftUnitRepository
                                .countByTenantIdInAndStatus(
                                                tenantIds,
                                                UnitStatus.ACTIVE);

                Long maintenanceUnits = airsoftUnitRepository
                                .countByTenantIdInAndStatus(
                                                tenantIds,
                                                UnitStatus.MAINTENANCE);

                Long installedParts = unitPartRepository
                                .countByTenantIdInAndStatus(
                                                tenantIds,
                                                UnitPartStatus.INSTALLED);

                Long removedParts = unitPartRepository
                                .countByTenantIdInAndStatus(
                                                tenantIds,
                                                UnitPartStatus.REMOVED);

                Long replacementEvents = serviceEventRepository
                                .countByTenantIdIn(
                                                tenantIds);

                Double averageHealthScore = airsoftUnitRepository
                                .findAll()
                                .stream()
                                .filter(unit -> tenantIdSet.contains(
                                                unit.getTenant().getId()))
                                .filter(unit -> unit.getHealthScore() != null)
                                .mapToInt(unit -> unit.getHealthScore())
                                .average()
                                .orElse(0);

                Long criticalUnits = airsoftUnitRepository
                                .findAll()
                                .stream()
                                .filter(unit -> tenantIdSet.contains(
                                                unit.getTenant().getId()))
                                .filter(unit -> unit.getHealthScore() != null)
                                .filter(unit -> unit.getHealthScore() < 40)
                                .count();

                Long openWorkOrders = workOrderRepository
                                .countByTenantIdInAndStatus(
                                                tenantIds,
                                                WorkOrderStatus.OPEN);

                Long criticalWorkOrders = workOrderRepository
                                .countByTenantIdInAndPriorityAndStatusNot(
                                                tenantIds,
                                                WorkOrderPriority.CRITICAL,
                                                WorkOrderStatus.COMPLETED);

                Long completedWorkOrders = workOrderRepository
                                .countByTenantIdInAndStatus(
                                                tenantIds,
                                                WorkOrderStatus.COMPLETED);

                Long overdueWorkOrders = workOrderRepository
                                .countByTenantIdInAndStatusNotAndTargetDateBefore(
                                                tenantIds,
                                                WorkOrderStatus.COMPLETED,
                                                LocalDate.now());
                return OperationalDashboardResponse
                                .builder()
                                .totalUnits(totalUnits)
                                .activeUnits(activeUnits)
                                .maintenanceUnits(maintenanceUnits)
                                .criticalUnits(criticalUnits)
                                .installedParts(installedParts)
                                .removedParts(removedParts)
                                .replacementEvents(
                                                replacementEvents)
                                .averageHealthScore(
                                                averageHealthScore)
                                .openWorkOrders(
                                                openWorkOrders)

                                .criticalWorkOrders(
                                                criticalWorkOrders)

                                .completedWorkOrders(
                                                completedWorkOrders)
                                .overdueWorkOrders(overdueWorkOrders)

                                .build();
        }

        public UnitStatusSummaryResponse getUnitStatusSummary() {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

                return UnitStatusSummaryResponse.builder()
                                .active(airsoftUnitRepository.countByTenantIdInAndStatus(
                                                tenantIds,
                                                UnitStatus.ACTIVE))

                                .maintenance(airsoftUnitRepository.countByTenantIdInAndStatus(
                                                tenantIds,
                                                UnitStatus.MAINTENANCE))

                                .broken(airsoftUnitRepository.countByTenantIdInAndStatus(
                                                tenantIds,
                                                UnitStatus.BROKEN))

                                .upgrade(airsoftUnitRepository.countByTenantIdInAndStatus(
                                                tenantIds,
                                                UnitStatus.UPGRADE))

                                .retired(airsoftUnitRepository.countByTenantIdInAndStatus(
                                                tenantIds,
                                                UnitStatus.RETIRED))

                                .build();
        }

}