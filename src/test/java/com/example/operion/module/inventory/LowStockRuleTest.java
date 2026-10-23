package com.example.operion.module.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.example.operion.module.maintenance.entity.MaintenancePolicy;
import com.example.operion.module.maintenance.repository.MaintenancePolicyRepository;
import com.example.operion.module.notification.enums.NotificationType;
import com.example.operion.module.notification.repository.NotificationRepository;
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

/**
 * BE-27: one definition of "low". The store's target is 20 days of cover (a
 * non-default policy value, rule 6). With usage data an item is low when it
 * runs out before 20 days; without, when it's at or below its minimum.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LowStockRuleTest {

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
    @Autowired private NotificationRepository notificationRepository;

    private Tenant store;
    private User sm;
    private User owner;
    private User admin;
    private User operator;
    private User hqPrincipal;
    private PartCategory gasCategory;
    private PartCategory hopUp;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenantRepository.save(Tenant.builder().code("BE27_HQ_" + s).name("HQ").active(true).build());
        store = tenantRepository.save(Tenant.builder().code("BE27_ST_" + s).name("Store").active(true).parent(hq).build());

        sm = user("be27-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);
        owner = user("be27-own-" + s, Role.OPERATOR, Scope.OWNER, store);
        admin = user("be27-adm-" + s, Role.ADMIN, null, store);
        operator = user("be27-op-" + s, Role.OPERATOR, null, store);
        hqPrincipal = user("be27-hq-" + s, Role.ADMIN, Scope.PRINCIPAL, hq);

        policyRepository.save(MaintenancePolicy.builder().tenant(store).daysOfCoverTarget(20).build());

        gasCategory = categoryRepository.save(PartCategory.builder().tenant(store).name("Gas").consumable(true).build());
        hopUp = categoryRepository.save(PartCategory.builder().tenant(store).name("Hop-up").consumable(false).build());
    }

    @Test
    void lowBecause_runsOutSoon_evenFarAboveMinimum_orBelowMinimumWithoutUsageData() throws Exception {

        // 10 cans at 5 a day = 2 days of cover, against a 20-day target; minimum is only 2
        Part soon = partRepository.save(Part.builder().tenant(store).name("Gas, fast").category(gasCategory)
                .currentStock(10).minimumStock(2).manualDailyUsage(new BigDecimal("5")).build());
        // no usage data: the minimum decides
        Part below = partRepository.save(Part.builder().tenant(store).name("Spring, below").category(hopUp)
                .currentStock(1).minimumStock(3).build());
        Part fine = partRepository.save(Part.builder().tenant(store).name("Spring, fine").category(hopUp)
                .currentStock(9).minimumStock(3).build());
        // usage data and 40 days of cover: not low even at its minimum
        Part covered = partRepository.save(Part.builder().tenant(store).name("Gas, slow").category(gasCategory)
                .currentStock(4).minimumStock(4).manualDailyUsage(new BigDecimal("0.1")).build());

        mvc.perform(as(sm, get("/inventory/low-stock")))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.partId=='" + soon.getId() + "')].lowBecause").value("RUNS_OUT_SOON"))
                .andExpect(jsonPath("$.data[?(@.partId=='" + soon.getId() + "')].targetDays").value(20))
                .andExpect(jsonPath("$.data[?(@.partId=='" + soon.getId() + "')].daysOfCover").value(2.0))
                .andExpect(jsonPath("$.data[?(@.partId=='" + below.getId() + "')].lowBecause").value("BELOW_MINIMUM"));

        mvc.perform(as(sm, get("/inventory/stock-summary")))
                .andExpect(jsonPath("$.data[?(@.partId=='" + soon.getId() + "')].status").value("LOW_STOCK"))
                .andExpect(jsonPath("$.data[?(@.partId=='" + fine.getId() + "')].status").value("NORMAL"))
                .andExpect(jsonPath("$.data[?(@.partId=='" + covered.getId() + "')].status").value("NORMAL"))
                .andExpect(jsonPath("$.data[?(@.partId=='" + covered.getId() + "')].lowBecause").value(
                        org.hamcrest.Matchers.contains((Object) null)));

        mvc.perform(as(sm, get("/inventory/dashboard")))
                .andExpect(jsonPath("$.data.lowStock").value(2))
                .andExpect(jsonPath("$.data.outOfStock").value(0));
    }

    @Test
    void lowStockAlert_firesOnceWhenItBecomesLow_toTheStoresManagerPlus() throws Exception {

        // 0.5 cans a day, 20-day target: low below 10 cans; minimum is only 2
        Part gas = partRepository.save(Part.builder().tenant(store).name("Green gas").category(gasCategory)
                .currentStock(0).minimumStock(2).manualDailyUsage(new BigDecimal("0.5")).build());

        // 2 pakets × 6 cans received (rule 2): 12 cans = 24 days
        RealisasiItem line = approvedPurchase(gas, 2);
        mvc.perform(as(owner, post("/goods-receipts")).content("{\"realisasiId\":\"" + line.getRealisasi().getId()
                + "\",\"items\":[{\"partId\":\"" + gas.getId() + "\",\"realisasiItemId\":\"" + line.getId()
                + "\",\"quantity\":12}]}"))
                .andExpect(status().isOk());

        take(gas);   // 11 = 22 days
        take(gas);   // 10 = 20 days: not below the target yet
        assertEquals(0, lowAlerts(sm));

        take(gas);   // 9 = 18 days: becomes low
        assertEquals(1, lowAlerts(sm));
        assertEquals(1, lowAlerts(owner));
        assertEquals(1, lowAlerts(admin));
        assertEquals(0, lowAlerts(operator), "no scope");
        assertEquals(0, lowAlerts(hqPrincipal), "store only");

        take(gas);   // 8: still low, no second alert
        assertEquals(1, lowAlerts(sm));

        mvc.perform(as(sm, get("/inventory/low-stock")))
                .andExpect(jsonPath("$.data[?(@.partId=='" + gas.getId() + "')].lowBecause").value("RUNS_OUT_SOON"));
    }

    // ---- helpers ----

    private void take(Part part) throws Exception {

        String barcode = "BE27-" + UUID.randomUUID();
        mvc.perform(as(sm, post("/part-instances/scan-in"))
                .content("{\"partId\":\"" + part.getId() + "\",\"barcode\":\"" + barcode + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(as(sm, post("/part-instances/take"))
                .content("{\"barcode\":\"" + barcode + "\",\"takenBy\":\"" + sm.getId() + "\"}"))
                .andExpect(status().isOk());
    }

    private long lowAlerts(User u) {

        return notificationRepository.findByRecipientUserId(u.getId()).stream()
                .filter(n -> n.getType() == NotificationType.LOW_STOCK)
                .count();
    }

    private RealisasiItem approvedPurchase(Part part, int pakets) {

        PurchaseRequestAuthorization pra = praRepository.save(PurchaseRequestAuthorization.builder()
                .tenant(store).approvedBy(owner).status(PurchaseRequestAuthorizationStatus.ACTIVE).build());

        Realisasi realisasi = realisasiRepository.save(Realisasi.builder()
                .tenant(store).purchaseRequestAuthorization(pra).createdBy(sm)
                .externalOrderRef("BE27-" + UUID.randomUUID())
                .status(RealisasiStatus.APPROVED)
                .attachments("https://shop.example/receipt/27")
                .subtotal(new BigDecimal("231000")).totalCost(new BigDecimal("231000"))
                .build());

        return realisasiItemRepository.save(RealisasiItem.builder()
                .realisasi(realisasi).part(part).purchaseUom("paket")
                .conversionFactor(new BigDecimal("6")).purchasedQty(pakets)
                .actualUnitPrice(new BigDecimal("115500")).allocatedLandedCost(new BigDecimal("231000"))
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
