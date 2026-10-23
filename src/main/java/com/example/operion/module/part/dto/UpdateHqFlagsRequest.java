package com.example.operion.module.part.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** BE-21: HQ's flags on one of its own items. Null leaves a flag unchanged. */
@Getter
@Setter
public class UpdateHqFlagsRequest {

    private Boolean soldByHq;

    /** Stores must buy it from HQ. Requires soldByHq. */
    private Boolean mandatoryFromHq;

    /** BE-28: HQ's price per unit (> 0). Required before soldByHq can be true. */
    private BigDecimal price;
}
