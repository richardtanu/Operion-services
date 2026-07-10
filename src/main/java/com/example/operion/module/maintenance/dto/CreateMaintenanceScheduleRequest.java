package com.example.operion.module.maintenance.dto;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMaintenanceScheduleRequest {

  private UUID airsoftUnitId;

  private String title;

  private String description;

  private Integer intervalDays;

  private LocalDate lastMaintenanceDate;

  private Boolean autoGenerateWorkOrder;
}