package com.example.operion.module.part.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RetirePartRequest {

  @NotBlank
  private String reason;

}