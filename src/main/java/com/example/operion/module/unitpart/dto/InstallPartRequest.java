package com.example.operion.module.unitpart.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstallPartRequest {

  @NotNull(message = "Airsoft unit id is required")
  private UUID airsoftUnitId;

  @NotNull(message = "Part id is required")
  private UUID partId;

  @NotNull(message = "Installed date is required")
  private LocalDate installedDate;

  private String notes;
}
