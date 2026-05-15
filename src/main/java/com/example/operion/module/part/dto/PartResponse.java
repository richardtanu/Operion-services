package com.example.operion.module.part.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PartResponse {

  private UUID id;

  private String name;

  private String brand;

  private String category;

  private Integer expectedLifespanDays;

  private String notes;
}