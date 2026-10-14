package com.example.operion.module.procurement.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.procurement.enums.ProcurementEntityType;

import lombok.Builder;
import lombok.Getter;

/**
 * BE-07: one entry in the procurement activity log — a single status change
 * on a PR, PRA, Realisasi or PO, with who made it. {@code previousStatus}
 * null means the record was created. {@code actorId}/{@code actorName} null
 * means a system-derived change (e.g. a PRA recomputed to FULFILLED) or a
 * row written before BE-07 started recording actors.
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
}
