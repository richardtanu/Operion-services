package com.example.operion.module.parthistory.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.unitpart.enums.PartCondition;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartConditionHistoryResponse {

  private UUID id;

  private PartCondition previousCondition;

  private PartCondition newCondition;

  private String reason;

  private String technicianAssessment;

  private LocalDateTime createdAt;
}