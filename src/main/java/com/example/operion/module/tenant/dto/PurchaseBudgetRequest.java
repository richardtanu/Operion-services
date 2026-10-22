package com.example.operion.module.tenant.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** BE-16: the store's monthly purchase budget; null clears it. */
@Getter
@Setter
public class PurchaseBudgetRequest {

    private BigDecimal amount;
}
