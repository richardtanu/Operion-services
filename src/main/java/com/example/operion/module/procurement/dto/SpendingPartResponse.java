package com.example.operion.module.procurement.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

/** BE-16: one part's spent in the month (landed cost for purchases, line total for orders). */
@Getter
@Builder
public class SpendingPartResponse {

    private UUID partId;

    private String partName;

    private BigDecimal amount;
}
