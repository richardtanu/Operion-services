package com.example.operion.module.maintenance;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.enums.UnitStatus;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

/**
 * BE-38: a store's settings. A store that never saved any reads the defaults
 * (and its units can be evaluated: before, "Maintenance Policy not
 * configured."); its own Owner changes them, within range; HQ reads, can't
 * change; burn rate follows the store's target days.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoreSettingsTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AirsoftUnitRepository unitRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartRepository partRepository;

    private Tenant store;
    private User owner;
    private User sm;
    private User hqPrincipal;
    private User siblingOwner;
    private AirsoftUnit unit;
    private Part gas;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenantRepository.save(Tenant.builder().code("BE38_HQ_" + s).name("HQ").active(true).build());
        store = tenantRepository.save(Tenant.builder().code("BE38_ST_" + s).name("Store " + s).active(true).parent(hq).build());
        Tenant sibling = tenantRepository.save(Tenant.builder().code("BE38_SB_" + s).name("Sibling").active(true).parent(hq).build());

        owner = user("be38-own-" + s, Role.OPERATOR, Scope.OWNER, store);
        sm = user("be38-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);
        hqPrincipal = user("be38-hq-" + s, Role.ADMIN, Scope.PRINCIPAL, hq);
        siblingOwner = user("be38-sib-" + s, Role.OPERATOR, Scope.OWNER, sibling);

        unit = unitRepository.save(AirsoftUnit.builder().tenant(store).serialNumber("BE38-" + s).name("AK #" + s)
                .status(UnitStatus.ACTIVE).healthScore(100).build());

        PartCategory gasCategory = categoryRepository.save(PartCategory.builder().tenant(hq).name("Gas").consumable(true).build());
        gas = partRepository.save(Part.builder().tenant(store).name("Green gas " + s).category(gasCategory)
                .currentStock(30).manualDailyUsage(new BigDecimal("2")).build());
    }

    @Test
    void aStoreWithNoSettings_readsTheDefaults_andItsOwnerSetsThem() throws Exception {

        settings(sm)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customised").value(false))
                .andExpect(jsonPath("$.data.daysOfCoverTarget").value(14))
                .andExpect(jsonPath("$.data.burnRateWindowDays").value(30))
                .andExpect(jsonPath("$.data.burnRateMinObservationDays").value(21))
                .andExpect(jsonPath("$.data.burnRateMinEvents").value(10))
                .andExpect(jsonPath("$.data.healthThreshold").value(50))
                .andExpect(jsonPath("$.data.minimumHealthForOperation").value(60))
                .andExpect(jsonPath("$.data.maintenanceIntervalDays").value(90))
                .andExpect(jsonPath("$.data.partEndOfLifeWarningDays").value(30))
                .andExpect(jsonPath("$.data.maxWornParts").value(2))
                .andExpect(jsonPath("$.data.autoCreateWorkOrder").value(true));

        save(owner, "{\"daysOfCoverTarget\":20,\"maintenanceIntervalDays\":45}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customised").value(true))
                .andExpect(jsonPath("$.data.daysOfCoverTarget").value(20))
                .andExpect(jsonPath("$.data.maintenanceIntervalDays").value(45))
                .andExpect(jsonPath("$.data.burnRateWindowDays").value(30));

        settings(sm).andExpect(jsonPath("$.data.daysOfCoverTarget").value(20));

        // burn rate follows the store's own target (30 cans at 2 a day = 15 days: low against 20)
        mvc.perform(as(sm, get("/burn-rate/" + gas.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetDays").value(20));
    }

    @Test
    void onlyTheStoresOwnerChangesThem_hqReads_andValuesStayInRange() throws Exception {

        save(sm, "{\"daysOfCoverTarget\":20}").andExpect(status().isForbidden());
        save(hqPrincipal, "{\"daysOfCoverTarget\":20}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only the store's own Owner can change its settings"));

        settings(hqPrincipal).andExpect(status().isOk());
        settings(siblingOwner).andExpect(status().isForbidden());

        save(owner, "{\"daysOfCoverTarget\":0}").andExpect(status().isBadRequest());
        save(owner, "{\"burnRateWindowDays\":14,\"burnRateMinObservationDays\":21}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Minimum observation can't be longer than the burn-rate window"));

        settings(owner).andExpect(jsonPath("$.data.customised").value(false));
    }

    @Test
    void aUnitInAStoreWithNoSettings_canBeEvaluated() throws Exception {

        mvc.perform(as(sm, post("/maintenance/evaluate/" + unit.getId())))
                .andExpect(status().isOk());
    }

    // ---- helpers ----

    private ResultActions settings(User by) throws Exception {

        return mvc.perform(as(by, get("/tenants/" + store.getId() + "/settings")));
    }

    private ResultActions save(User by, String body) throws Exception {

        return mvc.perform(as(by, patch("/tenants/" + store.getId() + "/settings")).content(body));
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
