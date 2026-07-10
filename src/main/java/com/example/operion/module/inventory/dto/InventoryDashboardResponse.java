package com.example.operion.module.inventory.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class InventoryDashboardResponse {

  private Integer totalParts;

  private Integer totalStock;

  private Integer lowStock;

  private Integer outOfStock;

  private Integer stockReceivedToday;

  private Integer stockUsedToday;

}