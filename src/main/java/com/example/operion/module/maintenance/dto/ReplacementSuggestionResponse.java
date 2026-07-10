package com.example.operion.module.maintenance.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReplacementSuggestionResponse {

  /*
   * Replacement Part
   */

  private UUID partId;

  private String partName;

  private String brand;

  /*
   * Category
   */

  private UUID categoryId;

  private String categoryName;

  /*
   * Part Type
   */

  private UUID partTypeId;

  private String partTypeName;

  /*
   * Inventory
   */

  private Integer currentStock;

  private Integer minimumStock;

  private Integer reorderQuantity;

  /*
   * Maintenance
   */

  private Integer expectedLifespanDays;

  /*
   * Recommendation
   */

  private Boolean available;

  private Integer recommendationScore;
}
