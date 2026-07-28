package com.example.operion.module.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RealisasiResponse {

    private UUID id;

    private UUID purchaseRequestAuthorizationId;

    private String status;

    private String varianceStatus;

    private String externalOrderRef;

    private String channel;

    private UUID supplierId;

    private String supplierName;

    private String paymentRef;

    private String paymentMethod;

    private String reimbursementStatus;

    private LocalDateTime purchasedAt;

    private BigDecimal subtotal;

    private BigDecimal sellerDiscount;

    private BigDecimal platformVoucher;

    private BigDecimal shipping;

    private BigDecimal insurance;

    private BigDecimal serviceFee;

    private BigDecimal totalCost;

    private String attachments;

    private Boolean retroPurchaseFlag;

    private UUID createdById;

    private String createdByName;

    private UUID approvedById;

    private String approvedByName;

    private UUID supersedesId;

    private List<RealisasiItemResponse> items;

    private LocalDateTime createdAt;
}
