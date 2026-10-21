package com.example.operion.common.security;

import java.util.Arrays;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.operion.module.auth.enums.Role;

/**
 * The caller's functional role, for service-layer checks that combine a role
 * with a scope ("MANAGER scope or the PROCUREMENT role"). {@code @PreAuthorize}
 * can only see roles, and scope lives in {@link ScopeContext}, so rules that
 * need both are written in the service with this and
 * {@link ScopeContext#hasAtLeast}.
 */
public final class CallerRoles {

    private CallerRoles() {
    }

    /** True if the caller holds any of the given roles. */
    public static boolean hasAny(Role... roles) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null) {
            return false;
        }

        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> Arrays.stream(roles).anyMatch(r -> a.equals("ROLE_" + r.name())));
    }
}
