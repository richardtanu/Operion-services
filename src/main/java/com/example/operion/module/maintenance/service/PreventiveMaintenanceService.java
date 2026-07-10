package com.example.operion.module.maintenance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.example.operion.module.serviceevent.entity.ServiceEvent;
import com.example.operion.module.serviceevent.repository.ServiceEventRepository;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.unitpart.enums.PartCondition;
import com.example.operion.module.unitpart.enums.UnitPartStatus;
import com.example.operion.module.unitpart.repository.AirsoftUnitPartRepository;
import com.example.operion.module.workorder.enums.WorkOrderPriority;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PreventiveMaintenanceService {

    private final AirsoftUnitRepository unitRepository;

    private final MaintenanceScheduleRepository scheduleRepository;

    private final MaintenancePolicyService maintenancePolicyService;

    private final AirsoftUnitPartRepository unitPartRepository;

    private final ServiceEventRepository serviceEventRepository;

    private final PartStockService partStockService;

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

    public List<String> evaluateUnit(UUID unitId) {

        MaintenancePolicy policy = maintenancePolicyService.getCurrentPolicy();

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        AirsoftUnit unit = unitRepository
                .findByIdAndTenantId(unitId, tenantId)
                .orElseThrow(() -> new RuntimeException("Unit not found"));

        List<String> recommendations = new ArrayList<>();

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

                recommendations.add(
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

            recommendations.add(
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

            recommendations.add(
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

            recommendations.add(
                    "Large number of refurbished components");
        }

        /*
         * -----------------------------------------------------
         * Health Score
         * -----------------------------------------------------
         */

        if (unit.getHealthScore() != null &&
                unit.getHealthScore() < policy.getHealthThreshold()) {

            recommendations.add(
                    "Health score below maintenance threshold");
        }

        if (unit.getHealthScore() != null &&
                unit.getHealthScore() < policy.getMinimumHealthForOperation()) {

            recommendations.add(
                    "Unit should be removed from operation immediately");
        }

        /*
         * -----------------------------------------------------
         * Last Maintenance
         * -----------------------------------------------------
         */

        List<ServiceEvent> events = serviceEventRepository.findAll();

        ServiceEvent lastMaintenance = serviceEventRepository
                .findFirstByTenantIdAndAirsoftUnitIdOrderByServiceDateDesc(
                        tenantId,
                        unitId)
                .orElse(null);

        if (lastMaintenance == null) {

            recommendations.add(
                    "Unit has never been serviced");
        }

        else {

            if (lastMaintenance.getServiceDate()

                    .plusDays(policy.getMaintenanceIntervalDays())

                    .isBefore(LocalDate.now())) {

                recommendations.add(
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

                    recommendations.add(
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

                    recommendations.add(
                            installed.getPart().getName()
                                    + " approaching end of lifespan ("
                                    + remainingDays
                                    + " days remaining)");
                }

                if (remainingDays <= 0) {

                    recommendations.add(
                            installed.getPart().getName()
                                    + " has exceeded expected lifespan");
                }
            }

        });

        return recommendations;
    }

}