package com.example.operion.module.analytics.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.unitpart.enums.PartCondition;
import com.example.operion.module.unitpart.enums.UnitPartStatus;
import com.example.operion.module.unitpart.repository.AirsoftUnitPartRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UnitHealthService {

  private final AirsoftUnitPartRepository repository;

  private final AirsoftUnitRepository airsoftUnitRepository;

  public void recalculateHealth(
      AirsoftUnit unit) {

    List<AirsoftUnitPart> history = repository.findByAirsoftUnitIdOrderByInstalledDateDesc(
        unit.getId());

    int score = 100;

    for (AirsoftUnitPart item : history) {

      if (item.getStatus() == UnitPartStatus.REMOVED) {

        score -= calculatePenalty(item);
      }
    }

    if (score < 0) {
      score = 0;
    }

    unit.setHealthScore(score);

    airsoftUnitRepository.save(unit);
  }

  private int calculatePenalty(AirsoftUnitPart part) {

    String category = part.getPart().getCategory().getName();

    boolean failed = part.getCondition() == PartCondition.FAILED;

    boolean worn = part.getCondition() == PartCondition.WORN;

    if (!failed && !worn) {
      return 0;
    }

    switch (category) {

      // CRITICAL PARTS

      case "Gearbox Shell":
      case "Gearset":
      case "Cylinder":
      case "Cylinder Head":
      case "Piston":
      case "Motor":
      case "MOSFET":

        return failed ? 20 : 10;

      // IMPORTANT PARTS

      case "Hop-Up Chamber":
      case "Inner Barrel":
      case "Nozzle":
      case "Spring":
      case "Spring Guide":

        return failed ? 10 : 5;

      // MINOR PARTS

      default:

        return failed ? 5 : 2;
    }
  }
}