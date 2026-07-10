package com.example.operion.module.part.dto;

import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePartRequest {

  private String name;

  private String brand;

  private UUID categoryId;

  private UUID partTypeId;
  // private String category;

  @Min(value = 1, message = "Expected lifespan must be positive")
  private Integer expectedLifespanDays;

  private Integer minimumStock;

  private Integer reorderQuantity;

  private String notes;

  // @AssertTrue(message = "Category is required")
  // public boolean isCategoryProvided() {
  // return categoryId != null || (category != null && !category.isBlank());
  // }
}