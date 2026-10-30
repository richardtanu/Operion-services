package com.example.operion.module.procurement.enums;

/** BE-41: what moved the money on a cashflow row. */
public enum CashflowKind {
    /** A purchase paid from the store's own account, on its purchase date. */
    PURCHASE,
    /** An own-money purchase paid back to the buyer, on the pay-back date. */
    PURCHASE_PAYBACK,
    /** An expense the store paid, on its date. */
    EXPENSE,
    /** An own-money expense paid back to the claimant, on the pay-back date. */
    EXPENSE_PAYBACK,
    /** An order sent to HQ, on its order date (when the Owner sent it). */
    ORDER_TO_HQ
}
