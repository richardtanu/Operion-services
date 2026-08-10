package com.example.operion.module.part.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
public class CreatePartCategoryRequest {

  private String name;

  private String description;

  private Boolean consumable;

}