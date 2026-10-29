package com.example.operion.module.upload;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.upload.service.ObjectStorage;
import com.example.operion.module.upload.service.R2Storage;

/**
 * BE-30: UPLOAD_STORAGE=r2 still means Cloudflare R2 (BE-23), and the local
 * endpoints don't exist then. With no R2 variables set, uploads answer 503.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "uploads.storage=r2")
class R2ModeTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ObjectStorage storage;

    @Test
    void r2IsUsed_andLocalLinksAreGone() throws Exception {

        assertTrue(storage instanceof R2Storage);

        String s = UUID.randomUUID().toString().substring(0, 8);
        Tenant store = tenantRepository.save(Tenant.builder().code("BE30R_" + s).name("Store").active(true).build());
        User u = new User();
        u.setEmail("be30r-" + s + "@test.com");
        u.setPasswordHash("not-a-real-hash");
        u.setFullName("be30r-" + s);
        u.setRole(Role.OPERATOR);
        u.setScope(Scope.MANAGER);
        u.setTenant(store);
        userRepository.save(u);

        String token = jwtUtil.generateToken(u.getId().toString(), u.getRole().name(), store.getId().toString(), "MANAGER");

        mvc.perform(post("/uploads").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"EXPENSE_RECEIPT\",\"contentType\":\"image/jpeg\",\"sizeBytes\":1200}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("File uploads are not configured on this server"));

        mvc.perform(get("/uploads-local").param("key", "a").param("expires", "1").param("sig", "x"))
                .andExpect(status().isNotFound());
    }
}
