package com.example.operion.module.maintenance.dto;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class MaintenanceScheduleResponse {

  private UUID id;

  private UUID airsoftUnitId;

  private String unitName;

  private String title;

  private String description;

  private Integer intervalDays;

  private LocalDate lastMaintenanceDate;

  private LocalDate nextDueDate;

  private Boolean autoGenerateWorkOrder;

  private Boolean active;
}
