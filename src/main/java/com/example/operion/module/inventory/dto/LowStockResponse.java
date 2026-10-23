package com.example.operion.module.inventory.dto;

import java.util.UUID;

import com.example.operion.module.inventory.enums.InventoryStockStatus;

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
}