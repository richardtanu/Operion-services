package com.example.operion.module.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.expense.entity.Expense;
import com.example.operion.module.expense.enums.ExpenseCategory;
import com.example.operion.module.expense.enums.ExpensePaidBy;
import com.example.operion.module.expense.enums.ExpenseStatus;
import com.example.operion.module.expense.repository.ExpenseRepository;
import com.example.operion.module.procurement.entity.PurchaseOrder;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;
import com.example.operion.module.procurement.entity.Realisasi;
import com.example.operion.module.procurement.entity.Supplier;
import com.example.operion.module.procurement.enums.PaymentMethod;
import com.example.operion.module.procurement.enums.PurchaseOrderStatus;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.enums.ReimbursementMethod;
import com.example.operion.module.procurement.enums.ReimbursementStatus;
import com.example.operion.module.procurement.repository.PurchaseOrderRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationRepository;
import com.example.operion.module.procurement.repository.RealisasiRepository;
import com.example.operion.module.procurement.repository.SupplierRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-41: Accounting reads every amount the Owner reads and acts on none, and
 * the cashflow list (GET /procurement/cashflow) shows each money movement on
 * the day the store's money moved. Records are built directly with distinct,
 * non-round amounts, so a missing, doubled or misdated row changes the total
 * (rule 6). October 2026: the 31st 23:30 is in, 1 November 00:00 is out.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountingCashflowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PurchaseRequestAuthorizationRepository praRepository;
    @Autowired private RealisasiRepository realisasiRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private SupplierRepository supplierRepository;

    private Tenant storeA;
    private Tenant storeB;
    private User accountant;     // store A, ACCOUNTING, no scope
    private User hqAccountant;   // HQ, ACCOUNTING
    private User owner;          // store A, OWNER
    private User sm;             // store A, MANAGER
    private User operator;       // store A, OPERATOR
    private Supplier hqCentre;
    private PurchaseRequestAuthorization praA;

    private Realisasi approved;
    private Realisasi owed;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenantRepository.save(Tenant.builder().code("BE41_HQ_" + s).name("HQ").active(true).build());
        storeA = tenantRepository.save(Tenant.builder().code("BE41_A_" + s).name("Store A " + s).active(true).parent(hq).build());
        storeB = tenantRepository.save(Tenant.builder().code("BE41_B_" + s).name("Store B " + s).active(true).parent(hq).build());

        accountant = user("be41-acc-" + s, Role.ACCOUNTING, null, storeA);
        hqAccountant = user("be41-hqacc-" + s, Role.ACCOUNTING, null, hq);
        owner = user("be41-own-" + s, Role.OPERATOR, Scope.OWNER, storeA);
        sm = user("be41-sm-" + s, Role.OPERATOR, Scope.MANAGER, storeA);
        operator = user("be41-op-" + s, Role.OPERATOR, null, storeA);

        hqCentre = supplierRepository.save(Supplier.builder().tenant(hq).name("HQ Bandung centre " + s).franchiseCentre(true).build());

        praA = praRepository.save(PurchaseRequestAuthorization.builder()
                .tenant(storeA).approvedBy(owner).status(PurchaseRequestAuthorizationStatus.ACTIVE).build());
        PurchaseRequestAuthorization praB = praRepository.save(PurchaseRequestAuthorization.builder()
                .tenant(storeB).approvedBy(owner).status(PurchaseRequestAuthorizationStatus.ACTIVE).build());

        // store A, in October: six movements out
        approved = purchase(storeA, praA, "487350", PaymentMethod.COMPANY_ACCOUNT, RealisasiStatus.APPROVED, at(5, 10));
        purchase(storeA, praA, "312800", PaymentMethod.COMPANY_ACCOUNT, RealisasiStatus.PENDING_APPROVAL, at(12, 14));
        purchase(storeA, praA, "11110", PaymentMethod.COMPANY_ACCOUNT, RealisasiStatus.APPROVED, LocalDateTime.of(2026, 10, 31, 23, 30));
        Realisasi paidBack = purchase(storeA, praA, "154250", PaymentMethod.PERSONAL_REIMBURSABLE, RealisasiStatus.APPROVED, at(3, 9));
        paidBack.setReimbursementStatus(ReimbursementStatus.REIMBURSED);
        paidBack.setReimbursedTo(sm);
        paidBack.setReimbursedAt(at(20, 0));
        paidBack.setReimbursementMethod(ReimbursementMethod.TRANSFER);
        realisasiRepository.save(paidBack);
        expense(storeA, "Printer paper", "76400", ExpensePaidBy.STORE, ExpenseStatus.ACTIVE, LocalDate.of(2026, 10, 15), null);
        expense(storeA, "Taxi to the bank", "43900", ExpensePaidBy.PERSONAL, ExpenseStatus.ACTIVE, LocalDate.of(2026, 10, 9), at(22, 0));
        order(storeA, "1236000", PurchaseOrderStatus.ORDERED, LocalDate.of(2026, 10, 18));

        // store A, not cash out in October
        purchase(storeA, praA, "199000", PaymentMethod.COMPANY_ACCOUNT, RealisasiStatus.SUPERSEDED, at(6, 11));      // replaced by its correction
        purchase(storeA, praA, "64300", PaymentMethod.COMPANY_ACCOUNT, RealisasiStatus.APPROVED, LocalDateTime.of(2026, 9, 30, 22, 0));
        purchase(storeA, praA, "22220", PaymentMethod.COMPANY_ACCOUNT, RealisasiStatus.APPROVED, LocalDateTime.of(2026, 11, 1, 0, 0));
        owed = purchase(storeA, praA, "98600", PaymentMethod.PERSONAL_REIMBURSABLE, RealisasiStatus.APPROVED, at(8, 16)); // owed, not paid
        owed.setReimbursementStatus(ReimbursementStatus.PENDING);
        realisasiRepository.save(owed);
        expense(storeA, "Cleaning", "51700", ExpensePaidBy.STORE, ExpenseStatus.VOIDED, LocalDate.of(2026, 10, 16), null);
        expense(storeA, "Parking", "8800", ExpensePaidBy.PERSONAL, ExpenseStatus.ACTIVE, LocalDate.of(2026, 10, 17), null); // owed
        order(storeA, "905000", PurchaseOrderStatus.DRAFT, LocalDate.of(2026, 10, 19));
        order(storeA, "377000", PurchaseOrderStatus.CANCELLED, LocalDate.of(2026, 10, 21));

        // store B
        purchase(storeB, praB, "55500", PaymentMethod.COMPANY_ACCOUNT, RealisasiStatus.APPROVED, at(10, 13));
    }

    @Test
    void theCashflow_movesOnTheDayTheStoresMoneyMoves() throws Exception {

        String body = cashflow(accountant, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2026-10-01"))
                .andExpect(jsonPath("$.data.to").value("2026-10-31"))
                .andExpect(jsonPath("$.data.totalOut").value(2321810))
                .andExpect(jsonPath("$.data.totalIn").value(0))
                .andExpect(jsonPath("$.data.net").value(-2321810))
                .andExpect(jsonPath("$.data.events.length()").value(7))
                .andReturn().getResponse().getContentAsString();

        // newest first, each on the day the money moved
        assertEquals(List.of("2026-10-31", "2026-10-22", "2026-10-20", "2026-10-18", "2026-10-15", "2026-10-12", "2026-10-05"),
                JsonPath.read(body, "$.data.events[*].date"));
        assertEquals(List.of("PURCHASE", "EXPENSE_PAYBACK", "PURCHASE_PAYBACK", "ORDER_TO_HQ", "EXPENSE", "PURCHASE", "PURCHASE"),
                JsonPath.read(body, "$.data.events[*].kind"));
        assertEquals(List.of("OUT", "OUT", "OUT", "OUT", "OUT", "OUT", "OUT"), JsonPath.read(body, "$.data.events[*].direction"));

        // the paid-back purchase: on the pay-back day, to the Store Manager, by transfer
        assertEquals(154250, JsonPath.<Number>read(body, "$.data.events[2].amount").intValue());
        assertEquals(sm.getFullName(), JsonPath.read(body, "$.data.events[2].counterparty"));
        assertEquals("TRANSFER", JsonPath.read(body, "$.data.events[2].method"));
        // the order goes to HQ; the pending purchase says so
        assertEquals(hqCentre.getName(), JsonPath.read(body, "$.data.events[3].counterparty"));
        assertEquals("PENDING_APPROVAL", JsonPath.read(body, "$.data.events[5].recordStatus"));
        assertEquals(storeA.getName(), JsonPath.read(body, "$.data.events[0].tenantName"));
    }

    @Test
    void hqSeesItsStores_oneStoreOnRequest_andAStoreCantLookSideways() throws Exception {

        cashflow(hqAccountant, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOut").value(2377310))
                .andExpect(jsonPath("$.data.events.length()").value(8));

        cashflow(hqAccountant, storeB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOut").value(55500))
                .andExpect(jsonPath("$.data.events[0].tenantName").value(storeB.getName()));

        cashflow(accountant, storeB)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Store not found"));

        mvc.perform(as(accountant, get("/procurement/cashflow")
                        .param("from", "2026-10-31").param("to", "2026-10-01")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accountingAndOwnersSeeIt_othersDont() throws Exception {

        cashflow(owner, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalOut").value(2321810));

        for (User refused : List.of(sm, operator)) {
            cashflow(refused, null).andExpect(status().isForbidden());
        }
    }

    @Test
    void accountingReadsTheMoney_butActsOnNone() throws Exception {

        // amounts the Owner sees, and a Store Manager doesn't
        mvc.perform(as(accountant, get("/realisasi/" + approved.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCost").value(487350));
        mvc.perform(as(sm, get("/realisasi/" + approved.getId())))
                .andExpect(jsonPath("$.data.totalCost").doesNotExist());

        mvc.perform(as(accountant, get("/procurement/spending").param("month", "2026-10")))
                .andExpect(status().isOk());
        mvc.perform(as(accountant, get("/procurement/activity")))
                .andExpect(status().isOk());
        mvc.perform(as(accountant, get("/expenses")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.description == 'Printer paper')].amount").value(76400));

        // but approves, rejects and pays back nothing
        mvc.perform(as(accountant, patch("/realisasi/reimbursements/mark-paid"))
                        .content("{\"ids\":[\"" + owed.getId() + "\"],\"paidAt\":\"2026-10-25\",\"method\":\"CASH\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(accountant, patch("/realisasi/" + approved.getId() + "/reject")).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private ResultActions cashflow(User by, Tenant store) throws Exception {

        MockHttpServletRequestBuilder req = get("/procurement/cashflow").param("from", "2026-10-01").param("to", "2026-10-31");
        if (store != null) {
            req.param("tenantId", store.getId().toString());
        }
        return mvc.perform(as(by, req));
    }

    private static LocalDateTime at(int october, int hour) {
        return LocalDateTime.of(2026, 10, october, hour, 0);
    }

    private Realisasi purchase(Tenant store, PurchaseRequestAuthorization pra, String total, PaymentMethod method,
            RealisasiStatus status, LocalDateTime purchasedAt) {

        return realisasiRepository.save(Realisasi.builder()
                .tenant(store)
                .purchaseRequestAuthorization(pra)
                .createdBy(sm)
                .externalOrderRef("BE41-" + UUID.randomUUID().toString().substring(0, 8))
                .sellerName("Shopee · Toko Airsoft Jaya")
                .paymentMethod(method)
                .reimbursementStatus(method == PaymentMethod.PERSONAL_REIMBURSABLE
                        ? ReimbursementStatus.PENDING : ReimbursementStatus.NOT_APPLICABLE)
                .status(status)
                .purchasedAt(purchasedAt)
                .subtotal(new BigDecimal(total))
                .totalCost(new BigDecimal(total))
                .build());
    }

    private void expense(Tenant store, String description, String amount, ExpensePaidBy paidBy, ExpenseStatus status,
            LocalDate date, LocalDateTime paidBackAt) {

        expenseRepository.save(Expense.builder()
                .tenant(store)
                .description(description)
                .amount(new BigDecimal(amount))
                .expenseDate(date)
                .category(ExpenseCategory.BRANCH_SPECIFIC)
                .paidBy(paidBy)
                .status(status)
                .createdBy(sm)
                .reimbursementStatus(paidBy == ExpensePaidBy.STORE ? ReimbursementStatus.NOT_APPLICABLE
                        : paidBackAt != null ? ReimbursementStatus.REIMBURSED : ReimbursementStatus.PENDING)
                .reimbursedBy(paidBackAt != null ? owner : null)
                .reimbursedAt(paidBackAt)
                .reimbursementMethod(paidBackAt != null ? ReimbursementMethod.CASH : null)
                .build());
    }

    private void order(Tenant store, String total, PurchaseOrderStatus status, LocalDate orderDate) {

        purchaseOrderRepository.save(PurchaseOrder.builder()
                .tenant(store)
                .supplier(hqCentre)
                .poNumber("PO-BE41-" + UUID.randomUUID().toString().substring(0, 8))
                .orderDate(orderDate)
                .status(status)
                .totalAmount(new BigDecimal(total))
                .createdBy(sm)
                .build());
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
