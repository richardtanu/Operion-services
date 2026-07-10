package com.example.operion.module.airsoft.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.airsoft.dto.AirsoftUnitOverviewResponse;
import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.maintenance.dto.MaintenanceRecommendationResponse;
import com.example.operion.module.maintenance.service.MaintenanceRecommendationService;
import com.example.operion.module.serviceevent.dto.ServiceEventResponse;
import com.example.operion.module.serviceevent.service.ServiceEventService;
import com.example.operion.module.unitpart.dto.AirsoftUnitPartResponse;
import com.example.operion.module.unitpart.enums.PartCondition;
import com.example.operion.module.unitpart.enums.UnitPartStatus;
import com.example.operion.module.unitpart.service.AirsoftUnitPartService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AirsoftUnitOverviewService {

    private final AirsoftUnitRepository unitRepository;

    private final AirsoftUnitPartService unitPartService;

    private final ServiceEventService serviceEventService;

    private final MaintenanceRecommendationService recommendationService;

    public AirsoftUnitOverviewResponse getOverview(UUID unitId) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        AirsoftUnit unit = unitRepository
                .findByIdAndTenantId(
                        unitId,
                        tenantId)
                .orElseThrow(() -> new RuntimeException(
                        "Unit not found"));

        /*
         * INSTALLED PARTS
         */

        List<AirsoftUnitPartResponse> installedParts = unitPartService
                .getInstalledParts(unitId);

        /*
         * RECENT EVENTS
         */

        List<ServiceEventResponse> recentEvents = serviceEventService
                .getRecentByUnit(unitId);

        /*
         * RECOMMENDATIONS
         */

        List<MaintenanceRecommendationResponse> recommendations = recommendationService
                .generate(unitId);

        /*
         * CONDITION SUMMARY
         */

        int wornParts = (int) installedParts.stream()
                .filter(part ->

                part.getCondition() != null

                        &&

                        part.getCondition()
                                .equals(
                                        PartCondition.WORN.name()))
                .count();

        int failedParts = (int) installedParts.stream()
                .filter(part ->

                part.getCondition() != null

                        &&

                        part.getCondition()
                                .equals(
                                        PartCondition.FAILED.name()))
                .count();

        return AirsoftUnitOverviewResponse
                .builder()
                .unitName(unit.getName())
                .status(unit.getStatus().name())
                .healthScore(unit.getHealthScore())

                .installedParts(
                        installedParts.size())

                .wornParts(wornParts)

                .failedParts(failedParts)

                .installedPartList(
                        installedParts)

                .recentEvents(recentEvents)

                .recommendations(
                        recommendations)
                .operationalSeverity(
                        unit.getOperationalSeverity() != null
                                ? unit.getOperationalSeverity().name()
                                : null)

                .build();
    }
}