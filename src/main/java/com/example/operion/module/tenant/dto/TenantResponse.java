package com.example.operion.module.tenant.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TenantResponse {

    private UUID id;

    private String name;

    private String code;

    private Boolean active;

    private UUID parentId;

    private String parentName;
}
