package com.example.operion.module.serviceevent.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.serviceevent.dto.CreateServiceEventRequest;
import com.example.operion.module.serviceevent.dto.ServiceEventResponse;
import com.example.operion.module.serviceevent.entity.ServiceEvent;
import com.example.operion.module.serviceevent.repository.ServiceEventRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;
import com.example.operion.module.airsoft.enums.UnitStatus;
import com.example.operion.module.workorder.entity.WorkOrder;
import com.example.operion.module.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ServiceEventService {

    private final ServiceEventRepository repository;

    private final TenantRepository tenantRepository;

    private final AirsoftUnitRepository airsoftUnitRepository;

    private final UserRepository userRepository;

    private final TenantHierarchyService tenantHierarchyService;

    private final WorkOrderRepository workOrderRepository;

    public ServiceEventResponse create(
            CreateServiceEventRequest request) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow();

        AirsoftUnit unit = airsoftUnitRepository
                .findById(request.getAirsoftUnitId())
                .orElseThrow();

        User technician = null;

        if (request.getTechnicianId() != null) {

            technician = userRepository
                    .findById(request.getTechnicianId())
                    .orElseThrow();
        }

        WorkOrder workOrder = null;

        if (request.getWorkOrderId() != null) {

            workOrder = workOrderRepository
                    .findByIdAndTenantId(request.getWorkOrderId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Work order not found"));
        }

        ServiceEvent event = ServiceEvent.builder()
                .tenant(tenant)
                .airsoftUnit(unit)
                .technician(technician)
                .workOrder(workOrder)
                .eventType(request.getEventType())
                .issue(request.getIssue())
                .actionTaken(request.getActionTaken())
                .cost(request.getCost())
                .nextCheckDate(request.getNextCheckDate())
                .notes(request.getNotes())
                .build();

        ServiceEvent saved = repository.save(event);

        /*
         * AUTOMATIC UNIT STATUS MANAGEMENT
         */

        switch (request.getEventType()) {

            case REPAIR:
                unit.setStatus(UnitStatus.MAINTENANCE);
                break;

            case MAINTENANCE:
                unit.setStatus(UnitStatus.MAINTENANCE);
                break;

            case UPGRADE:
                unit.setStatus(UnitStatus.MAINTENANCE);
                break;

            default:
                break;
        }

        /*
         * SAVE UPDATED UNIT STATUS
         */

        airsoftUnitRepository.save(unit);
        return mapToResponse(saved);
    }

    public List<ServiceEventResponse> getAll() {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return repository.findByTenantIdIn(tenantIds)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ServiceEventResponse mapToResponse(
            ServiceEvent event) {

        return ServiceEventResponse.builder()
                .id(event.getId())
                .unitName(event.getAirsoftUnit().getName())
                .technicianName(
                        event.getTechnician() != null
                                ? event.getTechnician().getFullName()
                                : null)
                .workOrderId(
                        event.getWorkOrder() != null
                                ? event.getWorkOrder().getId()
                                : null)
                .eventType(event.getEventType().name())
                .issue(event.getIssue())
                .actionTaken(event.getActionTaken())
                .cost(event.getCost())
                .serviceDate(event.getServiceDate())
                .nextCheckDate(event.getNextCheckDate())
                .build();
    }

    public List<ServiceEventResponse> getRecentByUnit(UUID unitId) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return repository
                .findTopByTenantIdInAndAirsoftUnitId(
                        tenantIds,
                        unitId,
                        PageRequest.of(0, 5))
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<ServiceEventResponse> getByWorkOrder(UUID workOrderId) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return repository
                .findByTenantIdInAndWorkOrderId(tenantIds, workOrderId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }
}
