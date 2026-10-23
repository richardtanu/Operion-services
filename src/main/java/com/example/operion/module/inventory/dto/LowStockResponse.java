package com.example.operion.module.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.example.operion.module.inventory.enums.InventoryStockStatus;
import com.example.operion.module.inventory.enums.LowBecause;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class LowStockResponse {

  private UUID partId;

  private String partName;

  private Integer currentStock;

  private Integer minimumStock;

  private InventoryStockStatus status;

  /** BE-26: which store the row belongs to, so HQ can tell stores apart. */
  private UUID tenantId;

  private String tenantName;

  /** BE-26: the HQ catalog item this store item is, if linked. */
  private UUID catalogPartId;

  /** BE-27: why it's low by the one rule (RUNS_OUT_SOON / BELOW_MINIMUM), null when it isn't. */
  private LowBecause lowBecause;

  /** BE-27: days of cover and the store's target, when the item has usage data. */
  private BigDecimal daysOfCover;

  private Integer targetDays;
}