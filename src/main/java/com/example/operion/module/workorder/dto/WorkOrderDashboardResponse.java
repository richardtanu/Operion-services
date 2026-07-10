package com.example.operion.module.workorder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderDashboardResponse {

  private long open;

  private long assigned;

  private long inProgress;

  private long waitingParts;

  private long completedThisMonth;

  private long overdue;
}