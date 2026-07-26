package com.example.operion.module.procurement.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PurchaseOrderItemResponse {

    private UUID id;

    private UUID partId;

    private String partName;

    private Integer quantity;

    private BigDecimal price;
}
