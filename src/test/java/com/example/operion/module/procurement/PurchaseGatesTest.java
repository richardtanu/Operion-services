package com.example.operion.module.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorizationItem;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationItemRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-14 (who files PRs; HQ files for a store) and BE-17 (who sets limits, logs,
 * rejects and corrects purchases; HQ buys for a store), end to end over HTTP.
 *
 * Fixtures: HQ → Outlet A and Outlet B, plus an unrelated root, so "for A, and
 * nobody at B" is really checked. Each outlet has its own part (parts are per
 * store). Purchases use conversionFactor 6, never 1 (CLAUDE.md rules 2 and 6):
 * a line authorized for 12 cans at Rp 900,000; 1 paket = 6 cans at Rp 300,000.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PurchaseGatesTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PurchaseRequestAuthorizationRepository praRepository;
    @Autowired private PurchaseRequestAuthorizationItemRepository praItemRepository;

    private Tenant hq;
    private Tenant outletA;
    private Tenant outletB;
    private Tenant other;

    private User hqAdmin;        // HQ, ADMIN, no scope
    private User hqProc;         // HQ, PROCUREMENT, no scope
    private User hqAccounting;   // HQ, ACCOUNTING, no scope
    private User aManager;       // A, the Store Manager: OPERATOR + MANAGER
    private User aManager2;      // A, a second MANAGER (for "someone else's purchase")
    private User aOwner;         // A, OPERATOR + OWNER
    private User aOperator;      // A, OPERATOR, no scope
    private User aTech;          // A, TECHNICIAN, no scope
    private User aAccounting;    // A, ACCOUNTING, no scope
    private User bManager;       // B, MANAGER
    private User bOwner;         // B, OWNER
    private User otherAdmin;     // unrelated root, ADMIN

    private Part partA;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        hq = tenant("BE14_HQ_" + s, "BE-14 HQ", null);
        outletA = tenant("BE14_A_" + s, "Outlet A " + s, hq);
        outletB = tenant("BE14_B_" + s, "Outlet B " + s, hq);
        other = tenant("BE14_OTHER_" + s, "Other root", null);

        hqAdmin = user("be14-hqadmin-" + s, Role.ADMIN, null, hq);
        hqProc = user("be14-hqproc-" + s, Role.PROCUREMENT, null, hq);
        hqAccounting = user("be14-hqacc-" + s, Role.ACCOUNTING, null, hq);
        aManager = user("be14-amgr-" + s, Role.OPERATOR, Scope.MANAGER, outletA);
        aManager2 = user("be14-amgr2-" + s, Role.OPERATOR, Scope.MANAGER, outletA);
        aOwner = user("be14-aown-" + s, Role.OPERATOR, Scope.OWNER, outletA);
        aOperator = user("be14-aop-" + s, Role.OPERATOR, null, outletA);
        aTech = user("be14-atech-" + s, Role.TECHNICIAN, null, outletA);
        aAccounting = user("be14-aacc-" + s, Role.ACCOUNTING, null, outletA);
        bManager = user("be14-bmgr-" + s, Role.OPERATOR, Scope.MANAGER, outletB);
        bOwner = user("be14-bown-" + s, Role.OPERATOR, Scope.OWNER, outletB);
        otherAdmin = user("be14-otheradmin-" + s, Role.ADMIN, null, other);

        partA = part(outletA, "Green gas can A");
        part(outletB, "Green gas can B");
    }

    // ================= BE-14: who files a PR, and for which store =================

    @Test
    void prCreate_gate() throws Exception {

        for (User refused : List.of(aOperator, aTech, aAccounting, hqAccounting)) {
            mvc.perform(as(refused, post("/purchase-requests")).content(prBody(null, refused, partA)))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(as(aManager, post("/purchase-requests")).content(prBody(null, aManager, partA)))
                .andExpect(status().isOk());
        mvc.perform(as(hqAdmin, post("/purchase-requests")).content(prBody(outletA, hqAdmin, partA)))
                .andExpect(status().isOk());
        mvc.perform(as(hqProc, post("/purchase-requests")).content(prBody(outletA, hqProc, partA)))
                .andExpect(status().isOk());
    }

    @Test
    void prCreate_hqFilesForAStore_savedOnThatStore_requesterIsHq() throws Exception {

        mvc.perform(as(hqAdmin, post("/purchase-requests")).content(prBody(outletA, hqAdmin, partA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(outletA.getId().toString()))
                .andExpect(jsonPath("$.data.tenantName").value(outletA.getName()))
                .andExpect(jsonPath("$.data.requestedById").value(hqAdmin.getId().toString()));
    }

    @Test
    void prCreate_targetStore_refusals() throws Exception {

        // a Store Manager can't file for HQ (or anyone else): 403
        mvc.perform(as(aManager, post("/purchase-requests")).content(prBody(hq, aManager, partA)))
                .andExpect(status().isForbidden());

        // outside the tree, or a made-up id: the same 400
        mvc.perform(as(hqAdmin, post("/purchase-requests")).content(prBody(other, hqAdmin, partA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Store not found"));
        mvc.perform(as(hqAdmin, post("/purchase-requests"))
                .content(prBodyRaw(UUID.randomUUID(), hqAdmin.getId(), partA.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Store not found"));

        // an Outlet A admin can't reach a sibling store either (only own + descendants)
        User aAdmin = user("be14-aadmin-" + UUID.randomUUID().toString().substring(0, 6), Role.ADMIN, null, outletA);
        mvc.perform(as(aAdmin, post("/purchase-requests")).content(prBody(outletB, aAdmin, partA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Store not found"));
    }

    @Test
    void prCreate_requesterRule() throws Exception {

        // HQ files for A naming an A user: fine; naming a B user: refused
        mvc.perform(as(hqAdmin, post("/purchase-requests")).content(prBody(outletA, aManager, partA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedById").value(aManager.getId().toString()));

        mvc.perform(as(hqAdmin, post("/purchase-requests")).content(prBody(outletA, bManager, partA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Requesting user not found in your branch"));
    }

    @Test
    void prCreate_hqFiledForA_notifiesAsChain_notB() throws Exception {

        mvc.perform(as(hqProc, post("/purchase-requests")).content(prBody(outletA, hqProc, partA)))
                .andExpect(status().isOk());

        assertEquals(List.of(NotificationType.NEW_PURCHASE_REQUEST), typesFor(aOwner));
        assertEquals(List.of(), typesFor(bOwner));
    }

    @Test
    void tenantTree_ownPlusDescendants_only() throws Exception {

        String body = mvc.perform(as(hqAdmin, get("/tenants/tree")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.data[*].id");
        assertEquals(hq.getId().toString(), ids.get(0), "own tenant first");
        assertTrue(ids.containsAll(List.of(outletA.getId().toString(), outletB.getId().toString())));
        assertEquals(3, ids.size());

        mvc.perform(as(aManager, get("/tenants/tree")))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(outletA.getId().toString()));
    }

    // ================= BE-17: limits, logging, rejecting, correcting =================

    @Test
    void realisasiCreate_gate() throws Exception {

        String praItem = activePraItem();
        String praId = praOf(praItem);

        for (User refused : List.of(aOperator, aTech, aAccounting, hqAdmin)) {
            mvc.perform(as(refused, post("/realisasi"))
                    .content(realisasiBody(refused == hqAdmin ? outletA : null, praId, praItem, 1)))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(as(aManager, post("/realisasi")).content(realisasiBody(null, praId, praItem, 1)))
                .andExpect(status().isOk());

        // the Owner who set the limits may still buy against them (DL-08 exemption)
        mvc.perform(as(aOwner, post("/realisasi")).content(realisasiBody(null, praId, praItem, 1)))
                .andExpect(status().isOk());
    }

    @Test
    void realisasiCreate_dl08_stillRefusesAManagerBuyingAgainstTheirOwnPra() throws Exception {

        // pre-existing data from when BE-13 let MANAGERs set limits
        PurchaseRequestAuthorization pra = praRepository.save(PurchaseRequestAuthorization.builder()
                .tenant(outletA).approvedBy(aManager).status(PurchaseRequestAuthorizationStatus.ACTIVE).build());
        PurchaseRequestAuthorizationItem item = praItemRepository.save(PurchaseRequestAuthorizationItem.builder()
                .purchaseRequestAuthorization(pra).part(partA).authorizedQty(12)
                .maxValue(new BigDecimal("900000")).build());

        mvc.perform(as(aManager, post("/realisasi"))
                .content(realisasiBody(null, pra.getId().toString(), item.getId().toString(), 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void realisasiCreate_hqProcurementBuysForA() throws Exception {

        String praItem = activePraItem();
        String praId = praOf(praItem);

        mvc.perform(as(hqProc, post("/realisasi")).content(realisasiBody(outletA, praId, praItem, 1)))
                .andExpect(status().isOk());

        // a Store Manager can't log for another store: 403
        mvc.perform(as(aManager, post("/realisasi")).content(realisasiBody(outletB, praId, praItem, 1)))
                .andExpect(status().isForbidden());

        // outside the tree: 400, same as a missing id
        mvc.perform(as(hqProc, post("/realisasi")).content(realisasiBody(other, praId, praItem, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Store not found"));
    }

    @Test
    void reject_ownerOnly_andOnlyThenDoesTheLimitReopen() throws Exception {

        String praItem = activePraItem();
        String praId = praOf(praItem);

        String approved = logPurchase(aManager, praId, praItem, 1);        // 6 cans, within limit
        String escalated = logPurchase(aManager, praId, praItem, 3);       // +18 cans: over 12 → waits for Owner

        assertEquals(6, remainingQty(praId), "12 authorized - 6 approved");

        for (String id : List.of(approved, escalated)) {
            mvc.perform(as(aManager, patch("/realisasi/" + id + "/reject")).content("{\"reason\":\"wrong item\"}"))
                    .andExpect(status().isForbidden());
        }
        assertEquals(6, remainingQty(praId), "a refused reject reopens nothing");

        mvc.perform(as(aOwner, patch("/realisasi/" + approved + "/reject")).content("{\"reason\":\"wrong item\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));
        assertEquals(12, remainingQty(praId), "the Owner's reject reopens the 6 cans");
    }

    @Test
    void supersede_creatorOrOwner_onlyLiveRecords() throws Exception {

        String praItem = activePraItem();
        String praId = praOf(praItem);

        String byManager = logPurchase(aManager, praId, praItem, 1);

        // another MANAGER: 403
        mvc.perform(as(aManager2, post("/realisasi/" + byManager + "/supersede"))
                .content(supersedeBody(praId, praItem)))
                .andExpect(status().isForbidden());

        // the creator: 200
        String body = mvc.perform(as(aManager, post("/realisasi/" + byManager + "/supersede"))
                .content(supersedeBody(praId, praItem)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String correction = JsonPath.read(body, "$.data.id");

        // superseding the now-SUPERSEDED original again: 400
        mvc.perform(as(aOwner, post("/realisasi/" + byManager + "/supersede"))
                .content(supersedeBody(praId, praItem)))
                .andExpect(status().isBadRequest());

        // an OWNER may correct someone else's live record: 200
        mvc.perform(as(aOwner, post("/realisasi/" + correction + "/supersede"))
                .content(supersedeBody(praId, praItem)))
                .andExpect(status().isOk());

        // a FAILED record can't be corrected: 400
        String failed = logPurchase(aManager, praId, praItem, 1);
        mvc.perform(as(aOwner, patch("/realisasi/" + failed + "/reject")).content("{\"reason\":\"x\"}"))
                .andExpect(status().isOk());
        mvc.perform(as(aManager, post("/realisasi/" + failed + "/supersede")).content(supersedeBody(praId, praItem)))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers ----

    /** PR by A's Store Manager → approved by A's Owner → PRA by A's Owner; returns the PRA line id. */
    private String activePraItem() throws Exception {

        String prBody = mvc.perform(as(aManager, post("/purchase-requests")).content(prBody(null, aManager, partA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String prId = JsonPath.read(prBody, "$.data.id");

        mvc.perform(as(aOwner, patch("/purchase-requests/" + prId + "/approve"))).andExpect(status().isOk());

        String praBody = mvc.perform(as(aOwner, post("/purchase-authorizations"))
                .content("{\"purchaseRequestIds\":[\"" + prId + "\"],\"items\":[{\"partId\":\"" + partA.getId()
                        + "\",\"authorizedQty\":12,\"maxValue\":900000}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        String itemId = JsonPath.read(praBody, "$.data.items[0].id");
        praIdByItem.put(itemId, JsonPath.read(praBody, "$.data.id"));
        return itemId;
    }

    private final java.util.Map<String, String> praIdByItem = new java.util.HashMap<>();

    private String praOf(String praItemId) {
        return praIdByItem.get(praItemId);
    }

    private String logPurchase(User u, String praId, String praItem, int pakets) throws Exception {

        String body = mvc.perform(as(u, post("/realisasi")).content(realisasiBody(null, praId, praItem, pakets)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private int remainingQty(String praId) throws Exception {

        String body = mvc.perform(as(aOwner, get("/purchase-authorizations/" + praId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Integer>read(body, "$.data.items[0].remainingQty");
    }

    private String prBody(Tenant target, User requestedBy, Part part) {
        return prBodyRaw(target != null ? target.getId() : null, requestedBy.getId(), part.getId());
    }

    private static String prBodyRaw(UUID target, UUID requestedBy, UUID partId) {
        return "{" + (target != null ? "\"tenantId\":\"" + target + "\"," : "")
                + "\"requestedBy\":\"" + requestedBy + "\",\"items\":[{\"partId\":\"" + partId + "\",\"quantity\":12}]}";
    }

    /** pakets × factor 6 cans at Rp 300,000 per paket. */
    private String realisasiBody(Tenant target, String praId, String praItem, int pakets) {

        long subtotal = 300_000L * pakets;

        return "{" + (target != null ? "\"tenantId\":\"" + target.getId() + "\"," : "")
                + "\"purchaseRequestAuthorizationId\":\"" + praId + "\","
                + "\"externalOrderRef\":\"BE17-" + UUID.randomUUID() + "\","
                + "\"paymentMethod\":\"COMPANY_ACCOUNT\",\"subtotal\":" + subtotal + ","
                + "\"items\":[{\"purchaseRequestAuthorizationItemId\":\"" + praItem + "\",\"partId\":\"" + partA.getId()
                + "\",\"purchaseUom\":\"paket\",\"conversionFactor\":6,\"purchasedQty\":" + pakets
                + ",\"actualUnitPrice\":300000}]}";
    }

    private String supersedeBody(String praId, String praItem) {
        return "{\"reason\":\"typo in the receipt\",\"correction\":" + realisasiBody(null, praId, praItem, 1) + "}";
    }

    private List<NotificationType> typesFor(User u) {
        return notificationRepository.findByRecipientUserId(u.getId()).stream()
                .map(Notification::getType).sorted().toList();
    }

    private Part part(Tenant tenant, String name) {

        PartCategory category = categoryRepository.save(PartCategory.builder()
                .tenant(tenant).name("Gas").consumable(true).build());

        return partRepository.save(Part.builder()
                .tenant(tenant).name(name).category(category)
                .currentStock(0).minimumStock(6).reorderQuantity(12)
                .active(true).retired(false).build());
    }

    private Tenant tenant(String code, String name, Tenant parent) {
        return tenantRepository.save(Tenant.builder().code(code).name(name).active(true).parent(parent).build());
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
