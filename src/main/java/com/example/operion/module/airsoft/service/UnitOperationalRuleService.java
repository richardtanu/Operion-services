package com.example.operion.module.airsoft.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.enums.OperationalSeverity;
import com.example.operion.module.airsoft.enums.UnitStatus;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.unitpart.enums.PartCondition;
import com.example.operion.module.unitpart.enums.UnitPartStatus;
import com.example.operion.module.unitpart.repository.AirsoftUnitPartRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UnitOperationalRuleService {

        private final AirsoftUnitRepository unitRepository;

        private final AirsoftUnitPartRepository unitPartRepository;

        public void evaluate(AirsoftUnit unit) {

                List<AirsoftUnitPart> installedParts = unitPartRepository
                                .findByAirsoftUnitIdAndStatus(
                                                unit.getId(),
                                                UnitPartStatus.INSTALLED);

                /*
                 * FAILED PARTS
                 */

                long failedParts = installedParts.stream()
                                .filter(p -> p.getCondition() == PartCondition.FAILED)
                                .count();

                /*
                 * WORN PARTS
                 */

                long wornParts = installedParts.stream()
                                .filter(part ->

                                part.getCondition() == PartCondition.WORN)
                                .count();

                /*
                 * OPERATIONAL SEVERITY
                 */

                if (failedParts >= 1
                                || unit.getHealthScore() < 40) {

                        unit.setOperationalSeverity(
                                        OperationalSeverity.CRITICAL);

                        unit.setStatus(
                                        UnitStatus.MAINTENANCE);

                } else if (wornParts >= 3
                                || unit.getHealthScore() < 70) {

                        unit.setOperationalSeverity(
                                        OperationalSeverity.WARNING);

                } else {

                        unit.setOperationalSeverity(
                                        OperationalSeverity.NORMAL);
                }

                unitRepository.save(unit);
        }
}