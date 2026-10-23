package com.example.operion.module.part;

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
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.procurement.entity.Supplier;
import com.example.operion.module.procurement.repository.SupplierRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-28: HQ sets the price of what it sells, and orders to HQ take it. Prices
 * and quantities are non-round and never 1 (rule 6), so a line taken from
 * the typed price or a total that ignores the quantity shows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HqPriceTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private SupplierRepository supplierRepository;

    private User hqAdmin;
    private User storeAdmin;
    private User storeOwner;
    private User sm;
    private Part hqTool;     // HQ's own, not sold yet, no price
    private Part hqSpring;   // sold at Rp 41,250
    private PartCategory hqHopUp;
    private Tenant hq;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        hq = tenantRepository.save(Tenant.builder().code("BE28_HQ_" + s).name("HQ").active(true).build());
        Tenant store = tenantRepository.save(Tenant.builder().code("BE28_ST_" + s).name("Store").active(true).parent(hq).build());

        hqAdmin = user("be28-hqadm-" + s, Role.ADMIN, null, hq);
        storeAdmin = user("be28-stadm-" + s, Role.ADMIN, null, store);
        storeOwner = user("be28-own-" + s, Role.OPERATOR, Scope.OWNER, store);
        sm = user("be28-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);

        hqHopUp = categoryRepository.save(PartCategory.builder().tenant(hq).name("Hop-up").consumable(false).build());
        categoryRepository.save(PartCategory.builder().tenant(store).name("Hop-up").consumable(false).build());

        hqTool = partRepository.save(Part.builder().tenant(hq).name("Chrono " + s).category(hqHopUp).build());
        hqSpring = partRepository.save(Part.builder().tenant(hq).name("Spring " + s).category(hqHopUp)
                .soldByHq(true).hqPrice(new BigDecimal("41250")).build());

        supplierRepository.save(Supplier.builder().tenant(hq).name("HQ").franchiseCentre(true).build());
    }

    // ---- item 1: setting the price ----

    @Test
    void price_setByHqAdminsOnly_andRequiredToSell() throws Exception {

        mvc.perform(as(hqAdmin, patch("/parts/" + hqTool.getId() + "/hq-flags")).content("{\"soldByHq\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Set HQ's price before selling this item"));
        mvc.perform(as(hqAdmin, patch("/parts/" + hqTool.getId() + "/hq-flags")).content("{\"soldByHq\":true,\"price\":0}"))
                .andExpect(status().isBadRequest());

        for (User refused : List.of(storeAdmin, storeOwner)) {
            mvc.perform(as(refused, patch("/parts/" + hqTool.getId() + "/hq-flags")).content("{\"price\":12500}"))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(as(hqAdmin, patch("/parts/" + hqTool.getId() + "/hq-flags")).content("{\"soldByHq\":true,\"price\":187500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.soldByHq").value(true))
                .andExpect(jsonPath("$.data.hqPrice").value(187500));

        // null leaves it unchanged
        mvc.perform(as(hqAdmin, patch("/parts/" + hqTool.getId() + "/hq-flags")).content("{\"mandatoryFromHq\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hqPrice").value(187500));
    }

    // ---- item 2: reading it ----

    @Test
    void catalogPrice_ownerPlusOnly() throws Exception {

        String spring = "$.data[?(@.id=='" + hqSpring.getId() + "')].price";

        mvc.perform(as(sm, get("/catalog")))
                .andExpect(jsonPath(spring).value(org.hamcrest.Matchers.contains((Object) null)));
        mvc.perform(as(storeOwner, get("/catalog")))
                .andExpect(jsonPath(spring).value(41250));
        mvc.perform(as(hqAdmin, get("/catalog")))
                .andExpect(jsonPath(spring).value(41250));
    }

    // ---- item 3: orders take HQ's price ----

    @Test
    void order_takesHqsPriceOnDraft_againOnSend_thenFrozen() throws Exception {

        // the Store Manager types Rp 1 a unit: ignored
        String po = JsonPath.read(mvc.perform(as(sm, post("/purchase-orders"))
                .content("{\"items\":[{\"partId\":\"" + hqSpring.getId() + "\",\"quantity\":4,\"price\":1}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");

        mvc.perform(as(storeOwner, get("/purchase-orders/" + po)))
                .andExpect(jsonPath("$.data.items[0].price").value(41250))
                .andExpect(jsonPath("$.data.totalAmount").value(4 * 41250));

        // HQ changes its price before the Owner sends: the sent order uses the new one
        price(hqSpring, "43750");
        mvc.perform(as(storeOwner, patch("/purchase-orders/" + po + "/send")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ORDERED"))
                .andExpect(jsonPath("$.data.items[0].price").value(43750))
                .andExpect(jsonPath("$.data.totalAmount").value(4 * 43750));

        // after sending: frozen
        price(hqSpring, "49900");
        mvc.perform(as(storeOwner, get("/purchase-orders/" + po)))
                .andExpect(jsonPath("$.data.items[0].price").value(43750))
                .andExpect(jsonPath("$.data.totalAmount").value(4 * 43750));
    }

    @Test
    void order_ofAnItemWithNoHqPrice_isRefused() throws Exception {

        // legacy data: sold by HQ before prices existed
        Part unpriced = partRepository.save(Part.builder().tenant(hq).name("Old target").category(hqHopUp)
                .soldByHq(true).build());

        mvc.perform(as(sm, post("/purchase-orders"))
                .content("{\"items\":[{\"partId\":\"" + unpriced.getId() + "\",\"quantity\":6}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("HQ hasn't set a price for Old target yet"));
    }

    // ---- helpers ----

    private void price(Part item, String amount) throws Exception {

        mvc.perform(as(hqAdmin, patch("/parts/" + item.getId() + "/hq-flags")).content("{\"price\":" + amount + "}"))
                .andExpect(status().isOk());
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
