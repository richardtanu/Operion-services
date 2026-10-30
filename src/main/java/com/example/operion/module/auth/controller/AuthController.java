package com.example.operion.module.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.auth.dto.AuthResponse;
import com.example.operion.module.auth.dto.ChangePasswordRequest;
import com.example.operion.module.auth.dto.LoginRequest;
import com.example.operion.module.auth.dto.RegisterRequest;
import com.example.operion.module.auth.dto.UserProfileResponse;
import com.example.operion.module.auth.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // BE-12: was public, which let anyone on the internet create an account
    // (and, until the same change, pick their own scope). Users are now
    // created by an ADMIN; the very first ADMIN is a direct DB insert.
    @PostMapping("/register")
    // BE-34: an ADMIN or the store's Owner, checked in the service
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {

        AuthResponse result = authService.register(request);

        ApiResponse<AuthResponse> response = ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Register successful")
                .data(result)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
        @Valid @RequestBody LoginRequest request
    ) {

        AuthResponse result = authService.login(request);

        ApiResponse<AuthResponse> response = ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Login successful")
                .data(result)
                .build();

        return ResponseEntity.ok(response);
    }

    // BE-32: any signed-in user, own password only; returns a fresh token
    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {

        AuthResponse result = authService.changePassword(authentication, request);

        ApiResponse<AuthResponse> response = ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Password changed")
                .data(result)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(
            Authentication authentication
    ) {

        UserProfileResponse user =
                authService.getCurrentUser(authentication);

        ApiResponse<UserProfileResponse> response =
                ApiResponse.<UserProfileResponse>builder()
                        .success(true)
                        .message("User fetched successfully")
                        .data(user)
                        .build();

        return ResponseEntity.ok(response);
    }
}

