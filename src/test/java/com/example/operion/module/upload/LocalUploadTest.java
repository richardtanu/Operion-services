package com.example.operion.module.upload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.upload.service.LocalDiskStorage;
import com.example.operion.module.upload.service.ObjectStorage;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-30: UPLOAD_STORAGE=local, end to end the way the phone does it: ask for a
 * ticket, PUT the bytes to the link with only the returned headers (no login),
 * attach the upload to a record, open the view link (no login). Plus the
 * link's protections: tampering, wrong type, too big, empty, expired, and
 * keys that try to leave the folder.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = { "uploads.storage=local", "uploads.local.dir=target/test-local-uploads" })
class LocalUploadTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ObjectStorage storage;

    private User sm;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenantRepository.save(Tenant.builder().code("BE30_HQ_" + s).name("HQ").active(true).build());
        Tenant store = tenantRepository.save(Tenant.builder().code("BE30_ST_" + s).name("Store").active(true).parent(hq).build());

        sm = new User();
        sm.setEmail("be30-sm-" + s + "@test.com");
        sm.setPasswordHash("not-a-real-hash");
        sm.setFullName("be30-sm-" + s);
        sm.setRole(Role.OPERATOR);
        sm.setScope(Scope.MANAGER);
        sm.setTenant(store);
        userRepository.save(sm);
    }

    @Test
    void localStorageIsTheDefault() {

        assertTrue(storage instanceof LocalDiskStorage, "UPLOAD_STORAGE=local uses the disk, not R2");
    }

    @Test
    void phoneFlow_putWithoutLogin_attach_thenViewWithoutLogin() throws Exception {

        byte[] photo = bytes(37_419);
        String ticket = ticket("EXPENSE_RECEIPT", "image/jpeg", photo.length);
        String uploadId = JsonPath.read(ticket, "$.data.uploadId");
        String putUrl = JsonPath.read(ticket, "$.data.putUrl");

        assertTrue(putUrl.startsWith("http://localhost/uploads-local?"), putUrl);

        // exactly what the app does: the returned headers, no Authorization
        mvc.perform(put(URI.create(putUrl)).contentType("image/jpeg").content(photo))
                .andExpect(status().isOk());

        // the upload counts as present, so a record can claim it
        mvc.perform(as(sm, post("/expenses")).content("{\"description\":\"Printer paper\",\"amount\":87300,"
                + "\"category\":\"BRANCH_SPECIFIC\",\"paidBy\":\"PERSONAL\",\"receiptUploadIds\":[\"" + uploadId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptUploadIds[0]").value(uploadId));

        String getUrl = JsonPath.read(mvc.perform(as(sm, get("/uploads/" + uploadId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.getUrl");

        byte[] served = mvc.perform(get(URI.create(getUrl)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();

        assertArrayEquals(photo, served);

        // a GET link is for reading only, and a forged one is refused
        mvc.perform(get(URI.create(getUrl.replaceAll("sig=[^&]+", "sig=forged")))).andExpect(status().isForbidden());
    }

    @Test
    void putLink_refusesTampering_wrongType_tooBig_andEmpty() throws Exception {

        byte[] photo = bytes(5_000);
        String putUrl = JsonPath.read(ticket("GOODS_RECEIPT_PHOTO", "image/png", photo.length), "$.data.putUrl");

        // a bigger size written into the link breaks its signature
        mvc.perform(put(URI.create(putUrl.replace("size=5000", "size=5000000"))).contentType("image/png").content(photo))
                .andExpect(status().isForbidden());

        mvc.perform(put(URI.create(putUrl)).contentType("image/jpeg").content(photo))
                .andExpect(status().isBadRequest());

        mvc.perform(put(URI.create(putUrl)).contentType("image/png").content(bytes(5_001)))
                .andExpect(status().isBadRequest());

        mvc.perform(put(URI.create(putUrl)).contentType("image/png").content(new byte[0]))
                .andExpect(status().isBadRequest());

        // nothing was saved by the refused attempts; the right one still works
        mvc.perform(put(URI.create(putUrl)).contentType("image/png").content(photo))
                .andExpect(status().isOk());
    }

    @Test
    void expiredLinks_andKeysOutsideTheFolder_areRefused(@TempDir Path dir) {

        LocalDiskStorage disk = new LocalDiskStorage(dir.toString(), "http://192.168.1.20:8080", "fixed-test-key");

        ObjectStorage.Presigned expired = disk.presignPut("t/p/a", "image/jpeg", 10, Duration.ofSeconds(-5));
        assertTrue(expired.url().startsWith("http://192.168.1.20:8080/uploads-local?"), "configured base URL");
        long past = expired.expiresAt().getEpochSecond();

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> disk.store(
                "t/p/a", "image/jpeg", 10, past, "whatever", "image/jpeg", new ByteArrayInputStream(bytes(10))));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        assertFalse(disk.exists("t/p/a"));

        assertThrows(ResponseStatusException.class, () -> disk.exists("../outside"));
        assertThrows(ResponseStatusException.class, () -> disk.delete("../../etc/passwd"));
    }

    // ---- helpers ----

    private String ticket(String purpose, String contentType, long size) throws Exception {

        return mvc.perform(as(sm, post("/uploads"))
                .content("{\"purpose\":\"" + purpose + "\",\"contentType\":\"" + contentType + "\",\"sizeBytes\":" + size + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("PUT"))
                .andExpect(jsonPath("$.data.headers['Content-Type']").value(contentType))
                .andReturn().getResponse().getContentAsString();
    }

    private static byte[] bytes(int n) {

        byte[] b = new byte[n];
        new Random(n).nextBytes(b);
        return b;
    }

    private MockHttpServletRequestBuilder as(User u, MockHttpServletRequestBuilder req) {

        String token = jwtUtil.generateToken(
                u.getId().toString(), u.getRole().name(), u.getTenant().getId().toString(),
                u.getScope() != null ? u.getScope().name() : null);

        return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }
}
