package com.example.operion.module.notification.enums;

// Adding a value here needs a Flyway migration for notifications_type_check in
// the same change (CLAUDE.md rule 3) — see the latest migration that recreates it (V5) for the current list.
public enum NotificationType {
    NEW_PURCHASE_REQUEST,
    PURCHASE_REQUEST_ORDERED,
    PART_END_OF_LIFE,
    LOW_STOCK,
    REALISASI_ESCALATED,
    PURCHASE_REQUEST_APPROVED,
    PURCHASE_REQUEST_REJECTED,
    PURCHASE_REQUEST_AUTHORIZED,
    PURCHASE_AUTHORIZATION_CREATED,
    PURCHASE_REQUEST_CANCELLED
}
