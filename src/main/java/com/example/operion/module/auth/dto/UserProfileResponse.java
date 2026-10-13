package com.example.operion.module.auth.dto;

import java.util.UUID;

import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class UserProfileResponse {

    private UUID id;
    private String email;
    private String fullName;
    private Role role;
    private Scope scope;
    /** Tenant.code (e.g. "BLITZ_BDG") — lets a client theme itself per tenant without a separate lookup. */
    private String tenantCode;
}

