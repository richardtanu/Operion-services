package com.example.operion.module.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    private User sm;
    private User sm2;
    private User owner;
    private Part gas;

    private String praId;
    private String praItem;

    @BeforeEach
    void seedFixtures() throws Exception {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenantRepository.save(Tenant.builder().code("BE39_HQ_" + s).name("HQ").active(true).build());
        Tenant store = tenantRepository.save(Tenant.builder().code("BE39_ST_" + s).name("Store " + s).active(true).parent(hq).build());

        sm = user("be39-sm-" + s, Scope.MANAGER, store);
        sm2 = user("be39-sm2-" + s, Scope.MANAGER, store);
        owner = user("be39-own-" + s, Scope.OWNER, store);

        PartCategory category = categoryRepository.save(PartCategory.builder().tenant(hq).name("Gas").consumable(true).build());
        gas = partRepository.save(Part.builder().tenant(store).name("Green gas " + s).category(category)
                .currentStock(0).minimumStock(6).reorderQuantity(12).active(true).retired(false).build());

        when(storage.exists(anyString())).thenReturn(true);

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
    void aCorrection_carriesItsOwnPhotos() throws Exception {

        String original = log(sm, receipt(sm, UploadPurpose.PURCHASE_RECEIPT));
        UUID corrected = receipt(sm, UploadPurpose.PURCHASE_RECEIPT);

        String body = mvc.perform(as(sm, post("/realisasi/" + original + "/supersede"))
                        .content("{\"reason\":\"typo in the receipt\",\"correction\":" + body(corrected) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptUploadIds.length()").value(1))
                .andExpect(jsonPath("$.data.receiptUploadIds[0]").value(corrected.toString()))
                .andReturn().getResponse().getContentAsString();

        assertEquals(JsonPath.read(body, "$.data.id"),
                uploadRepository.findById(corrected).orElseThrow().getAttachedId().toString());

        // the original keeps its own photo
        mvc.perform(as(owner, get("/realisasi/" + original)))
                .andExpect(jsonPath("$.data.status").value("SUPERSEDED"))
                .andExpect(jsonPath("$.data.receiptUploadIds.length()").value(1));
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

    /** 1 paket = 6 cans at Rp 300,000. */
    private String body(UUID... photos) {

        String ids = List.of(photos).stream().map(p -> "\"" + p + "\"").collect(Collectors.joining(","));

        return "{\"purchaseRequestAuthorizationId\":\"" + praId + "\","
                + "\"externalOrderRef\":\"BE39-" + UUID.randomUUID() + "\","
                + "\"paymentMethod\":\"COMPANY_ACCOUNT\",\"subtotal\":300000,"
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
