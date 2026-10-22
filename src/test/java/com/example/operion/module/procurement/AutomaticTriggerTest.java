package com.example.operion.module.procurement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.procurement.entity.PurchaseOrder;
import com.example.operion.module.procurement.entity.PurchaseOrderStatusHistory;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorizationItem;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorizationStatusHistory;
import com.example.operion.module.procurement.entity.Supplier;
import com.example.operion.module.procurement.enums.PurchaseOrderStatus;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.procurement.repository.PurchaseOrderRepository;
import com.example.operion.module.procurement.repository.PurchaseOrderStatusHistoryRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationItemRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationStatusHistoryRepository;
import com.example.operion.module.procurement.repository.SupplierRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-22: automatic status changes name the person and action that caused
 * them. Fixtures: HQ → Store; the store's gas is HQ's catalog item (so it
 * can be ordered from HQ). Purchases use conversionFactor 6 (rule 2): 2
 * pakets fill a 12-can limit, so the PRA flips to FULFILLED only if the
 * factor is applied.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AutomaticTriggerTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private PurchaseRequestAuthorizationRepository praRepository;
    @Autowired private PurchaseRequestAuthorizationItemRepository praItemRepository;
    @Autowired private PurchaseRequestAuthorizationStatusHistoryRepository praHistoryRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private PurchaseOrderStatusHistoryRepository poHistoryRepository;

    private Tenant store;
    private User sm;
    private User owner;
    private Part gas;
    private Supplier tokopedia;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenantRepository.save(Tenant.builder().code("BE22_HQ_" + s).name("HQ").active(true).build());
        store = tenantRepository.save(Tenant.builder().code("BE22_ST_" + s).name("Store").active(true).parent(hq).build());

        sm = user("be22-sm-" + s, Scope.MANAGER);
        owner = user("be22-own-" + s, Scope.OWNER);

        PartCategory hqGasCategory = categoryRepository.save(PartCategory.builder()
                .tenant(hq).name("Gas").consumable(true).build());
        Part hqGas = partRepository.save(Part.builder().tenant(hq).name("Green gas " + s)
                .category(hqGasCategory).soldByHq(true).build());

        PartCategory category = categoryRepository.save(PartCategory.builder()
                .tenant(store).name("Gas").consumable(true).build());
        gas = partRepository.save(Part.builder().tenant(store).name("Green gas " + s)
                .category(category).catalogPart(hqGas).build());

        supplierRepository.save(Supplier.builder().tenant(hq).name("HQ").franchiseCentre(true).build());
        tokopedia = supplierRepository.save(Supplier.builder().tenant(hq).name("Tokopedia").build());
    }

    @Test
    void approvingAPurchaseThatFulfilsItsLimit_isAutomatic_triggeredByTheApprovingOwner() throws Exception {

        // 12 cans up to Rp 500,000; 2 pakets × 6 cans at 300,000 = 600,000 → waits for the Owner
        String praId = praLine(12, "500000");
        String ref = "BE22-" + UUID.randomUUID().toString().substring(0, 8);

        String realisasiId = JsonPath.read(mvc.perform(as(sm, post("/realisasi")).content(buyBody(praId, ref, 2, 300000)))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString(), "$.data.id");

        mvc.perform(as(owner, patch("/realisasi/" + realisasiId + "/approve"))).andExpect(status().isOk());

        String fulfilled = "$.data[?(@.newStatus=='FULFILLED')]";
        mvc.perform(as(owner, get("/purchase-authorizations/" + praId + "/history")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(fulfilled + ".actorKind").value("AUTOMATIC"))
                .andExpect(jsonPath(fulfilled + ".changedByName").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath(fulfilled + ".triggeredByName").value(owner.getFullName()))
                .andExpect(jsonPath(fulfilled + ".triggerReference").value(ref + " approved"));

        // the Realisasi's own approval row is the Owner's, by hand
        mvc.perform(as(owner, get("/realisasi/" + realisasiId + "/history")))
                .andExpect(jsonPath("$.data[?(@.newStatus=='APPROVED')].actorKind").value("USER"));

        String entry = "$.data[?(@.entityId=='" + praId + "' && @.newStatus=='FULFILLED')]";
        mvc.perform(as(owner, get("/procurement/activity")))
                .andExpect(jsonPath(entry + ".actorKind").value("AUTOMATIC"))
                .andExpect(jsonPath(entry + ".triggeredByName").value(owner.getFullName()))
                .andExpect(jsonPath(entry + ".triggerReference").value(ref + " approved"));
    }

    @Test
    void loggingAPurchaseThatFulfilsItsLimit_isTriggeredByTheBuyer() throws Exception {

        String praId = praLine(12, "900000");
        String ref = "BE22-" + UUID.randomUUID().toString().substring(0, 8);

        mvc.perform(as(sm, post("/realisasi")).content(buyBody(praId, ref, 2, 300000)))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        String fulfilled = "$.data[?(@.newStatus=='FULFILLED')]";
        mvc.perform(as(owner, get("/purchase-authorizations/" + praId + "/history")))
                .andExpect(jsonPath(fulfilled + ".actorKind").value("AUTOMATIC"))
                .andExpect(jsonPath(fulfilled + ".triggeredByName").value(sm.getFullName()))
                .andExpect(jsonPath(fulfilled + ".triggerReference").value(ref + " logged"))
                .andExpect(jsonPath("$.data[?(@.newStatus=='ACTIVE')].actorKind").value("USER"));
    }

    @Test
    void aGoodsReceiptThatCompletesAnOrder_isAutomatic_triggeredByTheReceiver_namedByPoNumber() throws Exception {

        String body = mvc.perform(as(sm, post("/purchase-orders"))
                .content("{\"items\":[{\"partId\":\"" + gas.getId() + "\",\"quantity\":6,\"price\":38500}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String poId = JsonPath.read(body, "$.data.id");
        String poNumber = JsonPath.read(body, "$.data.poNumber");

        mvc.perform(as(owner, patch("/purchase-orders/" + poId + "/send"))).andExpect(status().isOk());

        mvc.perform(as(sm, post("/goods-receipts")).content("{\"purchaseOrderId\":\"" + poId
                + "\",\"items\":[{\"partId\":\"" + gas.getId() + "\",\"quantity\":6}]}"))
                .andExpect(status().isOk());

        String received = "$.data[?(@.newStatus=='RECEIVED')]";
        mvc.perform(as(owner, get("/purchase-orders/" + poId + "/history")))
                .andExpect(jsonPath(received + ".actorKind").value("AUTOMATIC"))
                .andExpect(jsonPath(received + ".triggeredByName").value(sm.getFullName()))
                .andExpect(jsonPath(received + ".triggerReference").value(poNumber + " goods received"))
                .andExpect(jsonPath("$.data[?(@.newStatus=='ORDERED')].actorKind").value("USER"))
                .andExpect(jsonPath("$.data[?(@.newStatus=='ORDERED')].changedByName").value(owner.getFullName()));
    }

    @Test
    void aLegacyRowWithNoActor_isNotRecorded() throws Exception {

        PurchaseOrder po = purchaseOrderRepository.save(PurchaseOrder.builder()
                .tenant(store).poNumber("BE22-PO-" + UUID.randomUUID()).status(PurchaseOrderStatus.ORDERED).build());
        poHistoryRepository.save(PurchaseOrderStatusHistory.builder()
                .purchaseOrder(po).newStatus(PurchaseOrderStatus.ORDERED).build());

        mvc.perform(as(owner, get("/purchase-orders/" + po.getId() + "/history")))
                .andExpect(jsonPath("$.data[0].actorKind").value("NOT_RECORDED"));

        mvc.perform(as(owner, get("/procurement/activity").param("type", "PURCHASE_ORDER")))
                .andExpect(jsonPath("$.data[?(@.entityId=='" + po.getId() + "')].actorKind").value("NOT_RECORDED"));
    }

    // ---- helpers ----

    private String praLine(int cans, String maxValue) {

        PurchaseRequestAuthorization pra = praRepository.save(PurchaseRequestAuthorization.builder()
                .tenant(store).approvedBy(owner).status(PurchaseRequestAuthorizationStatus.ACTIVE).build());
        praItemRepository.save(PurchaseRequestAuthorizationItem.builder()
                .purchaseRequestAuthorization(pra).part(gas).authorizedQty(cans)
                .maxValue(new BigDecimal(maxValue)).build());

        // the creation row a real PRA gets, by its Owner
        praHistoryRepository.save(PurchaseRequestAuthorizationStatusHistory.builder()
                .purchaseRequestAuthorization(pra).newStatus(PurchaseRequestAuthorizationStatus.ACTIVE)
                .changedBy(owner).build());

        return pra.getId().toString();
    }

    private String buyBody(String praId, String ref, int pakets, long unitPrice) {

        String lineId = praItemRepository.findByPurchaseRequestAuthorizationId(UUID.fromString(praId)).get(0).getId().toString();

        return "{\"purchaseRequestAuthorizationId\":\"" + praId + "\","
                + "\"externalOrderRef\":\"" + ref + "\",\"supplierId\":\"" + tokopedia.getId() + "\","
                + "\"paymentMethod\":\"COMPANY_ACCOUNT\",\"subtotal\":" + (unitPrice * pakets) + ","
                + "\"items\":[{\"purchaseRequestAuthorizationItemId\":\"" + lineId + "\",\"partId\":\"" + gas.getId()
                + "\",\"purchaseUom\":\"paket\",\"conversionFactor\":6,\"purchasedQty\":" + pakets
                + ",\"actualUnitPrice\":" + unitPrice + "}]}";
    }

    private User user(String prefix, Scope scope) {

        User u = new User();
        u.setEmail(prefix + "@test.com");
        u.setPasswordHash("not-a-real-hash");
        u.setFullName(prefix);
        u.setRole(Role.OPERATOR);
        u.setScope(scope);
        u.setTenant(store);
        return userRepository.save(u);
    }

    private MockHttpServletRequestBuilder as(User u, MockHttpServletRequestBuilder req) {

        String token = jwtUtil.generateToken(
                u.getId().toString(), u.getRole().name(), u.getTenant().getId().toString(),
                u.getScope() != null ? u.getScope().name() : null);

        return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }
}
