package com.example.operion.module.procurement.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PurchaseRequestItemResponse {

    private UUID id;

    private UUID partId;

    private String partName;

    private Integer quantity;
}
