package com.example.operion.module.part.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PartCategoryResponse {

  private UUID id;

  private String name;

  private String description;

}
