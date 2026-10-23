package com.example.operion.module.inventory.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.inventory.enums.StockMovementType;
import com.example.operion.module.inventory.enums.StockReferenceType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class StockMovementResponse {

  private UUID id;

  /** BE-26 */
  private UUID partId;

  private String partName;

  private StockMovementType movementType;

  private Integer quantity;

  private Integer beforeStock;

  private Integer afterStock;

  private String notes;

  /** BE-26: what the movement came from (STOCK_ADJUSTMENT, PART_INSTANCE, PURCHASE, …) and its id. */
  private StockReferenceType referenceType;

  private UUID referenceId;

  /** BE-26: who moved it (null on rows written before 23 Oct 2026). */
  private UUID actorId;

  private String actorName;

  private LocalDateTime createdAt;
}
