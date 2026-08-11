package com.example.operion.module.burnrate.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.example.operion.module.burnrate.enums.BurnRateSource;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * operion-burn-rate-spec.md §6. burnRateSource/observationDays/eventCount
 * are not diagnostics — they're what tells "5 days of cover from a manual
 * guess" apart from "5 days of cover from 378 observed takes."
 */
@Getter
@Setter
@Builder
public class BurnRateResponse {

    private UUID partId;

    private String partName;

    private Integer currentStock;

    private BurnRateSource burnRateSource;

    /** Stock units per day. Null for MANUAL_LEVEL and NONE. */
    private BigDecimal burnRate;

    /** Null for MANUAL_LEVEL and NONE — those aren't rate-based. */
    private Integer observationDays;

    /** Takes counted within the window. Null for MANUAL_LEVEL and NONE. */
    private Integer eventCount;

    /** Null for MANUAL_LEVEL and NONE (spec §3.1) — never 0, never a divide-by-zero. */
    private BigDecimal daysOfCover;

    private Integer targetDays;

    private Integer suggestedQty;

    /** Only meaningful for MANUAL_LEVEL. */
    private Integer manualReorderPoint;

    /** Only meaningful for MANUAL_LEVEL: currentStock <= manualReorderPoint. */
    private Boolean belowLevel;
}
