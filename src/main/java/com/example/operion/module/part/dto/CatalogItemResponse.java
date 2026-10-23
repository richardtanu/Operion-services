package com.example.operion.module.part.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

/**
 * BE-21: one item HQ sells, as a store sees it. {@code storePartId} is the
 * caller's own part for it (null until the store first orders it), so the
 * order screen can show "in stock: 14".
 */
@Getter
@Builder
public class CatalogItemResponse {

    private UUID id;

    private String name;

    private String brand;

    private String categoryName;

    private String partTypeName;

    private boolean consumable;

    private boolean mandatoryFromHq;

    /** BE-28: HQ's price per unit; Owner+ (and HQ admins) only, null below. */
    private BigDecimal price;

    private UUID storePartId;

    private Integer storeCurrentStock;
}
