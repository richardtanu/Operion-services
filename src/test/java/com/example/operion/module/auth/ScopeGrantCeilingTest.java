package com.example.operion.module.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

/**
 * BE-31: {@code PATCH /users/{id}/scope} grants only up to the granter's own
 * stored scope, never to themself, and never over someone already above them.
 * Before this, any ADMIN (a store's, with no scope) could make anyone in their
 * store a PRINCIPAL, themself included.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ScopeGrantCeilingTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    private User hqSuper;          // HQ, ADMIN + PRINCIPAL
    private User storeAdmin;       // the store's ADMIN, no scope
    private User storeOwnerAdmin;  // the store's ADMIN + OWNER
    private User storePrincipal;   // a PRINCIPAL placed at the store
    private User staff;            // OPERATOR, no scope

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenantRepository.save(Tenant.builder().code("BE31_HQ_" + s).name("HQ").active(true).build());
        Tenant store = tenantRepository.save(Tenant.builder().code("BE31_ST_" + s).name("Store").active(true).parent(hq).build());

        hqSuper = user("be31-super-" + s, Role.ADMIN, Scope.PRINCIPAL, hq);
        storeAdmin = user("be31-adm-" + s, Role.ADMIN, null, store);
        storeOwnerAdmin = user("be31-own-adm-" + s, Role.ADMIN, Scope.OWNER, store);
        storePrincipal = user("be31-pri-" + s, Role.OPERATOR, Scope.PRINCIPAL, store);
        staff = user("be31-staff-" + s, Role.OPERATOR, null, store);
    }

    @Test
    void anAdminWithNoScope_grantsNothing_notEvenToThemself() throws Exception {

        grant(storeAdmin, staff, "MANAGER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You can only grant a scope up to your own"));

        grant(storeAdmin, storeAdmin, "PRINCIPAL")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You cannot change your own scope"));

        assertNull(scopeOf(staff));
        assertNull(scopeOf(storeAdmin));
    }

    @Test
    void anOwnerAdmin_grantsUpToOwner_notPrincipal() throws Exception {

        grant(storeOwnerAdmin, staff, "MANAGER").andExpect(status().isOk());
        grant(storeOwnerAdmin, staff, "OWNER").andExpect(status().isOk());
        assertEquals(Scope.OWNER, scopeOf(staff));

        grant(storeOwnerAdmin, staff, "PRINCIPAL")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You can only grant a scope up to your own"));
        assertEquals(Scope.OWNER, scopeOf(staff));
    }

    @Test
    void nobodyChangesTheirOwnScope_theSuperAdminIncluded() throws Exception {

        grant(hqSuper, hqSuper, "SUPERVISOR")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You cannot change your own scope"));
        grant(storeOwnerAdmin, storeOwnerAdmin, "PRINCIPAL").andExpect(status().isForbidden());

        assertEquals(Scope.PRINCIPAL, scopeOf(hqSuper));
        assertEquals(Scope.OWNER, scopeOf(storeOwnerAdmin));
    }

    @Test
    void nobodyChangesTheScopeOfSomeoneAboveThem() throws Exception {

        grant(storeOwnerAdmin, storePrincipal, "SUPERVISOR")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You cannot change the scope of someone above you"));
        assertEquals(Scope.PRINCIPAL, scopeOf(storePrincipal));

        // HQ's PRINCIPAL can, anywhere in its tree, and can grant PRINCIPAL
        grant(hqSuper, storePrincipal, "OWNER").andExpect(status().isOk());
        grant(hqSuper, staff, "PRINCIPAL").andExpect(status().isOk());
        assertEquals(Scope.PRINCIPAL, scopeOf(staff));
    }

    @Test
    void theStoredScopeCounts_notTheToken() throws Exception {

        // a token still claiming PRINCIPAL (e.g. issued before a demotion to OWNER)
        String staleToken = jwtUtil.generateToken(storeOwnerAdmin.getId().toString(), "ADMIN",
                storeOwnerAdmin.getTenant().getId().toString(), "PRINCIPAL");

        mvc.perform(patch("/users/" + staff.getId() + "/scope")
                        .header("Authorization", "Bearer " + staleToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"PRINCIPAL\"}"))
                .andExpect(status().isForbidden());

        assertNull(scopeOf(staff));
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.ResultActions grant(User by, User to, String scope) throws Exception {

        return mvc.perform(as(by, patch("/users/" + to.getId() + "/scope")).content("{\"scope\":\"" + scope + "\"}"));
    }

    private Scope scopeOf(User u) {

        return userRepository.findById(u.getId()).orElseThrow().getScope();
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
