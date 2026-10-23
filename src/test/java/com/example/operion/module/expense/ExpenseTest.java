package com.example.operion.module.expense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.notification.entity.Notification;
import com.example.operion.module.notification.enums.NotificationReferenceType;
import com.example.operion.module.notification.enums.NotificationType;
import com.example.operion.module.notification.repository.NotificationRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.upload.entity.Upload;
import com.example.operion.module.upload.enums.UploadPurpose;
import com.example.operion.module.upload.repository.UploadRepository;
import com.example.operion.module.upload.service.R2Storage;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-19 (PROC-10): expense claims. Fixtures: HQ → Store plus an unrelated
 * root. Storage is mocked; receipts are upload rows that "exist" in storage.
 * Amounts are distinct and non-round so a sum or a mix-up shows (rule 6).
 * The REIMBURSEMENT_PAID insert with reference type EXPENSE proves V12's
 * notifications_reference_type_check (rule 3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExpenseTest {

    static final String DATE = "2026-10-14";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UploadRepository uploadRepository;
    @Autowired private NotificationRepository notificationRepository;

    @MockitoBean private R2Storage storage;

    private Tenant store;
    private User sm;
    private User sm2;
    private User owner;
    private User owner2;
    private User operator;
    private User hqPrincipal;
    private User otherOwner;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenantRepository.save(Tenant.builder().code("BE19_HQ_" + s).name("HQ").active(true).build());
        store = tenantRepository.save(Tenant.builder().code("BE19_ST_" + s).name("Store " + s).active(true).parent(hq).build());
        Tenant other = tenantRepository.save(Tenant.builder().code("BE19_OT_" + s).name("Other").active(true).build());

        sm = user("be19-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);
        sm2 = user("be19-sm2-" + s, Role.OPERATOR, Scope.MANAGER, store);
        owner = user("be19-own-" + s, Role.OPERATOR, Scope.OWNER, store);
        owner2 = user("be19-own2-" + s, Role.OPERATOR, Scope.OWNER, store);
        operator = user("be19-op-" + s, Role.OPERATOR, null, store);
        hqPrincipal = user("be19-hq-" + s, Role.ADMIN, Scope.PRINCIPAL, hq);
        otherOwner = user("be19-oth-" + s, Role.OPERATOR, Scope.OWNER, other);

        when(storage.exists(anyString())).thenReturn(true);
    }

    // ---- recording ----

    @Test
    void create_whoMayRecordWhat() throws Exception {

        mvc.perform(as(operator, post("/expenses")).content(body("Tissue", "48500", "BRANCH_SPECIFIC", "PERSONAL", receipt(operator))))
                .andExpect(status().isForbidden());

        // paid by the store is the Owner's to record
        mvc.perform(as(sm, post("/expenses")).content(body("Printer paper", "87300", "BRANCH_SPECIFIC", "STORE", null)))
                .andExpect(status().isForbidden());

        // paid by me needs the receipt
        mvc.perform(as(sm, post("/expenses")).content(body("Printer paper", "87300", "BRANCH_SPECIFIC", "PERSONAL", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Add a photo of the receipt: it's what you're paid back against"));

        mvc.perform(as(sm, post("/expenses")).content(body("Printer paper", "0", "BRANCH_SPECIFIC", "PERSONAL", receipt(sm))))
                .andExpect(status().isBadRequest());

        // someone else's photo answers like a missing one. In a real request the
        // whole transaction rolls back; this test's own transaction wraps the call,
        // so "nothing saved" can't be observed here.
        mvc.perform(as(sm, post("/expenses")).content(body("Printer paper", "87300", "BRANCH_SPECIFIC", "PERSONAL", receipt(sm2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Upload not found"));

        UUID photo = receipt(sm);
        mvc.perform(as(sm, post("/expenses")).content(body("Printer paper", "87300", "BRANCH_SPECIFIC", "PERSONAL", photo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.reimbursementStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.amount").value(87300))
                .andExpect(jsonPath("$.data.tenantId").value(store.getId().toString()))
                .andExpect(jsonPath("$.data.receiptUploadIds[0]").value(photo.toString()));
        assertEquals("EXPENSE", uploadRepository.findById(photo).orElseThrow().getAttachedType());

        // the Owner records the store's own spending; no receipt required
        mvc.perform(as(owner, post("/expenses")).content(body("Cleaning service", "350000", "HQ", "STORE", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reimbursementStatus").value("NOT_APPLICABLE"))
                .andExpect(jsonPath("$.data.receiptUploadIds.length()").value(0));
    }

    // ---- who sees amounts ----

    @Test
    void amounts_ownerPlus_andTheClaimantForTheirOwn() throws Exception {

        String smOwn = record(sm, "Printer paper", "87300", "BRANCH_SPECIFIC", "PERSONAL");
        String storePaid = record(owner, "Cleaning service", "350000", "HQ", "STORE");

        mvc.perform(as(sm, get("/expenses/" + smOwn))).andExpect(jsonPath("$.data.amount").value(87300));
        mvc.perform(as(sm2, get("/expenses/" + smOwn))).andExpect(jsonPath("$.data.amount").isEmpty());
        mvc.perform(as(sm, get("/expenses/" + storePaid))).andExpect(jsonPath("$.data.amount").isEmpty());
        mvc.perform(as(owner, get("/expenses/" + smOwn))).andExpect(jsonPath("$.data.amount").value(87300));

        // HQ reads every store's, amounts included; another root reads nothing
        mvc.perform(as(hqPrincipal, get("/expenses")))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.id=='" + storePaid + "')].amount").value(350000));
        mvc.perform(as(otherOwner, get("/expenses/" + smOwn)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Expense not found"));
    }

    @Test
    void list_tabsAndOwed() throws Exception {

        String paper = record(sm, "Printer paper", "87300", "BRANCH_SPECIFIC", "PERSONAL");
        String banner = record(sm, "Banner HQ asked for", "612500", "HQ", "PERSONAL");
        String cleaning = record(owner, "Cleaning service", "350000", "HQ", "STORE");

        mvc.perform(as(owner, get("/expenses").param("category", "HQ")))
                .andExpect(jsonPath("$.data[*].id").value(org.hamcrest.Matchers.containsInAnyOrder(banner, cleaning)));
        mvc.perform(as(owner, get("/expenses").param("category", "BRANCH_SPECIFIC")))
                .andExpect(jsonPath("$.data[*].id").value(org.hamcrest.Matchers.contains(paper)));
        mvc.perform(as(owner, get("/expenses").param("reimbursementStatus", "PENDING")))
                .andExpect(jsonPath("$.data[*].id").value(org.hamcrest.Matchers.containsInAnyOrder(paper, banner)));
        mvc.perform(as(owner, get("/expenses").param("paidBy", "STORE")))
                .andExpect(jsonPath("$.data[*].id").value(org.hamcrest.Matchers.contains(cleaning)));
    }

    // ---- paying back (BE-18's rules) ----

    @Test
    void markPaid_byTheStoresOwnOwner_severalAtOnce_claimantTold() throws Exception {

        String banner = record(sm, "Banner HQ asked for", "612500", "HQ", "PERSONAL");
        String toner = record(sm, "Toner", "438650", "BRANCH_SPECIFIC", "PERSONAL");

        for (User refused : List.of(sm, sm2, hqPrincipal)) {
            markPaid(refused, banner, toner).andExpect(status().isForbidden());
        }

        markPaid(owner, banner, toner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].reimbursementStatus").value("REIMBURSED"))
                .andExpect(jsonPath("$.data[0].reimbursedByName").value(owner.getFullName()))
                .andExpect(jsonPath("$.data[0].reimbursementMethod").value("TRANSFER"));

        List<Notification> told = notificationRepository.findByRecipientUserId(sm.getId());
        assertEquals(1, told.size());
        assertEquals(NotificationType.REIMBURSEMENT_PAID, told.get(0).getType());
        assertEquals(NotificationReferenceType.EXPENSE, told.get(0).getReferenceType());
        assertTrue(told.get(0).getMessage().startsWith("Rp 1.051.150 paid back by " + owner.getFullName()),
                told.get(0).getMessage());
    }

    @Test
    void markPaid_allOrNothing_andNeverYourOwn() throws Exception {

        String owed = record(sm, "Toner", "438650", "BRANCH_SPECIFIC", "PERSONAL");
        String storePaid = record(owner, "Cleaning service", "350000", "HQ", "STORE");

        markPaid(owner, owed, storePaid).andExpect(status().isBadRequest());
        mvc.perform(as(owner, get("/expenses/" + owed))).andExpect(jsonPath("$.data.reimbursementStatus").value("PENDING"));

        // an Owner who paid personally is paid back by another Owner, never by themself
        String ownersOwn = record(owner, "Fuel for the generator", "91750", "BRANCH_SPECIFIC", "PERSONAL");
        markPaid(owner, ownersOwn).andExpect(status().isForbidden());
        markPaid(owner2, ownersOwn).andExpect(status().isOk());
    }

    @Test
    void undo_ownerWithReason_andVoidRules() throws Exception {

        String toner = record(sm, "Toner", "438650", "BRANCH_SPECIFIC", "PERSONAL");
        markPaid(owner, toner).andExpect(status().isOk());

        // paid back: undo first, then void
        mvc.perform(as(sm, patch("/expenses/" + toner + "/void")).content("{\"reason\":\"duplicate\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(as(owner, patch("/expenses/" + toner + "/reimbursement/undo")).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(owner, patch("/expenses/" + toner + "/reimbursement/undo")).content("{\"reason\":\"Wrong person\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reimbursementStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.reimbursementUndoReason").value("Wrong person"))
                .andExpect(jsonPath("$.data.reimbursedAt").isEmpty());

        // a colleague can't void it; the claimant can, with a reason, once
        mvc.perform(as(sm2, patch("/expenses/" + toner + "/void")).content("{\"reason\":\"duplicate\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(sm, patch("/expenses/" + toner + "/void")).content("{\"reason\":\"Entered twice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOIDED"))
                .andExpect(jsonPath("$.data.voidReason").value("Entered twice"))
                .andExpect(jsonPath("$.data.voidedByName").value(sm.getFullName()))
                .andExpect(jsonPath("$.data.reimbursementStatus").value("NOT_APPLICABLE"));
        mvc.perform(as(sm, patch("/expenses/" + toner + "/void")).content("{\"reason\":\"again\"}"))
                .andExpect(status().isBadRequest());

        // HQ can read it, not void it
        String other = record(sm, "Stapler", "65400", "BRANCH_SPECIFIC", "PERSONAL");
        mvc.perform(as(hqPrincipal, patch("/expenses/" + other + "/void")).content("{\"reason\":\"no\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- BE-16: other expenses in the spending summary ----

    @Test
    void spending_countsOtherExpensesInTheBudget() throws Exception {

        store.setMonthlyPurchaseBudget(new BigDecimal("1000000"));
        tenantRepository.save(store);

        record(sm, "Banner HQ asked for", "612500", "HQ", "PERSONAL");
        record(sm, "Toner", "438650", "BRANCH_SPECIFIC", "PERSONAL");
        String voided = record(sm, "Mistake", "999999", "HQ", "PERSONAL");
        mvc.perform(as(sm, patch("/expenses/" + voided + "/void")).content("{\"reason\":\"typo\"}")).andExpect(status().isOk());
        mvc.perform(as(sm, post("/expenses")).content(bodyOn("2026-11-01", "Next month", "77700", "HQ", "PERSONAL", receipt(sm))))
                .andExpect(status().isOk());

        mvc.perform(as(owner, get("/procurement/spending").param("month", "2026-10")))
                .andExpect(jsonPath("$.data[0].otherExpenses").value(612500 + 438650))
                .andExpect(jsonPath("$.data[0].otherExpensesHq").value(612500))
                .andExpect(jsonPath("$.data[0].otherExpensesBranch").value(438650))
                .andExpect(jsonPath("$.data[0].spent").value(0))
                .andExpect(jsonPath("$.data[0].overBudget").value(true));
    }

    // ---- helpers ----

    private String record(User u, String what, String amount, String category, String paidBy) throws Exception {

        UUID photo = "PERSONAL".equals(paidBy) ? receipt(u) : null;

        return JsonPath.read(mvc.perform(as(u, post("/expenses")).content(body(what, amount, category, paidBy, photo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    private ResultActions markPaid(User u, String... ids) throws Exception {

        return mvc.perform(as(u, patch("/expenses/reimbursements/mark-paid")).content(
                "{\"ids\":[\"" + String.join("\",\"", ids) + "\"],\"paidAt\":\"2026-10-21\",\"method\":\"TRANSFER\","
                        + "\"reference\":\"BCA 0981\"}"));
    }

    private static String body(String what, String amount, String category, String paidBy, UUID receipt) {

        return bodyOn(DATE, what, amount, category, paidBy, receipt);
    }

    private static String bodyOn(String date, String what, String amount, String category, String paidBy, UUID receipt) {

        return "{\"description\":\"" + what + "\",\"amount\":" + amount + ",\"expenseDate\":\"" + date + "\","
                + "\"category\":\"" + category + "\",\"paidBy\":\"" + paidBy + "\""
                + (receipt != null ? ",\"receiptUploadIds\":[\"" + receipt + "\"]" : "") + "}";
    }

    /** A receipt photo {@code uploader} has put in storage. */
    private UUID receipt(User uploader) {

        UUID id = UUID.randomUUID();
        uploadRepository.save(Upload.builder()
                .id(id)
                .tenant(uploader.getTenant())
                .uploadedBy(uploader)
                .purpose(UploadPurpose.EXPENSE_RECEIPT)
                .objectKey(uploader.getTenant().getId() + "/expense_receipt/" + id)
                .contentType("image/jpeg")
                .sizeBytes(380_000L)
                .build());
        return id;
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
