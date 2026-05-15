package com.example.operion.module.unitpart.dto;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstallPartRequest {

  private UUID airsoftUnitId;

  private UUID partId;

  private LocalDate installedDate;

  private String notes;
}