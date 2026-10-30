package com.example.operion.module.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * BE-36: an Admin, or the store's Owner for their own staff, resets a
 * forgotten password; the person must change it at their next sign-in. A
 * reset or a change ends every session started before it. New users need 8
 * characters too, and change their first password.
 *
 * Tokens carry their issue time in whole seconds, so the tests that need an
 * "older" token wait a little over a second after minting it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetTest {

    private static final String SM_PASSWORD = "Sm-pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    private String s;
    private Tenant store;
    private User hqSuper;      // HQ, ADMIN + PRINCIPAL
    private User owner;        // the store's Owner, not an ADMIN
    private User coOwner;
    private User storeAdmin;
    private User sm;           // Store Manager, known password
    private User staff;
    private User siblingStaff;

    @BeforeEach
    void seedFixtures() {

        s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenantRepository.save(Tenant.builder().code("BE36_HQ_" + s).name("HQ").active(true).build());
        store = tenantRepository.save(Tenant.builder().code("BE36_ST_" + s).name("Store").active(true).parent(hq).build());
        Tenant sibling = tenantRepository.save(Tenant.builder().code("BE36_SB_" + s).name("Sibling").active(true).parent(hq).build());

        hqSuper = user("be36-super-" + s, Role.ADMIN, Scope.PRINCIPAL, hq);
        owner = user("be36-own-" + s, Role.OPERATOR, Scope.OWNER, store);
        coOwner = user("be36-coown-" + s, Role.OPERATOR, Scope.OWNER, store);
        storeAdmin = user("be36-adm-" + s, Role.ADMIN, null, store);
        sm = user("be36-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);
        sm.setPasswordHash(passwordEncoder.encode(SM_PASSWORD));
        userRepository.save(sm);
        staff = user("be36-staff-" + s, Role.OPERATOR, null, store);
        siblingStaff = user("be36-sib-" + s, Role.OPERATOR, null, sibling);
    }

    @Test
    void hqResetsAForgottenPassword_theTemporaryOneMustBeChanged_andOldSessionsEnd() throws Exception {

        String oldToken = token(sm);
        Thread.sleep(1100);

        reset(hqSuper, sm, "Temp-pass-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        login(sm, SM_PASSWORD).andExpect(status().isUnauthorized());
        String temp = JsonPath.read(login(sm, "Temp-pass-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString(), "$.data.token");

        me(oldToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Your password was changed; sign in again"));
        me(temp).andExpect(status().isOk()).andExpect(jsonPath("$.data.mustChangePassword").value(true));

        String own = JsonPath.read(mvc.perform(patch("/auth/password").header("Authorization", "Bearer " + temp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Temp-pass-1\",\"newPassword\":\"Own-pass-99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString(), "$.data.token");

        me(own).andExpect(status().isOk()).andExpect(jsonPath("$.data.mustChangePassword").value(false));
    }

    @Test
    void theStoresOwnerResetsTheirOwnStaff_only() throws Exception {

        reset(owner, staff, "Temp-pass-2").andExpect(status().isOk());

        reset(owner, coOwner, "Temp-pass-2")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only HQ manages Owners and Admins"));
        reset(owner, storeAdmin, "Temp-pass-2").andExpect(status().isForbidden());
        reset(owner, siblingStaff, "Temp-pass-2").andExpect(status().isForbidden());
        reset(owner, owner, "Temp-pass-2")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Use Change password for your own"));
        reset(sm, staff, "Temp-pass-2").andExpect(status().isForbidden());   // a Manager resets nobody
        reset(owner, staff, "short").andExpect(status().isBadRequest());

        login(coOwner, "Temp-pass-2").andExpect(status().isUnauthorized());   // untouched
    }

    @Test
    void changingYourPassword_endsYourOlderSessions_butNotTheNewOne() throws Exception {

        String oldToken = token(sm);
        Thread.sleep(1100);

        String newToken = JsonPath.read(mvc.perform(patch("/auth/password").header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + SM_PASSWORD + "\",\"newPassword\":\"New-pass-456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.token");

        me(oldToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Your password was changed; sign in again"));
        me(newToken).andExpect(status().isOk());
    }

    @Test
    void newUsers_need8Characters_andChangeTheirFirstPassword() throws Exception {

        register("be36-short-" + s, "123456")
                .andExpect(status().isBadRequest());

        register("be36-new-" + s, "Start-123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"be36-new-" + s + "@test.com\",\"password\":\"Start-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    // ---- helpers ----

    private ResultActions reset(User by, User who, String newPassword) throws Exception {

        return mvc.perform(patch("/users/" + who.getId() + "/password").header("Authorization", "Bearer " + token(by))
                .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"" + newPassword + "\"}"));
    }

    private ResultActions register(String prefix, String password) throws Exception {

        return mvc.perform(post("/auth/register").header("Authorization", "Bearer " + token(hqSuper))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + prefix + "@test.com\",\"password\":\"" + password + "\",\"fullName\":\"" + prefix
                        + "\",\"tenantCode\":\"" + store.getCode() + "\"}"));
    }

    private ResultActions login(User u, String password) throws Exception {

        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions me(String token) throws Exception {

        return mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token));
    }

    private String token(User u) {

        return jwtUtil.generateToken(u.getId().toString(), u.getRole().name(), u.getTenant().getId().toString(),
                u.getScope() != null ? u.getScope().name() : null);
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
}
