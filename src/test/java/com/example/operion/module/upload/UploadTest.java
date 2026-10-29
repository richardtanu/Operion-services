package com.example.operion.module.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.upload.entity.Upload;
import com.example.operion.module.upload.enums.UploadPurpose;
import com.example.operion.module.upload.repository.UploadRepository;
import com.example.operion.module.upload.service.ObjectStorage;
import com.example.operion.module.upload.service.UploadService;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-23 rules, through the real HTTP stack and the real database, with object
 * storage mocked (presigning itself is covered by {@link R2StorageTest}).
 * Fixtures: HQ → Outlet plus an unrelated root, two users in the outlet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UploadTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UploadRepository uploadRepository;
    @Autowired private UploadService uploadService;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private ObjectStorage storage;

    private Tenant outlet;
    private User outletUser;
    private User outletColleague;
    private User hqUser;
    private User otherUser;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenant("BE23_HQ_" + s, null);
        outlet = tenant("BE23_OUT_" + s, hq);
        Tenant other = tenant("BE23_OTHER_" + s, null);

        outletUser = user("be23-out-" + s, outlet);
        outletColleague = user("be23-col-" + s, outlet);
        hqUser = user("be23-hq-" + s, hq);
        otherUser = user("be23-other-" + s, other);

        when(storage.isConfigured()).thenReturn(true);
        when(storage.presignPut(anyString(), anyString(), anyLong(), any()))
                .thenAnswer(i -> new ObjectStorage.Presigned("https://r2.example/put/" + i.getArgument(0),
                        Instant.now().plusSeconds(600)));
        when(storage.presignGet(anyString(), any()))
                .thenAnswer(i -> new ObjectStorage.Presigned("https://r2.example/get/" + i.getArgument(0),
                        Instant.now().plusSeconds(300)));
        when(storage.exists(anyString())).thenReturn(true);
    }

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ---- POST /uploads ----

    @Test
    void create_returnsPutUrl_andRecordsTheUpload() throws Exception {

        String body = mvc.perform(as(outletUser, post("/uploads"))
                .content(uploadBody("GOODS_RECEIPT_PHOTO", "image/jpeg", 350_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("PUT"))
                .andExpect(jsonPath("$.data.headers['Content-Type']").value("image/jpeg"))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(JsonPath.read(body, "$.data.uploadId"));
        Upload saved = uploadRepository.findById(id).orElseThrow();

        assertEquals(outlet.getId() + "/goods_receipt_photo/" + id, saved.getObjectKey());
        assertEquals(outletUser.getId(), saved.getUploadedBy().getId());
        assertTrue(JsonPath.<String>read(body, "$.data.putUrl").endsWith(saved.getObjectKey()));
    }

    @Test
    void create_acceptsExactly5MB_andEveryAllowedType() throws Exception {

        for (String type : List.of("image/jpeg", "image/png", "image/webp", "application/pdf")) {
            mvc.perform(as(outletUser, post("/uploads"))
                    .content(uploadBody("EXPENSE_RECEIPT", type, 5L * 1024 * 1024)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void create_wrongTypeOrOversize_is400() throws Exception {

        mvc.perform(as(outletUser, post("/uploads")).content(uploadBody("EXPENSE_RECEIPT", "image/gif", 1000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported file type: use JPEG, PNG, WebP or PDF"));

        mvc.perform(as(outletUser, post("/uploads"))
                .content(uploadBody("EXPENSE_RECEIPT", "image/jpeg", 5L * 1024 * 1024 + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File too large: the limit is 5 MB"));

        mvc.perform(as(outletUser, post("/uploads")).content(uploadBody("NOT_A_PURPOSE", "image/jpeg", 1000)))
                .andExpect(status().isBadRequest());

        assertEquals(0, uploadRepository.findAll().stream()
                .filter(u -> u.getUploadedBy().getId().equals(outletUser.getId())).count(),
                "nothing recorded for a refused upload");
    }

    @Test
    void create_whenStorageNotConfigured_is503() throws Exception {

        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "File uploads are not configured on this server"))
                .when(storage).requireConfigured();

        mvc.perform(as(outletUser, post("/uploads")).content(uploadBody("EXPENSE_RECEIPT", "image/jpeg", 1000)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("File uploads are not configured on this server"));
    }

    // ---- GET /uploads/{id} ----

    @Test
    void view_unattached_onlyByItsUploader() throws Exception {

        Upload upload = upload(outletUser, outlet, UploadPurpose.GOODS_RECEIPT_PHOTO);

        mvc.perform(as(outletUser, get("/uploads/" + upload.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.getUrl").value("https://r2.example/get/" + upload.getObjectKey()))
                .andExpect(jsonPath("$.data.attached").value(false));

        mvc.perform(as(outletColleague, get("/uploads/" + upload.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Upload not found"));
    }

    @Test
    void view_attached_acrossTheTreeDownward_neverAcrossTenants() throws Exception {

        Upload upload = upload(outletUser, outlet, UploadPurpose.GOODS_RECEIPT_PHOTO);
        attachAs(outletUser, upload.getId(), UploadPurpose.GOODS_RECEIPT_PHOTO);

        mvc.perform(as(outletColleague, get("/uploads/" + upload.getId()))).andExpect(status().isOk());
        mvc.perform(as(hqUser, get("/uploads/" + upload.getId()))).andExpect(status().isOk());

        mvc.perform(as(otherUser, get("/uploads/" + upload.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Upload not found"));
    }

    // ---- attach (used by BE-20 / BE-19) ----

    @Test
    void attach_once_byItsUploader() {

        Upload upload = upload(outletUser, outlet, UploadPurpose.GOODS_RECEIPT_PHOTO);
        UUID receiptId = UUID.randomUUID();

        Upload attached = attachAs(outletUser, upload.getId(), UploadPurpose.GOODS_RECEIPT_PHOTO, receiptId);

        assertEquals("GOODS_RECEIPT", attached.getAttachedType());
        assertEquals(receiptId, attached.getAttachedId());
        assertNotNull(attached.getAttachedAt());

        RuntimeException twice = assertThrows(RuntimeException.class,
                () -> attachAs(outletUser, upload.getId(), UploadPurpose.GOODS_RECEIPT_PHOTO));
        assertEquals("Upload is already attached", twice.getMessage());
    }

    @Test
    void attach_refusals_allAnswerLikeAMissingId() {

        Upload mine = upload(outletUser, outlet, UploadPurpose.GOODS_RECEIPT_PHOTO);

        String missing = refusal(outletUser, UUID.randomUUID(), UploadPurpose.GOODS_RECEIPT_PHOTO);
        assertEquals("Upload not found", missing);

        // another tenant, another user in the same tenant, the wrong purpose
        assertEquals(missing, refusal(otherUser, mine.getId(), UploadPurpose.GOODS_RECEIPT_PHOTO));
        assertEquals(missing, refusal(outletColleague, mine.getId(), UploadPurpose.GOODS_RECEIPT_PHOTO));
        assertEquals(missing, refusal(outletUser, mine.getId(), UploadPurpose.EXPENSE_RECEIPT));

        // asked for a URL but never actually uploaded the file
        when(storage.exists(mine.getObjectKey())).thenReturn(false);
        assertEquals(missing, refusal(outletUser, mine.getId(), UploadPurpose.GOODS_RECEIPT_PHOTO));
    }

    // ---- sweep ----

    @Test
    void sweep_deletesOnlyUnattachedOlderThan24h() {

        Upload abandoned = upload(outletUser, outlet, UploadPurpose.EXPENSE_RECEIPT);
        Upload oldButAttached = upload(outletUser, outlet, UploadPurpose.EXPENSE_RECEIPT);
        Upload fresh = upload(outletUser, outlet, UploadPurpose.EXPENSE_RECEIPT);

        attachAs(outletUser, oldButAttached.getId(), UploadPurpose.EXPENSE_RECEIPT);
        uploadRepository.flush();
        jdbc.update("update uploads set created_at = now() - interval '25 hours' where id in (?, ?)",
                abandoned.getId(), oldButAttached.getId());

        uploadService.sweepAbandoned();
        uploadRepository.flush();

        assertTrue(uploadRepository.findById(abandoned.getId()).isEmpty());
        assertTrue(uploadRepository.findById(oldButAttached.getId()).isPresent());
        assertTrue(uploadRepository.findById(fresh.getId()).isPresent());
        verify(storage).delete(abandoned.getObjectKey());
        verify(storage, never()).delete(oldButAttached.getObjectKey());
        verify(storage, never()).delete(eq(fresh.getObjectKey()));
    }

    // ---- helpers ----

    private Upload upload(User owner, Tenant tenant, UploadPurpose purpose) {

        UUID id = UUID.randomUUID();

        return uploadRepository.save(Upload.builder()
                .id(id)
                .tenant(tenant)
                .uploadedBy(owner)
                .purpose(purpose)
                .objectKey(tenant.getId() + "/" + purpose.name().toLowerCase() + "/" + id)
                .contentType("image/jpeg")
                .sizeBytes(300_000L)
                .build());
    }

    private Upload attachAs(User u, UUID uploadId, UploadPurpose purpose) {
        return attachAs(u, uploadId, purpose, UUID.randomUUID());
    }

    private Upload attachAs(User u, UUID uploadId, UploadPurpose purpose, UUID targetId) {

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getId().toString(), null, List.of()));
        TenantContext.setTenantId(u.getTenant().getId().toString());
        try {
            return uploadService.attach(uploadId, purpose,
                    purpose == UploadPurpose.GOODS_RECEIPT_PHOTO ? "GOODS_RECEIPT" : "EXPENSE", targetId);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    private String refusal(User u, UUID uploadId, UploadPurpose purpose) {
        return assertThrows(RuntimeException.class, () -> attachAs(u, uploadId, purpose)).getMessage();
    }

    private Tenant tenant(String code, Tenant parent) {
        return tenantRepository.save(Tenant.builder().code(code).name(code).active(true).parent(parent).build());
    }

    private User user(String prefix, Tenant tenant) {

        User u = new User();
        u.setEmail(prefix + "@test.com");
        u.setPasswordHash("not-a-real-hash");
        u.setFullName(prefix);
        u.setRole(Role.OPERATOR);
        u.setTenant(tenant);
        return userRepository.save(u);
    }

    private static String uploadBody(String purpose, String type, long size) {
        return "{\"purpose\":\"" + purpose + "\",\"contentType\":\"" + type + "\",\"sizeBytes\":" + size + "}";
    }

    private MockHttpServletRequestBuilder as(User u, MockHttpServletRequestBuilder req) {

        String token = jwtUtil.generateToken(
                u.getId().toString(), u.getRole().name(), u.getTenant().getId().toString(), null);

        return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }
}
