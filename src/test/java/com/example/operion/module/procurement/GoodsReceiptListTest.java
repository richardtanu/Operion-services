package com.example.operion.module.procurement;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.procurement.entity.GoodsReceipt;
import com.example.operion.module.procurement.entity.PurchaseOrder;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;
import com.example.operion.module.procurement.entity.Realisasi;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.repository.GoodsReceiptRepository;
import com.example.operion.module.procurement.repository.PurchaseOrderRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationRepository;
import com.example.operion.module.procurement.repository.RealisasiRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

/**
 * BE-08: GET /goods-receipts (list + filters) and GET /goods-receipts/{id}.
 *
 * Fixtures are a real HQ → Outlet tree plus an unrelated root, with receipts
 * on both purchase paths, so tree scoping and the path filter each have
 * something to exclude. Receipts are written directly via repositories
 * (no stock side effects needed to test a read endpoint).
 *
 * Receipt timeline (newest first):
 *   rOutletPoLate   Outlet, PO,        outletUser, 2026-10-10 23:30  ← tests inclusive `to`
 *   rOutletReal     Outlet, Realisasi, outletUser, 2026-10-05 15:00
 *   rOther          Other root, PO,    otherUser,  2026-10-03 09:00  ← never visible
 *   rHqPo           HQ, PO,            hqUser,     2026-10-01 10:00
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GoodsReceiptListTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private PurchaseRequestAuthorizationRepository praRepository;
    @Autowired private RealisasiRepository realisasiRepository;
    @Autowired private GoodsReceiptRepository receiptRepository;

    private User hqUser;
    private User outletUser;

    private String rHqPo;
    private String rOutletReal;
    private String rOutletPoLate;
    private String rOther;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenant("BE08_HQ_" + s, null);
        Tenant outlet = tenant("BE08_OUT_" + s, hq);
        Tenant other = tenant("BE08_OTHER_" + s, null);

        hqUser = user("be08-hq-" + s, hq);
        outletUser = user("be08-out-" + s, outlet);
        User otherUser = user("be08-other-" + s, other);

        PurchaseRequestAuthorization pra = praRepository.save(PurchaseRequestAuthorization.builder()
                .tenant(outlet)
                .status(PurchaseRequestAuthorizationStatus.ACTIVE)
                .build());

        Realisasi realisasi = realisasiRepository.save(Realisasi.builder()
                .tenant(outlet)
                .purchaseRequestAuthorization(pra)
                .externalOrderRef("BE08-" + s)
                .status(RealisasiStatus.APPROVED)
                .build());

        rHqPo = receipt(hq, po(hq), null, hqUser, LocalDateTime.of(2026, 10, 1, 10, 0));
        rOutletReal = receipt(outlet, null, realisasi, outletUser, LocalDateTime.of(2026, 10, 5, 15, 0));
        rOutletPoLate = receipt(outlet, po(outlet), null, outletUser, LocalDateTime.of(2026, 10, 10, 23, 30));
        rOther = receipt(other, po(other), null, otherUser, LocalDateTime.of(2026, 10, 3, 9, 0));
    }

    @Test
    void list_asHq_coversWholeTree_newestFirst_excludesOtherRoot() throws Exception {

        mvc.perform(as(hqUser, get("/goods-receipts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id").value(contains(rOutletPoLate, rOutletReal, rHqPo)));
    }

    @Test
    void list_asOutlet_doesNotSeeParentHq() throws Exception {

        mvc.perform(as(outletUser, get("/goods-receipts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id").value(contains(rOutletPoLate, rOutletReal)));
    }

    @Test
    void list_pathFilter() throws Exception {

        mvc.perform(as(hqUser, get("/goods-receipts").param("path", "REALISASI")))
                .andExpect(jsonPath("$.data[*].id").value(contains(rOutletReal)));

        mvc.perform(as(hqUser, get("/goods-receipts").param("path", "PO")))
                .andExpect(jsonPath("$.data[*].id").value(contains(rOutletPoLate, rHqPo)));
    }

    @Test
    void list_receivedByFilter() throws Exception {

        mvc.perform(as(hqUser, get("/goods-receipts").param("receivedBy", outletUser.getId().toString())))
                .andExpect(jsonPath("$.data[*].id").value(contains(rOutletPoLate, rOutletReal)));
    }

    @Test
    void list_dateRange_isInclusiveOnBothEnds() throws Exception {

        // 2026-10-10 23:30 must be inside to=2026-10-10; 2026-10-05 15:00 inside from=2026-10-05
        mvc.perform(as(hqUser, get("/goods-receipts").param("from", "2026-10-05").param("to", "2026-10-10")))
                .andExpect(jsonPath("$.data[*].id").value(contains(rOutletPoLate, rOutletReal)));

        mvc.perform(as(hqUser, get("/goods-receipts").param("from", "2026-10-06")))
                .andExpect(jsonPath("$.data[*].id").value(contains(rOutletPoLate)));

        mvc.perform(as(hqUser, get("/goods-receipts").param("to", "2026-09-30")))
                .andExpect(jsonPath("$.data").value(empty()));
    }

    @Test
    void list_invalidPath_is400() throws Exception {

        mvc.perform(as(hqUser, get("/goods-receipts").param("path", "BOGUS")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_inOwnTree_returnsReceipt() throws Exception {

        mvc.perform(as(hqUser, get("/goods-receipts/" + rOutletReal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(rOutletReal))
                .andExpect(jsonPath("$.data.realisasiId").isNotEmpty())
                .andExpect(jsonPath("$.data.purchaseOrderId").isEmpty());
    }

    @Test
    void getById_outsideTree_answersLikeMissing() throws Exception {

        mvc.perform(as(hqUser, get("/goods-receipts/" + rOther)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Goods receipt not found"));

        // upward: an outlet can't read its parent HQ's receipt
        mvc.perform(as(outletUser, get("/goods-receipts/" + rHqPo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Goods receipt not found"));
    }

    // ---- helpers ----

    private Tenant tenant(String code, Tenant parent) {

        return tenantRepository.save(Tenant.builder().code(code).name(code).active(true).parent(parent).build());
    }

    private User user(String prefix, Tenant tenant) {

        User u = new User();
        u.setEmail(prefix + "@test.com");
        u.setPasswordHash("not-a-real-hash");
        u.setFullName(prefix);
        u.setRole(Role.OPERATOR);
        u.setTenant(tenant);
        return userRepository.save(u);
    }

    private PurchaseOrder po(Tenant tenant) {

        return purchaseOrderRepository.save(PurchaseOrder.builder()
                .tenant(tenant)
                .poNumber("BE08-PO-" + UUID.randomUUID())
                .build());
    }

    private String receipt(Tenant tenant, PurchaseOrder po, Realisasi realisasi, User receivedBy, LocalDateTime at) {

        return receiptRepository.save(GoodsReceipt.builder()
                .tenant(tenant)
                .purchaseOrder(po)
                .realisasi(realisasi)
                .receivedBy(receivedBy)
                .receivedDate(at)
                .build())
                .getId().toString();
    }

    private MockHttpServletRequestBuilder as(User u, MockHttpServletRequestBuilder req) {

        String token = jwtUtil.generateToken(
                u.getId().toString(), u.getRole().name(), u.getTenant().getId().toString(), null);

        return req.header("Authorization", "Bearer " + token);
    }
}
