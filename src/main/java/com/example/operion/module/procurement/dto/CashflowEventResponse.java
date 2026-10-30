package com.example.operion.module.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.example.operion.module.procurement.enums.CashflowDirection;
import com.example.operion.module.procurement.enums.CashflowKind;

import lombok.Builder;
import lombok.Getter;

/** BE-41: one money movement. */
@Getter
@Builder
public class CashflowEventResponse {

    private LocalDate date;

    private CashflowDirection direction;

    private CashflowKind kind;

    private BigDecimal amount;

    /** The marketplace order ref, the order number, or the expense's description. */
    private String reference;

    /** Who the money went to: the seller, the person paid back, or HQ. Null for a store expense. */
    private String counterparty;

    /** CASH / TRANSFER on a pay-back; null otherwise. */
    private String method;

    /** REALISASI, EXPENSE or PURCHASE_ORDER, with its id, to open the record. */
    private String recordType;

    private UUID recordId;

    /** The record's status now, e.g. a purchase still waiting for the Owner. */
    private String recordStatus;

    private UUID tenantId;

    private String tenantName;
}
