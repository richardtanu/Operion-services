package com.example.operion.module.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.notification.entity.Notification;
import com.example.operion.module.notification.enums.NotificationType;
import com.example.operion.module.notification.repository.NotificationRepository;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.procurement.entity.PurchaseOrderStatusHistory;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;
import com.example.operion.module.procurement.entity.Realisasi;
import com.example.operion.module.procurement.entity.RealisasiItem;
import com.example.operion.module.procurement.entity.Supplier;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.repository.PurchaseOrderStatusHistoryRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationRepository;
import com.example.operion.module.procurement.repository.RealisasiItemRepository;
import com.example.operion.module.procurement.repository.RealisasiRepository;
import com.example.operion.module.procurement.repository.SupplierRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.upload.entity.Upload;
import com.example.operion.module.upload.enums.UploadPurpose;
import com.example.operion.module.upload.repository.UploadRepository;
import com.example.operion.module.upload.service.R2Storage;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-20: who drafts, sends and cancels an order to HQ, and who receives goods.
 * Fixtures: HQ → Store plus an unrelated root. Realisasi lines use
 * conversionFactor 6 (rule 2): 2 pakets bought = 12 cans in stock. The
 * GOODS_SELF_RECEIVED insert also proves migration V7 (rule 3). Object
 * storage is mocked; uploads are rows that "exist" in storage.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrdersAndReceivingTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private PurchaseOrderStatusHistoryRepository poHistoryRepository;
    @Autowired private PurchaseRequestAuthorizationRepository praRepository;
    @Autowired private RealisasiRepository realisasiRepository;
    @Autowired private RealisasiItemRepository realisasiItemRepository;
    @Autowired private UploadRepository uploadRepository;
    @Autowired private NotificationRepository notificationRepository;

    @MockitoBean private R2Storage storage;

    private Tenant store;
    private User sm;            // Store Manager (MANAGER)
    private User sm2;           // a colleague MANAGER
    private User owner;         // store Owner
    private User procurement;   // PROCUREMENT role, no scope
    private User operator;
    private User technician;
    private User accounting;
    private User otherTenantUser;
    private Part gas;
    private Supplier supplier;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant hq = tenantRepository.save(Tenant.builder().code("BE20_HQ_" + s).name("HQ").active(true).build());
        store = tenantRepository.save(Tenant.builder().code("BE20_ST_" + s).name("Store").active(true).parent(hq).build());
        Tenant other = tenantRepository.save(Tenant.builder().code("BE20_OT_" + s).name("Other").active(true).build());

        sm = user("be20-sm-" + s, Role.OPERATOR, Scope.MANAGER, store);
        sm2 = user("be20-sm2-" + s, Role.OPERATOR, Scope.MANAGER, store);
        owner = user("be20-own-" + s, Role.OPERATOR, Scope.OWNER, store);
        procurement = user("be20-proc-" + s, Role.PROCUREMENT, null, store);
        operator = user("be20-op-" + s, Role.OPERATOR, null, store);
        technician = user("be20-tech-" + s, Role.TECHNICIAN, null, store);
        accounting = user("be20-acc-" + s, Role.ACCOUNTING, Scope.SUPERVISOR, store);
        otherTenantUser = user("be20-other-" + s, Role.OPERATOR, Scope.MANAGER, other);

        // BE-21: orders to HQ need an item HQ sells and HQ's franchise-centre supplier
        PartCategory hqCategory = categoryRepository.save(PartCategory.builder()
                .tenant(hq).name("Gas").consumable(true).build());
        Part hqGas = partRepository.save(Part.builder()
                .tenant(hq).name("Green gas " + s).category(hqCategory)
                .soldByHq(true).hqPrice(new java.math.BigDecimal("38500")).active(true).retired(false).build());

        PartCategory category = categoryRepository.save(PartCategory.builder()
                .tenant(store).name("Gas").consumable(true).build());
        gas = partRepository.save(Part.builder()
                .tenant(store).name("Green gas " + s).category(category).catalogPart(hqGas)
                .currentStock(5).minimumStock(6).reorderQuantity(12)
                .active(true).retired(false).build());

        supplier = supplierRepository.save(Supplier.builder().tenant(hq).name("HQ centre").franchiseCentre(true).build());

        when(storage.exists(anyString())).thenReturn(true);
    }

    // ---- orders to HQ: draft, send, cancel ----

    @Test
    void draft_managerOrProcurementOnly() throws Exception {

        for (User refused : List.of(operator, technician, accounting)) {
            mvc.perform(as(refused, post("/purchase-orders")).content(poBody()))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(as(procurement, post("/purchase-orders")).content(poBody()))
                .andExpect(status().isOk());

        mvc.perform(as(sm, post("/purchase-orders")).content(poBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.createdById").value(sm.getId().toString()))
                .andExpect(jsonPath("$.data.createdByName").value(sm.getFullName()));
    }

    @Test
    void poNumbers_doNotCollide_withinTheSameMillisecond() throws Exception {

        String a = JsonPath.read(mvc.perform(as(sm, post("/purchase-orders")).content(poBody()))
                .andReturn().getResponse().getContentAsString(), "$.data.poNumber");
        String b = JsonPath.read(mvc.perform(as(sm, post("/purchase-orders")).content(poBody()))
                .andReturn().getResponse().getContentAsString(), "$.data.poNumber");

        assertTrue(!a.equals(b), a + " vs " + b);
    }

    @Test
    void send_isTheOwnersAct() throws Exception {

        String po = draft(sm);

        mvc.perform(as(sm, patch("/purchase-orders/" + po + "/send")))
                .andExpect(status().isForbidden());

        mvc.perform(as(owner, patch("/purchase-orders/" + po + "/send")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ORDERED"));
    }

    @Test
    void cancelDraft_creatorOrOwner() throws Exception {

        String po = draft(sm);

        mvc.perform(as(sm2, patch("/purchase-orders/" + po + "/cancel")))
                .andExpect(status().isForbidden());

        mvc.perform(as(sm, patch("/purchase-orders/" + po + "/cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        String another = draft(sm);
        mvc.perform(as(owner, patch("/purchase-orders/" + another + "/cancel")))
                .andExpect(status().isOk());
    }

    @Test
    void cancelSent_ownerOnly_withAReason() throws Exception {

        String po = draft(sm);
        mvc.perform(as(owner, patch("/purchase-orders/" + po + "/send"))).andExpect(status().isOk());

        // even the creator can't drop it once money is committed
        mvc.perform(as(sm, patch("/purchase-orders/" + po + "/cancel")).content("{\"reason\":\"No longer needed\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(as(owner, patch("/purchase-orders/" + po + "/cancel")))
                .andExpect(status().isBadRequest());
        mvc.perform(as(owner, patch("/purchase-orders/" + po + "/cancel")).content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(as(owner, patch("/purchase-orders/" + po + "/cancel")).content("{\"reason\":\"HQ out of stock\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        List<PurchaseOrderStatusHistory> history = poHistoryRepository.findAll().stream()
                .filter(h -> h.getPurchaseOrder().getId().toString().equals(po))
                .toList();
        assertTrue(history.stream().anyMatch(h -> "HQ out of stock".equals(h.getReason())
                && h.getChangedBy().getId().equals(owner.getId())), "reason and actor on history");
    }

    // ---- receiving ----

    @Test
    void receivedBy_mustBeTheCaller() throws Exception {

        String po = sentOrder();

        mvc.perform(as(sm, post("/goods-receipts")).content(poReceiptBody(po, otherTenantUser.getId(), 3)))
                .andExpect(status().isBadRequest());
        mvc.perform(as(sm, post("/goods-receipts")).content(poReceiptBody(po, sm2.getId(), 3)))
                .andExpect(status().isBadRequest());

        assertEquals(5, partRepository.findById(gas.getId()).orElseThrow().getCurrentStock(), "no stock moved");

        // omitted → the caller; the PO path has no "receiver ≠ buyer" rule
        mvc.perform(as(sm, post("/goods-receipts")).content(poReceiptBody(po, null, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receivedById").value(sm.getId().toString()))
                .andExpect(jsonPath("$.data.selfReceived").value(false));

        assertEquals(8, partRepository.findById(gas.getId()).orElseThrow().getCurrentStock());
    }

    @Test
    void buyerReceivesOwnPurchase_needsAPhoto() throws Exception {

        RealisasiItem line = approvedPurchase(sm);

        mvc.perform(as(sm, post("/goods-receipts")).content(realisasiReceiptBody(line, 12, List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("You bought this: add a photo of the goods to receive it"));

        assertEquals(5, partRepository.findById(gas.getId()).orElseThrow().getCurrentStock(), "no stock moved");
        assertTrue(notificationRepository.findByRecipientUserId(owner.getId()).isEmpty());
    }

    @Test
    void buyerReceivesOwnPurchase_withAPhoto_isFlaggedAndTheOwnerTold() throws Exception {

        RealisasiItem line = approvedPurchase(sm);
        Upload photo = photo(sm);

        String body = mvc.perform(as(sm, post("/goods-receipts"))
                .content(realisasiReceiptBody(line, 12, List.of(photo.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selfReceived").value(true))
                .andExpect(jsonPath("$.data.goodsPhotoUploadIds[0]").value(photo.getId().toString()))
                .andReturn().getResponse().getContentAsString();

        // 2 pakets × 6 = 12 cans on top of 5
        assertEquals(17, partRepository.findById(gas.getId()).orElseThrow().getCurrentStock());

        Upload attached = uploadRepository.findById(photo.getId()).orElseThrow();
        assertEquals("GOODS_RECEIPT", attached.getAttachedType());
        assertEquals(JsonPath.read(body, "$.data.id"), attached.getAttachedId().toString());

        List<Notification> told = notificationRepository.findByRecipientUserId(owner.getId());
        assertEquals(1, told.size());
        assertEquals(NotificationType.GOODS_SELF_RECEIVED, told.get(0).getType());
        assertTrue(told.get(0).getMessage().startsWith(sm.getFullName() + " received their own purchase"),
                told.get(0).getMessage());
        assertTrue(notificationRepository.findByRecipientUserId(sm.getId()).isEmpty(), "not told about own act");

        // the list filter
        mvc.perform(as(owner, get("/goods-receipts").param("selfReceived", "true")))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value((String) JsonPath.read(body, "$.data.id")));
    }

    @Test
    void someoneElseReceives_notFlagged_noNotification() throws Exception {

        RealisasiItem line = approvedPurchase(sm);

        mvc.perform(as(sm2, post("/goods-receipts")).content(realisasiReceiptBody(line, 12, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selfReceived").value(false));

        assertEquals(17, partRepository.findById(gas.getId()).orElseThrow().getCurrentStock());
        assertTrue(notificationRepository.findByRecipientUserId(owner.getId()).isEmpty());

        mvc.perform(as(owner, get("/goods-receipts").param("selfReceived", "true")))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void receivingMoreThanBought_inStockUnits_isRefused() throws Exception {

        RealisasiItem line = approvedPurchase(sm);

        // 13 cans > 2 pakets × 6
        mvc.perform(as(sm2, post("/goods-receipts")).content(realisasiReceiptBody(line, 13, List.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPhotoSomeoneElseUploaded_isRefused() throws Exception {

        RealisasiItem line = approvedPurchase(sm);
        Upload colleaguesPhoto = photo(sm2);

        mvc.perform(as(sm, post("/goods-receipts"))
                .content(realisasiReceiptBody(line, 12, List.of(colleaguesPhoto.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Upload not found"));

        assertEquals(5, partRepository.findById(gas.getId()).orElseThrow().getCurrentStock(), "rolled back");
    }

    // ---- helpers ----

    private String poBody() {

        return "{\"supplierId\":\"" + supplier.getId() + "\",\"items\":[{\"partId\":\"" + gas.getId()
                + "\",\"quantity\":6,\"price\":38500}]}";
    }

    private String draft(User by) throws Exception {

        return JsonPath.read(mvc.perform(as(by, post("/purchase-orders")).content(poBody()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    private String sentOrder() throws Exception {

        String po = draft(sm);
        mvc.perform(as(owner, patch("/purchase-orders/" + po + "/send"))).andExpect(status().isOk());
        return po;
    }

    private String poReceiptBody(String po, UUID receivedBy, int qty) {

        return "{\"purchaseOrderId\":\"" + po + "\""
                + (receivedBy != null ? ",\"receivedBy\":\"" + receivedBy + "\"" : "")
                + ",\"receivedDate\":\"2026-10-22T10:15:00\""
                + ",\"items\":[{\"partId\":\"" + gas.getId() + "\",\"quantity\":" + qty + "}]}";
    }

    private String realisasiReceiptBody(RealisasiItem line, int qty, List<UUID> photos) {

        String ids = photos.isEmpty() ? "" : "\"" + String.join("\",\"", photos.stream().map(UUID::toString).toList()) + "\"";

        return "{\"realisasiId\":\"" + line.getRealisasi().getId() + "\""
                + ",\"receivedDate\":\"2026-10-22T10:15:00\""
                + ",\"goodsPhotoUploadIds\":[" + ids + "]"
                + ",\"items\":[{\"partId\":\"" + gas.getId() + "\",\"realisasiItemId\":\"" + line.getId()
                + "\",\"quantity\":" + qty + "}]}";
    }

    /** An APPROVED purchase of 2 pakets (× 6 cans) of gas, logged by {@code buyer}. */
    private RealisasiItem approvedPurchase(User buyer) {

        PurchaseRequestAuthorization pra = praRepository.save(PurchaseRequestAuthorization.builder()
                .tenant(store).approvedBy(owner).status(PurchaseRequestAuthorizationStatus.ACTIVE).build());

        Realisasi realisasi = realisasiRepository.save(Realisasi.builder()
                .tenant(store)
                .purchaseRequestAuthorization(pra)
                .createdBy(buyer)
                .externalOrderRef("BE20-" + UUID.randomUUID().toString().substring(0, 8))
                .status(RealisasiStatus.APPROVED)
                .attachments("https://shop.example/receipt/1")
                .subtotal(new BigDecimal("231000"))
                .totalCost(new BigDecimal("246500"))
                .build());

        return realisasiItemRepository.save(RealisasiItem.builder()
                .realisasi(realisasi)
                .part(gas)
                .purchaseUom("paket")
                .conversionFactor(new BigDecimal("6"))
                .purchasedQty(2)
                .actualUnitPrice(new BigDecimal("115500"))
                .allocatedLandedCost(new BigDecimal("246500"))
                .build());
    }

    private Upload photo(User uploader) {

        UUID id = UUID.randomUUID();
        return uploadRepository.save(Upload.builder()
                .id(id)
                .tenant(store)
                .uploadedBy(uploader)
                .purpose(UploadPurpose.GOODS_RECEIPT_PHOTO)
                .objectKey(store.getId() + "/goods_receipt_photo/" + id)
                .contentType("image/jpeg")
                .sizeBytes(420_000L)
                .build());
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
