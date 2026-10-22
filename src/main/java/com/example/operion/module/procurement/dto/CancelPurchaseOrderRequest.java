package com.example.operion.module.procurement.dto;

import lombok.Data;

/**
 * BE-20: why an order is cancelled. Optional for a DRAFT; required (non-blank)
 * once the order has been sent (ORDERED / PARTIALLY_RECEIVED). Written to the
 * order's history, so it shows in the activity log.
 */
@Data
public class CancelPurchaseOrderRequest {

    private String reason;
}
