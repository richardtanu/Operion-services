package com.example.operion.module.part.dto;

import lombok.Getter;
import lombok.Setter;

/** BE-21: HQ's flags on one of its own items. Null leaves a flag unchanged. */
@Getter
@Setter
public class UpdateHqFlagsRequest {

    private Boolean soldByHq;

    /** Stores must buy it from HQ. Requires soldByHq. */
    private Boolean mandatoryFromHq;
}
