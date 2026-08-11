package com.example.operion.module.part.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PartResponse {

  private UUID id;

  private String name;

  private String brand;

  private String category;

  private UUID categoryId;

  private String categoryName;

  private UUID partTypeId;

  private String partTypeName;

  private Integer expectedLifespanDays;

  private String notes;

  private Integer currentStock;

  private Integer minimumStock;

  private Integer reorderQuantity;

  private BigDecimal manualDailyUsage;

  private Integer manualReorderPoint;

  private Boolean retired;

  private LocalDateTime retiredAt;
}