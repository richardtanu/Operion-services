package com.example.operion.module.inventory.enums;

/**
 * BE-27: why an item counts as low (null when it isn't). One rule for every
 * list, the dashboard and the LOW_STOCK alert.
 */
public enum LowBecause {

    /** It has usage data (computed or manual rate) and runs out before its target days. */
    RUNS_OUT_SOON,

    /** No usage data, and stock is at or below its minimum. */
    BELOW_MINIMUM
}
