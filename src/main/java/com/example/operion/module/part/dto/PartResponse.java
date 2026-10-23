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

  /** BE-21: the HQ catalog item this store part is (null on HQ rows and unlinked items). */
  private UUID catalogPartId;

  /** BE-21: read from the HQ item (itself on HQ rows). */
  private boolean soldByHq;

  /** BE-21: read from the HQ item (itself on HQ rows). Hide these from "Buy elsewhere". */
  private boolean mandatoryFromHq;

  /** BE-26: which store the item belongs to, so HQ can tell stores apart. */
  private UUID tenantId;

  private String tenantName;
}