package com.example.operion.module.auth.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.dto.UserProfileResponse;
import com.example.operion.module.auth.entity.User;
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

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        List<UUID> effectiveTenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        if (!effectiveTenantIds.contains(user.getTenant().getId())) {
            throw new RuntimeException("User is outside your tenant hierarchy");
        }

        user.setScope(scope);
        userRepository.save(user);

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .scope(user.getScope())
                .build();
    }
}
