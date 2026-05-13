package com.example.operion.module.serviceevent.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ServiceEventResponse {

    private UUID id;

    private String unitName;

    private String technicianName;

    private String eventType;

    private String issue;

    private String actionTaken;

    private BigDecimal cost;

    private LocalDate serviceDate;

    private LocalDate nextCheckDate;
}