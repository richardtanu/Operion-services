package com.example.operion.module.inventory.dto;

import java.util.UUID;

import com.example.operion.module.inventory.enums.StockMovementType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockAdjustmentRequest {

  private UUID partId;

  private Integer quantity;

  private StockMovementType movementType;

  private String notes;
}
