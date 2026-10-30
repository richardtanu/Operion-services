package com.example.operion.module.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.notification.repository.NotificationRepository;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.partinstance.entity.PartInstance;
import com.example.operion.module.partinstance.enums.PartInstanceStatus;
import com.example.operion.module.partinstance.repository.PartInstanceRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-33: switching off a user who has left. Out at once (login 403, an
 * existing token 401), not one of the store's Owners any more, no new
 * notifications, can't be named on a new record. Only an ADMIN inside the
 * tree, never yourself, never someone above you, with a reason; and
 * {@code GET /users} so an Admin can find them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SwitchOffUserTest {

    private static final String SM_PASSWORD = "Sm-pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private PartInstanceRepository partInstanceRepository;

    private Tenant hq;
    private Tenant store;
    private User hqSuper;     // HQ, ADMIN + PRINCIPAL
    private User storeAdmin;  // the store's ADMIN, no scope
    private User owner1;
    private User owner2;
    private User sm;          // Store Manager, known password
    private User staff;       // OPERATOR, no scope
    private Part gas;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        hq = tenantRepository.save(Tenant.builder().code("BE33_HQ_" + s).name("HQ").active(true).build());
        store = tenantRepository.save(Tenant.builder().code("BE33_ST_" + s).name("Store " + s).active(true).parent(hq).build());

        hqSuper = user("be33-super-" + s, Role.ADMIN, Scope.PRINCIPAL, hq);
        storeAdmin = user("be33-adm-" + s, Role.ADMIN, null, store);
        owner1 = user("be33-own1-" + s, Role.OPERATOR, Scope.OWNER, store);
        owner2 = user("be33-own2-" + s, Role.OPERATOR, Scope.OWNER, store);
        sm = user("be33-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);
        sm.setPasswordHash(passwordEncoder.encode(SM_PASSWORD));
        userRepository.save(sm);
        staff = user("be33-staff-" + s, Role.OPERATOR, null, store);

        PartCategory consumable = categoryRepository.save(PartCategory.builder().tenant(hq).name("Gas").consumable(true).build());
        gas = partRepository.save(Part.builder().tenant(store).name("Green gas " + s).category(consumable).currentStock(10).build());
    }

    @Test
    void aSwitchedOffUser_isOutAtOnce_andBackWhenSwitchedOn() throws Exception {

        String smToken = token(sm);

        setActive(hqSuper, sm, false, "Left on 30 Oct")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.deactivationReason").value("Left on 30 Oct"))
                .andExpect(jsonPath("$.data.deactivatedByName").value(hqSuper.getFullName()));

        login(SM_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("This account is switched off"));
        login("wrong-password").andExpect(status().isUnauthorized());   // says nothing to a stranger

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + smToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("This account is switched off"));

        setActive(hqSuper, sm, true, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.deactivationReason").value(org.hamcrest.Matchers.nullValue()));

        login(SM_PASSWORD).andExpect(status().isOk());
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + smToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void whoMaySwitchSomeoneOff() throws Exception {

        setActive(hqSuper, sm, false, " ").andExpect(status().isBadRequest());   // a reason is required
        setActive(hqSuper, hqSuper, false, "Testing")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You cannot switch yourself off or on"));
        setActive(storeAdmin, owner1, false, "Left")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You cannot switch off someone above you"));
        setActive(sm, staff, false, "Left").andExpect(status().isForbidden());   // neither an ADMIN nor the store's Owner (BE-34)

        assertTrue(stored(sm).isActive());
        assertTrue(stored(owner1).isActive());

        setActive(storeAdmin, staff, false, "Left").andExpect(status().isOk());
        setActive(hqSuper, owner1, false, "Sold their share").andExpect(status().isOk());
        assertFalse(stored(staff).isActive());
        assertFalse(stored(owner1).isActive());
    }

    @Test
    void aSwitchedOffOwner_noLongerCounts_soTheOtherOwnersOwnAdjustmentIsSelfApproved() throws Exception {

        String id = JsonPath.read(mvc.perform(as(owner1, post("/stock-adjustments"))
                        .content("{\"partId\":\"" + gas.getId() + "\",\"quantity\":-2,\"reason\":\"Counted at closing\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");

        mvc.perform(as(owner1, patch("/stock-adjustments/" + id + "/approve"))).andExpect(status().isForbidden());

        setActive(hqSuper, owner2, false, "Left").andExpect(status().isOk());

        mvc.perform(as(owner1, patch("/stock-adjustments/" + id + "/approve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selfApproved").value(true));
    }

    @Test
    void aSwitchedOffUser_getsNoNewNotifications() throws Exception {

        setActive(hqSuper, owner2, false, "Left").andExpect(status().isOk());

        mvc.perform(as(sm, post("/purchase-requests"))
                        .content("{\"requestedBy\":\"" + sm.getId() + "\",\"items\":[{\"partId\":\"" + gas.getId() + "\",\"quantity\":12}]}"))
                .andExpect(status().isOk());

        assertEquals(1, notificationRepository.findByRecipientUserId(owner1.getId()).size(), "the Owner still on is told");
        assertTrue(notificationRepository.findByRecipientUserId(owner2.getId()).isEmpty(), "the one switched off isn't");
    }

    @Test
    void aSwitchedOffUser_cantBeNamedOnANewRecord() throws Exception {

        staff.setActive(false);
        userRepository.save(staff);

        mvc.perform(as(sm, post("/purchase-requests"))
                        .content("{\"requestedBy\":\"" + staff.getId() + "\",\"items\":[{\"partId\":\"" + gas.getId() + "\",\"quantity\":12}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(staff.getFullName() + " is switched off"));

        PartInstance can = partInstanceRepository.save(PartInstance.builder().tenant(store).part(gas)
                .barcode("BE33-" + UUID.randomUUID()).status(PartInstanceStatus.IN_STOCK).build());

        mvc.perform(as(sm, post("/part-instances/take"))
                        .content("{\"barcode\":\"" + can.getBarcode() + "\",\"takenBy\":\"" + staff.getId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(staff.getFullName() + " is switched off"));
        assertEquals(10, partRepository.findById(gas.getId()).orElseThrow().getCurrentStock(), "nothing taken");
    }

    @Test
    void adminsListTheirTreesUsers() throws Exception {

        setActive(hqSuper, staff, false, "Left").andExpect(status().isOk());

        List<String> all = ids(mvc.perform(as(hqSuper, get("/users"))).andExpect(status().isOk()));
        assertTrue(all.containsAll(List.of(hqSuper.getId().toString(), sm.getId().toString(), staff.getId().toString())));

        List<String> off = ids(mvc.perform(as(hqSuper, get("/users")).param("active", "false")));
        assertEquals(List.of(staff.getId().toString()), off.stream().filter(all::contains).toList());

        List<String> storeOnly = ids(mvc.perform(as(hqSuper, get("/users")).param("tenantId", store.getId().toString())));
        assertFalse(storeOnly.contains(hqSuper.getId().toString()));
        assertTrue(storeOnly.contains(sm.getId().toString()));

        List<String> seenByStore = ids(mvc.perform(as(storeAdmin, get("/users"))).andExpect(status().isOk()));
        assertFalse(seenByStore.contains(hqSuper.getId().toString()), "a store's Admin sees its store, not HQ");

        mvc.perform(as(sm, get("/users"))).andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private ResultActions setActive(User by, User who, boolean active, String reason) throws Exception {

        String body = "{\"active\":" + active + (reason != null ? ",\"reason\":\"" + reason + "\"" : "") + "}";
        return mvc.perform(as(by, patch("/users/" + who.getId() + "/active")).content(body));
    }

    private ResultActions login(String password) throws Exception {

        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + sm.getEmail() + "\",\"password\":\"" + password + "\"}"));
    }

    private static List<String> ids(ResultActions result) throws Exception {

        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data[*].id");
    }

    private User stored(User u) {

        return userRepository.findById(u.getId()).orElseThrow();
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

    private String token(User u) {

        return jwtUtil.generateToken(u.getId().toString(), u.getRole().name(), u.getTenant().getId().toString(),
                u.getScope() != null ? u.getScope().name() : null);
    }

    private MockHttpServletRequestBuilder as(User u, MockHttpServletRequestBuilder req) {

        return req.header("Authorization", "Bearer " + token(u)).contentType(MediaType.APPLICATION_JSON);
    }
}
