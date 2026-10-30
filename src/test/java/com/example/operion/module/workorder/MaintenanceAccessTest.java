package com.example.operion.module.workorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import com.example.operion.module.part.entity.PartType;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.part.repository.PartTypeRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-37: maintenance (fitting and replacing parts, part condition, work
 * orders, service events, schedules) needs MANAGER scope or above, or the
 * TECHNICIAN role. Operators and Accounting are refused, and nothing moves.
 * A request with no token answers 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MaintenanceAccessTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AirsoftUnitRepository unitRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartTypeRepository partTypeRepository;
    @Autowired private PartRepository partRepository;

    private User sm;          // Store Manager
    private User tech;        // TECHNICIAN role, no scope
    private User operator;    // OPERATOR, no scope
    private User accounting;  // ACCOUNTING role, no scope
    private AirsoftUnit unit;
    private Part spring;
    private Part spare;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant store = tenantRepository.save(Tenant.builder().code("BE37_ST_" + s).name("Store").active(true).build());

        sm = user("be37-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);
        tech = user("be37-tech-" + s, Role.TECHNICIAN, null, store);
        operator = user("be37-op-" + s, Role.OPERATOR, null, store);
        accounting = user("be37-acc-" + s, Role.ACCOUNTING, null, store);

        unit = unitRepository.save(AirsoftUnit.builder().tenant(store).serialNumber("BE37-" + s).name("M4 #" + s)
                .status(UnitStatus.ACTIVE).healthScore(100).build());

        PartCategory internals = categoryRepository.save(PartCategory.builder().tenant(store).name("Internal").consumable(false).build());
        PartType springType = partTypeRepository.save(PartType.builder().tenant(store).category(internals).name("Spring").build());
        spring = partRepository.save(Part.builder().tenant(store).name("M120 spring " + s).category(internals)
                .partType(springType).currentStock(5).build());
        spare = partRepository.save(Part.builder().tenant(store).name("M110 spring " + s).category(internals)
                .partType(springType).currentStock(5).build());
    }

    @Test
    void operatorsAndAccounting_areRefusedEveryMaintenanceAction_andNothingMoves() throws Exception {

        // a part already fitted, for the condition and replace attempts
        String fitted = JsonPath.read(install(tech, spring).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.airsoftUnitPartId");
        assertEquals(4, stock(spring));

        for (User refused : List.of(operator, accounting)) {
            install(refused, spare)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Maintenance needs a Store Manager or above, or a Technician"));
            mvc.perform(as(refused, post("/unit-parts/" + fitted + "/replace")).content("{\"newPartId\":\"" + spare.getId()
                    + "\",\"removedPartCondition\":\"WORN\"}")).andExpect(status().isForbidden());
            mvc.perform(as(refused, patch("/unit-parts/" + fitted + "/condition")).content("{\"condition\":\"FAILED\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(as(refused, post("/work-orders")).content(workOrder())).andExpect(status().isForbidden());
            mvc.perform(as(refused, post("/service-events")).content("{\"airsoftUnitId\":\"" + unit.getId()
                    + "\",\"eventType\":\"MAINTENANCE\",\"issue\":\"Weak shots\"}")).andExpect(status().isForbidden());
            mvc.perform(as(refused, post("/maintenance/create")).content("{\"airsoftUnitId\":\"" + unit.getId()
                    + "\",\"title\":\"Monthly clean\",\"intervalDays\":30}")).andExpect(status().isForbidden());
            mvc.perform(as(refused, post("/maintenance/evaluate/" + unit.getId()))).andExpect(status().isForbidden());
            mvc.perform(as(refused, post("/maintenance/process-due"))).andExpect(status().isForbidden());
        }

        assertEquals(4, stock(spring), "nothing fitted or replaced");
        assertEquals(5, stock(spare));
        mvc.perform(as(sm, get("/work-orders/unit/" + unit.getId()))).andExpect(jsonPath("$.data.length()").value(0));

        // the Store Manager and the Technician can; the Manager runs a work order through
        String workOrderId = JsonPath.read(mvc.perform(as(tech, post("/work-orders")).content(workOrder()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");
        mvc.perform(as(sm, patch("/work-orders/" + workOrderId + "/assign")).content("{\"technicianId\":\"" + tech.getId() + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(as(operator, patch("/work-orders/" + workOrderId + "/start"))).andExpect(status().isForbidden());
        mvc.perform(as(sm, patch("/work-orders/" + workOrderId + "/start")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        mvc.perform(as(operator, patch("/work-orders/" + workOrderId + "/complete"))).andExpect(status().isForbidden());
        mvc.perform(as(sm, patch("/work-orders/" + workOrderId + "/complete"))).andExpect(status().isOk());
    }

    @Test
    void noToken_answers401_notAllowed_stays403() throws Exception {

        mvc.perform(get("/parts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Sign in first"));
        mvc.perform(post("/unit-parts/install").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        install(operator, spare).andExpect(status().isForbidden());   // signed in, not allowed
    }

    // ---- helpers ----

    private ResultActions install(User by, Part part) throws Exception {

        return mvc.perform(as(by, post("/unit-parts/install")).content("{\"airsoftUnitId\":\"" + unit.getId()
                + "\",\"partId\":\"" + part.getId() + "\",\"installedDate\":\"" + LocalDate.now() + "\"}"));
    }

    private String workOrder() {

        return "{\"airsoftUnitId\":\"" + unit.getId() + "\",\"title\":\"Gearbox noise\",\"priority\":\"MEDIUM\"}";
    }

    private int stock(Part part) {

        return partRepository.findById(part.getId()).orElseThrow().getCurrentStock();
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
