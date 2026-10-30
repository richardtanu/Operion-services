package com.example.operion.module.auth.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.auth.dto.UpdateUserActiveRequest;
import com.example.operion.module.auth.dto.UpdateUserRoleRequest;
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
    // BE-34: an ADMIN or the store's Owner, checked in the service
    public ApiResponse<UserProfileResponse> updateScope(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserScopeRequest request) {

        return ApiResponse.<UserProfileResponse>builder()
                .success(true)
                .message("User scope updated")
                .data(service.updateScope(id, request.getScope()))
                .build();
    }

    @PatchMapping("/{id}/role")
    // BE-34: an ADMIN or the store's Owner, checked in the service
    public ApiResponse<UserProfileResponse> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRoleRequest request) {

        return ApiResponse.<UserProfileResponse>builder()
                .success(true)
                .message("User role updated")
                .data(service.updateRole(id, request.getRole()))
                .build();
    }

    // BE-33: switch someone off (they left) or back on; a reason to switch off
    @PatchMapping("/{id}/active")
    // BE-34: an ADMIN or the store's Owner, checked in the service
    public ApiResponse<UserProfileResponse> updateActive(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserActiveRequest request) {

        return ApiResponse.<UserProfileResponse>builder()
                .success(true)
                .message(request.getActive() ? "User switched on" : "User switched off")
                .data(service.setActive(id, request.getActive(), request.getReason()))
                .build();
    }

    // BE-33: the caller's tree, or one store in it; ?active= filters
    @GetMapping
    // BE-34: an ADMIN or the store's Owner, checked in the service
    public ApiResponse<List<UserProfileResponse>> list(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) Boolean active) {

        return ApiResponse.<List<UserProfileResponse>>builder()
                .success(true)
                .message("Users fetched")
                .data(service.list(tenantId, active))
                .build();
    }
}
