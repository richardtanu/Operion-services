package com.example.operion.module.maintenance.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceRecommendationResponse {

  private String category;

  private String severity;

  private String recommendation;
}