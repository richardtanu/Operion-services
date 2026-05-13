package com.example.operion.module.serviceevent.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.example.operion.module.serviceevent.enums.ServiceEventType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateServiceEventRequest {

  private UUID airsoftUnitId;

  private UUID technicianId;

  private ServiceEventType eventType;

  private String issue;

  private String actionTaken;

  private BigDecimal cost;

  private LocalDate nextCheckDate;

  private String notes;
}