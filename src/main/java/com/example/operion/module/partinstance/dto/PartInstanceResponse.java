package com.example.operion.module.partinstance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PartInstanceResponse {

    private UUID id;

    private UUID partId;

    private String partName;

    private String barcode;

    private String status;

    private LocalDateTime receivedAt;

    private LocalDateTime takenAt;

    private String takenByName;

    private LocalDateTime exhaustedAt;

    private UUID installedUnitPartId;

    private String notes;

    private BigDecimal landedCost;
}
