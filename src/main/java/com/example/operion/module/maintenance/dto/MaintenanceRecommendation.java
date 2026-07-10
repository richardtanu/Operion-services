package com.example.operion.module.maintenance.dto;

import com.example.operion.module.maintenance.enums.RecommendationSeverity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceRecommendation {

  private RecommendationSeverity severity;

  private String title;

  private String message;

}