package com.example.operion.module.auth.service;

import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.dto.LoginRequest;
import com.example.operion.module.auth.dto.RegisterRequest;
import com.example.operion.module.auth.dto.UserProfileResponse;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TenantRepository tenantRepository;
    private final TenantHierarchyService tenantHierarchyService;

    /**
     * BE-12: ADMIN-only (see {@code AuthController}). The new user always
     * starts as {@code OPERATOR} with no scope — scope is granted separately
     * via {@code PATCH /users/{id}/scope}, so nobody can register themselves
     * into privilege.
     */
    public String register(RegisterRequest request) {

        // 1. Check email exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        // 2. Resolve tenant: defaults to the calling admin's own tenant, and
        // must sit inside the admin's tenant tree either way
        Tenant tenant = request.getTenantCode() != null
                ? tenantRepository
                        .findByCode(request.getTenantCode())
                        .orElseThrow(() -> new RuntimeException("Tenant not found"))
                : tenantRepository
                        .findById(UUID.fromString(TenantContext.getTenantId()))
                        .orElseThrow(() -> new RuntimeException("Tenant not found"));

        tenantHierarchyService.requireWithinCallerTenantTree(tenant.getId());

        // 3. Create user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRole(Role.OPERATOR);
        user.setTenant(tenant);
        userRepository.save(user);

        // 4. Generate token
        return jwtUtil.generateToken(
                user.getId().toString(),
                user.getRole().name(),
                user.getTenant().getId().toString(),
                user.getScope() != null ? user.getScope().name() : null);
    }

    public String login(LoginRequest request) {

        // 1. Find user
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        // 2. Validate password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // throw new RuntimeException("Invalid credentials");
            throw new BadCredentialsException("Invalid credentials");
        }

        // 3. Return JWT
        return jwtUtil.generateToken(
                user.getId().toString(),
                user.getRole().name(),
                user.getTenant().getId().toString(),
                user.getScope() != null ? user.getScope().name() : null);
    }

    public UserProfileResponse getCurrentUser(Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

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

