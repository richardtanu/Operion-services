package com.example.operion.module.procurement.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PurchaseRequestResponse {

    private UUID id;

    private String status;

    private UUID requestedById;

    private String requestedByName;

    private UUID purchaseOrderId;

    private List<PurchaseRequestItemResponse> items;

    private LocalDateTime createdAt;
}
