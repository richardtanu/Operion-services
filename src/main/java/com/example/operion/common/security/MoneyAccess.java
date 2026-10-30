package com.example.operion.common.security;

import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;

/**
 * BE-41: who sees money amounts. OWNER scope or above (BE-06, BE-11, BE-13,
 * BE-16), and the ACCOUNTING role: Accounting reads every amount the Owner
 * reads, and acts on none of it (approving, rejecting and paying back stay
 * the Owner's). Below that, amounts come back null, not omitted.
 *
 * Seeing is all this decides; each action keeps its own check.
 */
public final class MoneyAccess {

    private MoneyAccess() {
    }

    public static boolean canSee() {

        return ScopeContext.hasAtLeast(Scope.OWNER) || CallerRoles.hasAny(Role.ACCOUNTING);
    }
}
