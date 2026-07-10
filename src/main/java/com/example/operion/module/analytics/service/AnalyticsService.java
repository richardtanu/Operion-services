package com.example.operion.module.analytics.service;

import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.operion.module.analytics.dto.PartDurabilityResponse;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.unitpart.enums.UnitPartStatus;
import com.example.operion.module.unitpart.repository.AirsoftUnitPartRepository;

import java.util.UUID;

import com.example.operion.common.security.TenantContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

  private final AirsoftUnitPartRepository repository;

  public List<PartDurabilityResponse> getPartDurability() {

    UUID tenantId = UUID.fromString(
        TenantContext.getTenantId());

    List<AirsoftUnitPart> removedParts = repository.findByTenantId(tenantId)
        .stream()
        .filter(item -> item.getStatus() == UnitPartStatus.REMOVED)
        .filter(item -> item.getInstalledDate() != null &&
            item.getRemovedDate() != null)
        .toList();

    return removedParts.stream()

        .collect(
            java.util.stream.Collectors.groupingBy(
                item -> item.getPart().getId()))

        .values()
        .stream()

        .map(group -> {

          AirsoftUnitPart sample = group.get(0);

          double avgDays = group.stream()
              .mapToLong(item -> ChronoUnit.DAYS.between(
                  item.getInstalledDate(),
                  item.getRemovedDate()))
              .average()
              .orElse(0);

          return PartDurabilityResponse
              .builder()
              .partName(
                  sample.getPart().getName())
              .brand(
                  sample.getPart().getBrand())
              .averageUsageDays(avgDays)
              .replacementCount(
                  (long) group.size())
              .build();
        })

        .sorted((a, b) -> Double.compare(
            b.getAverageUsageDays(),
            a.getAverageUsageDays()))

        .toList();
  }
}