package com.example.operion.module.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-34: a store's Owner (OWNER scope, not an ADMIN) manages the store's own
 * people, up to Manager: adds them, gives Supervisor or Manager, gives any
 * role but Admin, switches them off and on, lists them. Never another Owner,
 * the store's Admin, another store, HQ, or themself. HQ still creates a
 * store's Owners.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoreOwnersManagePeopleTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    private String s;
    private Tenant hq;
    private Tenant store;
    private Tenant sibling;
    private User hqSuper;      // HQ, ADMIN + PRINCIPAL
    private User owner;        // the store's Owner, not an ADMIN
    private User coOwner;      // a second Owner of the store
    private User storeAdmin;   // the store's ADMIN
    private User sm;           // Store Manager
    private User staff;        // OPERATOR, no scope
    private User siblingStaff; // another store's staff

    @BeforeEach
    void seedFixtures() {

        s = UUID.randomUUID().toString().substring(0, 8);

        hq = tenantRepository.save(Tenant.builder().code("BE34_HQ_" + s).name("HQ").active(true).build());
        store = tenantRepository.save(Tenant.builder().code("BE34_ST_" + s).name("Store").active(true).parent(hq).build());
        sibling = tenantRepository.save(Tenant.builder().code("BE34_SB_" + s).name("Sibling").active(true).parent(hq).build());

        hqSuper = user("be34-super-" + s, Role.ADMIN, Scope.PRINCIPAL, hq);
        owner = user("be34-own-" + s, Role.OPERATOR, Scope.OWNER, store);
        coOwner = user("be34-coown-" + s, Role.OPERATOR, Scope.OWNER, store);
        storeAdmin = user("be34-adm-" + s, Role.ADMIN, null, store);
        sm = user("be34-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);
        staff = user("be34-staff-" + s, Role.OPERATOR, null, store);
        siblingStaff = user("be34-sib-" + s, Role.OPERATOR, null, sibling);
    }

    @Test
    void anOwnerAddsPeople_toTheirOwnStoreOnly() throws Exception {

        register(owner, "be34-new-" + s, null).andExpect(status().isOk());
        User added = userRepository.findByEmail("be34-new-" + s + "@test.com").orElseThrow();
        assertEquals(store.getId(), added.getTenant().getId());
        assertEquals(Role.OPERATOR, added.getRole());
        assertNull(added.getScope());

        register(owner, "be34-sib-new-" + s, sibling.getCode())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Owners add people to their own store only"));
        register(owner, "be34-hq-new-" + s, hq.getCode()).andExpect(status().isForbidden());

        // HQ still creates a store's Owner: add the person, then give Owner
        register(hqSuper, "be34-next-owner-" + s, store.getCode()).andExpect(status().isOk());
        User nextOwner = userRepository.findByEmail("be34-next-owner-" + s + "@test.com").orElseThrow();
        scope(hqSuper, nextOwner, "OWNER").andExpect(status().isOk());
    }

    @Test
    void anOwnerGivesSupervisorOrManager_neverOwner() throws Exception {

        scope(owner, staff, "SUPERVISOR").andExpect(status().isOk());
        scope(owner, staff, "MANAGER").andExpect(status().isOk());
        assertEquals(Scope.MANAGER, stored(staff).getScope());

        scope(owner, staff, "OWNER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Owners give Supervisor or Manager only; HQ gives Owner"));
        scope(owner, staff, "PRINCIPAL").andExpect(status().isForbidden());
        assertEquals(Scope.MANAGER, stored(staff).getScope());
    }

    @Test
    void anOwnerGivesAnyRoleButAdmin() throws Exception {

        role(owner, staff, "PROCUREMENT").andExpect(status().isOk());
        role(owner, staff, "TECHNICIAN").andExpect(status().isOk());
        assertEquals(Role.TECHNICIAN, stored(staff).getRole());

        role(owner, staff, "ADMIN")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only HQ gives the Admin role"));
        assertEquals(Role.TECHNICIAN, stored(staff).getRole());
    }

    @Test
    void anOwnerNeverTouchesOwnersAdminsOtherStoresOrThemself() throws Exception {

        setActive(owner, coOwner, false, "Left")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only HQ manages Owners and Admins"));
        scope(owner, coOwner, "MANAGER").andExpect(status().isForbidden());
        role(owner, storeAdmin, "OPERATOR").andExpect(status().isForbidden());
        setActive(owner, storeAdmin, false, "Left").andExpect(status().isForbidden());
        setActive(owner, siblingStaff, false, "Left")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Owners manage people in their own store only"));
        setActive(owner, owner, false, "Leaving").andExpect(status().isForbidden());

        assertTrue(stored(coOwner).isActive());
        assertTrue(stored(storeAdmin).isActive());
        assertTrue(stored(siblingStaff).isActive());
        assertEquals(Scope.OWNER, stored(coOwner).getScope());

        // their own store's staff, yes: off with a reason, and back on
        setActive(owner, staff, false, "Left on 30 Oct").andExpect(status().isOk());
        assertFalse(stored(staff).isActive());
        setActive(owner, staff, true, null).andExpect(status().isOk());
        assertTrue(stored(staff).isActive());
    }

    @Test
    void anOwnerListsTheirOwnStore_aManagerNothing() throws Exception {

        List<String> seen = JsonPath.read(mvc.perform(as(owner, get("/users")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data[*].id");

        assertTrue(seen.containsAll(List.of(staff.getId().toString(), sm.getId().toString(), coOwner.getId().toString())));
        assertFalse(seen.contains(siblingStaff.getId().toString()));
        assertFalse(seen.contains(hqSuper.getId().toString()));

        mvc.perform(as(owner, get("/users")).param("tenantId", sibling.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Owners see their own store's people only"));

        // a Store Manager manages nobody
        mvc.perform(as(sm, get("/users"))).andExpect(status().isForbidden());
        register(sm, "be34-by-sm-" + s, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only an Admin or the store's Owner can manage people"));
        scope(sm, staff, "SUPERVISOR").andExpect(status().isForbidden());
        setActive(sm, staff, false, "Left").andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private ResultActions register(User by, String prefix, String tenantCode) throws Exception {

        String body = "{\"email\":\"" + prefix + "@test.com\",\"password\":\"Start-123\",\"fullName\":\"" + prefix + "\""
                + (tenantCode != null ? ",\"tenantCode\":\"" + tenantCode + "\"" : "") + "}";
        return mvc.perform(as(by, post("/auth/register")).content(body));
    }

    private ResultActions scope(User by, User who, String scope) throws Exception {

        return mvc.perform(as(by, patch("/users/" + who.getId() + "/scope")).content("{\"scope\":\"" + scope + "\"}"));
    }

    private ResultActions role(User by, User who, String role) throws Exception {

        return mvc.perform(as(by, patch("/users/" + who.getId() + "/role")).content("{\"role\":\"" + role + "\"}"));
    }

    private ResultActions setActive(User by, User who, boolean active, String reason) throws Exception {

        String body = "{\"active\":" + active + (reason != null ? ",\"reason\":\"" + reason + "\"" : "") + "}";
        return mvc.perform(as(by, patch("/users/" + who.getId() + "/active")).content(body));
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

    private MockHttpServletRequestBuilder as(User u, MockHttpServletRequestBuilder req) {

        String token = jwtUtil.generateToken(
                u.getId().toString(), u.getRole().name(), u.getTenant().getId().toString(),
                u.getScope() != null ? u.getScope().name() : null);

        return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }
}
