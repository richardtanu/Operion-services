package com.example.operion.module.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * BE-12 (OPERION_BE_CHANGE_QUEUE.md): registration, scope grants and tenant
 * endpoints are ADMIN-only and tenant-tree-contained, and denials answer 403
 * rather than 400.
 *
 * Runs through the real HTTP stack (MockMvc → JWT filter → method security →
 * service → GlobalExceptionHandler) with real signed JWTs, because every
 * piece of this change lives in a different layer and a service-level test
 * would skip the filter and @PreAuthorize entirely.
 *
 * Fixtures are a real three-node hierarchy — HQ → Outlet, plus an unrelated
 * root — never one flat tenant. With a single tenant, "checks containment"
 * and "checks nothing" pass identically (CLAUDE.md rule 6). The Outlet admin
 * reaching *up* into HQ is the case that proves ancestors are excluded.
 * {@code @Transactional} rolls everything back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthHardeningTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    private Tenant hq;
    private Tenant outlet;
    private Tenant unrelated;

    private User hqAdmin;
    private User hqOperator;
    private User outletAdmin;

    @BeforeEach
    void seedFixtures() {

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        hq = tenantRepository.save(Tenant.builder()
                .code("BE12_HQ_" + suffix).name("BE-12 HQ").active(true).build());
        outlet = tenantRepository.save(Tenant.builder()
                .code("BE12_OUT_" + suffix).name("BE-12 Outlet").active(true).parent(hq).build());
        unrelated = tenantRepository.save(Tenant.builder()
                .code("BE12_OTHER_" + suffix).name("BE-12 Other").active(true).build());

        hqAdmin = user("be12-hq-admin-" + suffix, Role.ADMIN, null, hq);
        hqOperator = user("be12-hq-op-" + suffix, Role.OPERATOR, null, hq);
        outletAdmin = user("be12-out-admin-" + suffix, Role.ADMIN, null, outlet);
    }

    // ---- registration ----

    @Test
    void register_withoutToken_isRejected() throws Exception {

        // BE-37: no token answers 401 (Spring's default entry point gave 403)
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("anon-" + UUID.randomUUID() + "@test.com", null, true)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_asOperator_isForbidden() throws Exception {

        mvc.perform(as(hqOperator, post("/auth/register"))
                .content(registerBody("x-" + UUID.randomUUID() + "@test.com", null, false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_asAdmin_ignoresScopeInBody_andDefaultsToAdminsTenant() throws Exception {

        String email = "new-" + UUID.randomUUID() + "@test.com";

        mvc.perform(as(hqAdmin, post("/auth/register"))
                .content(registerBody(email, null, true)))
                .andExpect(status().isOk());

        User created = userRepository.findByEmail(email).orElseThrow();
        assertNull(created.getScope(), "scope in the request body must be ignored");
        assertEquals(Role.OPERATOR, created.getRole());
        assertEquals(hq.getId(), created.getTenant().getId());
    }

    @Test
    void register_asAdmin_intoDescendantTenant_isAllowed() throws Exception {

        String email = "child-" + UUID.randomUUID() + "@test.com";

        mvc.perform(as(hqAdmin, post("/auth/register"))
                .content(registerBody(email, outlet.getCode(), false)))
                .andExpect(status().isOk());

        assertEquals(outlet.getId(), userRepository.findByEmail(email).orElseThrow().getTenant().getId());
    }

    @Test
    void register_asOutletAdmin_intoParentHq_isForbidden() throws Exception {

        mvc.perform(as(outletAdmin, post("/auth/register"))
                .content(registerBody("up-" + UUID.randomUUID() + "@test.com", hq.getCode(), false)))
                .andExpect(status().isForbidden());
    }

    // ---- scope grants / 403 mapping ----

    @Test
    void updateScope_asOperator_is403Not400() throws Exception {

        // @PreAuthorize denial path — was 400 before the AccessDeniedException handler
        mvc.perform(as(hqOperator, patch("/users/" + hqOperator.getId() + "/scope"))
                .content("{\"scope\":\"PRINCIPAL\"}"))
                .andExpect(status().isForbidden());

        assertNull(userRepository.findById(hqOperator.getId()).orElseThrow().getScope());
    }

    @Test
    void updateScope_asOutletAdmin_onHqUser_isForbidden() throws Exception {

        // manual tenant-tree check path, upward direction
        mvc.perform(as(outletAdmin, patch("/users/" + hqOperator.getId() + "/scope"))
                .content("{\"scope\":\"OWNER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateScope_asHqAdmin_onOwnTenantUser_isAllowed() throws Exception {

        // BE-31: granting needs a scope at least as high as the one granted
        User hqOwnerAdmin = user("be12-hq-owner-admin-" + UUID.randomUUID(), Role.ADMIN, Scope.OWNER, hq);

        mvc.perform(as(hqOwnerAdmin, patch("/users/" + hqOperator.getId() + "/scope"))
                .content("{\"scope\":\"OWNER\"}"))
                .andExpect(status().isOk());

        assertEquals(Scope.OWNER, userRepository.findById(hqOperator.getId()).orElseThrow().getScope());
    }

    @Test
    void manualOwnerScopeGate_is403Not400() throws Exception {

        // StockAdjustmentService.approve() checks scope before looking the
        // id up, so a random id is enough to reach requireOwnerScope()
        mvc.perform(as(hqOperator, patch("/stock-adjustments/" + UUID.randomUUID() + "/approve")))
                .andExpect(status().isForbidden());
    }

    // ---- tenants ----

    @Test
    void createTenant_asOperator_isForbidden() throws Exception {

        mvc.perform(as(hqOperator, post("/tenants"))
                .content(tenantBody(hq.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRootTenant_asAdminWithoutPrincipal_isForbidden() throws Exception {

        mvc.perform(as(hqAdmin, post("/tenants"))
                .content(tenantBody(null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRootTenant_asPrincipalAdmin_isAllowed() throws Exception {

        User principal = user("be12-principal-" + UUID.randomUUID(), Role.ADMIN, Scope.PRINCIPAL, hq);

        mvc.perform(as(principal, post("/tenants"))
                .content(tenantBody(null)))
                .andExpect(status().isOk());
    }

    @Test
    void createChildTenant_underOwnTree_isAllowed() throws Exception {

        mvc.perform(as(hqAdmin, post("/tenants"))
                .content(tenantBody(outlet.getId())))
                .andExpect(status().isOk());
    }

    @Test
    void createChildTenant_underUnrelatedRoot_isForbidden() throws Exception {

        mvc.perform(as(hqAdmin, post("/tenants"))
                .content(tenantBody(unrelated.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTenant_descendant_isAllowed() throws Exception {

        mvc.perform(as(hqAdmin, get("/tenants/" + outlet.getId())))
                .andExpect(status().isOk());
    }

    @Test
    void getTenant_ancestor_isForbidden() throws Exception {

        mvc.perform(as(outletAdmin, get("/tenants/" + hq.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTenant_unrelated_isForbidden_evenIfIdDoesNotExist() throws Exception {

        mvc.perform(as(hqAdmin, get("/tenants/" + unrelated.getId())))
                .andExpect(status().isForbidden());
        mvc.perform(as(hqAdmin, get("/tenants/" + UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTenantChildren_ofUnrelated_isForbidden() throws Exception {

        mvc.perform(as(hqAdmin, get("/tenants/" + unrelated.getId() + "/children")))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private User user(String emailPrefix, Role role, Scope scope, Tenant tenant) {

        User u = new User();
        u.setEmail(emailPrefix + "@test.com");
        u.setPasswordHash("not-a-real-hash");
        u.setFullName(emailPrefix);
        u.setRole(role);
        u.setScope(scope);
        u.setTenant(tenant);
        return userRepository.save(u);
    }

    private MockHttpServletRequestBuilder as(User u, MockHttpServletRequestBuilder req) {

        String token = jwtUtil.generateToken(
                u.getId().toString(),
                u.getRole().name(),
                u.getTenant().getId().toString(),
                u.getScope() != null ? u.getScope().name() : null);

        return req.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static String registerBody(String email, String tenantCode, boolean includePrincipalScope) {

        return "{\"email\":\"" + email + "\",\"password\":\"Start-123\",\"fullName\":\"BE-12 Test\""
                + (tenantCode != null ? ",\"tenantCode\":\"" + tenantCode + "\"" : "")
                + (includePrincipalScope ? ",\"scope\":\"PRINCIPAL\"" : "")
                + "}";
    }

    private static String tenantBody(UUID parentId) {

        String code = "BE12_NEW_" + UUID.randomUUID().toString().substring(0, 8);

        return "{\"name\":\"BE-12 New\",\"code\":\"" + code + "\""
                + (parentId != null ? ",\"parentId\":\"" + parentId + "\"" : "")
                + "}";
    }
}
