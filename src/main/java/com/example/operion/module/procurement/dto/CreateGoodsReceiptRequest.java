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

    /**
     * BE-20: deprecated. The receiver is always the signed-in user; this is
     * still accepted for one release but must equal the caller (400 otherwise).
     */
    private UUID receivedBy;

    /**
     * BE-20: photos of the goods (BE-23 upload ids, purpose GOODS_RECEIPT_PHOTO).
     * Required when you receive a purchase you made yourself (Realisasi path).
     */
    private List<UUID> goodsPhotoUploadIds;

    private LocalDateTime receivedDate;

    private String notes;

    @NotEmpty
    @Valid
    private List<GoodsReceiptItemRequest> items;
}
