package com.example.operion.module.part.dto;

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
public class PartTypeResponse {

  private UUID id;

  private UUID categoryId;

  private String categoryName;

  private String name;

  private String description;

}