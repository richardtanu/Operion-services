package com.example.operion.module.maintenance.dto;

import java.util.ArrayList;
import java.util.List;

import com.example.operion.module.workorder.enums.WorkOrderPriority;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceEvaluationResult {

  @Builder.Default
  private boolean requiresMaintenance = false;

  @Builder.Default
  private WorkOrderPriority priority = WorkOrderPriority.LOW;

  @Builder.Default
  private List<MaintenanceRecommendation> recommendations = new ArrayList<>();

}