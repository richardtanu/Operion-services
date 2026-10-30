package com.example.operion.common.security;

import org.springframework.security.access.AccessDeniedException;

import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;

/**
 * BE-37: who records maintenance (fitting or replacing parts, part condition,
 * work orders, service events, schedules): MANAGER scope or above, or the
 * TECHNICIAN role. At a store the Store Manager records the technicians' work
 * on the store phone. Operators and Accounting are refused.
 *
 * Call it from the entry points people reach, not from code the nightly job
 * also runs (nobody is signed in there).
 */
public final class MaintenanceAccess {

    private MaintenanceAccess() {
    }

    public static void require() {

        if (!ScopeContext.hasAtLeast(Scope.MANAGER) && !CallerRoles.hasAny(Role.TECHNICIAN)) {
            throw new AccessDeniedException("Maintenance needs a Store Manager or above, or a Technician");
        }
    }
}
