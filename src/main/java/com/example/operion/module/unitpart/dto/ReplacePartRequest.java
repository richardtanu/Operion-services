package com.example.operion.module.unitpart.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.example.operion.module.unitpart.enums.PartCondition;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReplacePartRequest {

  /*
   * PART TO INSTALL
   */

  @NotNull(message = "New part id is required")
  private UUID newPartId;

  /*
   * OLD PART CONDITION WHEN REMOVED
   */

  @NotNull(message = "Removed part condition is required")
  private PartCondition removedPartCondition;

  /*
   * OPTIONAL WORK ORDER
   */

  private UUID workOrderId;

  /*
   * AUDIT
   */

  private String reason;

  private String technicianAssessment;

  private String notes;

  private LocalDate installedDate;

  private LocalDate removedDate;
}