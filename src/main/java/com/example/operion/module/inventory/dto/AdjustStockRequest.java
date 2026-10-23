package com.example.operion.module.inventory.dto;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdjustStockRequest {

  private UUID partId;

  private Integer quantity;

  private String notes;

  /** BE-25: required — this shortcut is for repairing data (ADMIN only). */
  private String reason;
}
