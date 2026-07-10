package com.example.operion.module.part.dto;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePartRequest {

  private UUID id;

  private UUID partId;

  private UUID categoryId;

  private UUID partTypeId;

  private String name;

  private String manufacturer;

  private Integer expectedLifespan;

  private Integer minimumStock;

  private Integer reorderQuantity;

  private String notes;

}