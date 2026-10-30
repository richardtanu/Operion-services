package com.example.operion.module.upload.enums;

/**
 * BE-23: what an upload is for. Part of the object key
 * ({tenantId}/{purpose}/{uploadId}) and checked when the upload is attached, so
 * a receipt photo can't be attached where a goods photo is expected.
 *
 * Stored as text with no DB CHECK constraint on purpose, so adding a purpose
 * later is a Java-only change (CLAUDE.md rule 3 is about CHECK constraints).
 */
public enum UploadPurpose {
    /** BE-20: photo of received goods, required when you receive your own purchase. */
    GOODS_RECEIPT_PHOTO,
    /** BE-19: receipt for a non-stock expense claim. */
    EXPENSE_RECEIPT,
    /** BE-39: marketplace receipt for a Realisasi (the free-text attachments field stays, for links). */
    PURCHASE_RECEIPT
}
