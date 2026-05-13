package com.example.operion.module.auth.controller;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.operion.common.security.TenantContext;

@RestController
public class TestController {

    @GetMapping("/test")
    public String test() {
        return "Protected endpoint accessed!";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOnly() {
        return "Admin access granted";
    }

    @GetMapping("/api/test/me")
    public Map<String, Object> me(
            Authentication authentication) {

        return Map.of(
                "userId", authentication.getPrincipal(),
                "role", authentication.getAuthorities(),
                "tenantId", TenantContext.getTenantId());
    }
}