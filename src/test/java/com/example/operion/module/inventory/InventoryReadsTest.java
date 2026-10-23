package com.example.operion.module.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.hamcrest.Matchers;
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
import com.example.operion.module.maintenance.entity.MaintenancePolicy;
import com.example.operion.module.maintenance.repository.MaintenancePolicyRepository;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;
import com.example.operion.module.procurement.entity.Realisasi;
import com.example.operion.module.procurement.entity.RealisasiItem;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationRepository;
import com.example.operion.module.procurement.repository.RealisasiItemRepository;
import com.example.operion.module.procurement.repository.RealisasiRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-26: inventory reads say which store each row is from, item history
 * names who moved the stock, "used today" counts consumable takes, and burn
 * rate uses the item's own store's policy. Fixtures: HQ → Store A, Store B,
 * plus an unrelated root. Store A's policy has a non-default target (20 days,
 * rule 6) and HQ's a different one (7), so "whose policy" is visible.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InventoryReadsTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private MaintenancePolicyRepository policyRepository;
    @Autowired private PurchaseRequestAuthorizationRepository praRepository;
    @Autowired private RealisasiRepository realisasiRepository;
    @Autowired private RealisasiItemRepository realisasiItemRepository;

    private Tenant storeA;
    private Tenant storeB;
    private Tenant other;
    private User hqPrincipal;
    private User smA;
    private User sm2A;
    private User ownerA;
    private User otherUser;
    private Part hqGas;
    private Part gasA;
    private Part gasB;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenantRepository.save(Tenant.builder().code("BE26_HQ_" + s).name("HQ " + s).active(true).build());
        storeA = tenantRepository.save(Tenant.builder().code("BE26_A_" + s).name("Store A " + s).active(true).parent(hq).build());
        storeB = tenantRepository.save(Tenant.builder().code("BE26_B_" + s).name("Store B " + s).active(true).parent(hq).build());
        other = tenantRepository.save(Tenant.builder().code("BE26_OT_" + s).name("Other").active(true).build());

        hqPrincipal = user("be26-hq-" + s, Role.ADMIN, Scope.PRINCIPAL, hq);
        smA = user("be26-sm-" + s, Role.OPERATOR, Scope.MANAGER, storeA);
        sm2A = user("be26-sm2-" + s, Role.OPERATOR, Scope.MANAGER, storeA);
        ownerA = user("be26-own-" + s, Role.OPERATOR, Scope.OWNER, storeA);
        otherUser = user("be26-oth-" + s, Role.OPERATOR, Scope.OWNER, other);

        hqGas = partRepository.save(Part.builder().tenant(hq).name("HQ gas " + s)
                .category(category(hq)).soldByHq(true).build());
        gasA = partRepository.save(Part.builder().tenant(storeA).name("Gas A " + s).category(category(storeA))
                .catalogPart(hqGas).currentStock(30).minimumStock(5)
                .manualDailyUsage(new BigDecimal("2.5")).build());
        gasB = partRepository.save(Part.builder().tenant(storeB).name("Gas B " + s).category(category(storeB))
                .currentStock(2).minimumStock(5).build());

        policyRepository.save(MaintenancePolicy.builder().tenant(storeA).daysOfCoverTarget(20).build());
        policyRepository.save(MaintenancePolicy.builder().tenant(hq).daysOfCoverTarget(7).build());
    }

    // ---- item 1: the store on every row, and ?tenantId= ----

    @Test
    void hqSeesStoresToldApart_andCanNarrowToOne() throws Exception {

        String a = "$.data[?(@.partId=='" + gasA.getId() + "')]";
        String b = "$.data[?(@.partId=='" + gasB.getId() + "')]";

        mvc.perform(as(hqPrincipal, get("/inventory/stock-summary")))
                .andExpect(jsonPath(a + ".tenantId").value(storeA.getId().toString()))
                .andExpect(jsonPath(a + ".tenantName").value(storeA.getName()))
                .andExpect(jsonPath(a + ".catalogPartId").value(hqGas.getId().toString()))
                .andExpect(jsonPath(b + ".tenantId").value(storeB.getId().toString()));

        mvc.perform(as(hqPrincipal, get("/inventory/stock-summary").param("tenantId", storeA.getId().toString())))
                .andExpect(jsonPath("$.data[*].tenantId").value(Matchers.everyItem(Matchers.is(storeA.getId().toString()))))
                .andExpect(jsonPath("$.data.length()").value(1));

        mvc.perform(as(hqPrincipal, get("/inventory/low-stock").param("tenantId", storeB.getId().toString())))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].partId").value(gasB.getId().toString()));

        mvc.perform(as(hqPrincipal, get("/parts").param("tenantId", storeB.getId().toString())))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].tenantName").value(storeB.getName()));

        mvc.perform(as(hqPrincipal, get("/burn-rate").param("tenantId", storeA.getId().toString())))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].tenantId").value(storeA.getId().toString()))
                .andExpect(jsonPath("$.data[0].catalogPartId").value(hqGas.getId().toString()));

        for (String path : new String[] { "/inventory/stock-summary", "/inventory/low-stock", "/parts", "/burn-rate" }) {
            mvc.perform(as(hqPrincipal, get(path).param("tenantId", other.getId().toString())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Store not found"));
        }
    }

    // ---- item 2: item history with the actor ----

    @Test
    void itemHistory_namesWhoMovedTheStock_theApproverForAnApproval() throws Exception {

        String adjustment = JsonPath.read(mvc.perform(as(smA, post("/stock-adjustments"))
                .content("{\"partId\":\"" + gasA.getId() + "\",\"quantity\":13,\"reason\":\"Found a box\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");
        mvc.perform(as(ownerA, patch("/stock-adjustments/" + adjustment + "/approve"))).andExpect(status().isOk());

        mvc.perform(as(smA, get("/parts/" + gasA.getId() + "/movements")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].movementType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.data[0].quantity").value(13))
                .andExpect(jsonPath("$.data[0].beforeStock").value(30))
                .andExpect(jsonPath("$.data[0].afterStock").value(43))
                .andExpect(jsonPath("$.data[0].referenceType").value("STOCK_ADJUSTMENT"))
                .andExpect(jsonPath("$.data[0].referenceId").value(adjustment))
                .andExpect(jsonPath("$.data[0].actorId").value(ownerA.getId().toString()))
                .andExpect(jsonPath("$.data[0].actorName").value(ownerA.getFullName()));

        // HQ reads it too; another root doesn't
        mvc.perform(as(hqPrincipal, get("/parts/" + gasA.getId() + "/movements").param("limit", "1")))
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(as(otherUser, get("/parts/" + gasA.getId() + "/movements")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Part not found"));
    }

    // ---- item 3: "used today" counts consumable takes ----

    @Test
    void usedToday_countsConsumableTakes_afterAReceiptOfTwoPaketsOfSix() throws Exception {

        // 2 pakets × 6 cans received (rule 2), then 3 cans scanned in and taken
        RealisasiItem line = approvedPurchase(2);
        mvc.perform(as(sm2A, post("/goods-receipts")).content("{\"realisasiId\":\"" + line.getRealisasi().getId()
                + "\",\"items\":[{\"partId\":\"" + gasA.getId() + "\",\"realisasiItemId\":\"" + line.getId()
                + "\",\"quantity\":12}]}"))
                .andExpect(status().isOk());

        for (int i = 0; i < 3; i++) {
            String barcode = "BE26-" + UUID.randomUUID();
            mvc.perform(as(smA, post("/part-instances/scan-in"))
                    .content("{\"partId\":\"" + gasA.getId() + "\",\"barcode\":\"" + barcode + "\"}"))
                    .andExpect(status().isOk());
            mvc.perform(as(smA, post("/part-instances/take"))
                    .content("{\"barcode\":\"" + barcode + "\",\"takenBy\":\"" + smA.getId() + "\"}"))
                    .andExpect(status().isOk());
        }

        mvc.perform(as(smA, get("/inventory/dashboard")))
                .andExpect(jsonPath("$.data.stockReceivedToday").value(12))
                .andExpect(jsonPath("$.data.stockUsedToday").value(3))
                .andExpect(jsonPath("$.data.totalStock").value(30 + 12 - 3));

        // each take names the person who took it
        mvc.perform(as(smA, get("/parts/" + gasA.getId() + "/movements")))
                .andExpect(jsonPath("$.data[0].movementType").value("CONSUMABLE_TAKE"))
                .andExpect(jsonPath("$.data[0].actorName").value(smA.getFullName()));
    }

    // ---- item 4: burn rate uses the item's own store's policy ----

    @Test
    void burnRate_usesTheStoresPolicy_notTheCallers() throws Exception {

        // gas A: manual 2.5 a day; Store A's target is 20 days, HQ's is 7
        String a = "$.data[?(@.partId=='" + gasA.getId() + "')]";

        mvc.perform(as(hqPrincipal, get("/burn-rate")))
                .andExpect(jsonPath(a + ".burnRateSource").value("MANUAL_RATE"))
                .andExpect(jsonPath(a + ".targetDays").value(20));

        mvc.perform(as(smA, get("/burn-rate/" + gasA.getId())))
                .andExpect(jsonPath("$.data.targetDays").value(20));
    }

    // ---- helpers ----

    /** An APPROVED purchase of {@code pakets} × 6 cans of gas A, logged by Store A's Manager. */
    private RealisasiItem approvedPurchase(int pakets) {

        PurchaseRequestAuthorization pra = praRepository.save(PurchaseRequestAuthorization.builder()
                .tenant(storeA).approvedBy(ownerA).status(PurchaseRequestAuthorizationStatus.ACTIVE).build());

        Realisasi realisasi = realisasiRepository.save(Realisasi.builder()
                .tenant(storeA).purchaseRequestAuthorization(pra).createdBy(smA)
                .externalOrderRef("BE26-" + UUID.randomUUID())
                .status(RealisasiStatus.APPROVED)
                .attachments("https://shop.example/receipt/26")
                .subtotal(new BigDecimal("231000")).totalCost(new BigDecimal("231000"))
                .build());

        return realisasiItemRepository.save(RealisasiItem.builder()
                .realisasi(realisasi).part(gasA).purchaseUom("paket")
                .conversionFactor(new BigDecimal("6")).purchasedQty(pakets)
                .actualUnitPrice(new BigDecimal("115500")).allocatedLandedCost(new BigDecimal("231000"))
                .build());
    }

    private PartCategory category(Tenant tenant) {

        return categoryRepository.save(PartCategory.builder().tenant(tenant).name("Gas").consumable(true).build());
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
