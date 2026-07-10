package com.example.operion.module.airsoft.dto;

import java.util.List;

import com.example.operion.module.maintenance.dto.MaintenanceRecommendationResponse;
import com.example.operion.module.serviceevent.dto.ServiceEventResponse;
import com.example.operion.module.unitpart.dto.AirsoftUnitPartResponse;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirsoftUnitOverviewResponse {

  /*
   * UNIT INFO
   */

  private String unitName;

  private String status;

  private Integer healthScore;

  /*
   * PART SUMMARY
   */

  private Integer installedParts;

  private Integer wornParts;

  private Integer failedParts;

  /*
   * DETAILS
   */

  private List<AirsoftUnitPartResponse> installedPartList;

  private List<ServiceEventResponse> recentEvents;

  private List<MaintenanceRecommendationResponse> recommendations;

  private String operationalSeverity;
}