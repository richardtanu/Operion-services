package com.example.operion.module.procurement.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePurchaseRequestAuthorizationRequest {

    /** Must all be APPROVED PurchaseRequests, same as CreatePurchaseOrderRequest today. */
    @NotEmpty
    private List<UUID> purchaseRequestIds;

    @NotEmpty
    @Valid
    private List<PurchaseRequestAuthorizationItemRequest> items;
}
