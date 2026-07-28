package com.example.operion.module.procurement.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateGoodsReceiptRequest {

    /** Exactly one of purchaseOrderId / realisasiId must be set. */
    private UUID purchaseOrderId;

    private UUID realisasiId;

    @NotNull
    private UUID receivedBy;

    private LocalDateTime receivedDate;

    private String notes;

    @NotEmpty
    @Valid
    private List<GoodsReceiptItemRequest> items;
}
