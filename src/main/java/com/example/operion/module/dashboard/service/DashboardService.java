package com.example.operion.module.dashboard.service;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.airsoft.enums.UnitStatus;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.dashboard.dto.OperationalDashboardResponse;
import com.example.operion.module.dashboard.dto.UnitStatusSummaryResponse;
import com.example.operion.module.serviceevent.repository.ServiceEventRepository;
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

        public OperationalDashboardResponse getOverview() {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                Long totalUnits = airsoftUnitRepository
                                .countByTenantId(
                                                tenantId);

                Long activeUnits = airsoftUnitRepository
                                .countByTenantIdAndStatus(
                                                tenantId,
                                                UnitStatus.ACTIVE);

                Long maintenanceUnits = airsoftUnitRepository
                                .countByTenantIdAndStatus(
                                                tenantId,
                                                UnitStatus.MAINTENANCE);

                Long installedParts = unitPartRepository
                                .countByTenantIdAndStatus(
                                                tenantId,
                                                UnitPartStatus.INSTALLED);

                Long removedParts = unitPartRepository
                                .countByTenantIdAndStatus(
                                                tenantId,
                                                UnitPartStatus.REMOVED);

                Long replacementEvents = serviceEventRepository
                                .countByTenantId(
                                                tenantId);

                Double averageHealthScore = airsoftUnitRepository
                                .findAll()
                                .stream()
                                .filter(unit -> unit.getTenant()
                                                .getId()
                                                .equals(tenantId))
                                .filter(unit -> unit.getHealthScore() != null)
                                .mapToInt(unit -> unit.getHealthScore())
                                .average()
                                .orElse(0);

                Long criticalUnits = airsoftUnitRepository
                                .findAll()
                                .stream()
                                .filter(unit -> unit.getTenant()
                                                .getId()
                                                .equals(tenantId))
                                .filter(unit -> unit.getHealthScore() != null)
                                .filter(unit -> unit.getHealthScore() < 40)
                                .count();

                Long openWorkOrders = workOrderRepository
                                .countByTenantIdAndStatus(
                                                tenantId,
                                                WorkOrderStatus.OPEN);

                // Long criticalWorkOrders = workOrderRepository
                // .countByTenantIdAndPriority(
                // tenantId,
                // WorkOrderPriority.CRITICAL);
                Long criticalWorkOrders = workOrderRepository
                                .countByTenantIdAndPriorityAndStatusNot(
                                                tenantId,
                                                WorkOrderPriority.CRITICAL,
                                                WorkOrderStatus.COMPLETED);

                Long completedWorkOrders = workOrderRepository
                                .countByTenantIdAndStatus(
                                                tenantId,
                                                WorkOrderStatus.COMPLETED);

                Long overdueWorkOrders = workOrderRepository
                                .countByTenantIdAndStatusNotAndTargetDateBefore(
                                                tenantId,
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

                return UnitStatusSummaryResponse.builder()
                                .active(airsoftUnitRepository.countByTenantIdAndStatus(
                                                tenantId,
                                                UnitStatus.ACTIVE))

                                .maintenance(airsoftUnitRepository.countByTenantIdAndStatus(
                                                tenantId,
                                                UnitStatus.MAINTENANCE))

                                .broken(airsoftUnitRepository.countByTenantIdAndStatus(
                                                tenantId,
                                                UnitStatus.BROKEN))

                                .upgrade(airsoftUnitRepository.countByTenantIdAndStatus(
                                                tenantId,
                                                UnitStatus.UPGRADE))

                                .retired(airsoftUnitRepository.countByTenantIdAndStatus(
                                                tenantId,
                                                UnitStatus.RETIRED))

                                .build();
        }

}