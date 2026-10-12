package com.example.operion.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

/**
 * The catch-all {@code Exception} handler used to turn Spring MVC's own web
 * exceptions into 500: an unknown URL (NoResourceFoundException) and a wrong
 * HTTP method (HttpRequestMethodNotSupportedException, not a
 * RuntimeException) both answered 500 with a stack trace in the log. Found
 * while verifying BE-12's deleted debug endpoints answered 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GlobalExceptionHandlerStatusTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    private String token;

    @BeforeEach
    void seedFixtures() {

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .code("EXH_" + UUID.randomUUID().toString().substring(0, 8))
                .name("Exception handler test").active(true).build());

        User u = new User();
        u.setEmail("exh-" + UUID.randomUUID() + "@test.com");
        u.setPasswordHash("not-a-real-hash");
        u.setRole(Role.OPERATOR);
        u.setTenant(tenant);
        userRepository.save(u);

        token = jwtUtil.generateToken(u.getId().toString(), u.getRole().name(), tenant.getId().toString(), null);
    }

    @Test
    void unknownPath_is404Not500() throws Exception {

        mvc.perform(get("/test").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongMethod_is405Not500() throws Exception {

        // /part-instances/scan-in is POST-only
        mvc.perform(get("/part-instances/scan-in").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed());
    }
}
