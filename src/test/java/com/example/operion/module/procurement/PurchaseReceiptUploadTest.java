package com.example.operion.module.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.upload.entity.Upload;
import com.example.operion.module.upload.enums.UploadPurpose;
import com.example.operion.module.upload.repository.UploadRepository;
import com.example.operion.module.upload.service.ObjectStorage;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-39: a purchase's receipt photos are uploads (purpose PURCHASE_RECEIPT),
 * attached when the purchase is saved. Their ids show to Owner+ and to whoever
 * logged it; anyone else gets null, like the money (a receipt shows what was
 * paid). Storage is mocked; receipts are upload rows that "exist" in storage.
 * Purchases use conversionFactor 6 (rules 2 and 6).
 *
 * BE-40: a purchase paid with your own money needs a receipt photo (a
 * correction may rely on the original's); a correction lists the photos of
 * what it corrects; a photo HQ attaches for a store moves to that store.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PurchaseReceiptUploadTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private UploadRepository uploadRepository;

    @MockitoBean private ObjectStorage storage;

    private Tenant store;
    private User sm;
    private User sm2;
    private User owner;
    private User hqProc;
    private Part gas;

    private String praId;
    private String praItem;

    @BeforeEach
    void seedFixtures() throws Exception {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenantRepository.save(Tenant.builder().code("BE39_HQ_" + s).name("HQ").active(true).build());
        store = tenantRepository.save(Tenant.builder().code("BE39_ST_" + s).name("Store " + s).active(true).parent(hq).build());

        sm = user("be39-sm-" + s, Scope.MANAGER, store);
        sm2 = user("be39-sm2-" + s, Scope.MANAGER, store);
        owner = user("be39-own-" + s, Scope.OWNER, store);
        hqProc = user("be40-hqproc-" + s, null, hq);
        hqProc.setRole(Role.PROCUREMENT);
        hqProc = userRepository.save(hqProc);

        PartCategory category = categoryRepository.save(PartCategory.builder().tenant(hq).name("Gas").consumable(true).build());
        gas = partRepository.save(Part.builder().tenant(store).name("Green gas " + s).category(category)
                .currentStock(0).minimumStock(6).reorderQuantity(12).active(true).retired(false).build());

        when(storage.exists(anyString())).thenReturn(true);
        when(storage.presignGet(anyString(), any()))
                .thenReturn(new ObjectStorage.Presigned("https://storage.test/get", Instant.now().plusSeconds(300)));

        authorize();
    }

    @Test
    void photosAreAttachedOnSave_andShowToTheOwnerAndTheBuyerOnly() throws Exception {

        UUID front = receipt(sm, UploadPurpose.PURCHASE_RECEIPT);
        UUID back = receipt(sm, UploadPurpose.PURCHASE_RECEIPT);

        // the same photo sent twice is attached once
        String id = log(sm, front, back, front);

        Upload saved = uploadRepository.findById(front).orElseThrow();
        assertEquals("REALISASI", saved.getAttachedType());
        assertEquals(id, saved.getAttachedId().toString());
        assertEquals(id, uploadRepository.findById(back).orElseThrow().getAttachedId().toString());

        for (User sees : List.of(owner, sm)) {
            String body = mvc.perform(as(sees, get("/realisasi/" + id)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<String> ids = JsonPath.read(body, "$.data.receiptUploadIds");
            assertEquals(Set.of(front.toString(), back.toString()), Set.copyOf(ids));
            assertEquals(2, ids.size());
        }

        // another Store Manager sees the purchase, not the receipt (null, not omitted)
        String body = mvc.perform(as(sm2, get("/realisasi/" + id)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> data = JsonPath.read(body, "$.data");
        assertTrue(data.containsKey("receiptUploadIds"));
        assertNull(data.get("receiptUploadIds"));
    }

    @Test
    void aPurchaseWithoutPhotos_isStillSaved() throws Exception {

        String id = log(sm);

        mvc.perform(as(owner, get("/realisasi/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptUploadIds.length()").value(0));
    }

    @Test
    void theWrongPhoto_isRefused() throws Exception {

        // an expense receipt, someone else's photo, or one already on another purchase
        UUID expenseReceipt = receipt(sm, UploadPurpose.EXPENSE_RECEIPT);
        UUID colleagues = receipt(sm2, UploadPurpose.PURCHASE_RECEIPT);

        for (UUID refused : List.of(expenseReceipt, colleagues, UUID.randomUUID())) {
            mvc.perform(as(sm, post("/realisasi")).content(body(refused)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Upload not found"));
        }

        UUID used = receipt(sm, UploadPurpose.PURCHASE_RECEIPT);
        log(sm, used);
        mvc.perform(as(sm, post("/realisasi")).content(body(used)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Upload is already attached"));
    }

    @Test
    void aCorrection_takesItsOwnPhotos_andListsTheOriginals() throws Exception {

        UUID first = receipt(sm, UploadPurpose.PURCHASE_RECEIPT);
        String original = log(sm, first);
        UUID corrected = receipt(sm, UploadPurpose.PURCHASE_RECEIPT);

        // BE-40: its own photo first, then the original's (the same receipt)
        String body = mvc.perform(as(sm, post("/realisasi/" + original + "/supersede"))
                        .content("{\"reason\":\"typo in the receipt\",\"correction\":" + body(corrected) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptUploadIds.length()").value(2))
                .andExpect(jsonPath("$.data.receiptUploadIds[0]").value(corrected.toString()))
                .andExpect(jsonPath("$.data.receiptUploadIds[1]").value(first.toString()))
                .andReturn().getResponse().getContentAsString();

        assertEquals(JsonPath.read(body, "$.data.id"),
                uploadRepository.findById(corrected).orElseThrow().getAttachedId().toString());

        // the original keeps only its own photo
        mvc.perform(as(owner, get("/realisasi/" + original)))
                .andExpect(jsonPath("$.data.status").value("SUPERSEDED"))
                .andExpect(jsonPath("$.data.receiptUploadIds.length()").value(1))
                .andExpect(jsonPath("$.data.receiptUploadIds[0]").value(first.toString()));
    }

    // ================= BE-40 =================

    @Test
    void ownMoney_needsAReceiptPhoto_companyMoneyDoesNot() throws Exception {

        mvc.perform(as(sm, post("/realisasi")).content(body("PERSONAL_REIMBURSABLE", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Add a photo of the receipt: it's what you're paid back against"));

        UUID photo = receipt(sm, UploadPurpose.PURCHASE_RECEIPT);
        mvc.perform(as(sm, post("/realisasi")).content(body("PERSONAL_REIMBURSABLE", null, photo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reimbursementStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.receiptUploadIds[0]").value(photo.toString()));

        mvc.perform(as(sm, post("/realisasi")).content(body("COMPANY_ACCOUNT", null)))
                .andExpect(status().isOk());
    }

    @Test
    void ownMoney_aCorrectionMayRelyOnTheOriginalsPhoto() throws Exception {

        UUID photo = receipt(sm, UploadPurpose.PURCHASE_RECEIPT);
        String original = JsonPath.read(mvc.perform(as(sm, post("/realisasi"))
                        .content(body("PERSONAL_REIMBURSABLE", null, photo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");

        // a typo fixed, no new photo: still has its receipt
        String fixed = JsonPath.read(mvc.perform(as(sm, post("/realisasi/" + original + "/supersede"))
                        .content("{\"reason\":\"typo in the total\",\"correction\":" + body("PERSONAL_REIMBURSABLE", null) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptUploadIds.length()").value(1))
                .andExpect(jsonPath("$.data.receiptUploadIds[0]").value(photo.toString()))
                .andReturn().getResponse().getContentAsString(), "$.data.id");

        // a correction of the correction still reaches the first photo
        mvc.perform(as(owner, post("/realisasi/" + fixed + "/supersede"))
                        .content("{\"reason\":\"wrong seller\",\"correction\":" + body("PERSONAL_REIMBURSABLE", null) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptUploadIds.length()").value(1))
                .andExpect(jsonPath("$.data.receiptUploadIds[0]").value(photo.toString()));

        // an own-money correction of a company-money purchase with no photo: refused
        String company = log(sm);
        mvc.perform(as(sm, post("/realisasi/" + company + "/supersede"))
                        .content("{\"reason\":\"paid it myself\",\"correction\":" + body("PERSONAL_REIMBURSABLE", null) + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Add a photo of the receipt: it's what you're paid back against"));
    }

    @Test
    void aPhotoHqAttachesForAStore_theStoresOwnerCanOpen() throws Exception {

        UUID photo = receipt(hqProc, UploadPurpose.PURCHASE_RECEIPT);

        mvc.perform(as(hqProc, post("/realisasi")).content(body("PERSONAL_REIMBURSABLE", store, photo)))
                .andExpect(status().isOk());

        assertEquals(store.getId(), uploadRepository.findById(photo).orElseThrow().getTenant().getId());

        mvc.perform(as(owner, get("/uploads/" + photo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadId").value(photo.toString()));

        // HQ, who took it, still can
        mvc.perform(as(hqProc, get("/uploads/" + photo))).andExpect(status().isOk());
    }

    // ---- helpers ----

    /** PR by the Store Manager → approved and authorized by the Owner: 12 cans, Rp 900,000. */
    private void authorize() throws Exception {

        String pr = mvc.perform(as(sm, post("/purchase-requests"))
                        .content("{\"requestedBy\":\"" + sm.getId() + "\",\"items\":[{\"partId\":\"" + gas.getId() + "\",\"quantity\":12}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String prId = JsonPath.read(pr, "$.data.id");

        mvc.perform(as(owner, patch("/purchase-requests/" + prId + "/approve"))).andExpect(status().isOk());

        String pra = mvc.perform(as(owner, post("/purchase-authorizations"))
                        .content("{\"purchaseRequestIds\":[\"" + prId + "\"],\"items\":[{\"partId\":\"" + gas.getId()
                                + "\",\"authorizedQty\":12,\"maxValue\":900000}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        praId = JsonPath.read(pra, "$.data.id");
        praItem = JsonPath.read(pra, "$.data.items[0].id");
    }

    private String log(User by, UUID... photos) throws Exception {

        String body = mvc.perform(as(by, post("/realisasi")).content(body(photos)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String body(UUID... photos) {
        return body("COMPANY_ACCOUNT", null, photos);
    }

    /** 1 paket = 6 cans at Rp 300,000; {@code target}: the store HQ logs it for, or null. */
    private String body(String paymentMethod, Tenant target, UUID... photos) {

        String ids = List.of(photos).stream().map(p -> "\"" + p + "\"").collect(Collectors.joining(","));

        return "{" + (target != null ? "\"tenantId\":\"" + target.getId() + "\"," : "")
                + "\"purchaseRequestAuthorizationId\":\"" + praId + "\","
                + "\"externalOrderRef\":\"BE39-" + UUID.randomUUID() + "\","
                + "\"paymentMethod\":\"" + paymentMethod + "\",\"subtotal\":300000,"
                + (photos.length > 0 ? "\"receiptUploadIds\":[" + ids + "]," : "")
                + "\"items\":[{\"purchaseRequestAuthorizationItemId\":\"" + praItem + "\",\"partId\":\"" + gas.getId()
                + "\",\"purchaseUom\":\"paket\",\"conversionFactor\":6,\"purchasedQty\":1,\"actualUnitPrice\":300000}]}";
    }

    private UUID receipt(User uploader, UploadPurpose purpose) {

        UUID id = UUID.randomUUID();
        uploadRepository.save(Upload.builder()
                .id(id)
                .tenant(uploader.getTenant())
                .uploadedBy(uploader)
                .purpose(purpose)
                .objectKey(uploader.getTenant().getId() + "/" + purpose.name().toLowerCase() + "/" + id)
                .contentType("image/jpeg")
                .sizeBytes(412_000L)
                .build());
        return id;
    }

    private User user(String prefix, Scope scope, Tenant tenant) {

        User u = new User();
        u.setEmail(prefix + "@test.com");
        u.setPasswordHash("not-a-real-hash");
        u.setFullName(prefix);
        u.setRole(Role.OPERATOR);
        u.setScope(scope);
        u.setTenant(tenant);
        return userRepository.save(u);
    }

    private MockHttpServletRequestBuilder as(User u, MockHttpServletRequestBuilder req) {

        String token = jwtUtil.generateToken(
                u.getId().toString(), u.getRole().name(), u.getTenant().getId().toString(),
                u.getScope() != null ? u.getScope().name() : null);

        return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }
}
