package com.example.operion.module.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.notification.entity.Notification;
import com.example.operion.module.notification.enums.NotificationType;
import com.example.operion.module.notification.repository.NotificationRepository;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.procurement.entity.PurchaseRequestStatusHistory;
import com.example.operion.module.procurement.enums.PurchaseRequestStatus;
import com.example.operion.module.procurement.repository.PurchaseRequestStatusHistoryRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-15: who may cancel a PR (by status), the required reason, and telling the
 * requester. The PURCHASE_REQUEST_CANCELLED notification insert also proves
 * migration V5 (notifications_type_check, CLAUDE.md rule 3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PrCancelTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PurchaseRequestStatusHistoryRepository historyRepository;

    private User storeManager;   // the requester: OPERATOR + MANAGER
    private User otherManager;   // same store, MANAGER
    private User owner;          // same store, OWNER
    private User operator;
    private User technician;
    private User accounting;
    private User otherStoreOwner;

    private Part part;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenant("BE15_HQ_" + s, null);
        Tenant store = tenant("BE15_STORE_" + s, hq);
        Tenant otherStore = tenant("BE15_OTHER_" + s, hq);

        storeManager = user("be15-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);
        otherManager = user("be15-mgr2-" + s, Role.OPERATOR, Scope.MANAGER, store);
        owner = user("be15-own-" + s, Role.OPERATOR, Scope.OWNER, store);
        operator = user("be15-op-" + s, Role.OPERATOR, null, store);
        technician = user("be15-tech-" + s, Role.TECHNICIAN, null, store);
        accounting = user("be15-acc-" + s, Role.ACCOUNTING, null, store);
        otherStoreOwner = user("be15-own2-" + s, Role.OPERATOR, Scope.OWNER, otherStore);

        PartCategory category = categoryRepository.save(PartCategory.builder()
                .tenant(store).name("Gas").consumable(true).build());
        part = partRepository.save(Part.builder()
                .tenant(store).name("Green gas can").category(category)
                .currentStock(0).minimumStock(6).reorderQuantity(12).active(true).retired(false).build());
    }

    @Test
    void pending_requesterOrOwner_only() throws Exception {

        for (User refused : List.of(otherManager, operator, technician, accounting)) {
            mvc.perform(as(refused, patch("/purchase-requests/" + pendingPr() + "/cancel")).content(reason("dup")))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(as(storeManager, patch("/purchase-requests/" + pendingPr() + "/cancel")).content(reason("dup")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mvc.perform(as(owner, patch("/purchase-requests/" + pendingPr() + "/cancel")).content(reason("dup")))
                .andExpect(status().isOk());

        // another store's Owner can't even see it
        mvc.perform(as(otherStoreOwner, patch("/purchase-requests/" + pendingPr() + "/cancel")).content(reason("dup")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approved_ownerOnly() throws Exception {

        String prId = pendingPr();
        mvc.perform(as(owner, patch("/purchase-requests/" + prId + "/approve"))).andExpect(status().isOk());

        mvc.perform(as(storeManager, patch("/purchase-requests/" + prId + "/cancel")).content(reason("wrong part")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only Owner-level users or higher can cancel an approved purchase request"));

        mvc.perform(as(owner, patch("/purchase-requests/" + prId + "/cancel")).content(reason("wrong part")))
                .andExpect(status().isOk());
    }

    @Test
    void reasonRequired_andStoredOnHistory() throws Exception {

        String prId = pendingPr();

        mvc.perform(as(storeManager, patch("/purchase-requests/" + prId + "/cancel")).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(storeManager, patch("/purchase-requests/" + prId + "/cancel")).content(reason("   ")))
                .andExpect(status().isBadRequest());

        mvc.perform(as(storeManager, patch("/purchase-requests/" + prId + "/cancel")).content(reason("Filed twice")))
                .andExpect(status().isOk());

        PurchaseRequestStatusHistory row = historyRepository
                .findByPurchaseRequest_IdOrderByCreatedAtDesc(UUID.fromString(prId)).stream()
                .filter(h -> h.getNewStatus() == PurchaseRequestStatus.CANCELLED)
                .findFirst().orElseThrow();
        assertEquals("Filed twice", row.getReason());
        assertEquals(storeManager.getId(), row.getChangedBy().getId());
    }

    @Test
    void requesterIsTold_unlessTheyCancelledItThemselves() throws Exception {

        mvc.perform(as(owner, patch("/purchase-requests/" + pendingPr() + "/cancel")).content(reason("Use spare stock")))
                .andExpect(status().isOk());

        List<Notification> received = notificationRepository.findByRecipientUserId(storeManager.getId());
        assertEquals(1, received.size());
        assertEquals(NotificationType.PURCHASE_REQUEST_CANCELLED, received.get(0).getType());
        assertEquals(true, received.get(0).getMessage().contains("Use spare stock"));

        mvc.perform(as(storeManager, patch("/purchase-requests/" + pendingPr() + "/cancel")).content(reason("mine")))
                .andExpect(status().isOk());
        assertEquals(1, notificationRepository.findByRecipientUserId(storeManager.getId()).size(),
                "no notification for cancelling your own PR");
    }

    @Test
    void hqRequester_canWithdrawWhatTheyFiledForAStore_butHqOwnerCannotCancelAStoresPr() throws Exception {

        String s = UUID.randomUUID().toString().substring(0, 6);
        User hqProc = user("be15-hqproc-" + s, Role.PROCUREMENT, null, storeManager.getTenant().getParent());
        User hqPrincipal = user("be15-hqprin-" + s, Role.ADMIN, Scope.PRINCIPAL, storeManager.getTenant().getParent());

        // HQ files for the store (BE-14); the PR lives on the store
        String filedByHq = JsonPath.read(mvc.perform(as(hqProc, post("/purchase-requests"))
                .content("{\"tenantId\":\"" + storeManager.getTenant().getId() + "\",\"requestedBy\":\""
                        + hqProc.getId() + "\",\"items\":[{\"partId\":\"" + part.getId() + "\",\"quantity\":12}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");

        // HQ can open it, and as its requester withdraw it while pending
        mvc.perform(as(hqProc, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/purchase-requests/" + filedByHq))).andExpect(status().isOk());
        mvc.perform(as(hqProc, patch("/purchase-requests/" + filedByHq + "/cancel")).content(reason("not needed")))
                .andExpect(status().isOk());

        // but an Owner+ at HQ can't cancel a store's own request: stores are independent
        mvc.perform(as(hqPrincipal, patch("/purchase-requests/" + pendingPr() + "/cancel")).content(reason("x")))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private String pendingPr() throws Exception {

        String body = mvc.perform(as(storeManager, post("/purchase-requests"))
                .content("{\"requestedBy\":\"" + storeManager.getId() + "\",\"items\":[{\"partId\":\""
                        + part.getId() + "\",\"quantity\":12}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private static String reason(String r) {
        return "{\"reason\":\"" + r + "\"}";
    }

    private Tenant tenant(String code, Tenant parent) {
        return tenantRepository.save(Tenant.builder().code(code).name(code).active(true).parent(parent).build());
    }

    private User user(String prefix, Role role, Scope scope, Tenant tenant) {

        User u = new User();
        u.setEmail(prefix + "@test.com");
        u.setPasswordHash("not-a-real-hash");
        u.setFullName(prefix);
        u.setRole(role);
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
