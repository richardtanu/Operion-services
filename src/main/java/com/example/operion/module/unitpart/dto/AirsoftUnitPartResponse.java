package com.example.operion.module.unitpart.dto;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AirsoftUnitPartResponse {

  private UUID id;

  private String unitName;

  private String partName;

  private String brand;

  private String category;

  private String status;

  private LocalDate installedDate;

  private String notes;
}