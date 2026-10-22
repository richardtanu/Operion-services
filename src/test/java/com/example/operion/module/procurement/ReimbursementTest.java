package com.example.operion.module.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;
import com.example.operion.module.procurement.entity.Realisasi;
import com.example.operion.module.procurement.enums.PaymentMethod;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.enums.ReimbursementStatus;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationRepository;
import com.example.operion.module.procurement.repository.RealisasiRepository;
import com.example.operion.module.procurement.repository.RealisasiStatusHistoryRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-18: the store Owner pays the Store Manager back. Purchases are built
 * directly (the logging flow itself is covered elsewhere) with distinct,
 * non-round totals so a sum or a mix-up shows (rule 6). The REIMBURSEMENT_PAID
 * insert also proves migration V6 (notifications_type_check, rule 3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReimbursementTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PurchaseRequestAuthorizationRepository praRepository;
    @Autowired private RealisasiRepository realisasiRepository;
    @Autowired private RealisasiStatusHistoryRepository historyRepository;
    @Autowired private NotificationRepository notificationRepository;

    private Tenant store;
    private User sm;            // Store Manager: bought with own money
    private User sm2;           // a colleague MANAGER
    private User owner;         // the store Owner
    private User hqPrincipal;   // HQ super admin: ADMIN + PRINCIPAL
    private PurchaseRequestAuthorization pra;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenantRepository.save(Tenant.builder().code("BE18_HQ_" + s).name("HQ").active(true).build());
        store = tenantRepository.save(Tenant.builder().code("BE18_ST_" + s).name("Store").active(true).parent(hq).build());

        sm = user("be18-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);
        sm2 = user("be18-sm2-" + s, Role.OPERATOR, Scope.MANAGER, store);
        owner = user("be18-own-" + s, Role.OPERATOR, Scope.OWNER, store);
        hqPrincipal = user("be18-hq-" + s, Role.ADMIN, Scope.PRINCIPAL, hq);

        pra = praRepository.save(PurchaseRequestAuthorization.builder()
                .tenant(store).approvedBy(owner).status(PurchaseRequestAuthorizationStatus.ACTIVE).build());
    }

    @Test
    void markPaid_severalAtOnce_byTheStoreOwner() throws Exception {

        Realisasi a = owed(sm, "612500");
        Realisasi b = owed(sm, "348750");
        Realisasi c = owed(sm, "89900");

        mvc.perform(as(owner, patch("/realisasi/reimbursements/mark-paid"))
                .content(markBody(List.of(a, b, c), "TRANSFER", "BCA 0981")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].reimbursementStatus").value("REIMBURSED"))
                .andExpect(jsonPath("$.data[0].reimbursedToName").value(sm.getFullName()))
                .andExpect(jsonPath("$.data[0].reimbursementMethod").value("TRANSFER"))
                .andExpect(jsonPath("$.data[0].reimbursementReference").value("BCA 0981"));

        Realisasi reloaded = realisasiRepository.findById(a.getId()).orElseThrow();
        assertEquals(sm.getId(), reloaded.getReimbursedTo().getId());
        assertEquals(2026, reloaded.getReimbursedAt().getYear());

        // one history row per record, by the Owner
        assertTrue(historyRepository.findByRealisasi_IdOrderByCreatedAtDesc(b.getId()).stream()
                .anyMatch(h -> h.getChangedBy().getId().equals(owner.getId())
                        && h.getReason().startsWith("Paid back by transfer")));

        // one notification per call, with the total of all three: 612.500 + 348.750 + 89.900
        List<Notification> received = notificationRepository.findByRecipientUserId(sm.getId());
        assertEquals(1, received.size());
        assertEquals(NotificationType.REIMBURSEMENT_PAID, received.get(0).getType());
        assertTrue(received.get(0).getMessage().startsWith("Rp 1.051.150 paid back"), received.get(0).getMessage());
    }

    @Test
    void markPaid_allOrNothing() throws Exception {

        Realisasi pending = owed(sm, "100000");
        Realisasi alreadyPaid = owed(sm, "200000");
        alreadyPaid.setReimbursementStatus(ReimbursementStatus.REIMBURSED);
        realisasiRepository.save(alreadyPaid);

        mvc.perform(as(owner, patch("/realisasi/reimbursements/mark-paid"))
                .content(markBody(List.of(pending, alreadyPaid), "CASH", null)))
                .andExpect(status().isBadRequest());

        assertEquals(ReimbursementStatus.PENDING,
                realisasiRepository.findById(pending.getId()).orElseThrow().getReimbursementStatus(),
                "nothing changed");
    }

    @Test
    void markPaid_whoMayNot() throws Exception {

        Realisasi r = owed(sm, "150000");

        for (User refused : List.of(sm, sm2, hqPrincipal)) {
            mvc.perform(as(refused, patch("/realisasi/reimbursements/mark-paid")).content(markBody(List.of(r), "CASH", null)))
                    .andExpect(status().isForbidden());
        }

        // an Owner can't mark money owed to themself
        Realisasi ownersOwn = owed(owner, "75000");
        mvc.perform(as(owner, patch("/realisasi/reimbursements/mark-paid")).content(markBody(List.of(ownersOwn), "CASH", null)))
                .andExpect(status().isForbidden());

        assertEquals(ReimbursementStatus.PENDING, realisasiRepository.findById(r.getId()).orElseThrow().getReimbursementStatus());
    }

    @Test
    void undo_ownerWithReason() throws Exception {

        Realisasi r = owed(sm, "150000");
        mvc.perform(as(owner, patch("/realisasi/reimbursements/mark-paid")).content(markBody(List.of(r), "CASH", "envelope")))
                .andExpect(status().isOk());

        mvc.perform(as(owner, patch("/realisasi/" + r.getId() + "/reimbursement/undo")).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(as(owner, patch("/realisasi/" + r.getId() + "/reimbursement/undo"))
                .content("{\"reason\":\"Marked the wrong one\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reimbursementStatus").value("PENDING"));

        Realisasi reloaded = realisasiRepository.findById(r.getId()).orElseThrow();
        assertNull(reloaded.getReimbursedTo());
        assertNull(reloaded.getReimbursedAt());
        assertNull(reloaded.getReimbursementMethod());
        assertNull(reloaded.getReimbursementReference());
    }

    @Test
    void theSmSeesWhatTheyAreOwed_onlyThat() throws Exception {

        Realisasi mine = owed(sm, "612500");
        Realisasi colleagues = owed(sm2, "348750");
        Realisasi myCompanyPaid = owed(sm, "89900");
        myCompanyPaid.setPaymentMethod(PaymentMethod.COMPANY_ACCOUNT);
        myCompanyPaid.setReimbursementStatus(ReimbursementStatus.NOT_APPLICABLE);
        realisasiRepository.save(myCompanyPaid);

        mvc.perform(as(sm, get("/realisasi/" + mine.getId())))
                .andExpect(jsonPath("$.data.totalCost").value(612500))
                .andExpect(jsonPath("$.data.subtotal").isEmpty());
        mvc.perform(as(sm, get("/realisasi/" + colleagues.getId())))
                .andExpect(jsonPath("$.data.totalCost").isEmpty());
        mvc.perform(as(sm, get("/realisasi/" + myCompanyPaid.getId())))
                .andExpect(jsonPath("$.data.totalCost").isEmpty());
    }

    @Test
    void aRejectedPurchaseIsStillOwed_andCanBePaidBack() throws Exception {

        Realisasi failed = owed(sm, "120000");
        failed.setStatus(RealisasiStatus.FAILED);
        realisasiRepository.save(failed);

        mvc.perform(as(owner, patch("/realisasi/reimbursements/mark-paid")).content(markBody(List.of(failed), "CASH", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data[0].reimbursementStatus").value("REIMBURSED"));
    }

    @Test
    void list_filtersOnTheServer() throws Exception {

        Realisasi owedOne = owed(sm, "100000");
        Realisasi paidOne = owed(sm, "200000");
        paidOne.setReimbursementStatus(ReimbursementStatus.REIMBURSED);
        realisasiRepository.save(paidOne);

        String body = mvc.perform(as(owner, get("/realisasi")
                .param("paymentMethod", "PERSONAL_REIMBURSABLE").param("reimbursementStatus", "PENDING")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.data[*].id");

        assertTrue(ids.contains(owedOne.getId().toString()));
        assertTrue(!ids.contains(paidOne.getId().toString()));
    }

    // ---- helpers ----

    private Realisasi owed(User buyer, String total) {

        return realisasiRepository.save(Realisasi.builder()
                .tenant(store)
                .purchaseRequestAuthorization(pra)
                .createdBy(buyer)
                .externalOrderRef("BE18-" + UUID.randomUUID().toString().substring(0, 8))
                .status(RealisasiStatus.APPROVED)
                .paymentMethod(PaymentMethod.PERSONAL_REIMBURSABLE)
                .reimbursementStatus(ReimbursementStatus.PENDING)
                .subtotal(new BigDecimal(total))
                .totalCost(new BigDecimal(total))
                .build());
    }

    private static String markBody(List<Realisasi> records, String method, String reference) {

        String ids = String.join("\",\"", records.stream().map(r -> r.getId().toString()).toList());
        return "{\"ids\":[\"" + ids + "\"],\"paidAt\":\"2026-10-21\",\"method\":\"" + method + "\""
                + (reference != null ? ",\"reference\":\"" + reference + "\"" : "") + "}";
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
