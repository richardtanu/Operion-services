package com.example.operion.module.procurement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.procurement.entity.PurchaseOrder;
import com.example.operion.module.procurement.entity.PurchaseOrderItem;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorizationItem;
import com.example.operion.module.procurement.entity.Realisasi;
import com.example.operion.module.procurement.entity.Supplier;
import com.example.operion.module.procurement.enums.PurchaseOrderStatus;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.repository.PurchaseOrderItemRepository;
import com.example.operion.module.procurement.repository.PurchaseOrderRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationItemRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationRepository;
import com.example.operion.module.procurement.repository.RealisasiRepository;
import com.example.operion.module.procurement.repository.SupplierRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-16: spending summary and budget. Fixtures: HQ → Store plus an unrelated
 * root. Purchases are logged through the API (so the ceiling logic decides
 * APPROVED vs PENDING_APPROVAL) with conversionFactor 6 (rule 2); the records
 * that only need a status (FAILED, SUPERSEDED, orders) are written directly.
 * Amounts are distinct and non-round so a mix-up shows (rule 6).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SpendingTest {

    static final String MONTH = "2026-10";
    static final LocalDateTime IN_MONTH = LocalDateTime.of(2026, 10, 14, 10, 30);

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private PurchaseRequestAuthorizationRepository praRepository;
    @Autowired private PurchaseRequestAuthorizationItemRepository praItemRepository;
    @Autowired private RealisasiRepository realisasiRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private PurchaseOrderItemRepository purchaseOrderItemRepository;

    private Tenant hq;
    private Tenant store;
    private Tenant other;
    private User sm;
    private User owner;
    private User hqPrincipal;
    private User otherOwner;
    private Part gas;
    private Part spring;
    private Supplier centre;
    private Supplier tokopedia;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        hq = tenantRepository.save(Tenant.builder().code("BE16_HQ_" + s).name("HQ " + s).active(true).build());
        store = tenantRepository.save(Tenant.builder().code("BE16_ST_" + s).name("Store " + s).active(true).parent(hq).build());
        other = tenantRepository.save(Tenant.builder().code("BE16_OT_" + s).name("Other " + s).active(true).build());

        sm = user("be16-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);
        owner = user("be16-own-" + s, Role.OPERATOR, Scope.OWNER, store);
        hqPrincipal = user("be16-hq-" + s, Role.ADMIN, Scope.PRINCIPAL, hq);
        otherOwner = user("be16-oth-" + s, Role.OPERATOR, Scope.OWNER, other);

        PartCategory category = categoryRepository.save(PartCategory.builder()
                .tenant(store).name("Gas").consumable(true).build());
        gas = partRepository.save(Part.builder().tenant(store).name("Green gas " + s).category(category).build());
        spring = partRepository.save(Part.builder().tenant(store).name("Spring " + s).category(category).build());

        centre = supplierRepository.save(Supplier.builder().tenant(hq).name("HQ").franchiseCentre(true).build());
        tokopedia = supplierRepository.save(Supplier.builder().tenant(hq).name("Tokopedia").build());
    }

    // ---- spent ----

    @Test
    void spent_countsApprovedPurchasesAndSentOrders_splitByWhoWeBoughtFrom() throws Exception {

        PurchaseRequestAuthorizationItem line = praLine(gas, 60, "5000000");

        // outside, approved: 2 pakets × 310,000 + shipping 18,000 = 638,000 landed
        buy(line, gas, 2, "310000", "18000", tokopedia, IN_MONTH).andExpect(jsonPath("$.data.status").value("APPROVED"));
        // from the centre, approved (urgent buy paid at HQ): 1 paket × 297,500
        buy(line, gas, 1, "297500", "0", centre, IN_MONTH).andExpect(jsonPath("$.data.status").value("APPROVED"));

        // in neither
        direct(RealisasiStatus.FAILED, "111111");
        direct(RealisasiStatus.SUPERSEDED, "222222");

        // orders to HQ: sent ones count, DRAFT / CANCELLED don't
        order(PurchaseOrderStatus.ORDERED, spring, 4, "41250");             // 165,000
        order(PurchaseOrderStatus.RECEIVED, gas, 6, "38500");               // 231,000
        order(PurchaseOrderStatus.DRAFT, gas, 100, "38500");
        order(PurchaseOrderStatus.CANCELLED, gas, 100, "38500");

        summary(owner, MONTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].tenantId").value(store.getId().toString()))
                .andExpect(jsonPath("$.data[0].spentOutside").value(638000))
                .andExpect(jsonPath("$.data[0].spentFromCentre").value(297500 + 165000 + 231000))
                .andExpect(jsonPath("$.data[0].spent").value(638000 + 297500 + 165000 + 231000))
                .andExpect(jsonPath("$.data[0].topParts[0].partId").value(gas.getId().toString()))
                .andExpect(jsonPath("$.data[0].topParts[0].amount").value(638000 + 297500 + 231000))
                .andExpect(jsonPath("$.data[0].topParts[1].partId").value(spring.getId().toString()))
                .andExpect(jsonPath("$.data[0].topParts[1].amount").value(165000));
    }

    @Test
    void spent_lastDayOfTheMonth_vsFirstDayOfTheNext() throws Exception {

        PurchaseRequestAuthorizationItem line = praLine(gas, 60, "5000000");

        buy(line, gas, 1, "123450", "0", tokopedia, LocalDateTime.of(2026, 9, 30, 23, 59, 59));
        buy(line, gas, 1, "67890", "0", tokopedia, LocalDateTime.of(2026, 10, 1, 0, 0));

        summary(owner, "2026-09").andExpect(jsonPath("$.data[0].spentOutside").value(123450));
        summary(owner, "2026-10").andExpect(jsonPath("$.data[0].spentOutside").value(67890));
    }

    // ---- committed ----

    @Test
    void committed_isTheLargerOfRemainingAndPending_neverTheSum() throws Exception {

        // limit Rp 1,000,000; 2 pakets × 310,000 approved → Rp 380,000 left
        PurchaseRequestAuthorizationItem line = praLine(gas, 60, "1000000");
        buy(line, gas, 2, "310000", "0", tokopedia, IN_MONTH).andExpect(jsonPath("$.data.status").value("APPROVED"));

        summary(owner, MONTH).andExpect(jsonPath("$.data[0].committed").value(380000));

        // Rp 600,000 over the limit, waiting for the Owner → committed 600,000, not 980,000
        String escalated = JsonPath.read(buy(line, gas, 2, "300000", "0", tokopedia, IN_MONTH)
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString(), "$.data.id");

        summary(owner, MONTH)
                .andExpect(jsonPath("$.data[0].committed").value(600000))
                .andExpect(jsonPath("$.data[0].spentOutside").value(620000));

        // rejected: back to Rp 380,000
        mvc.perform(as(owner, patch("/realisasi/" + escalated + "/reject")).content("{\"reason\":\"too much\"}"))
                .andExpect(status().isOk());
        summary(owner, MONTH).andExpect(jsonPath("$.data[0].committed").value(380000));
    }

    @Test
    void committed_afterApproval_movesToSpent_andRemainingClampsAtZero() throws Exception {

        PurchaseRequestAuthorizationItem line = praLine(gas, 60, "1000000");
        buy(line, gas, 2, "310000", "0", tokopedia, IN_MONTH);
        String escalated = JsonPath.read(buy(line, gas, 2, "300000", "0", tokopedia, IN_MONTH)
                .andReturn().getResponse().getContentAsString(), "$.data.id");

        mvc.perform(as(owner, patch("/realisasi/" + escalated + "/approve"))).andExpect(status().isOk());

        summary(owner, MONTH)
                .andExpect(jsonPath("$.data[0].spentOutside").value(1220000))
                .andExpect(jsonPath("$.data[0].committed").value(0));
    }

    @Test
    void committed_quantityEscalation_usesStockUnits() throws Exception {

        // 12 cans authorized, Rp 2,000,000; 3 pakets × 6 = 18 cans escalates though the value fits
        PurchaseRequestAuthorizationItem line = praLine(gas, 12, "2000000");
        buy(line, gas, 3, "95000", "0", tokopedia, IN_MONTH).andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

        summary(owner, MONTH)
                .andExpect(jsonPath("$.data[0].spent").value(0))
                .andExpect(jsonPath("$.data[0].committed").value(2000000));
    }

    @Test
    void committed_onlyLivePras() throws Exception {

        praLine(gas, 12, "456789");
        PurchaseRequestAuthorizationItem cancelled = praLine(gas, 12, "999999");
        cancelled.getPurchaseRequestAuthorization().setStatus(PurchaseRequestAuthorizationStatus.CANCELLED);
        praRepository.save(cancelled.getPurchaseRequestAuthorization());

        summary(owner, MONTH).andExpect(jsonPath("$.data[0].committed").value(456789));
    }

    // ---- who sees what ----

    @Test
    void scope_ownerSeesTheirStore_hqSeesItsTree_nobodyElse() throws Exception {

        summary(sm, MONTH).andExpect(status().isForbidden());

        summary(owner, MONTH)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].tenantId").value(store.getId().toString()));

        summary(hqPrincipal, MONTH)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].tenantId").value(hq.getId().toString()))
                .andExpect(jsonPath("$.data[1].tenantId").value(store.getId().toString()));

        mvc.perform(as(hqPrincipal, get("/procurement/spending").param("month", MONTH)
                .param("tenantId", store.getId().toString())))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].tenantName").value(store.getName()));

        mvc.perform(as(hqPrincipal, get("/procurement/spending").param("tenantId", other.getId().toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Store not found"));

        summary(otherOwner, MONTH)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].tenantId").value(other.getId().toString()));
    }

    // ---- budget ----

    @Test
    void budget_setByTheStoresOwnOwnerOnly_andWarnsWhenOver() throws Exception {

        mvc.perform(as(owner, patch("/tenants/" + store.getId() + "/purchase-budget")).content("{\"amount\":750000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyPurchaseBudget").value(750000));

        mvc.perform(as(hqPrincipal, patch("/tenants/" + store.getId() + "/purchase-budget")).content("{\"amount\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(sm, patch("/tenants/" + store.getId() + "/purchase-budget")).content("{\"amount\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(owner, patch("/tenants/" + store.getId() + "/purchase-budget")).content("{\"amount\":-5}"))
                .andExpect(status().isBadRequest());

        PurchaseRequestAuthorizationItem line = praLine(gas, 60, "1000000");
        buy(line, gas, 2, "310000", "0", tokopedia, IN_MONTH);   // spent 620,000 + committed 380,000 > 750,000

        summary(hqPrincipal, MONTH)
                .andExpect(jsonPath("$.data[1].budget").value(750000))
                .andExpect(jsonPath("$.data[1].overBudget").value(true))
                .andExpect(jsonPath("$.data[0].budget").isEmpty())
                .andExpect(jsonPath("$.data[0].overBudget").isEmpty());

        mvc.perform(as(owner, patch("/tenants/" + store.getId() + "/purchase-budget")).content("{\"amount\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyPurchaseBudget").isEmpty());
        summary(owner, MONTH).andExpect(jsonPath("$.data[0].overBudget").isEmpty());
    }

    // ---- helpers ----

    private ResultActions summary(User u, String month) throws Exception {

        return mvc.perform(as(u, get("/procurement/spending").param("month", month)));
    }

    /** A limit set by the store Owner: {@code cans} stock units, up to {@code maxValue}. */
    private PurchaseRequestAuthorizationItem praLine(Part part, int cans, String maxValue) {

        PurchaseRequestAuthorization pra = praRepository.save(PurchaseRequestAuthorization.builder()
                .tenant(store).approvedBy(owner).status(PurchaseRequestAuthorizationStatus.ACTIVE).build());

        return praItemRepository.save(PurchaseRequestAuthorizationItem.builder()
                .purchaseRequestAuthorization(pra).part(part).authorizedQty(cans)
                .maxValue(new BigDecimal(maxValue)).build());
    }

    /** The Store Manager logs {@code pakets} × factor 6 at {@code unitPrice} per paket. */
    private ResultActions buy(PurchaseRequestAuthorizationItem line, Part part, int pakets, String unitPrice,
            String shipping, Supplier supplier, LocalDateTime purchasedAt) throws Exception {

        BigDecimal subtotal = new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(pakets));

        String body = "{\"purchaseRequestAuthorizationId\":\"" + line.getPurchaseRequestAuthorization().getId() + "\","
                + "\"externalOrderRef\":\"BE16-" + UUID.randomUUID() + "\","
                + "\"supplierId\":\"" + supplier.getId() + "\","
                + "\"purchasedAt\":\"" + purchasedAt + "\","
                + "\"paymentMethod\":\"COMPANY_ACCOUNT\",\"subtotal\":" + subtotal.toPlainString()
                + ",\"shipping\":" + shipping + ","
                + "\"items\":[{\"purchaseRequestAuthorizationItemId\":\"" + line.getId() + "\",\"partId\":\"" + part.getId()
                + "\",\"purchaseUom\":\"paket\",\"conversionFactor\":6,\"purchasedQty\":" + pakets
                + ",\"actualUnitPrice\":" + unitPrice + "}]}";

        return mvc.perform(as(sm, post("/realisasi")).content(body)).andExpect(status().isOk());
    }

    private void direct(RealisasiStatus status, String total) {

        PurchaseRequestAuthorization pra = praRepository.save(PurchaseRequestAuthorization.builder()
                .tenant(store).approvedBy(owner).status(PurchaseRequestAuthorizationStatus.FULFILLED).build());

        realisasiRepository.save(Realisasi.builder()
                .tenant(store).purchaseRequestAuthorization(pra).createdBy(sm)
                .externalOrderRef("BE16-" + UUID.randomUUID())
                .status(status).purchasedAt(IN_MONTH)
                .subtotal(new BigDecimal(total)).totalCost(new BigDecimal(total))
                .build());
    }

    private void order(PurchaseOrderStatus status, Part part, int qty, String price) {

        BigDecimal total = new BigDecimal(price).multiply(BigDecimal.valueOf(qty));

        PurchaseOrder po = purchaseOrderRepository.save(PurchaseOrder.builder()
                .tenant(store).supplier(centre).createdBy(sm)
                .poNumber("BE16-PO-" + UUID.randomUUID())
                .orderDate(LocalDate.of(2026, 10, 9))
                .status(status).totalAmount(total)
                .build());

        purchaseOrderItemRepository.save(PurchaseOrderItem.builder()
                .purchaseOrder(po).part(part).quantity(qty).price(new BigDecimal(price)).build());
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
