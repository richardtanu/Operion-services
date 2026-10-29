package com.example.operion.module.part;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.example.operion.config.MasterDataInitializer;
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
import com.example.operion.module.procurement.entity.Supplier;
import com.example.operion.module.procurement.repository.SupplierRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-29: one category list, owned by HQ. Categories and part types live on
 * the root tenant only; stores read that list, only HQ admins change it, a
 * store's first order of an HQ item uses HQ's own category and type, and a
 * new store gets no copies. A store-level category left from before BE-29
 * ({@code leftover}) is never read or used.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OneCategoryListTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartTypeRepository partTypeRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private MasterDataInitializer masterDataInitializer;

    private String s;
    private Tenant hq;
    private Tenant store;
    private User hqAdmin;       // HQ, ADMIN, no scope
    private User hqPrincipal;   // HQ, OPERATOR + PRINCIPAL
    private User storeAdmin;    // the store's own ADMIN
    private User storeOwner;
    private User sm;            // Store Manager
    private PartCategory gas;   // HQ's, consumable
    private PartType greenGas;  // HQ's, in Gas
    private PartCategory hopUp; // HQ's, no types
    private PartCategory leftover;  // a store-level copy from before BE-29
    private PartCategory otherBb;   // another root's
    private PartType other025;      // another root's
    private Part hqGasItem;

    @BeforeEach
    void seedFixtures() {

        s = UUID.randomUUID().toString().substring(0, 8);

        hq = tenantRepository.save(Tenant.builder().code("BE29_HQ_" + s).name("HQ").active(true).build());
        store = tenantRepository.save(Tenant.builder().code("BE29_ST_" + s).name("Store").active(true).parent(hq).build());
        Tenant other = tenantRepository.save(Tenant.builder().code("BE29_OT_" + s).name("Other").active(true).build());

        hqAdmin = user("be29-hqadm-" + s, Role.ADMIN, null, hq);
        hqPrincipal = user("be29-hqpri-" + s, Role.OPERATOR, Scope.PRINCIPAL, hq);
        storeAdmin = user("be29-stadm-" + s, Role.ADMIN, null, store);
        storeOwner = user("be29-own-" + s, Role.OPERATOR, Scope.OWNER, store);
        sm = user("be29-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);

        gas = category(hq, "Gas", true);
        greenGas = partTypeRepository.save(PartType.builder().tenant(hq).category(gas).name("Green gas").build());
        hopUp = category(hq, "Hop-up", false);
        leftover = category(store, "Gas", false);
        otherBb = category(other, "BB", true);
        other025 = partTypeRepository.save(PartType.builder().tenant(other).category(otherBb).name("0.25g").build());

        hqGasItem = partRepository.save(Part.builder().tenant(hq).name("Green gas 1000ml " + s)
                .category(gas).partType(greenGas).soldByHq(true).hqPrice(new BigDecimal("38500")).build());

        supplierRepository.save(Supplier.builder().tenant(hq).name("HQ").franchiseCentre(true).build());
    }

    @Test
    void aStoreReadsHqsList_notItsOwnCopies() throws Exception {

        List<String> categoryIds = ids(mvc.perform(as(sm, get("/part-categories")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertTrue(categoryIds.contains(gas.getId().toString()), "HQ's category");
        assertTrue(categoryIds.contains(hopUp.getId().toString()));
        assertFalse(categoryIds.contains(leftover.getId().toString()), "never the store's own copy");
        assertFalse(categoryIds.contains(otherBb.getId().toString()), "never another tree's");

        List<String> typeIds = ids(mvc.perform(as(sm, get("/part-types")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(typeIds.contains(greenGas.getId().toString()));
        assertFalse(typeIds.contains(other025.getId().toString()));

        mvc.perform(as(sm, get("/part-types/category/" + gas.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(greenGas.getId().toString()));
    }

    @Test
    void hqAddsACategory_everyStoreSeesIt() throws Exception {

        String id = JsonPath.read(mvc.perform(as(hqAdmin, post("/part-categories"))
                        .content("{\"name\":\"Plate targets\",\"consumable\":false}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");

        assertEquals(hq.getId(), categoryRepository.findById(UUID.fromString(id)).orElseThrow().getTenant().getId());

        List<String> seen = ids(mvc.perform(as(sm, get("/part-categories")))
                .andReturn().getResponse().getContentAsString());
        assertTrue(seen.contains(id), "the store sees HQ's new category");
    }

    @Test
    void onlyHqAdminsChangeTheList() throws Exception {

        for (User refused : List.of(storeAdmin, storeOwner, sm)) {
            mvc.perform(as(refused, post("/part-categories")).content("{\"name\":\"Targets\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(as(refused, put("/part-categories/" + gas.getId())).content("{\"name\":\"Gas renamed\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(as(refused, put("/part-categories/" + gas.getId())).content("{\"consumable\":false}"))
                    .andExpect(status().isForbidden());
            mvc.perform(as(refused, delete("/part-categories/" + hopUp.getId())))
                    .andExpect(status().isForbidden());
            mvc.perform(as(refused, post("/part-types"))
                            .content("{\"categoryId\":\"" + gas.getId() + "\",\"name\":\"Propane\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(as(refused, post("/part-categories/seed"))).andExpect(status().isForbidden());
        }

        PartCategory unchanged = categoryRepository.findById(gas.getId()).orElseThrow();
        assertEquals("Gas", unchanged.getName());
        assertTrue(unchanged.getConsumable(), "nobody at the store flipped how gas is counted");

        // HQ's Principal (not an ADMIN) may, signed in at HQ
        mvc.perform(as(hqPrincipal, put("/part-categories/" + gas.getId())).content("{\"description\":\"Gas cans\"}"))
                .andExpect(status().isOk());
        mvc.perform(as(hqPrincipal, post("/part-types"))
                        .content("{\"categoryId\":\"" + gas.getId() + "\",\"name\":\"Propane\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryId").value(gas.getId().toString()));
    }

    @Test
    void firstOrderOfAnHqItem_usesHqsCategoryAndType_noCopies() throws Exception {

        String body = mvc.perform(as(sm, post("/purchase-orders"))
                        .content("{\"items\":[{\"partId\":\"" + hqGasItem.getId() + "\",\"quantity\":6}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        Part storePart = partRepository.findById(UUID.fromString(JsonPath.read(body, "$.data.items[0].partId"))).orElseThrow();

        assertEquals(store.getId(), storePart.getTenant().getId());
        assertEquals(gas.getId(), storePart.getCategory().getId(), "HQ's category, not the store's same-named copy");
        assertEquals(greenGas.getId(), storePart.getPartType().getId(), "HQ's type");
        assertTrue(storePart.isConsumable(), "counted exactly as HQ counts it");

        assertEquals(List.of(leftover.getId()),
                categoryRepository.findByTenantId(store.getId()).stream().map(PartCategory::getId).toList(),
                "no category created for the store");
        assertTrue(partTypeRepository.findByTenantId(store.getId()).isEmpty(), "no type created for the store");
    }

    @Test
    void onlyARootIsSeeded() throws Exception {

        String newStoreId = JsonPath.read(mvc.perform(as(hqAdmin, post("/tenants"))
                        .content("{\"name\":\"Outlet\",\"code\":\"BE29_NEW_" + s + "\",\"parentId\":\"" + hq.getId() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");

        assertTrue(categoryRepository.findByTenantId(UUID.fromString(newStoreId)).isEmpty(), "a new store gets no categories");
        assertTrue(partTypeRepository.findByTenantId(UUID.fromString(newStoreId)).isEmpty(), "and no types");

        // the startup sync runs this for every tenant: a store stays empty, a root gets the defaults
        masterDataInitializer.initialize(store.getId());
        assertEquals(1, categoryRepository.findByTenantId(store.getId()).size(), "only the leftover");

        Tenant newRoot = tenantRepository.save(Tenant.builder().code("BE29_RT_" + s).name("Root").active(true).build());
        masterDataInitializer.initialize(newRoot.getId());
        assertEquals(6, categoryRepository.findByTenantId(newRoot.getId()).size());
        assertEquals(45, partTypeRepository.findByTenantId(newRoot.getId()).size());
    }

    @Test
    void storeItems_takeHqsCategoryAndType_only() throws Exception {

        mvc.perform(as(sm, post("/parts")).content(partBody(gas, greenGas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryId").value(gas.getId().toString()));

        // another tree's category, or the store's own leftover copy: 400
        mvc.perform(as(sm, post("/parts")).content(partBody(otherBb, other025)))
                .andExpect(status().isBadRequest());
        mvc.perform(as(sm, post("/parts")).content(partBody(leftover, greenGas)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingACategory_refusedWhileAStoreItemUsesIt() throws Exception {

        partRepository.save(Part.builder().tenant(store).name("Hop-up rubber " + s).category(hopUp).build());

        mvc.perform(as(hqAdmin, delete("/part-categories/" + hopUp.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot delete category because items still use it."));
        assertTrue(categoryRepository.findById(hopUp.getId()).isPresent());

        PartCategory unused = category(hq, "Unused " + s, false);
        mvc.perform(as(hqAdmin, delete("/part-categories/" + unused.getId())))
                .andExpect(status().isNoContent());
    }

    // ---- helpers ----

    private static List<String> ids(String json) {

        return JsonPath.read(json, "$.data[*].id");
    }

    private static String partBody(PartCategory category, PartType type) {

        return "{\"name\":\"Item " + UUID.randomUUID() + "\",\"categoryId\":\"" + category.getId()
                + "\",\"partTypeId\":\"" + type.getId() + "\",\"minimumStock\":2,\"reorderQuantity\":12}";
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
