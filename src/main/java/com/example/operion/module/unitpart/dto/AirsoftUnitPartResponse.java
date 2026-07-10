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

  private UUID airsoftUnitPartId;

  private String unitName;

  private String partName;

  private UUID partTypeId;

  private String partTypeName;

  private String brand;

  private String category;

  private UUID categoryId;

  private String categoryName;

  private String status;

  private LocalDate installedDate;

  private String notes;

  private Long usageDays;

  private LocalDate removedDate;

  private String condition;

  private String technicianAssessment;
}