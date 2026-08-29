package com.example.operion.module.part.dto;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PartsByCategoryResponse {

  private UUID categoryId;

  private String categoryName;

  private List<PartResponse> parts;
}
