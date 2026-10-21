package com.example.operion.module.procurement.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePurchaseRequestRequest {

    /**
     * BE-14: which store the PR is for. Omitted = the caller's own store.
     * Another store: ADMIN, PROCUREMENT or OWNER+ only, and only inside the
     * caller's tenant tree.
     */
    private UUID tenantId;

    @NotNull
    private UUID requestedBy;

    @NotEmpty
    @Valid
    private List<PurchaseRequestItemRequest> items;
}
