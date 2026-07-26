package com.example.operion.module.analytics.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumablePerformanceResponse {

  private String partName;

  private String brand;

  private Double averageUsageHours;

  private Long instancesExhausted;
}
