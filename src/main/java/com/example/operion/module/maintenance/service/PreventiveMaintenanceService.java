package com.example.operion.module.maintenance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.inventory.enums.InventoryStockStatus;
import com.example.operion.module.inventory.services.PartStockService;
import com.example.operion.module.maintenance.dto.MaintenanceEvaluationResult;
import com.example.operion.module.maintenance.dto.MaintenanceRecommendation;
import com.example.operion.module.maintenance.entity.MaintenancePolicy;
import com.example.operion.module.maintenance.entity.MaintenanceSchedule;
import com.example.operion.module.maintenance.enums.RecommendationSeverity;
import com.example.operion.module.maintenance.repository.MaintenanceScheduleRepository;
import com.example.operion.module.notification.service.NotificationService;
import com.example.operion.module.serviceevent.entity.ServiceEvent;
import com.example.operion.module.serviceevent.repository.ServiceEventRepository;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.unitpart.enums.PartCondition;
import com.example.operion.module.unitpart.enums.UnitPartStatus;
import com.example.operion.module.unitpart.repository.AirsoftUnitPartRepository;
import com.example.operion.module.workorder.entity.WorkOrder;
import com.example.operion.module.workorder.enums.WorkOrderPriority;
import com.example.operion.module.workorder.enums.WorkOrderStatus;
import com.example.operion.module.workorder.repository.WorkOrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreventiveMaintenanceService {

    private final AirsoftUnitRepository unitRepository;

    private final MaintenanceScheduleRepository scheduleRepository;

    private final MaintenancePolicyService maintenancePolicyService;

    private final AirsoftUnitPartRepository unitPartRepository;

    private final ServiceEventRepository serviceEventRepository;

    private final PartStockService partStockService;

    private final WorkOrderRepository workOrderRepository;

    private final NotificationService notificationService;

    private void addRecommendation(
            MaintenanceEvaluationResult result,
            RecommendationSeverity severity,
            String title,
            String message) {

        result.getRecommendations().add(

                MaintenanceRecommendation.builder()

                        .severity(severity)

                        .title(title)

                        .message(message)

                        .build());

        result.setRequiresMaintenance(true);

        updatePriority(result, severity);

    }

    private void updatePriority(

            MaintenanceEvaluationResult result,

            RecommendationSeverity severity) {

        switch (severity) {

            case CRITICAL -> result.setPriority(
                    WorkOrderPriority.CRITICAL);

            case HIGH -> {

                if (result.getPriority() != WorkOrderPriority.CRITICAL) {

                    result.setPriority(
                            WorkOrderPriority.HIGH);
                }
            }

            case MEDIUM -> {

                if (result.getPriority() == WorkOrderPriority.LOW) {

                    result.setPriority(
                            WorkOrderPriority.MEDIUM);
                }
            }

            default -> {
            }

        }

    }

    public MaintenanceEvaluationResult evaluateUnit(UUID unitId) {

        MaintenancePolicy policy = maintenancePolicyService.getCurrentPolicy();

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        AirsoftUnit unit = unitRepository
                .findByIdAndTenantId(unitId, tenantId)
                .orElseThrow(() -> new RuntimeException("Unit not found"));

        MaintenanceEvaluationResult result = MaintenanceEvaluationResult.builder().build();

        /*
         * -----------------------------------------------------
         * Installed Parts
         * -----------------------------------------------------
         */

        List<AirsoftUnitPart> installedParts = unitPartRepository.findByAirsoftUnitIdAndTenantIdAndStatus(
                unitId,
                tenantId,
                UnitPartStatus.INSTALLED);

        /*
         * -----------------------------------------------------
         * Maintenance Schedule
         * -----------------------------------------------------
         */

        List<MaintenanceSchedule> schedules = scheduleRepository.findByAirsoftUnitId(unitId);

        for (MaintenanceSchedule schedule : schedules) {

            if (!Boolean.TRUE.equals(schedule.getActive())) {
                continue;
            }

            if (!schedule.getNextDueDate().isAfter(LocalDate.now())) {

                long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(
                        schedule.getNextDueDate(),
                        LocalDate.now());

                addRecommendation(
                        result,
                        RecommendationSeverity.HIGH,
                        "Scheduled Maintenance Overdue",
                        "Scheduled maintenance overdue: "
                                + schedule.getTitle()
                                + " ("
                                + overdueDays
                                + " days overdue)");
            }
        }

        /*
         * -----------------------------------------------------
         * Failed Parts
         * -----------------------------------------------------
         */

        boolean failed = installedParts.stream()

                .anyMatch(p -> p.getCondition() == PartCondition.FAILED);

        if (failed) {

            addRecommendation(
                    result,
                    RecommendationSeverity.CRITICAL,
                    "Failed Component",
                    "Failed component detected");
        }

        /*
         * -----------------------------------------------------
         * Worn Parts
         * -----------------------------------------------------
         */

        long worn = installedParts.stream()

                .filter(p -> p.getCondition() == PartCondition.WORN)

                .count();

        if (worn >= policy.getMaxWornParts()) {

            addRecommendation(
                    result,
                    RecommendationSeverity.MEDIUM,
                    "Multiple Worn Components",
                    "Multiple worn components installed");
        }

        /*
         * -----------------------------------------------------
         * Refurbished Parts
         * -----------------------------------------------------
         */

        long refurbished = installedParts.stream()

                .filter(p -> p.getCondition() == PartCondition.REFURBISHED)

                .count();

        if (refurbished >= policy.getMaxRefurbishedParts()) {

            addRecommendation(
                    result,
                    RecommendationSeverity.MEDIUM,
                    "Excessive Refurbished Components",
                    "Large number of refurbished components");
        }

        /*
         * -----------------------------------------------------
         * Health Score
         * -----------------------------------------------------
         */

        if (unit.getHealthScore() != null &&
                unit.getHealthScore() < policy.getHealthThreshold()) {

            addRecommendation(
                    result,
                    RecommendationSeverity.HIGH,
                    "Health Score Below Threshold",
                    "Health score below maintenance threshold");
        }

        if (unit.getHealthScore() != null &&
                unit.getHealthScore() < policy.getMinimumHealthForOperation()) {

            addRecommendation(
                    result,
                    RecommendationSeverity.CRITICAL,
                    "Unit Unsafe For Operation",
                    "Unit should be removed from operation immediately");
        }

        /*
         * -----------------------------------------------------
         * Last Maintenance
         * -----------------------------------------------------
         */

        ServiceEvent lastMaintenance = serviceEventRepository
                .findFirstByTenantIdAndAirsoftUnitIdOrderByServiceDateDesc(
                        tenantId,
                        unitId)
                .orElse(null);

        if (lastMaintenance == null) {

            addRecommendation(
                    result,
                    RecommendationSeverity.MEDIUM,
                    "Never Serviced",
                    "Unit has never been serviced");
        }

        else {

            if (lastMaintenance.getServiceDate()

                    .plusDays(policy.getMaintenanceIntervalDays())

                    .isBefore(LocalDate.now())) {

                addRecommendation(
                        result,
                        RecommendationSeverity.MEDIUM,
                        "Maintenance Interval Overrun",
                        "No maintenance performed within the last "
                                + policy.getMaintenanceIntervalDays()
                                + " days");
            }
        }

        /*
         * -----------------------------------------------------
         * Low Stock Check
         * -----------------------------------------------------
         */

        installedParts.forEach(installed -> {

            Integer currentStock = installed.getPart().getCurrentStock();
            Integer minimumStock = installed.getPart().getMinimumStock();

            if (currentStock != null && minimumStock != null) {

                BigDecimal threshold = BigDecimal.valueOf(minimumStock)
                        .multiply(policy.getLowStockMultiplier());

                if (BigDecimal.valueOf(currentStock).compareTo(threshold) <= 0) {

                    addRecommendation(
                            result,
                            RecommendationSeverity.LOW,
                            "Low Replacement Stock",
                            "Replacement stock running low for "
                                    + installed.getPart().getName());
                }
            }

            /*
             * Remaining lifespan
             */

            if (installed.getInstalledDate() != null &&
                    installed.getPart().getExpectedLifespanDays() != null) {

                long usedDays = java.time.temporal.ChronoUnit.DAYS.between(
                        installed.getInstalledDate(),
                        LocalDate.now());

                long remainingDays = installed.getPart().getExpectedLifespanDays()
                        - usedDays;

                if (remainingDays <= policy.getPartEndOfLifeWarningDays()) {

                    addRecommendation(
                            result,
                            RecommendationSeverity.MEDIUM,
                            "Approaching End Of Lifespan",
                            installed.getPart().getName()
                                    + " approaching end of lifespan ("
                                    + remainingDays
                                    + " days remaining)");
                }

                if (remainingDays <= 0) {

                    addRecommendation(
                            result,
                            RecommendationSeverity.HIGH,
                            "Exceeded Expected Lifespan",
                            installed.getPart().getName()
                                    + " has exceeded expected lifespan");
                }
            }

        });

        /*
         * -----------------------------------------------------
         * Auto-Create Work Order
         * -----------------------------------------------------
         *
         * A pure LOW-only result (e.g. only a low-stock finding) is a
         * procurement concern, not a "this unit needs a work order"
         * concern, so it deliberately does not trigger creation.
         */

        if (result.isRequiresMaintenance()
                && result.getPriority() != WorkOrderPriority.LOW
                && Boolean.TRUE.equals(policy.getAutoCreateWorkOrder())) {

            boolean hasOpenWorkOrder = workOrderRepository.existsByAirsoftUnitIdAndStatusIn(
                    unitId,
                    List.of(
                            WorkOrderStatus.OPEN,
                            WorkOrderStatus.ASSIGNED,
                            WorkOrderStatus.IN_PROGRESS,
                            WorkOrderStatus.WAITING_PARTS));

            if (hasOpenWorkOrder) {

                log.debug(
                        "Skipping auto-create for unit {} because an active work order already exists",
                        unitId);
            } else {

                LocalDate targetDate = switch (result.getPriority()) {
                    case CRITICAL -> LocalDate.now();
                    case HIGH -> LocalDate.now().plusDays(3);
                    default -> LocalDate.now().plusDays(7);
                };

                String description = result.getRecommendations().stream()
                        .map(MaintenanceRecommendation::getMessage)
                        .collect(java.util.stream.Collectors.joining("\n"));

                WorkOrder workOrder = WorkOrder.builder()
                        .tenant(unit.getTenant())
                        .airsoftUnit(unit)
                        .priority(result.getPriority())
                        .status(WorkOrderStatus.OPEN)
                        .title("Preventive Maintenance - " + unit.getName())
                        .description(description)
                        .targetDate(targetDate)
                        .build();

                workOrderRepository.saveAndFlush(workOrder);

                log.info(
                        "Auto-created work order for unit {} with priority {}",
                        unitId,
                        result.getPriority());
            }
        }

        /*
         * -----------------------------------------------------
         * End-Of-Life Notifications
         * -----------------------------------------------------
         */

        for (MaintenanceRecommendation recommendation : result.getRecommendations()) {

            if ("Approaching End Of Lifespan".equals(recommendation.getTitle())
                    || "Exceeded Expected Lifespan".equals(recommendation.getTitle())) {

                notificationService.notifyPartEndOfLife(unit, recommendation.getMessage());
            }
        }

        return result;
    }

}