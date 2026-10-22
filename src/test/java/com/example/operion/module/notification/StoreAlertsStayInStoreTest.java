package com.example.operion.module.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.notification.entity.Notification;
import com.example.operion.module.notification.enums.NotificationType;
import com.example.operion.module.notification.repository.NotificationRepository;
import com.example.operion.module.notification.service.NotificationService;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;
import com.example.operion.module.procurement.entity.Realisasi;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationRepository;
import com.example.operion.module.procurement.repository.RealisasiRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

/**
 * BE-24: a store's approval alerts (new request, over-limit purchase,
 * self-received goods) go to that store's own Owner+ and ADMINs only. HQ's
 * Admin and Principal, who can't approve a store's records, aren't told.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoreAlertsStayInStoreTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private PurchaseRequestAuthorizationRepository praRepository;
    @Autowired private RealisasiRepository realisasiRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;

    private Tenant store;
    private User sm;
    private User storeOwner;
    private User hqAdmin;       // ADMIN, no scope
    private User hqPrincipal;   // ADMIN + PRINCIPAL, like the HQ super admin
    private Part part;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenantRepository.save(Tenant.builder().code("BE24_HQ_" + s).name("HQ").active(true).build());
        store = tenantRepository.save(Tenant.builder().code("BE24_ST_" + s).name("Store").active(true).parent(hq).build());

        sm = user("be24-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);
        storeOwner = user("be24-own-" + s, Role.OPERATOR, Scope.OWNER, store);
        hqAdmin = user("be24-hqadm-" + s, Role.ADMIN, null, hq);
        hqPrincipal = user("be24-hqpri-" + s, Role.ADMIN, Scope.PRINCIPAL, hq);

        PartCategory category = categoryRepository.save(PartCategory.builder()
                .tenant(store).name("Gas").consumable(true).build());
        part = partRepository.save(Part.builder().tenant(store).name("Green gas " + s).category(category).build());
    }

    @Test
    void newPurchaseRequest_storeOwnerOnly() throws Exception {

        String token = jwtUtil.generateToken(sm.getId().toString(), sm.getRole().name(),
                store.getId().toString(), sm.getScope().name());

        mvc.perform(post("/purchase-requests").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestedBy\":\"" + sm.getId() + "\",\"items\":[{\"partId\":\"" + part.getId()
                        + "\",\"quantity\":12}]}"))
                .andExpect(status().isOk());

        assertEquals(List.of(NotificationType.NEW_PURCHASE_REQUEST), typesFor(storeOwner));
        assertHqNotTold();
    }

    @Test
    void overLimitPurchase_andSelfReceived_storeOwnerOnly() {

        PurchaseRequestAuthorization pra = praRepository.save(PurchaseRequestAuthorization.builder()
                .tenant(store).approvedBy(storeOwner).status(PurchaseRequestAuthorizationStatus.ACTIVE).build());
        Realisasi realisasi = realisasiRepository.save(Realisasi.builder()
                .tenant(store).purchaseRequestAuthorization(pra).createdBy(sm)
                .externalOrderRef("BE24-" + UUID.randomUUID()).status(RealisasiStatus.PENDING_APPROVAL).build());

        notificationService.notifyRealisasiEscalated(realisasi);
        notificationService.notifyGoodsSelfReceived(realisasi, sm);

        assertEquals(List.of(NotificationType.REALISASI_ESCALATED, NotificationType.GOODS_SELF_RECEIVED),
                typesFor(storeOwner));
        assertTrue(typesFor(sm).isEmpty(), "the receiver isn't told about their own receipt");
        assertHqNotTold();
    }

    private void assertHqNotTold() {

        assertTrue(typesFor(hqAdmin).isEmpty(), "HQ ADMIN got " + typesFor(hqAdmin));
        assertTrue(typesFor(hqPrincipal).isEmpty(), "HQ PRINCIPAL got " + typesFor(hqPrincipal));
    }

    private List<NotificationType> typesFor(User u) {

        return notificationRepository.findByRecipientUserId(u.getId()).stream()
                .sorted(java.util.Comparator.comparing(Notification::getCreatedAt))
                .map(Notification::getType)
                .toList();
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
