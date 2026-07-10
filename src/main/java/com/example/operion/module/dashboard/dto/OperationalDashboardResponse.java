package com.example.operion.module.dashboard.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationalDashboardResponse {

  private Long totalUnits;

  private Long activeUnits;

  private Long maintenanceUnits;

  private Long criticalUnits;

  private Long installedParts;

  private Long removedParts;

  private Long replacementEvents;

  private Double averageHealthScore;

  private Long openWorkOrders;

  private Long criticalWorkOrders;

  private Long completedWorkOrders;

  private Long overdueWorkOrders;
}