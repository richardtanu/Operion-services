package com.example.operion.module.procurement.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePurchaseOrderRequest {

    /**
     * Optional at creation (a PO can be drafted before a supplier is
     * settled on); required before it can be sent to supplier.
     */
    private UUID supplierId;

    @NotEmpty
    @Valid
    private List<PurchaseOrderItemRequest> items;

    /**
     * If present, converts these APPROVED purchase requests into this PO
     * (outlet -> center -> supplier chain). If absent, this is a standalone
     * PO for center's own restocking.
     */
    private List<UUID> purchaseRequestIds;
}
