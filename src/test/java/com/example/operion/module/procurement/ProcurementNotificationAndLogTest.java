package com.example.operion.module.procurement;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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
 * BE-07 (decided 14 Oct 2026), end to end through the real HTTP stack:
 * <ul>
 * <li>the PR requester is notified on approve, reject and authorize — never
 *     for their own action;</li>
 * <li>PROCUREMENT users in the PRA's own tenant are notified when a PRA is
 *     created — not HQ's buyers, not the PRA's creator;</li>
 * <li>every PR transition records who made it (it was always null before);</li>
 * <li>GET /procurement/activity is readable by ADMIN, PROCUREMENT or
 *     OWNER+, tree-scoped;</li>
 * <li>PATCH /users/{id}/role is ADMIN-only, tree-contained, never self.</li>
 * </ul>
 * Every notification insert here also proves the V3 migration: before it,
 * the new types violated notifications_type_check (CLAUDE.md rule 3).
 *
 * Fixtures: HQ → Outlet plus an unrelated root. The outlet Owner who
 * approves and authorizes also holds the PROCUREMENT role, so "never notify
 * the PRA's creator" has a PROCUREMENT user to exclude.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProcurementNotificationAndLogTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PurchaseRequestStatusHistoryRepository prHistoryRepository;

    private Tenant hq;
    private Tenant outlet;
    private Tenant other;

    private User requester;      // outlet, OPERATOR, no scope
    private User outletOwner;    // outlet, PROCUREMENT + OWNER: approves and authorizes
    private User outletBuyer;    // outlet, PROCUREMENT
    private User outletManager;  // outlet, OPERATOR + MANAGER
    private User hqBuyer;        // HQ, PROCUREMENT
    private User hqAdmin;        // HQ, ADMIN
    private User otherBuyer;     // unrelated root, PROCUREMENT

    private Part outletPart;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        hq = tenant("BE07_HQ_" + s, null);
        outlet = tenant("BE07_OUT_" + s, hq);
        other = tenant("BE07_OTHER_" + s, null);

        requester = user("be07-req-" + s, Role.OPERATOR, null, outlet);
        outletOwner = user("be07-owner-" + s, Role.PROCUREMENT, Scope.OWNER, outlet);
        outletBuyer = user("be07-buyer-" + s, Role.PROCUREMENT, null, outlet);
        outletManager = user("be07-mgr-" + s, Role.OPERATOR, Scope.MANAGER, outlet);
        hqBuyer = user("be07-hqbuyer-" + s, Role.PROCUREMENT, null, hq);
        hqAdmin = user("be07-hqadmin-" + s, Role.ADMIN, null, hq);
        otherBuyer = user("be07-otherbuyer-" + s, Role.PROCUREMENT, null, other);

        PartCategory category = categoryRepository.save(PartCategory.builder()
                .tenant(outlet).name("BE-07 Category").build());

        outletPart = partRepository.save(Part.builder()
                .tenant(outlet)
                .name("BE-07 Part")
                .category(category)
                .currentStock(0)
                .minimumStock(1)
                .reorderQuantity(6)
                .active(true)
                .retired(false)
                .build());
    }

    // ---- notifications ----

    @Test
    void approve_notifiesRequester_andRecordsApprover() throws Exception {

        String prId = createPr(requester);

        mvc.perform(as(outletOwner, patch("/purchase-requests/" + prId + "/approve")))
                .andExpect(status().isOk());

        assertEquals(List.of(NotificationType.PURCHASE_REQUEST_APPROVED), typesFor(requester));
        assertEquals(outletOwner.getId(), actorOf(prId, PurchaseRequestStatus.APPROVED));
        assertEquals(requester.getId(), actorOf(prId, PurchaseRequestStatus.PENDING));
    }

    @Test
    void reject_notifiesRequesterWithReason_andRecordsRejecter() throws Exception {

        String prId = createPr(requester);

        mvc.perform(as(outletOwner, patch("/purchase-requests/" + prId + "/reject"))
                .content("{\"reason\":\"Use the spare stock first\"}"))
                .andExpect(status().isOk());

        List<Notification> received = notificationRepository.findByRecipientUserId(requester.getId());
        assertEquals(1, received.size());
        assertEquals(NotificationType.PURCHASE_REQUEST_REJECTED, received.get(0).getType());
        assertTrue(received.get(0).getMessage().contains("Use the spare stock first"));
        assertEquals(outletOwner.getId(), actorOf(prId, PurchaseRequestStatus.REJECTED));
    }

    @Test
    void approvingYourOwnPr_sendsYouNothing() throws Exception {

        String prId = createPr(outletOwner);

        mvc.perform(as(outletOwner, patch("/purchase-requests/" + prId + "/approve")))
                .andExpect(status().isOk());

        assertEquals(List.of(), typesFor(outletOwner));
    }

    @Test
    void createPra_notifiesRequester_andOnlyOutletBuyers_notCreatorHqOrOtherRoot() throws Exception {

        String prId = createPr(requester);
        mvc.perform(as(outletOwner, patch("/purchase-requests/" + prId + "/approve")))
                .andExpect(status().isOk());

        createPra(outletOwner, prId);

        assertEquals(
                List.of(NotificationType.PURCHASE_REQUEST_APPROVED, NotificationType.PURCHASE_REQUEST_AUTHORIZED),
                typesFor(requester));
        assertEquals(List.of(NotificationType.PURCHASE_AUTHORIZATION_CREATED), typesFor(outletBuyer));
        // the Owner is an approver, so "new PR" is right; "PRA created" (their own act) is not
        assertEquals(List.of(NotificationType.NEW_PURCHASE_REQUEST), typesFor(outletOwner),
                "the PRA's creator is not notified of their own PRA");
        assertEquals(List.of(), typesFor(hqBuyer), "HQ buyers are not notified for an outlet PRA");
        assertEquals(List.of(), typesFor(otherBuyer));

        // the APPROVED -> AUTHORIZED step now reaches the PR's own history (needs V3)
        assertEquals(outletOwner.getId(), actorOf(prId, PurchaseRequestStatus.AUTHORIZED));
    }

    // ---- requester must belong to the filing branch ----

    @Test
    void requester_fromSameBranch_isAccepted_evenOnSomeonesBehalf() throws Exception {

        // the outlet's buyer files on behalf of a colleague in the same outlet
        String body = mvc.perform(as(outletBuyer, post("/purchase-requests"))
                .content(prBody(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedById").value(requester.getId().toString()))
                .andReturn().getResponse().getContentAsString();

        String prId = JsonPath.read(body, "$.data.id");
        assertEquals(outletBuyer.getId(), actorOf(prId, PurchaseRequestStatus.PENDING),
                "history records who actually filed it");
    }

    @Test
    void requester_fromAnyOtherTenant_isRejected_andNothingIsCreated() throws Exception {

        // unrelated root, the parent HQ, and (from HQ) the child outlet: all refused
        assertRequesterRefused(outletBuyer, otherBuyer);
        assertRequesterRefused(outletBuyer, hqBuyer);
        assertRequesterRefused(hqAdmin, requester);

        assertEquals(List.of(), typesFor(otherBuyer));
        assertEquals(List.of(), typesFor(hqBuyer));
        assertEquals(List.of(), typesFor(requester));
    }

    private void assertRequesterRefused(User caller, User namedRequester) throws Exception {

        mvc.perform(as(caller, post("/purchase-requests")).content(prBody(namedRequester)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Requesting user not found in your branch"));
    }

    private String prBody(User namedRequester) {

        return "{\"requestedBy\":\"" + namedRequester.getId() + "\",\"items\":[{\"partId\":\""
                + outletPart.getId() + "\",\"quantity\":6}]}";
    }

    // ---- PRA create/cancel need MANAGER scope or above ----

    @Test
    void praCreate_needsManagerOrAbove() throws Exception {

        User supervisor = user("be07-sup-" + UUID.randomUUID().toString().substring(0, 8),
                Role.PROCUREMENT, Scope.SUPERVISOR, outlet);

        String prId = createPr(requester);
        mvc.perform(as(outletOwner, patch("/purchase-requests/" + prId + "/approve")))
                .andExpect(status().isOk());

        // no scope (even with the PROCUREMENT role), and SUPERVISOR: refused
        mvc.perform(as(outletBuyer, post("/purchase-authorizations")).content(praBody(prId)))
                .andExpect(status().isForbidden());
        mvc.perform(as(supervisor, post("/purchase-authorizations")).content(praBody(prId)))
                .andExpect(status().isForbidden());

        // MANAGER: allowed
        createPra(outletManager, prId);
    }

    @Test
    void praCancel_needsManagerOrAbove() throws Exception {

        String prId = createPr(requester);
        mvc.perform(as(outletOwner, patch("/purchase-requests/" + prId + "/approve")))
                .andExpect(status().isOk());
        String praId = createPra(outletOwner, prId);

        mvc.perform(as(outletBuyer, patch("/purchase-authorizations/" + praId + "/cancel")))
                .andExpect(status().isForbidden());

        mvc.perform(as(outletManager, patch("/purchase-authorizations/" + praId + "/cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    // ---- activity log ----

    @Test
    void log_access_byRoleOrOwnerScope() throws Exception {

        createPr(requester);

        mvc.perform(as(hqAdmin, get("/procurement/activity"))).andExpect(status().isOk());
        mvc.perform(as(hqBuyer, get("/procurement/activity"))).andExpect(status().isOk());
        mvc.perform(as(outletOwner, get("/procurement/activity"))).andExpect(status().isOk());

        mvc.perform(as(outletManager, get("/procurement/activity"))).andExpect(status().isForbidden());
        mvc.perform(as(requester, get("/procurement/activity"))).andExpect(status().isForbidden());
    }

    @Test
    void log_showsWhoDidWhat_acrossTheTree_newestFirst() throws Exception {

        String prId = createPr(requester);
        mvc.perform(as(outletOwner, patch("/purchase-requests/" + prId + "/approve")))
                .andExpect(status().isOk());
        String praId = createPra(outletOwner, prId);

        String body = mvc.perform(as(hqAdmin, get("/procurement/activity")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> entries = JsonPath.read(body,
                "$.data[?(@.entityId in ['" + prId + "','" + praId + "'])].newStatus");
        assertTrue(entries.containsAll(List.of("PENDING", "APPROVED", "AUTHORIZED", "ACTIVE")), entries.toString());

        List<String> prActors = JsonPath.read(body, "$.data[?(@.entityId == '" + prId + "')].actorName");
        assertTrue(prActors.contains(requester.getFullName()));
        assertTrue(prActors.contains(outletOwner.getFullName()));

        List<String> times = JsonPath.read(body, "$.data[*].at");
        for (int i = 1; i < times.size(); i++) {
            assertTrue(LocalDateTime.parse(times.get(i - 1)).compareTo(LocalDateTime.parse(times.get(i))) >= 0,
                    "log must be newest first");
        }
    }

    @Test
    void log_filters_andTreeScope() throws Exception {

        String prId = createPr(requester);
        mvc.perform(as(outletOwner, patch("/purchase-requests/" + prId + "/approve")))
                .andExpect(status().isOk());
        createPra(outletOwner, prId);

        mvc.perform(as(hqAdmin, get("/procurement/activity").param("type", "PURCHASE_REQUEST_AUTHORIZATION")))
                .andExpect(jsonPath("$.data[*].entityType", everyItem(is("PURCHASE_REQUEST_AUTHORIZATION"))))
                .andExpect(jsonPath("$.data[*].newStatus", hasItem("ACTIVE")));

        mvc.perform(as(hqAdmin, get("/procurement/activity").param("actorId", requester.getId().toString())))
                .andExpect(jsonPath("$.data[*].actorId", everyItem(is(requester.getId().toString()))))
                .andExpect(jsonPath("$.data[*].newStatus", hasItem("PENDING")));

        // a tenant outside the caller's tree is refused, an inside one is fine
        mvc.perform(as(hqAdmin, get("/procurement/activity").param("tenantId", other.getId().toString())))
                .andExpect(status().isForbidden());
        mvc.perform(as(hqAdmin, get("/procurement/activity").param("tenantId", outlet.getId().toString())))
                .andExpect(status().isOk());

        // an unrelated root's buyer sees none of this tree's activity
        mvc.perform(as(otherBuyer, get("/procurement/activity")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].tenantId", not(hasItem(outlet.getId().toString()))));
    }

    // ---- role change ----

    @Test
    void roleChange_byAdmin_inTree_isPersisted() throws Exception {

        mvc.perform(as(hqAdmin, patch("/users/" + requester.getId() + "/role"))
                .content("{\"role\":\"PROCUREMENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("PROCUREMENT"));

        assertEquals(Role.PROCUREMENT, userRepository.findById(requester.getId()).orElseThrow().getRole());
    }

    @Test
    void roleChange_refusals() throws Exception {

        // not ADMIN
        mvc.perform(as(outletOwner, patch("/users/" + requester.getId() + "/role"))
                .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        // own role
        mvc.perform(as(hqAdmin, patch("/users/" + hqAdmin.getId() + "/role"))
                .content("{\"role\":\"OPERATOR\"}"))
                .andExpect(status().isForbidden());

        // outside the tree
        mvc.perform(as(hqAdmin, patch("/users/" + otherBuyer.getId() + "/role"))
                .content("{\"role\":\"OPERATOR\"}"))
                .andExpect(status().isForbidden());

        // invalid role value
        mvc.perform(as(hqAdmin, patch("/users/" + requester.getId() + "/role"))
                .content("{\"role\":\"WIZARD\"}"))
                .andExpect(status().isBadRequest());

        assertEquals(Role.OPERATOR, userRepository.findById(requester.getId()).orElseThrow().getRole());
        assertEquals(Role.PROCUREMENT, userRepository.findById(otherBuyer.getId()).orElseThrow().getRole());
    }

    // ---- helpers ----

    private String createPr(User asUser) throws Exception {

        String body = mvc.perform(as(asUser, post("/purchase-requests"))
                .content("{\"requestedBy\":\"" + asUser.getId() + "\",\"items\":[{\"partId\":\""
                        + outletPart.getId() + "\",\"quantity\":6}]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.data.id");
    }

    private String createPra(User asUser, String prId) throws Exception {

        String body = mvc.perform(as(asUser, post("/purchase-authorizations")).content(praBody(prId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.data.id");
    }

    private String praBody(String prId) {

        return "{\"purchaseRequestIds\":[\"" + prId + "\"],\"items\":[{\"partId\":\""
                + outletPart.getId() + "\",\"authorizedQty\":6,\"maxValue\":600000}]}";
    }

    private List<NotificationType> typesFor(User u) {

        return notificationRepository.findByRecipientUserId(u.getId()).stream()
                .map(Notification::getType)
                .sorted()
                .toList();
    }

    private UUID actorOf(String prId, PurchaseRequestStatus status) {

        PurchaseRequestStatusHistory row = prHistoryRepository
                .findByPurchaseRequest_IdOrderByCreatedAtDesc(UUID.fromString(prId)).stream()
                .filter(h -> h.getNewStatus() == status)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + status + " history row"));

        return row.getChangedBy() != null ? row.getChangedBy().getId() : null;
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
                u.getId().toString(),
                u.getRole().name(),
                u.getTenant().getId().toString(),
                u.getScope() != null ? u.getScope().name() : null);

        return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }
}
