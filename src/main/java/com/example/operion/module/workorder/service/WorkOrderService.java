package com.example.operion.module.workorder.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.maintenance.service.MaintenanceRecommendationService;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.unitpart.enums.UnitPartStatus;
import com.example.operion.module.unitpart.repository.AirsoftUnitPartRepository;
import com.example.operion.module.workorder.dto.AssignWorkOrderRequest;
import com.example.operion.module.workorder.dto.CreateWorkOrderRequest;
import com.example.operion.module.workorder.dto.UpdateWorkOrderStatusRequest;
import com.example.operion.module.workorder.dto.WorkOrderDashboardResponse;
import com.example.operion.module.workorder.dto.WorkOrderResponse;
import com.example.operion.module.workorder.entity.WorkOrder;
import com.example.operion.module.workorder.enums.WorkOrderStatus;
import com.example.operion.module.workorder.repository.*;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkOrderService {

        private final WorkOrderRepository repository;

        private final TenantRepository tenantRepository;

        private final AirsoftUnitRepository unitRepository;

        private final AirsoftUnitPartRepository unitPartRepository;

        private final UserRepository userRepository;

        private final MaintenanceRecommendationService maintenanceRecommendationService;

        private final TenantHierarchyService tenantHierarchyService;

        public WorkOrderResponse create(
                        CreateWorkOrderRequest request) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                Tenant tenant = tenantRepository.findById(
                                tenantId)
                                .orElseThrow();

                AirsoftUnit unit = unitRepository
                                .findByIdAndTenantId(
                                                request.getAirsoftUnitId(),
                                                tenantId)
                                .orElseThrow();

                AirsoftUnitPart unitPart = null;

                if (request.getAirsoftUnitPartId() != null) {

                        unitPart = unitPartRepository.findByIdAndTenantId(request.getAirsoftUnitPartId(), tenantId)
                                        .orElseThrow(() -> new RuntimeException("Unit part not found"));
                }

                if (unitPart != null && !unitPart.getAirsoftUnit().getId().equals(unit.getId())) {

                        throw new RuntimeException("Selected part does not belong to selected unit");
                }

                if (unitPart != null && unitPart.getStatus() != UnitPartStatus.INSTALLED) {

                        throw new RuntimeException("Work order can only be created for installed parts");
                }

                User technician = null;

                if (request.getTechnicianId() != null) {

                        technician = userRepository.findById(request.getTechnicianId()).orElseThrow();
                }

                WorkOrder workOrder = WorkOrder.builder()
                                .tenant(tenant)
                                .airsoftUnit(unit)
                                .airsoftUnitPart(unitPart)
                                .assignedTechnician(technician)
                                .priority(request.getPriority())
                                .title(request.getTitle())
                                .description(request.getDescription())
                                .targetDate(request.getTargetDate())
                                .build();

                /*
                 * AUTO STATUS
                 */

                if (technician != null) {
                        workOrder.setStatus(WorkOrderStatus.ASSIGNED);
                        workOrder.setAssignedAt(LocalDateTime.now());

                } else {
                        workOrder.setStatus(WorkOrderStatus.OPEN);
                }

                return map(repository.save(workOrder));
        }

        public Page<WorkOrderResponse> getAll(
                        int page,
                        int size) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

                PageRequest pageable = PageRequest.of(page, size);

                return repository
                                .findByTenantIdIn(
                                                tenantIds,
                                                pageable)
                                .map(this::map);
        }

        public WorkOrderResponse updateStatus(
                        UUID workOrderId,
                        UpdateWorkOrderStatusRequest request) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                WorkOrder workOrder = repository
                                .findByIdAndTenantId(
                                                workOrderId,
                                                tenantId)
                                .orElseThrow();

                WorkOrderStatus newStatus = request.getStatus();

                workOrder.setStatus(
                                newStatus);

                switch (newStatus) {

                        case ASSIGNED:

                                if (workOrder.getAssignedAt() == null) {

                                        workOrder.setAssignedAt(
                                                        LocalDateTime.now());
                                }

                                break;

                        case IN_PROGRESS:

                                if (workOrder.getStartedAt() == null) {

                                        workOrder.setStartedAt(
                                                        LocalDateTime.now());
                                }

                                break;

                        case COMPLETED:

                                if (workOrder.getCompletedAt() == null) {

                                        workOrder.setCompletedAt(
                                                        LocalDateTime.now());
                                }

                                break;

                        default:
                                break;
                }

                return map(
                                repository.save(workOrder));
        }

        private WorkOrderResponse map(
                        WorkOrder workOrder) {

                return WorkOrderResponse.builder()
                                .id(
                                                workOrder.getId())
                                .unitName(
                                                workOrder.getAirsoftUnit().getName())
                                .unitPartId(
                                                workOrder.getAirsoftUnitPart() != null
                                                                ? workOrder.getAirsoftUnitPart().getId()
                                                                : null)

                                .partName(
                                                workOrder.getAirsoftUnitPart() != null
                                                                ? workOrder.getAirsoftUnitPart().getPart().getName()
                                                                : null)
                                .technicianName(
                                                workOrder.getAssignedTechnician() != null
                                                                ? workOrder.getAssignedTechnician()
                                                                                .getFullName()
                                                                : null)
                                .priority(
                                                workOrder.getPriority().name())
                                .status(
                                                workOrder.getStatus() != null
                                                                ? workOrder.getStatus().name()
                                                                : null)
                                .title(
                                                workOrder.getTitle())
                                .description(
                                                workOrder.getDescription())
                                .targetDate(
                                                workOrder.getTargetDate())
                                .createdAt(
                                                workOrder.getCreatedAt())
                                .assignedAt(
                                                workOrder.getAssignedAt())
                                .startedAt(
                                                workOrder.getStartedAt())
                                .completedAt(
                                                workOrder.getCompletedAt())
                                .build();
        }

        public boolean hasOpenWorkOrder(
                        UUID unitId) {

                return repository.existsByAirsoftUnitIdAndStatusIn(
                                unitId,
                                List.of(
                                                WorkOrderStatus.OPEN,
                                                WorkOrderStatus.ASSIGNED,
                                                WorkOrderStatus.IN_PROGRESS,
                                                WorkOrderStatus.WAITING_PARTS));
        }

        public WorkOrderResponse complete(
                        UUID id) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                WorkOrder workOrder = repository
                                .findByIdAndTenantId(
                                                id,
                                                tenantId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Work order not found"));

                workOrder.setStatus(
                                WorkOrderStatus.COMPLETED);

                workOrder.setCompletedAt(
                                LocalDateTime.now());

                WorkOrder saved = repository.save(workOrder);

                if (workOrder.getMaintenanceSchedule() != null) {

                        maintenanceRecommendationService.completeSchedule(workOrder.getMaintenanceSchedule());
                }

                return map(saved);
        }

        public List<WorkOrderResponse> getByUnit(
                        UUID unitId) {

                return repository
                                .findByAirsoftUnitIdOrderByCreatedAtDesc(
                                                unitId)
                                .stream()
                                .map(this::map)
                                .toList();
        }

        @Transactional
        public void autoCloseOpenWorkOrders(UUID unitId) {

                List<WorkOrder> openWorkOrders = repository.findByAirsoftUnitIdAndStatusIn(
                                unitId,
                                List.of(
                                                WorkOrderStatus.OPEN,
                                                WorkOrderStatus.ASSIGNED,
                                                WorkOrderStatus.IN_PROGRESS,
                                                WorkOrderStatus.WAITING_PARTS));

                for (WorkOrder workOrder : openWorkOrders) {

                        workOrder.setStatus(WorkOrderStatus.COMPLETED);

                        workOrder.setCompletedDate(LocalDate.now());

                        workOrder.setCompletedAt(
                                        LocalDateTime.now());
                }

                repository.saveAll(openWorkOrders);
        }

        @Transactional
        public void autoCompleteForPart(UUID unitPartId) {

                List<WorkOrder> workOrders = repository.findByAirsoftUnitPartIdAndStatusIn(
                                unitPartId,
                                List.of(
                                                WorkOrderStatus.OPEN,
                                                WorkOrderStatus.ASSIGNED,
                                                WorkOrderStatus.IN_PROGRESS,
                                                WorkOrderStatus.WAITING_PARTS));

                LocalDateTime now = LocalDateTime.now();

                for (WorkOrder workOrder : workOrders) {

                        workOrder.setStatus(WorkOrderStatus.COMPLETED);

                        workOrder.setCompletedAt(now);

                        workOrder.setCompletedDate(now.toLocalDate());
                }

                repository.saveAll(workOrders);
        }

        public WorkOrderResponse assign(
                        UUID workOrderId,
                        AssignWorkOrderRequest request) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                WorkOrder workOrder = repository
                                .findByIdAndTenantId(
                                                workOrderId,
                                                tenantId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Work order not found"));

                if (workOrder.getStatus() == WorkOrderStatus.COMPLETED) {
                        throw new RuntimeException("Cannot assign technician to completed work order");
                }

                if (workOrder.getAssignedTechnician() != null) {
                        throw new RuntimeException("Work order already assigned");
                }

                User technician = userRepository
                                .findById(
                                                request.getTechnicianId())
                                .orElseThrow(() -> new RuntimeException(
                                                "Technician not found"));

                workOrder.setAssignedTechnician(
                                technician);

                workOrder.setStatus(
                                WorkOrderStatus.ASSIGNED);

                workOrder.setAssignedAt(
                                LocalDateTime.now());

                return map(
                                repository.save(workOrder));
        }

        // public WorkOrderResponse start(
        // UUID workOrderId) {

        // UUID tenantId = UUID.fromString(
        // TenantContext.getTenantId());

        // WorkOrder workOrder = repository
        // .findByIdAndTenantId(
        // workOrderId,
        // tenantId)
        // .orElseThrow(() -> new RuntimeException(
        // "Work order not found"));

        // workOrder.setStatus(
        // WorkOrderStatus.IN_PROGRESS);

        // workOrder.setStartedAt(
        // LocalDateTime.now());

        // return map(
        // repository.save(workOrder));
        // }
        public WorkOrderResponse start(
                        UUID workOrderId) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                WorkOrder workOrder = repository
                                .findByIdAndTenantId(
                                                workOrderId,
                                                tenantId)
                                .orElseThrow(() -> new RuntimeException("Work order not found"));

                /*
                 * VALIDATION
                 */

                if (workOrder.getStatus() == WorkOrderStatus.COMPLETED) {

                        throw new RuntimeException("Completed work order cannot be started");
                }

                if (workOrder.getStatus() == WorkOrderStatus.IN_PROGRESS) {

                        throw new RuntimeException("Work order already in progress");
                }

                if (workOrder.getStatus() == WorkOrderStatus.OPEN) {

                        throw new RuntimeException("Assign technician before starting work");
                }

                /*
                 * START WORK
                 */

                workOrder.setStatus(WorkOrderStatus.IN_PROGRESS);

                if (workOrder.getStartedAt() == null) {

                        workOrder.setStartedAt(LocalDateTime.now());
                }

                return map(repository.save(workOrder));
        }

        // public WorkOrderResponse waitingParts(
        // UUID workOrderId) {

        // UUID tenantId = UUID.fromString(
        // TenantContext.getTenantId());

        // WorkOrder workOrder = repository
        // .findByIdAndTenantId(
        // workOrderId,
        // tenantId)
        // .orElseThrow(() -> new RuntimeException(
        // "Work order not found"));

        // workOrder.setStatus(
        // WorkOrderStatus.WAITING_PARTS);

        // return map(
        // repository.save(workOrder));
        // }
        public WorkOrderResponse waitingParts(
                        UUID workOrderId) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                WorkOrder workOrder = repository
                                .findByIdAndTenantId(
                                                workOrderId,
                                                tenantId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Work order not found"));

                /*
                 * VALIDATION
                 */

                if (workOrder.getStatus() == WorkOrderStatus.COMPLETED) {

                        throw new RuntimeException("Completed work order cannot wait for parts");
                }

                if (workOrder.getStatus() != WorkOrderStatus.IN_PROGRESS) {

                        throw new RuntimeException("Only in-progress work orders can wait for parts");
                }

                /*
                 * UPDATE STATUS
                 */

                workOrder.setStatus(WorkOrderStatus.WAITING_PARTS);

                return map(repository.save(workOrder));
        }

        public WorkOrderDashboardResponse dashboard() {
                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

                LocalDateTime startMonth = LocalDate.now()
                                .withDayOfMonth(1)
                                .atStartOfDay();

                LocalDateTime endMonth = LocalDateTime.now();

                return WorkOrderDashboardResponse.builder()
                                .open(repository.countByTenantIdInAndStatus(
                                                tenantIds,
                                                WorkOrderStatus.OPEN))
                                .assigned(repository.countByTenantIdInAndStatus(
                                                tenantIds,
                                                WorkOrderStatus.ASSIGNED))
                                .inProgress(repository.countByTenantIdInAndStatus(
                                                tenantIds,
                                                WorkOrderStatus.IN_PROGRESS))
                                .waitingParts(repository.countByTenantIdInAndStatus(
                                                tenantIds,
                                                WorkOrderStatus.WAITING_PARTS))
                                .completedThisMonth(repository.countByTenantIdInAndStatusAndCompletedAtBetween(
                                                tenantIds,
                                                WorkOrderStatus.COMPLETED,
                                                startMonth,
                                                endMonth))
                                .overdue(repository.countByTenantIdInAndStatusNotAndTargetDateBefore(
                                                tenantIds,
                                                WorkOrderStatus.COMPLETED,
                                                LocalDate.now()))
                                .build();
        }
}