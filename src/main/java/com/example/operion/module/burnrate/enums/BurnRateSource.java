package com.example.operion.module.burnrate.enums;

/**
 * Which data backs a part's burn-rate figures (operion-burn-rate-spec.md
 * §3). Not persisted anywhere — computed fresh on every read, same as the
 * figures it describes.
 */
public enum BurnRateSource {

    COMPUTED,

    MANUAL_RATE,

    MANUAL_LEVEL,

    NONE
}
