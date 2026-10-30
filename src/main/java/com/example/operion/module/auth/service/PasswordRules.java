package com.example.operion.module.auth.service;

import java.nio.charset.StandardCharsets;

/**
 * BE-36: one rule for every new password (change, reset, register): 8 to 72
 * characters, and at most 72 bytes, because BCrypt reads only the first 72
 * bytes and an accented letter takes two. {@code @Size} on the request
 * catches most cases first; this catches the bytes.
 */
final class PasswordRules {

    private PasswordRules() {
    }

    /** {@code label}: "Password" or "New password", for the message. */
    static void check(String password, String label) {

        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new RuntimeException(label + " must be 8 to 72 characters");
        }
    }
}
