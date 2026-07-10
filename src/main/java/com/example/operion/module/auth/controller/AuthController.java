package com.example.operion.module.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.auth.dto.AuthResponse;
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

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {

        String token = authService.register(request);

        ApiResponse<AuthResponse> response = ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Register successful")
                .data(new AuthResponse(token))
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
        @Valid @RequestBody LoginRequest request
    ) {

        String token = authService.login(request);

        ApiResponse<AuthResponse> response = ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Login successful")
                .data(new AuthResponse(token))
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

