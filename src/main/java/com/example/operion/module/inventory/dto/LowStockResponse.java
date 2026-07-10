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
}