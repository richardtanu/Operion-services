package com.example.operion.module.auth.dto;

import java.time.LocalDateTime;
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
    /** BE-33: the store's name, for HQ's user list. */
    private String tenantName;
    /** BE-33: false = switched off. */
    private boolean active;
    private LocalDateTime deactivatedAt;
    private String deactivatedByName;
    private String deactivationReason;
}

