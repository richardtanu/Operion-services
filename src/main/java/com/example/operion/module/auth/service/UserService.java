package com.example.operion.module.auth.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.dto.UserProfileResponse;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private final TenantHierarchyService tenantHierarchyService;

    /**
     * BE-31: a scope is granted only up to the granter's own, never to
     * themself, and never over someone already above them. The granter's
     * scope is read from their stored row, not the token: a token lives 24
     * hours, and an Admin demoted since must not keep granting with the old
     * scope. An ADMIN with no scope can grant none.
     */
    public UserProfileResponse updateScope(UUID userId, Scope scope) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        tenantHierarchyService.requireWithinCallerTenantTree(user.getTenant().getId());

        User caller = currentUser();

        if (user.getId().equals(caller.getId())) {
            throw new AccessDeniedException("You cannot change your own scope");
        }

        if (!atOrBelow(scope, caller.getScope())) {
            throw new AccessDeniedException("You can only grant a scope up to your own");
        }

        if (user.getScope() != null && !atOrBelow(user.getScope(), caller.getScope())) {
            throw new AccessDeniedException("You cannot change the scope of someone above you");
        }

        user.setScope(scope);
        userRepository.save(user);

        return map(user);
    }

    /** BE-31: no ceiling (no scope) means nothing can be granted. */
    private static boolean atOrBelow(Scope scope, Scope ceiling) {

        return ceiling != null && scope.ordinal() <= ceiling.ordinal();
    }

    /**
     * BE-07 prerequisite: until this existed, a user's functional role could
     * only be changed by direct DB edit, so no PROCUREMENT user could exist to
     * receive PRA notifications or read the procurement log. ADMIN-only (see
     * {@code UserController}), tenant-tree-contained like the scope grant.
     * An admin cannot change their own role, so nobody can strip the last
     * ADMIN off themselves by accident.
     */
    public UserProfileResponse updateRole(UUID userId, Role role) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        tenantHierarchyService.requireWithinCallerTenantTree(user.getTenant().getId());

        String callerId = SecurityContextHolder.getContext().getAuthentication().getName();

        if (user.getId().toString().equals(callerId)) {
            throw new AccessDeniedException("You cannot change your own role");
        }

        user.setRole(role);
        userRepository.save(user);

        return map(user);
    }

    /**
     * BE-33: switch a user off (they left) or back on. ADMIN-only (see
     * {@code UserController}), inside the caller's tree, never yourself, and
     * never someone whose scope is above yours (BE-31's rule, stored scope).
     * Switching off needs a reason; switching back on clears who/when/why.
     */
    public UserProfileResponse setActive(UUID userId, boolean active, String reason) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        tenantHierarchyService.requireWithinCallerTenantTree(user.getTenant().getId());

        User caller = currentUser();

        if (user.getId().equals(caller.getId())) {
            throw new AccessDeniedException("You cannot switch yourself off or on");
        }

        if (user.getScope() != null && !atOrBelow(user.getScope(), caller.getScope())) {
            throw new AccessDeniedException("You cannot switch off someone above you");
        }

        if (active) {

            user.setActive(true);
            user.setDeactivatedAt(null);
            user.setDeactivatedBy(null);
            user.setDeactivationReason(null);

        } else {

            if (reason == null || reason.isBlank()) {
                throw new RuntimeException("Say why this person is being switched off");
            }

            if (user.isActive()) {
                user.setActive(false);
                user.setDeactivatedAt(LocalDateTime.now());
                user.setDeactivatedBy(caller);
                user.setDeactivationReason(reason.trim());
            }
        }

        userRepository.save(user);

        return map(user);
    }

    /**
     * BE-33: {@code GET /users}: the caller's tree, or one store in it
     * ({@code tenantId}, through {@code readScope}), optionally only the
     * switched-on or switched-off ones.
     */
    public List<UserProfileResponse> list(UUID tenantId, Boolean active) {

        List<UUID> tenantIds = tenantId != null
                ? tenantHierarchyService.readScope(tenantId)
                : tenantHierarchyService.getEffectiveTenantIds(UUID.fromString(TenantContext.getTenantId()));

        return userRepository.findInTenants(tenantIds, active).stream()
                .map(UserService::map)
                .toList();
    }

    private User currentUser() {

        return userRepository.findById(UUID.fromString(
                SecurityContextHolder.getContext().getAuthentication().getName()))
                .orElseThrow(() -> new AccessDeniedException("Signed-in user not found"));
    }

    /** Also {@code /auth/me}'s shape. */
    static UserProfileResponse map(User user) {

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .scope(user.getScope())
                .tenantCode(user.getTenant().getCode())
                .tenantName(user.getTenant().getName())
                .active(user.isActive())
                .deactivatedAt(user.getDeactivatedAt())
                .deactivatedByName(user.getDeactivatedBy() != null ? user.getDeactivatedBy().getFullName() : null)
                .deactivationReason(user.getDeactivationReason())
                .build();
    }
}
