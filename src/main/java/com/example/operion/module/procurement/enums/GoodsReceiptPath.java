package com.example.operion.module.procurement.enums;

/**
 * Which of the two procurement transactions a goods receipt closes — the
 * franchise leg ({@code PurchaseOrder}) or a direct purchase
 * ({@code Realisasi}). Used only as a list filter (BE-08); the receipt itself
 * records it as which of {@code purchaseOrder}/{@code realisasi} is set.
 */
public enum GoodsReceiptPath {
    PO,
    REALISASI
}
