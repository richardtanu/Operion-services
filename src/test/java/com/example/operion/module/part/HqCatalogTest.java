package com.example.operion.module.part;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.entity.PartType;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.part.repository.PartTypeRepository;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorizationItem;
import com.example.operion.module.procurement.entity.Supplier;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationItemRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationRepository;
import com.example.operion.module.procurement.repository.SupplierRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-21: the HQ catalog and the two ways a store buys. Fixtures: HQ → Store,
 * plus an unrelated root with its own catalog item. HQ sells green gas
 * (mandatory) and a hop-up spring (not mandatory); an HQ-internal tool isn't
 * sold. Purchases use conversionFactor 6 (rule 2): 2 pakets = 12 cans.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HqCatalogTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartTypeRepository partTypeRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private PurchaseRequestAuthorizationRepository praRepository;
    @Autowired private PurchaseRequestAuthorizationItemRepository praItemRepository;

    private Tenant hq;
    private Tenant store;
    private User hqAdmin;       // HQ, ADMIN, no scope
    private User hqPrincipal;   // HQ, OPERATOR + PRINCIPAL
    private User hqManager;     // HQ, OPERATOR + MANAGER: at HQ but neither
    private User storeAdmin;    // the store's own ADMIN
    private User storeOwner;
    private User sm;            // Store Manager
    private User otherUser;     // another root
    private Part hqGas;         // sold, mandatory
    private Part hqSpring;      // sold, not mandatory
    private Part hqTool;        // HQ's own, not sold
    private Part storeSpare;    // a store-only item
    private PartCategory storeGasCategory;
    private Supplier franchiseCentre;
    private Supplier tokopedia;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        hq = tenantRepository.save(Tenant.builder().code("BE21_HQ_" + s).name("HQ").active(true).build());
        store = tenantRepository.save(Tenant.builder().code("BE21_ST_" + s).name("Store").active(true).parent(hq).build());
        Tenant other = tenantRepository.save(Tenant.builder().code("BE21_OT_" + s).name("Other").active(true).build());

        hqAdmin = user("be21-hqadm-" + s, Role.ADMIN, null, hq);
        hqPrincipal = user("be21-hqpri-" + s, Role.OPERATOR, Scope.PRINCIPAL, hq);
        hqManager = user("be21-hqman-" + s, Role.OPERATOR, Scope.MANAGER, hq);
        storeAdmin = user("be21-stadm-" + s, Role.ADMIN, null, store);
        storeOwner = user("be21-own-" + s, Role.OPERATOR, Scope.OWNER, store);
        sm = user("be21-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);
        otherUser = user("be21-other-" + s, Role.OPERATOR, Scope.MANAGER, other);

        PartCategory hqGasCategory = category(hq, "Gas", true);
        PartType hqGreenGas = partTypeRepository.save(PartType.builder()
                .tenant(hq).category(hqGasCategory).name("Green gas").build());
        PartCategory hqParts = category(hq, "Hop-up", false);

        hqGas = partRepository.save(Part.builder().tenant(hq).name("Green gas 1000ml " + s).brand("Puff Dino")
                .category(hqGasCategory).partType(hqGreenGas).reorderQuantity(24)
                .soldByHq(true).hqPrice(new java.math.BigDecimal("38500")).mandatoryFromHq(true).build());
        hqSpring = partRepository.save(Part.builder().tenant(hq).name("Hop-up spring " + s)
                .category(hqParts).soldByHq(true).hqPrice(new java.math.BigDecimal("38500")).build());
        hqTool = partRepository.save(Part.builder().tenant(hq).name("Chrono tool " + s).category(hqParts).build());

        Part othersItem = partRepository.save(Part.builder().tenant(other).name("Other root BB " + s)
                .category(category(other, "BB", true)).soldByHq(true).hqPrice(new java.math.BigDecimal("38500")).build());
        assertNotEquals(hqGas.getId(), othersItem.getId());

        // a store-level "Gas" left over from before BE-29; a first order no longer maps to it
        storeGasCategory = category(store, "Gas", true);
        storeSpare = partRepository.save(Part.builder().tenant(store).name("Store-only spare " + s)
                .category(category(store, "Hop-up", false)).build());

        franchiseCentre = supplierRepository.save(Supplier.builder().tenant(hq).name("HQ").franchiseCentre(true).build());
        tokopedia = supplierRepository.save(Supplier.builder().tenant(hq).name("Tokopedia").build());
    }

    // ---- item 2: HQ's flags ----

    @Test
    void flags_onlyHqAdminsOrPrincipal() throws Exception {

        for (User refused : List.of(sm, storeOwner, storeAdmin, hqManager)) {
            mvc.perform(as(refused, patch("/parts/" + hqTool.getId() + "/hq-flags")).content("{\"soldByHq\":true}"))
                    .andExpect(status().isForbidden());
        }
        assertTrue(!partRepository.findById(hqTool.getId()).orElseThrow().isSoldByHq());

        mvc.perform(as(hqAdmin, patch("/parts/" + hqTool.getId() + "/hq-flags"))
                .content("{\"soldByHq\":true,\"mandatoryFromHq\":true,\"price\":41250}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.soldByHq").value(true))
                .andExpect(jsonPath("$.data.mandatoryFromHq").value(true));

        mvc.perform(as(hqPrincipal, patch("/parts/" + hqTool.getId() + "/hq-flags")).content("{\"mandatoryFromHq\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.soldByHq").value(true))
                .andExpect(jsonPath("$.data.mandatoryFromHq").value(false));
    }

    @Test
    void flags_mandatoryNeedsSold_andLiveOnHqRowsOnly() throws Exception {

        mvc.perform(as(hqAdmin, patch("/parts/" + hqGas.getId() + "/hq-flags")).content("{\"soldByHq\":false}"))
                .andExpect(status().isBadRequest());

        mvc.perform(as(hqAdmin, patch("/parts/" + storeSpare.getId() + "/hq-flags")).content("{\"soldByHq\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Part not found"));
    }

    // ---- item 3: catalog read ----

    @Test
    void catalog_whatHqSells_forAnyoneInTheTree() throws Exception {

        mvc.perform(as(sm, get("/catalog")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(hqGas.getId().toString()))
                .andExpect(jsonPath("$.data[0].mandatoryFromHq").value(true))
                .andExpect(jsonPath("$.data[0].consumable").value(true))
                .andExpect(jsonPath("$.data[0].storePartId").isEmpty())
                .andExpect(jsonPath("$.data[1].id").value(hqSpring.getId().toString()))
                .andExpect(jsonPath("$.data[1].mandatoryFromHq").value(false));

        // another root sees its own HQ's catalog, never this one
        mvc.perform(as(otherUser, get("/catalog")))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value(org.hamcrest.Matchers.startsWith("Other root BB")));
    }

    // ---- item 4: buy from HQ ----

    @Test
    void orderFromHq_firstOrderCreatesTheStoresLinkedPart() throws Exception {

        String body = mvc.perform(as(sm, post("/purchase-orders")).content(poBody(hqGas, 24)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplierId").value(franchiseCentre.getId().toString()))
                .andReturn().getResponse().getContentAsString();

        UUID storeGasId = UUID.fromString(JsonPath.read(body, "$.data.items[0].partId"));
        assertNotEquals(hqGas.getId(), storeGasId, "the line is on the store's own part");

        Part storeGas = partRepository.findById(storeGasId).orElseThrow();
        assertEquals(store.getId(), storeGas.getTenant().getId());
        assertEquals(hqGas.getId(), storeGas.getCatalogPart().getId());
        assertEquals(hqGas.getName(), storeGas.getName());
        // BE-29 (was: mapped to the store's same-named category, type created for the store)
        assertEquals(hqGas.getCategory().getId(), storeGas.getCategory().getId(), "HQ's own category");
        assertEquals(hqGas.getPartType().getId(), storeGas.getPartType().getId(), "HQ's own type");
        assertEquals("Green gas", storeGas.getPartType().getName());
        assertEquals(0, storeGas.getCurrentStock());

        // the second order, by catalog id or by the store's id, reuses it
        mvc.perform(as(sm, post("/purchase-orders")).content(poBody(hqGas, 6)))
                .andExpect(jsonPath("$.data.items[0].partId").value(storeGasId.toString()));
        mvc.perform(as(sm, post("/purchase-orders")).content(poBody(storeGas, 6)))
                .andExpect(jsonPath("$.data.items[0].partId").value(storeGasId.toString()));
        assertEquals(1, partRepository.findByTenantId(store.getId()).stream()
                .filter(p -> p.getCatalogPart() != null && p.getCatalogPart().getId().equals(hqGas.getId())).count());

        mvc.perform(as(sm, get("/catalog")))
                .andExpect(jsonPath("$.data[0].storePartId").value(storeGasId.toString()))
                .andExpect(jsonPath("$.data[0].storeCurrentStock").value(0));

        // the store's part says it's mandatory, read from HQ's row
        mvc.perform(as(sm, get("/parts")))
                .andExpect(jsonPath("$.data[?(@.id=='" + storeGasId + "')].mandatoryFromHq").value(true))
                .andExpect(jsonPath("$.data[?(@.id=='" + storeGasId + "')].catalogPartId").value(hqGas.getId().toString()));
    }

    @Test
    void orderFromHq_onlyItemsHqSells() throws Exception {

        mvc.perform(as(sm, post("/purchase-orders")).content(poBody(hqTool, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Only items HQ sells can be ordered from HQ: " + hqTool.getName()));

        mvc.perform(as(sm, post("/purchase-orders")).content(poBody(storeSpare, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void orderFromHq_needsHqsFranchiseCentreSupplier() throws Exception {

        franchiseCentre.setFranchiseCentre(false);
        supplierRepository.save(franchiseCentre);

        mvc.perform(as(sm, post("/purchase-orders")).content(poBody(hqSpring, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "HQ has no franchise-centre supplier set up, so orders to HQ can't be placed yet"));
    }

    // ---- items 5–6: buy elsewhere ----

    @Test
    void buyElsewhere_mandatoryItemOnlyWithHqAsTheSeller() throws Exception {

        Part storeGas = linkedStorePart(hqGas);
        PurchaseRequestAuthorizationItem line = praLine(storeGas, 12);
        String praId = line.getPurchaseRequestAuthorization().getId().toString();

        mvc.perform(as(sm, post("/realisasi")).content(buyBody(praId, line, storeGas, 2, tokopedia, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("This item must be bought from HQ: " + storeGas.getName()));

        mvc.perform(as(sm, post("/realisasi")).content(buyBody(praId, line, storeGas, 2, null, "Shopee · Toko Airsoft Jaya")))
                .andExpect(status().isBadRequest());

        assertEquals(12, remainingQty(praId), "nothing consumed by the refused buys");

        // urgent buy paid at HQ: 2 pakets × 6 = 12 cans, exactly the limit
        mvc.perform(as(sm, post("/realisasi")).content(buyBody(praId, line, storeGas, 2, franchiseCentre, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        assertEquals(0, remainingQty(praId));
    }

    @Test
    void buyElsewhere_nonMandatoryItem_fromAnOutsideSeller_withSellerName() throws Exception {

        Part storeSpring = linkedStorePart(hqSpring);
        PurchaseRequestAuthorizationItem line = praLine(storeSpring, 12);
        String praId = line.getPurchaseRequestAuthorization().getId().toString();

        mvc.perform(as(sm, post("/realisasi")).content(buyBody(praId, line, storeSpring, 1, null, "Shopee · Toko Airsoft Jaya")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.sellerName").value("Shopee · Toko Airsoft Jaya"));

        assertEquals(6, remainingQty(praId), "12 authorized - 1 paket × 6");

        // a store-only item, no HQ link at all
        PurchaseRequestAuthorizationItem spareLine = praLine(storeSpare, 12);
        mvc.perform(as(sm, post("/realisasi")).content(buyBody(
                spareLine.getPurchaseRequestAuthorization().getId().toString(), spareLine, storeSpare, 1, tokopedia, null)))
                .andExpect(status().isOk());
    }

    // ---- linking an existing store part ----

    @Test
    void link_hqAdminsOnly_onceperStore_andOrdersThenReuseIt() throws Exception {

        mvc.perform(as(hqAdmin, patch("/parts/" + storeSpare.getId() + "/catalog-link"))
                .content("{\"catalogPartId\":\"" + hqSpring.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.catalogPartId").value(hqSpring.getId().toString()))
                .andExpect(jsonPath("$.data.soldByHq").value(true));

        // BE-25: a store may create a link, but once linked only HQ changes it
        // (was: any store link → 403, before the product owner's 23 Oct decision)
        mvc.perform(as(storeOwner, patch("/parts/" + storeSpare.getId() + "/catalog-link"))
                .content("{\"catalogPartId\":\"" + hqSpring.getId() + "\"}"))
                .andExpect(status().isForbidden());

        Part second = partRepository.save(Part.builder().tenant(store).name("Another spring")
                .category(storeSpare.getCategory()).build());
        mvc.perform(as(hqAdmin, patch("/parts/" + second.getId() + "/catalog-link"))
                .content("{\"catalogPartId\":\"" + hqSpring.getId() + "\"}"))
                .andExpect(status().isBadRequest());

        // HQ's own rows aren't linked: they are the catalog
        mvc.perform(as(hqAdmin, patch("/parts/" + hqTool.getId() + "/catalog-link"))
                .content("{\"catalogPartId\":\"" + hqSpring.getId() + "\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(as(sm, post("/purchase-orders")).content(poBody(hqSpring, 4)))
                .andExpect(jsonPath("$.data.items[0].partId").value(storeSpare.getId().toString()));
    }

    // ---- helpers ----

    private Part linkedStorePart(Part hqItem) {

        return partRepository.save(Part.builder().tenant(store).name(hqItem.getName())
                .category(storeGasCategory).catalogPart(hqItem).build());
    }

    /** An active limit set by the store Owner: {@code cans} stock units, Rp 2,000,000. */
    private PurchaseRequestAuthorizationItem praLine(Part part, int cans) {

        PurchaseRequestAuthorization pra = praRepository.save(PurchaseRequestAuthorization.builder()
                .tenant(store).approvedBy(storeOwner).status(PurchaseRequestAuthorizationStatus.ACTIVE).build());

        return praItemRepository.save(PurchaseRequestAuthorizationItem.builder()
                .purchaseRequestAuthorization(pra).part(part).authorizedQty(cans)
                .maxValue(new BigDecimal("2000000")).build());
    }

    private int remainingQty(String praId) throws Exception {

        String body = mvc.perform(as(storeOwner, get("/purchase-authorizations/" + praId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Integer>read(body, "$.data.items[0].remainingQty");
    }

    /** pakets × factor 6 at Rp 330,000 per paket. */
    private String buyBody(String praId, PurchaseRequestAuthorizationItem line, Part part, int pakets,
            Supplier supplier, String sellerName) {

        return "{\"purchaseRequestAuthorizationId\":\"" + praId + "\","
                + "\"externalOrderRef\":\"BE21-" + UUID.randomUUID() + "\","
                + (supplier != null ? "\"supplierId\":\"" + supplier.getId() + "\"," : "")
                + (sellerName != null ? "\"sellerName\":\"" + sellerName + "\"," : "")
                + "\"paymentMethod\":\"COMPANY_ACCOUNT\",\"subtotal\":" + (330_000L * pakets) + ","
                + "\"items\":[{\"purchaseRequestAuthorizationItemId\":\"" + line.getId() + "\",\"partId\":\"" + part.getId()
                + "\",\"purchaseUom\":\"paket\",\"conversionFactor\":6,\"purchasedQty\":" + pakets
                + ",\"actualUnitPrice\":330000}]}";
    }

    private static String poBody(Part part, int qty) {

        return "{\"items\":[{\"partId\":\"" + part.getId() + "\",\"quantity\":" + qty + ",\"price\":38500}]}";
    }

    private PartCategory category(Tenant tenant, String name, boolean consumable) {

        return categoryRepository.save(PartCategory.builder().tenant(tenant).name(name).consumable(consumable).build());
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
