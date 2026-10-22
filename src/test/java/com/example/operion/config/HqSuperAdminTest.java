package com.example.operion.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;

/**
 * BE-21 side effect 3: the HQ super admin from V11, created locked, and the
 * one-time bootstrap that gives it its first password. Passwords here are
 * random per run; everything rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HqSuperAdminTest {

    static final String EMAIL = "tanugraha.richard@gmail.com";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private InitialPasswordBootstrap bootstrap;

    @Test
    void v11_createsTheSuperAdminAtHq_locked() throws Exception {

        User admin = userRepository.findByEmail(EMAIL).orElseThrow();

        assertEquals(Role.ADMIN, admin.getRole());
        assertEquals(Scope.PRINCIPAL, admin.getScope());
        assertEquals("HQ_BANDUNG", admin.getTenant().getCode());
        assertTrue(admin.getTenant().getParent() == null, "HQ is the root");

        // no password matches a locked account, not even the marker itself
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"!\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void bootstrap_setsTheFirstPassword_thenLoginWorks() throws Exception {

        String password = "Hq-" + UUID.randomUUID();

        assertTrue(bootstrap.apply(EMAIL, password));
        assertTrue(passwordEncoder.matches(password, userRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash()));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());

        // a second run (variables left set) changes nothing
        assertFalse(bootstrap.apply(EMAIL, "Other-" + UUID.randomUUID()));
        assertTrue(passwordEncoder.matches(password, userRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash()));
    }

    @Test
    void bootstrap_neverTouchesARealPassword_orTakesAShortOne() {

        User someone = new User();
        someone.setEmail("be21-real-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com");
        String hash = passwordEncoder.encode("Existing-" + UUID.randomUUID());
        someone.setPasswordHash(hash);
        someone.setFullName("Someone");
        someone.setRole(Role.ADMIN);
        someone.setTenant(userRepository.findByEmail(EMAIL).orElseThrow().getTenant());
        userRepository.save(someone);

        assertFalse(bootstrap.apply(someone.getEmail(), "Replaced-" + UUID.randomUUID()));
        assertEquals(hash, userRepository.findByEmail(someone.getEmail()).orElseThrow().getPasswordHash());

        assertFalse(bootstrap.apply(EMAIL, "short"));
        assertEquals("!", userRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash());

        assertFalse(bootstrap.apply("nobody-" + UUID.randomUUID() + "@test.com", "Long-enough-" + UUID.randomUUID()));
    }

    @Test
    void theSuperAdminSeesTheWholeTree() throws Exception {

        User admin = userRepository.findByEmail(EMAIL).orElseThrow();
        String token = jwtUtil.generateToken(admin.getId().toString(), admin.getRole().name(),
                admin.getTenant().getId().toString(), admin.getScope().name());

        mvc.perform(get("/tenants/tree").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("HQ_BANDUNG"))
                .andExpect(jsonPath("$.data[?(@.code=='BLITZ_BDG')].parentName").value("HQ BANDUNG"));
    }
}
