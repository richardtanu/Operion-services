package com.example.operion.module.maintenance.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.maintenance.dto.CreateMaintenanceScheduleRequest;
import com.example.operion.module.maintenance.dto.MaintenanceRecommendationResponse;
import com.example.operion.module.maintenance.dto.MaintenanceScheduleResponse;
import com.example.operion.module.maintenance.entity.MaintenanceSchedule;
import com.example.operion.module.maintenance.repository.MaintenanceScheduleRepository;
import com.example.operion.module.serviceevent.entity.ServiceEvent;
import com.example.operion.module.serviceevent.repository.ServiceEventRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.unitpart.enums.PartCondition;
import com.example.operion.module.unitpart.enums.UnitPartStatus;
import com.example.operion.module.unitpart.repository.AirsoftUnitPartRepository;
import com.example.operion.module.workorder.entity.WorkOrder;
import com.example.operion.module.workorder.enums.WorkOrderPriority;
import com.example.operion.module.workorder.enums.WorkOrderStatus;
import com.example.operion.module.workorder.repository.WorkOrderRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MaintenanceRecommendationService {

        private static final Logger log = LoggerFactory.getLogger(
                        MaintenanceRecommendationService.class);

        private final AirsoftUnitRepository airsoftUnitRepository;

        private final AirsoftUnitPartRepository unitPartRepository;

        private final ServiceEventRepository serviceEventRepository;

        private final MaintenanceRecommendationEngineService engine;

        private final TenantRepository tenantRepository;

        private final MaintenanceScheduleRepository repository;

        private final WorkOrderRepository workOrderRepository;

        public List<MaintenanceRecommendationResponse> generate(UUID unitId) {

                UUID tenantId = UUID.fromString(TenantContext.getTenantId());

                AirsoftUnit unit = airsoftUnitRepository
                                .findByIdAndTenantId(unitId, tenantId)
                                .orElseThrow();

                return engine.generateRecommendations(unit)
                                .stream()
                                .map(text -> MaintenanceRecommendationResponse.builder()
                                                .recommendation(text)
                                                .severity("INFO")
                                                .build())
                                .toList();
        }

        @Transactional
        public MaintenanceScheduleResponse create(
                        CreateMaintenanceScheduleRequest request) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                Tenant tenant = tenantRepository
                                .findById(tenantId)
                                .orElseThrow();

                AirsoftUnit unit = airsoftUnitRepository
                                .findByIdAndTenantId(
                                                request.getAirsoftUnitId(),
                                                tenantId)
                                .orElseThrow(() -> new RuntimeException("Unit not found"));

                LocalDate nextDueDate = request.getLastMaintenanceDate()
                                .plusDays(request.getIntervalDays());

                MaintenanceSchedule schedule = MaintenanceSchedule.builder()
                                .tenant(tenant)
                                .airsoftUnit(unit)
                                .title(request.getTitle())
                                .description(request.getDescription())
                                .intervalDays(request.getIntervalDays())
                                .lastMaintenanceDate(request.getLastMaintenanceDate())
                                .nextDueDate(nextDueDate)
                                .autoGenerateWorkOrder(request.getAutoGenerateWorkOrder())
                                .active(true)
                                .build();

                return map(repository.save(schedule));
        }

        private MaintenanceScheduleResponse map(
                        MaintenanceSchedule schedule) {

                return MaintenanceScheduleResponse.builder()
                                .id(schedule.getId())
                                .airsoftUnitId(
                                                schedule.getAirsoftUnit().getId())
                                .unitName(
                                                schedule.getAirsoftUnit().getName())
                                .title(schedule.getTitle())
                                .description(schedule.getDescription())
                                .intervalDays(schedule.getIntervalDays())
                                .lastMaintenanceDate(
                                                schedule.getLastMaintenanceDate())
                                .nextDueDate(
                                                schedule.getNextDueDate())
                                .autoGenerateWorkOrder(
                                                schedule.getAutoGenerateWorkOrder())
                                .active(
                                                schedule.getActive())
                                .build();
        }

        public List<MaintenanceScheduleResponse> getAll() {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                return repository.findByTenantId(tenantId)
                                .stream()
                                .map(this::map)
                                .toList();
        }

        public List<MaintenanceScheduleResponse> getByUnit(UUID unitId) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                return repository.findByAirsoftUnitId(unitId)
                                .stream()
                                .filter(schedule -> schedule.getTenant()
                                                .getId()
                                                .equals(tenantId))
                                .map(this::map)
                                .toList();
        }

        public List<MaintenanceScheduleResponse> getMaintenanceRecommendationByUnit(
                        UUID unitId) {

                return repository.findByAirsoftUnitId(unitId)
                                .stream()
                                .map(this::map)
                                .toList();
        }

        @Transactional
        public int processDueSchedules() {

                LocalDate today = LocalDate.now();

                List<MaintenanceSchedule> schedules = repository.findByActiveTrueAndNextDueDateLessThanEqual(
                                today);

                int createdCount = 0;

                for (MaintenanceSchedule schedule : schedules) {

                        if (!Boolean.TRUE.equals(
                                        schedule.getAutoGenerateWorkOrder())) {
                                log.debug(
                                                "Skipping maintenance schedule {} because autoGenerateWorkOrder is disabled",
                                                schedule.getId());
                                continue;
                        }

                        boolean alreadyExists = workOrderRepository.existsByMaintenanceScheduleIdAndStatusIn(
                                        schedule.getId(), List.of(WorkOrderStatus.OPEN, WorkOrderStatus.ASSIGNED,
                                                        WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.WAITING_PARTS));

                        if (alreadyExists) {
                                log.debug("Skipping maintenance schedule {} because an active work order already exists",
                                                schedule.getId());
                                continue;
                        }

                        WorkOrder workOrder = WorkOrder.builder()
                                        .tenant(schedule.getTenant())
                                        .airsoftUnit(schedule.getAirsoftUnit())
                                        .maintenanceSchedule(schedule)
                                        .priority(WorkOrderPriority.HIGH)
                                        .status(WorkOrderStatus.OPEN)
                                        .title("Scheduled Maintenance - "
                                                        + schedule.getTitle())
                                        .description(schedule.getDescription())
                                        .targetDate(schedule.getNextDueDate())
                                        .build();

                        workOrderRepository.saveAndFlush(workOrder);
                        createdCount++;
                }

                log.info("Created {} work orders from {} due maintenance schedules",
                                createdCount,
                                schedules.size());

                return createdCount;
        }

        @Transactional
        public void completeSchedule(
                        MaintenanceSchedule schedule) {

                LocalDate today = LocalDate.now();

                schedule.setLastMaintenanceDate(today);

                schedule.setNextDueDate(
                                today.plusDays(
                                                schedule.getIntervalDays()));

                repository.save(schedule);
        }

}
