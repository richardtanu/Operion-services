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
public class GoodsReceiptResponse {

    private UUID id;

    private UUID purchaseOrderId;

    private String poNumber;

    private UUID realisasiId;

    private String externalOrderRef;

    private UUID receivedById;

    private String receivedByName;

    private LocalDateTime receivedDate;

    private String notes;

    private List<GoodsReceiptItemResponse> items;
}
