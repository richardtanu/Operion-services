package com.example.operion.module.procurement.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

/**
 * BE-16: one store's money for one month. The words are the change queue's
 * Definitions: "spent" is counted in the month of the purchase, "committed"
 * is a snapshot as of now (not filtered by month).
 */
@Getter
@Builder
public class StoreSpendingResponse {

    private UUID tenantId;

    private String tenantName;

    /** yyyy-MM */
    private String month;

    /** Orders to HQ, plus purchases with HQ (the franchise centre) as the seller. */
    private BigDecimal spentFromCentre;

    /** Every other approved purchase. */
    private BigDecimal spentOutside;

    /** spentFromCentre + spentOutside. */
    private BigDecimal spent;

    /** Now: what's left on live limits, or what's waiting for the Owner, per limit line, whichever is larger. */
    private BigDecimal committed;

    /** The store's monthly budget, or null when it has none. */
    private BigDecimal budget;

    /** spent + committed > budget; null when there's no budget. Warns, never blocks. */
    private Boolean overBudget;

    /** Top 5 parts by spent in the month. */
    private List<SpendingPartResponse> topParts;
}
