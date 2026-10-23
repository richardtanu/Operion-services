package com.example.operion.module.notification.enums;

// Adding a value here needs a Flyway migration for
// notifications_reference_type_check in the same change (CLAUDE.md rule 3).
public enum NotificationReferenceType {
    PURCHASE_REQUEST,
    PART,
    AIRSOFT_UNIT,
    REALISASI,
    PURCHASE_REQUEST_AUTHORIZATION,
    EXPENSE
}
