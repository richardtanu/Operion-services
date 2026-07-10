package com.example.operion.module.serviceevent.service;

import java.util.List;
import java.util.UUID;

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
import com.example.operion.module.airsoft.enums.UnitStatus;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ServiceEventService {

    private final ServiceEventRepository repository;

    private final TenantRepository tenantRepository;

    private final AirsoftUnitRepository airsoftUnitRepository;

    private final UserRepository userRepository;

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

        ServiceEvent event = ServiceEvent.builder()
                .tenant(tenant)
                .airsoftUnit(unit)
                .technician(technician)
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

        return repository.findByTenantId(tenantId)
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

        return repository
                .findTop5ByTenantIdAndAirsoftUnitIdOrderByServiceDateDesc(
                        tenantId,
                        unitId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }
}
