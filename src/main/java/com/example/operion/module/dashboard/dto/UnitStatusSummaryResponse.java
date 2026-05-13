package com.example.operion.module.dashboard.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UnitStatusSummaryResponse {

  private long active;

  private long maintenance;

  private long broken;

  private long upgrade;

  private long retired;
}