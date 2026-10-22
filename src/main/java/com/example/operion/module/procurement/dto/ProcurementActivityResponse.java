package com.example.operion.module.procurement.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.procurement.enums.ActorKind;
import com.example.operion.module.procurement.enums.ProcurementEntityType;

import lombok.Builder;
import lombok.Getter;

/**
 * BE-07: one entry in the procurement activity log — a single status change
 * on a PR, PRA, Realisasi or PO, with who made it. {@code previousStatus}
 * null means the record was created. {@code actorKind} (BE-22) says how to read
 * an empty actor: AUTOMATIC (derived from {@code triggeredByName}'s action,
 * described by {@code triggerReference}) or NOT_RECORDED (old test rows).
 */
@Getter
@Builder
public class ProcurementActivityResponse {

    private LocalDateTime at;

    private ProcurementEntityType entityType;

    private UUID entityId;

    /** Human reference where one exists: PO number, Realisasi external order ref. */
    private String reference;

    private UUID tenantId;

    private String tenantName;

    private UUID actorId;

    private String actorName;

    private String previousStatus;

    private String newStatus;

    private String reason;

    /** BE-22 */
    private ActorKind actorKind;

    /** BE-22: whose action caused an automatic change. */
    private String triggeredByName;

    /** BE-22: what caused it, in words. */
    private String triggerReference;
}
