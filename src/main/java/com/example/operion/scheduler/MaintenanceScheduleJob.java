package com.example.operion.scheduler;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.maintenance.dto.MaintenanceEvaluationResult;
import com.example.operion.module.maintenance.service.MaintenanceRecommendationService;
import com.example.operion.module.maintenance.service.PreventiveMaintenanceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceScheduleJob {

  private final PreventiveMaintenanceService preventiveMaintenanceService;
  private final AirsoftUnitRepository unitRepository;

  @Scheduled(cron = "0 0 2 * * *") // every day at 02:00
  public void runDailyMaintenanceCheck() {

    List<AirsoftUnit> units = unitRepository.findAll();

    int evaluated = 0;
    int failed = 0;

    for (AirsoftUnit unit : units) {

      try {
        TenantContext.setTenantId(unit.getTenant().getId().toString());

        MaintenanceEvaluationResult result = preventiveMaintenanceService.evaluateUnit(unit.getId());
        evaluated++;

        if (result.isRequiresMaintenance()) {
          log.debug(
              "Unit {} requires maintenance ({} recommendations, priority {})",
              unit.getId(),
              result.getRecommendations().size(),
              result.getPriority());
        }

      } catch (Exception e) {

        failed++;
        log.warn(
            "Failed to evaluate unit {} (tenant {}): {}",
            unit.getId(),
            unit.getTenant().getId(),
            e.getMessage());

      } finally {
        TenantContext.clear();
      }
    }

    log.info(
        "Daily maintenance check complete: {} units evaluated, {} failed, out of {} total",
        evaluated,
        failed,
        units.size());
  }
}