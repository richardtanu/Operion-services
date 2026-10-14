package com.example.operion.module.auth.service;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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

    public UserProfileResponse updateScope(UUID userId, Scope scope) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        tenantHierarchyService.requireWithinCallerTenantTree(user.getTenant().getId());

        user.setScope(scope);
        userRepository.save(user);

        return map(user);
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

    private UserProfileResponse map(User user) {

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .scope(user.getScope())
                .tenantCode(user.getTenant().getCode())
                .build();
    }
}
