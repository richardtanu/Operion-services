package com.example.operion.module.workorder.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.example.operion.module.workorder.enums.WorkOrderPriority;

import lombok.*;

@Data
public class CreateWorkOrderRequest {

  private UUID airsoftUnitId;

  private UUID technicianId;

  private WorkOrderPriority priority;

  private String title;

  private String description;

  private LocalDate targetDate;

  private UUID airsoftUnitPartId;
}