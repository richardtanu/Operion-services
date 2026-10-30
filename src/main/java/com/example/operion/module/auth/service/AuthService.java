package com.example.operion.module.auth.service;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.dto.AuthResponse;
import com.example.operion.module.auth.dto.ChangePasswordRequest;
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
     * BE-12: an ADMIN, inside their tree; since BE-34 also a store's Owner,
     * into their own store only. The new user always starts as
     * {@code OPERATOR} with no scope — scope is granted separately via
     * {@code PATCH /users/{id}/scope}, so nobody can register themselves into
     * privilege.
     */
    public AuthResponse register(RegisterRequest request) {

        User caller = userRepository.findById(UUID.fromString(
                SecurityContextHolder.getContext().getAuthentication().getName()))
                .orElseThrow(() -> new AccessDeniedException("Signed-in user not found"));

        if (!UserService.isAdmin(caller)) {
            UserService.requireStoreOwner(caller);
        }

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

        if (UserService.isAdmin(caller)) {
            tenantHierarchyService.requireWithinCallerTenantTree(tenant.getId());
        } else if (!tenant.getId().equals(caller.getTenant().getId())) {
            throw new AccessDeniedException("Owners add people to their own store only");
        }

        // 3. Create user. BE-36: someone else chose this first password, so the
        // new person changes it at their first sign-in
        PasswordRules.check(request.getPassword(), "Password");

        User user = new User();
        user.setEmail(request.getEmail());
        user.setNewPassword(passwordEncoder.encode(request.getPassword()), false);
        user.setFullName(request.getFullName());
        user.setRole(Role.OPERATOR);
        user.setTenant(tenant);
        userRepository.save(user);

        // 4. Generate token
        return tokenFor(user);
    }

    public AuthResponse login(LoginRequest request) {

        // 1. Find user
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        // 2. Validate password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // throw new RuntimeException("Invalid credentials");
            throw new BadCredentialsException("Invalid credentials");
        }

        // BE-33: checked after the password, so only someone who knows it learns this
        if (!user.isActive()) {
            throw new AccessDeniedException("This account is switched off");
        }

        // 3. Return JWT
        return tokenFor(user);
    }

    /**
     * BE-32: the signed-in user changes their own password, proving the
     * current one first. A wrong current password is 400, not 401: the app
     * reads 401 as "signed out". Returns a fresh token, like login. BE-36:
     * tokens issued before the change stop working, and a "must change" flag
     * from a reset or a new account is cleared.
     */
    public AuthResponse changePassword(Authentication authentication, ChangePasswordRequest request) {

        User user = userRepository.findById(UUID.fromString(authentication.getName()))
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Your current password is wrong");
        }

        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new RuntimeException("Choose a password different from your current one");
        }

        // @Size counts characters; BCrypt counts bytes (an accented letter is two)
        PasswordRules.check(request.getNewPassword(), "New password");

        user.setNewPassword(passwordEncoder.encode(request.getNewPassword()), true);
        userRepository.save(user);

        return tokenFor(user);
    }

    private AuthResponse tokenFor(User user) {

        return new AuthResponse(
                jwtUtil.generateToken(
                        user.getId().toString(),
                        user.getRole().name(),
                        user.getTenant().getId().toString(),
                        user.getScope() != null ? user.getScope().name() : null),
                user.isMustChangePassword());
    }

    public UserProfileResponse getCurrentUser(Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return UserService.map(user);
    }
}

