package com.example.operion.module.workorder.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkOrderResponse {

  private UUID id;

  private String unitName;

  private UUID unitPartId;

  private String partName;

  private String technicianName;

  private String priority;

  private String status;

  private String title;

  private String description;

  private LocalDate targetDate;

  private LocalDateTime createdAt;

  private LocalDateTime assignedAt;

  private LocalDateTime startedAt;

  private LocalDateTime completedAt;

}