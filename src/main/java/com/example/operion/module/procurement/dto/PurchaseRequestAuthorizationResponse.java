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
public class PurchaseRequestAuthorizationResponse {

    private UUID id;

    private String status;

    private UUID approvedById;

    private String approvedByName;

    /** Needed by the client: the PRA-approver/Realisasi-creator segregation exemption depends on this. */
    private String approvedByScope;

    private List<PurchaseRequestAuthorizationItemResponse> items;

    private List<UUID> purchaseRequestIds;

    private LocalDateTime createdAt;
}
