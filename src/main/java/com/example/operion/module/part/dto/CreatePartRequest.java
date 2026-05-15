package com.example.operion.module.part.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePartRequest {

  private String name;

  private String brand;

  private String category;

  private Integer expectedLifespanDays;

  private String notes;
}