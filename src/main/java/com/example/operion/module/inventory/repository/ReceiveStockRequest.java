package com.example.operion.module.inventory.repository;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReceiveStockRequest {

  @NotNull
  private UUID partId;

  @Min(1)
  private Integer quantity;

  private String notes;
}
