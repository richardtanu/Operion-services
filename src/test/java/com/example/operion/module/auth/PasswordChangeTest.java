package com.example.operion.module.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-32: {@code PATCH /auth/password}. The signed-in user changes their own
 * password by proving the current one; a wrong one is 400 (the app reads 401
 * as "signed out"); the new one is 8 to 72 characters (and bytes, for BCrypt)
 * and differs from the current one; a fresh token comes back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordChangeTest {

    private static final String OLD = "Old-pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    private User sm;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);
        Tenant store = tenantRepository.save(Tenant.builder().code("BE32_ST_" + s).name("Store").active(true).build());

        sm = new User();
        sm.setEmail("be32-sm-" + s + "@test.com");
        sm.setPasswordHash(passwordEncoder.encode(OLD));
        sm.setFullName("be32-sm-" + s);
        sm.setRole(Role.OPERATOR);
        sm.setScope(Scope.MANAGER);
        sm.setTenant(store);
        userRepository.save(sm);
    }

    @Test
    void theOldPasswordStopsWorking_theNewOneWorks_andTheTokenComesBack() throws Exception {

        String body = change(OLD, "New-pass-456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed"))
                .andReturn().getResponse().getContentAsString();

        login(OLD).andExpect(status().isUnauthorized());
        login("New-pass-456").andExpect(status().isOk());

        String token = JsonPath.read(body, "$.data.token");
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(sm.getEmail()));
    }

    @Test
    void aWrongCurrentPassword_is400_andNothingChanges() throws Exception {

        change("not-my-password", "New-pass-456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Your current password is wrong"));

        assertTrue(passwordEncoder.matches(OLD, stored()), "the old password still works");
    }

    @Test
    void aBadNewPassword_is400_andNothingChanges() throws Exception {

        change(OLD, "short77").andExpect(status().isBadRequest());               // 7 characters
        change(OLD, "x".repeat(73)).andExpect(status().isBadRequest());          // over 72
        change(OLD, "é".repeat(40)).andExpect(status().isBadRequest())      // 40 é: 40 characters, 80 bytes
                .andExpect(jsonPath("$.error").value("New password must be 8 to 72 characters"));
        change(OLD, OLD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Choose a password different from your current one"));
        mvc.perform(patch("/auth/password").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"" + OLD + "\"}"))
                .andExpect(status().isBadRequest());

        assertTrue(passwordEncoder.matches(OLD, stored()), "the old password still works");
    }

    // ---- helpers ----

    private ResultActions change(String current, String next) throws Exception {

        return mvc.perform(patch("/auth/password").header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private ResultActions login(String password) throws Exception {

        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + sm.getEmail() + "\",\"password\":\"" + password + "\"}"));
    }

    private String stored() {

        return userRepository.findById(sm.getId()).orElseThrow().getPasswordHash();
    }

    private String token() {

        return jwtUtil.generateToken(sm.getId().toString(), sm.getRole().name(),
                sm.getTenant().getId().toString(), sm.getScope().name());
    }
}
