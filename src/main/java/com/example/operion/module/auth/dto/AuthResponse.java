package com.example.operion.module.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {

    private String token;

    /** BE-36: someone else chose this password; the app opens Change password first. */
    private boolean mustChangePassword;
}

