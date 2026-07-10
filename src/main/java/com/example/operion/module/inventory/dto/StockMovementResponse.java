package com.example.operion.module.inventory.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.inventory.enums.StockMovementType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class StockMovementResponse {

  private UUID id;

  private String partName;

  private StockMovementType movementType;

  private Integer quantity;

  private Integer beforeStock;

  private Integer afterStock;

  private String notes;

  private LocalDateTime createdAt;
}
