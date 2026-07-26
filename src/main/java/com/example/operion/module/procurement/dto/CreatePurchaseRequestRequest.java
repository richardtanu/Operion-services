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

    @NotNull
    private UUID requestedBy;

    @NotEmpty
    @Valid
    private List<PurchaseRequestItemRequest> items;
}
