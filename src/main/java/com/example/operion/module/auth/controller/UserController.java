package com.example.operion.module.auth.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.auth.dto.UpdateUserScopeRequest;
import com.example.operion.module.auth.dto.UserProfileResponse;
import com.example.operion.module.auth.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    @PatchMapping("/{id}/scope")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserProfileResponse> updateScope(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserScopeRequest request) {

        return ApiResponse.<UserProfileResponse>builder()
                .success(true)
                .message("User scope updated")
                .data(service.updateScope(id, request.getScope()))
                .build();
    }
}
