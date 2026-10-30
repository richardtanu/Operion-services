package com.example.operion.module.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** BE-41: the money movements between two dates, newest first, with their totals. */
@Getter
@Builder
public class CashflowResponse {

    private LocalDate from;

    private LocalDate to;

    private BigDecimal totalIn;

    private BigDecimal totalOut;

    /** totalIn − totalOut. */
    private BigDecimal net;

    private List<CashflowEventResponse> events;
}
