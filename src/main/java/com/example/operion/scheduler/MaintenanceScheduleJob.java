package com.example.operion.scheduler;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.maintenance.service.MaintenanceRecommendationService;
import com.example.operion.module.maintenance.service.PreventiveMaintenanceService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MaintenanceScheduleJob {

  private final PreventiveMaintenanceService preventiveMaintenanceService;
  private final AirsoftUnitRepository unitRepository;

  @Scheduled(cron = "0 0 2 * * *") // every day at 02:00
  public void runDailyMaintenanceCheck() {

    List<AirsoftUnit> units = unitRepository.findAll();

    for (AirsoftUnit unit : units) {
      preventiveMaintenanceService.evaluateUnit(unit.getId());
    }
  }
}