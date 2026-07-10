package com.example.operion.module.maintenance.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.inventory.entity.StockMovement;
import com.example.operion.module.inventory.enums.StockMovementType;
import com.example.operion.module.inventory.repository.StockMovementRepository;
import com.example.operion.module.serviceevent.entity.ServiceEvent;
import com.example.operion.module.serviceevent.repository.ServiceEventRepository;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.unitpart.enums.PartCondition;
import com.example.operion.module.unitpart.enums.UnitPartStatus;
import com.example.operion.module.unitpart.repository.AirsoftUnitPartRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MaintenanceRecommendationEngineService {

  private final AirsoftUnitPartRepository unitPartRepository;

  private final ServiceEventRepository serviceEventRepository;

  private final StockMovementRepository stockMovementRepository;

  public List<String> generateRecommendations(
      AirsoftUnit unit) {

    List<String> recommendations = new ArrayList<>();

    /*
     * HEALTH SCORE
     */

    if (unit.getHealthScore() != null &&
        unit.getHealthScore() < 40) {

      recommendations.add(
          "Overall unit health is critical. Perform complete internal inspection.");
    }

    /*
     * PART CONDITION
     */

    List<AirsoftUnitPart> installedParts = unitPartRepository.findByAirsoftUnitIdAndStatus(
        unit.getId(),
        UnitPartStatus.INSTALLED);

    for (AirsoftUnitPart part : installedParts) {

      if (part.getCondition() == PartCondition.WORN) {

        recommendations.add(
            part.getPart().getName()
                + " is worn and should be monitored.");
      }

      if (part.getCondition() == PartCondition.REFURBISHED) {

        recommendations.add(
            part.getPart().getName()
                + " is refurbished. Inspect during next maintenance.");
      }

      if (part.getCondition() == PartCondition.DONOR) {

        recommendations.add(
            part.getPart().getName()
                + " came from donor unit. Verify reliability.");
      }

      if (part.getCondition() == PartCondition.FAILED) {

        recommendations.add(
            part.getPart().getName()
                + " has failed and must be replaced immediately.");
      }

      /*
       * AGE CHECK
       */

      if (part.getInstalledDate() != null &&
          part.getPart().getExpectedLifespanDays() != null) {

        long age = ChronoUnit.DAYS.between(
            part.getInstalledDate(),
            LocalDate.now());

        if (age >= part.getPart().getExpectedLifespanDays()) {

          recommendations.add(
              part.getPart().getName()
                  + " exceeded expected lifespan.");
        }
      }
    }

    /*
     * LAST SERVICE
     */

    List<ServiceEvent> events = serviceEventRepository.findByAirsoftUnitIdOrderByServiceDateDesc(
        unit.getId());

    if (!events.isEmpty()) {

      long days = ChronoUnit.DAYS.between(
          events.get(0).getServiceDate(),
          LocalDate.now());

      if (days > 90) {

        recommendations.add(
            "Routine maintenance overdue (" + days + " days).");
      }
    }

    /*
     * HEAVY PART USAGE
     */

    LocalDate lastMonth = LocalDate.now().minusDays(30);

    List<StockMovement> usage = stockMovementRepository.findByMovementTypeAndCreatedAtAfter(
        StockMovementType.MAINTENANCE_USAGE,
        lastMonth.atStartOfDay());

    if (usage.size() > 25) {

      recommendations.add(
          "High maintenance activity detected during last 30 days.");
    }

    return recommendations;
  }
}