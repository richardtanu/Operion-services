package com.example.operion.module.part.dto;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

/** BE-21: which HQ catalog item a store's part is; null removes the link. */
@Getter
@Setter
public class LinkCatalogPartRequest {

    private UUID catalogPartId;
}
