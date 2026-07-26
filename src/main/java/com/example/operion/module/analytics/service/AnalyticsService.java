package com.example.operion.module.analytics.service;

import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.operion.module.analytics.dto.ConsumablePerformanceResponse;
import com.example.operion.module.analytics.dto.PartDurabilityResponse;
import com.example.operion.module.partinstance.entity.PartInstance;
import com.example.operion.module.partinstance.enums.PartInstanceStatus;
import com.example.operion.module.partinstance.repository.PartInstanceRepository;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.unitpart.enums.UnitPartStatus;
import com.example.operion.module.unitpart.repository.AirsoftUnitPartRepository;

import java.util.UUID;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

  private final AirsoftUnitPartRepository repository;

  private final PartInstanceRepository partInstanceRepository;

  private final TenantHierarchyService tenantHierarchyService;

  public List<PartDurabilityResponse> getPartDurability() {

    UUID tenantId = UUID.fromString(
        TenantContext.getTenantId());

    List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

    List<AirsoftUnitPart> removedParts = repository.findByTenantIdIn(tenantIds)
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

  public List<ConsumablePerformanceResponse> getConsumablePerformance() {

    UUID tenantId = UUID.fromString(
        TenantContext.getTenantId());

    List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

    List<PartInstance> exhaustedInstances = partInstanceRepository.findByTenantIdIn(tenantIds)
        .stream()
        .filter(instance -> instance.getStatus() == PartInstanceStatus.EXHAUSTED)
        .filter(instance -> instance.getTakenAt() != null &&
            instance.getExhaustedAt() != null)
        .toList();

    return exhaustedInstances.stream()

        .collect(
            java.util.stream.Collectors.groupingBy(
                instance -> instance.getPart().getId()))

        .values()
        .stream()

        .map(group -> {

          PartInstance sample = group.get(0);

          double avgHours = group.stream()
              .mapToLong(instance -> ChronoUnit.HOURS.between(
                  instance.getTakenAt(),
                  instance.getExhaustedAt()))
              .average()
              .orElse(0);

          return ConsumablePerformanceResponse
              .builder()
              .partName(
                  sample.getPart().getName())
              .brand(
                  sample.getPart().getBrand())
              .averageUsageHours(avgHours)
              .instancesExhausted(
                  (long) group.size())
              .build();
        })

        .sorted((a, b) -> Double.compare(
            b.getAverageUsageHours(),
            a.getAverageUsageHours()))

        .toList();
  }
}