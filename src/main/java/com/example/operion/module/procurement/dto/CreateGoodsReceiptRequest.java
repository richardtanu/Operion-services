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

    @NotNull
    private UUID purchaseOrderId;

    @NotNull
    private UUID receivedBy;

    private LocalDateTime receivedDate;

    private String notes;

    @NotEmpty
    @Valid
    private List<GoodsReceiptItemRequest> items;
}
