package com.example.operion.module.stockadjustment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.example.operion.module.inventory.repository.StockMovementRepository;
import com.example.operion.module.notification.entity.Notification;
import com.example.operion.module.notification.enums.NotificationType;
import com.example.operion.module.notification.repository.NotificationRepository;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.entity.PartType;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.part.repository.PartTypeRepository;
import com.example.operion.module.partinstance.repository.PartInstanceRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-25: who changes stock and item data. Fixtures: HQ → Store (two Owners) and
 * HQ → Small store (one Owner), plus HQ's catalog. Stock moves use quantities
 * other than 1 (rule 6): there is no conversion factor on these paths, so the
 * quantity itself is the neutral value to avoid.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WhoChangesStockTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartTypeRepository partTypeRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private PartInstanceRepository partInstanceRepository;
    @Autowired private StockMovementRepository movementRepository;
    @Autowired private NotificationRepository notificationRepository;

    private Tenant store;
    private Tenant smallStore;
    private User admin;        // the store's ADMIN, no scope
    private User sm;           // Store Manager
    private User owner;
    private User owner2;
    private User operator;
    private User hqPrincipal;
    private User loneOwner;    // the small store's only Owner
    private PartCategory gasCategory;   // consumable, NOT named "Consumable"
    private PartCategory hopUp;         // per unit
    private PartType hopUpSpring;
    private Part gas;
    private Part spring;
    private Part hqGas;
    private Part hqSpring;
    private Part hqTool;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenantRepository.save(Tenant.builder().code("BE25_HQ_" + s).name("HQ").active(true).build());
        store = tenantRepository.save(Tenant.builder().code("BE25_ST_" + s).name("Store").active(true).parent(hq).build());
        smallStore = tenantRepository.save(Tenant.builder().code("BE25_SM_" + s).name("Small").active(true).parent(hq).build());

        admin = user("be25-adm-" + s, Role.ADMIN, null, store);
        sm = user("be25-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);
        owner = user("be25-own-" + s, Role.OPERATOR, Scope.OWNER, store);
        owner2 = user("be25-own2-" + s, Role.OPERATOR, Scope.OWNER, store);
        operator = user("be25-op-" + s, Role.OPERATOR, null, store);
        hqPrincipal = user("be25-hq-" + s, Role.ADMIN, Scope.PRINCIPAL, hq);
        loneOwner = user("be25-lone-" + s, Role.OPERATOR, Scope.OWNER, smallStore);

        gasCategory = category(store, "Gas", true);
        hopUp = category(store, "Hop-up", false);
        hopUpSpring = partTypeRepository.save(PartType.builder().tenant(store).category(hopUp).name("Spring").build());
        gas = partRepository.save(Part.builder().tenant(store).name("Green gas " + s).category(gasCategory).currentStock(20).build());
        spring = partRepository.save(Part.builder().tenant(store).name("Spring " + s).category(hopUp).currentStock(0).build());

        PartCategory hqGasCategory = category(hq, "Gas", true);
        PartCategory hqHopUp = category(hq, "Hop-up", false);
        hqGas = partRepository.save(Part.builder().tenant(hq).name("HQ gas " + s).category(hqGasCategory).soldByHq(true).build());
        hqSpring = partRepository.save(Part.builder().tenant(hq).name("HQ spring " + s).category(hqHopUp).soldByHq(true).build());
        hqTool = partRepository.save(Part.builder().tenant(hq).name("HQ tool " + s).category(hqHopUp).build());
    }

    // ---- item 1: the two shortcuts ----

    @Test
    void shortcuts_adminOnly_withAReason() throws Exception {

        for (User refused : List.of(sm, owner, operator)) {
            mvc.perform(as(refused, post("/inventory/adjust")).content(shortcut(gas, -7, "Miscount")))
                    .andExpect(status().isForbidden());
            mvc.perform(as(refused, post("/inventory/receive")).content(shortcut(gas, 13, "Found a box")))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(as(admin, post("/inventory/adjust")).content(shortcut(gas, -7, " ")))
                .andExpect(status().isBadRequest());
        assertEquals(20, partRepository.findById(gas.getId()).orElseThrow().getCurrentStock(), "nothing moved");

        mvc.perform(as(admin, post("/inventory/adjust")).content(shortcut(gas, -7, "Miscount at closing")))
                .andExpect(status().isOk());
        assertEquals(13, partRepository.findById(gas.getId()).orElseThrow().getCurrentStock());

        // BE-26: the admin is the movement's actor; the note says why
        var movement = movementRepository.findByPartIdOrderByCreatedAtDesc(gas.getId()).get(0);
        assertEquals("Data repair: Miscount at closing", movement.getNotes());
        assertEquals(admin.getId(), movement.getActor().getId());
    }

    @Test
    void receiveShortcut_decidesConsumableByTheFlag_notTheName() throws Exception {

        // a consumable category named "Gas": the old name check created 13 part instances here
        mvc.perform(as(admin, post("/inventory/receive")).content(shortcut(gas, 13, "Box found in storage")))
                .andExpect(status().isOk());
        assertEquals(33, partRepository.findById(gas.getId()).orElseThrow().getCurrentStock());
        assertEquals(0, partInstanceRepository.findByPartIdAndTenantId(gas.getId(), store.getId()).size());

        // a per-unit item still gets one instance per unit
        mvc.perform(as(admin, post("/inventory/receive")).content(shortcut(spring, 3, "Returned from repair")))
                .andExpect(status().isOk());
        assertEquals(3, partInstanceRepository.findByPartIdAndTenantId(spring.getId(), store.getId()).size());
    }

    // ---- items 2–3: stock adjustments ----

    @Test
    void request_managerPlus_andTheStoresOwnersAreTold() throws Exception {

        mvc.perform(as(operator, post("/stock-adjustments")).content(adjustment(gas, -7)))
                .andExpect(status().isForbidden());

        request(sm, gas, -7);

        assertEquals(List.of(NotificationType.STOCK_ADJUSTMENT_REQUESTED), typesFor(owner));
        assertEquals(List.of(NotificationType.STOCK_ADJUSTMENT_REQUESTED), typesFor(owner2));
        assertTrue(typesFor(sm).isEmpty(), "not the requester");
        assertTrue(typesFor(hqPrincipal).isEmpty(), "not HQ");
        assertTrue(notificationRepository.findByRecipientUserId(owner.getId()).get(0).getMessage()
                .startsWith(sm.getFullName() + " asks to remove 7 × " + gas.getName()));
    }

    @Test
    void approve_theStoresOwnOwner_notHq_notTheManager() throws Exception {

        String id = request(sm, gas, -7);

        mvc.perform(as(sm, patch("/stock-adjustments/" + id + "/approve"))).andExpect(status().isForbidden());
        mvc.perform(as(hqPrincipal, patch("/stock-adjustments/" + id + "/approve")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Stock adjustment not found"));
        mvc.perform(as(hqPrincipal, patch("/stock-adjustments/" + id + "/reject")).content("{\"reason\":\"no\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(as(owner, patch("/stock-adjustments/" + id + "/approve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.selfApproved").value(false));
        assertEquals(13, partRepository.findById(gas.getId()).orElseThrow().getCurrentStock());
    }

    @Test
    void ownApproval_anotherOwnerMustApprove() throws Exception {

        String id = request(owner, gas, 13);

        mvc.perform(as(owner, patch("/stock-adjustments/" + id + "/approve")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Another Owner must approve your own adjustment"));
        assertEquals(20, partRepository.findById(gas.getId()).orElseThrow().getCurrentStock());

        mvc.perform(as(owner2, patch("/stock-adjustments/" + id + "/approve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selfApproved").value(false));
        assertEquals(33, partRepository.findById(gas.getId()).orElseThrow().getCurrentStock());
    }

    @Test
    void ownApproval_theOnlyOwnerMay_andItIsFlagged() throws Exception {

        PartCategory c = category(smallStore, "Gas", true);
        Part smallGas = partRepository.save(Part.builder().tenant(smallStore).name("Small gas").category(c).currentStock(20).build());

        String id = request(loneOwner, smallGas, -7);
        assertTrue(typesFor(loneOwner).isEmpty(), "nobody else to tell");

        mvc.perform(as(loneOwner, patch("/stock-adjustments/" + id + "/approve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selfApproved").value(true));
        assertEquals(13, partRepository.findById(smallGas.getId()).orElseThrow().getCurrentStock());

        mvc.perform(as(loneOwner, get("/stock-adjustments").param("selfApproved", "true")))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(id));
        mvc.perform(as(loneOwner, get("/stock-adjustments").param("selfApproved", "false")))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ---- items 4–5: items, categories and types ----

    @Test
    void items_managerPlusEdits_ownerPlusRetires() throws Exception {

        String body = "{\"name\":\"Hop-up rubber\",\"categoryId\":\"" + hopUp.getId() + "\",\"partTypeId\":\""
                + hopUpSpring.getId() + "\",\"minimumStock\":4,\"reorderQuantity\":12}";

        mvc.perform(as(operator, post("/parts")).content(body)).andExpect(status().isForbidden());
        String created = JsonPath.read(mvc.perform(as(sm, post("/parts")).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");

        mvc.perform(as(operator, patch("/parts/" + created + "/retire")).content("{\"reason\":\"gone\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(sm, patch("/parts/" + created + "/retire")).content("{\"reason\":\"gone\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(owner, patch("/parts/" + created + "/retire")).content("{\"reason\":\"No longer stocked\"}"))
                .andExpect(status().isOk());
        mvc.perform(as(sm, patch("/parts/" + created + "/restore"))).andExpect(status().isForbidden());
        mvc.perform(as(admin, patch("/parts/" + created + "/restore"))).andExpect(status().isOk());
    }

    @Test
    void categoriesAndTypes_adminOnly() throws Exception {

        String body = "{\"name\":\"Targets\",\"consumable\":true}";

        for (User refused : List.of(sm, owner, operator)) {
            mvc.perform(as(refused, post("/part-categories")).content(body)).andExpect(status().isForbidden());
            mvc.perform(as(refused, post("/part-categories/seed"))).andExpect(status().isForbidden());
            mvc.perform(as(refused, post("/part-types/seed"))).andExpect(status().isForbidden());
        }

        mvc.perform(as(admin, post("/part-categories")).content(body)).andExpect(status().isOk());
        mvc.perform(as(sm, get("/part-categories"))).andExpect(status().isOk());
    }

    // ---- item 6: linking to HQ's catalog ----

    @Test
    void link_theStoreManagerCreatesALink_onlyHqChangesOrRemovesIt() throws Exception {

        mvc.perform(as(sm, patch("/parts/" + spring.getId() + "/catalog-link")).content(link(hqSpring)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.catalogPartId").value(hqSpring.getId().toString()));

        // re-link or unlink: HQ only
        mvc.perform(as(sm, patch("/parts/" + spring.getId() + "/catalog-link")).content(link(hqTool)))
                .andExpect(status().isForbidden());
        mvc.perform(as(sm, patch("/parts/" + spring.getId() + "/catalog-link")).content("{\"catalogPartId\":null}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(operator, patch("/parts/" + gas.getId() + "/catalog-link")).content(link(hqGas)))
                .andExpect(status().isForbidden());

        mvc.perform(as(hqPrincipal, patch("/parts/" + spring.getId() + "/catalog-link")).content("{\"catalogPartId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.catalogPartId").isEmpty());
    }

    @Test
    void link_onlyToWhatHqSells_andOnlyItemsCountedTheSameWay() throws Exception {

        mvc.perform(as(sm, patch("/parts/" + spring.getId() + "/catalog-link")).content(link(hqTool)))
                .andExpect(status().isBadRequest());

        // consumable gas to a per-unit spring: refused for the store and for HQ
        mvc.perform(as(sm, patch("/parts/" + gas.getId() + "/catalog-link")).content(link(hqSpring)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("These two items are counted differently"));
        mvc.perform(as(hqPrincipal, patch("/parts/" + gas.getId() + "/catalog-link")).content(link(hqSpring)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("These two items are counted differently"));

        mvc.perform(as(sm, patch("/parts/" + gas.getId() + "/catalog-link")).content(link(hqGas)))
                .andExpect(status().isOk());
    }

    // ---- helpers ----

    private String request(User u, Part part, int qty) throws Exception {

        return JsonPath.read(mvc.perform(as(u, post("/stock-adjustments")).content(adjustment(part, qty)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    private static String adjustment(Part part, int qty) {

        return "{\"partId\":\"" + part.getId() + "\",\"quantity\":" + qty + ",\"reason\":\"Counted at closing\"}";
    }

    private static String shortcut(Part part, int qty, String reason) {

        return "{\"partId\":\"" + part.getId() + "\",\"quantity\":" + qty + ",\"reason\":\"" + reason + "\"}";
    }

    private static String link(Part item) {

        return "{\"catalogPartId\":\"" + item.getId() + "\"}";
    }

    private List<NotificationType> typesFor(User u) {

        return notificationRepository.findByRecipientUserId(u.getId()).stream().map(Notification::getType).toList();
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
