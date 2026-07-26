package com.example.operion.module.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PurchaseOrderResponse {

    private UUID id;

    private String poNumber;

    private String status;

    private UUID supplierId;

    private String supplierName;

    private LocalDate orderDate;

    private BigDecimal totalAmount;

    private List<PurchaseOrderItemResponse> items;

    private List<UUID> purchaseRequestIds;

    private LocalDateTime createdAt;
}
