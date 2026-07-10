package com.example.operion.module.analytics.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartDurabilityResponse {

  private String partName;

  private String brand;

  private Double averageUsageDays;

  private Long replacementCount;
}