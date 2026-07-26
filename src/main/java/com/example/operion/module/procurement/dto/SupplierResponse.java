package com.example.operion.module.procurement.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SupplierResponse {

    private UUID id;

    private String name;

    private String contact;

    private LocalDateTime createdAt;
}
