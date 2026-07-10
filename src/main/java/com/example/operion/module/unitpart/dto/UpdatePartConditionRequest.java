package com.example.operion.module.unitpart.dto;

import com.example.operion.module.unitpart.enums.PartCondition;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePartConditionRequest {

  private PartCondition condition;

  private String reason;

  private String technicianAssessment;
}