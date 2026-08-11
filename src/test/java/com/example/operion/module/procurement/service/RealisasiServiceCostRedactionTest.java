package com.example.operion.module.procurement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.common.security.ScopeContext;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.procurement.dto.RealisasiResponse;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorizationItem;
import com.example.operion.module.procurement.entity.Realisasi;
import com.example.operion.module.procurement.entity.RealisasiItem;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationItemRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationRepository;
import com.example.operion.module.procurement.repository.RealisasiItemRepository;
import com.example.operion.module.procurement.repository.RealisasiRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

/**
 * BE-06 (OPERION_BE_CHANGE_QUEUE.md): cost fields on {@link RealisasiResponse}
 * must be redacted (null), not just hidden by a client, below OWNER scope —
 * the exposure BE-10.1 confirmed live between real tenants (Franchise HQ /
 * Outlet 2). A clean compile doesn't prove the redaction actually fires
 * (CLAUDE.md rule 7's reasoning), so this exercises the real
 * {@link RealisasiService#getById(UUID)} path against a real datasource with
 * {@link ScopeContext} set to each tier.
 *
 * Fixtures are built directly via repositories, bypassing the full
 * PR-\>PRA-\>Realisasi creation chain — {@link PurchaseRequestAuthorization}
 * only requires a tenant at the entity level, so this test doesn't need a
 * real PurchaseRequest to get a valid PRA to hang a Realisasi off of.
 * {@code @Transactional} rolls everything back; nothing persists.
 */
@SpringBootTest
@Transactional
class RealisasiServiceCostRedactionTest {

    @Autowired
    private RealisasiService realisasiService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PartCategoryRepository partCategoryRepository;

    @Autowired
    private PartRepository partRepository;

    @Autowired
    private PurchaseRequestAuthorizationRepository praRepository;

    @Autowired
    private PurchaseRequestAuthorizationItemRepository praItemRepository;

    @Autowired
    private RealisasiRepository realisasiRepository;

    @Autowired
    private RealisasiItemRepository realisasiItemRepository;

    private UUID realisasiId;

    @BeforeEach
    void seedFixtures() {

        Tenant tenant = tenantRepository.findAll().stream()
                .filter(t -> !partCategoryRepository.findByTenantId(t.getId()).isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tenant with a category found"));

        PartCategory category = partCategoryRepository.findByTenantId(tenant.getId()).get(0);

        Part part = partRepository.save(Part.builder()
                .tenant(tenant)
                .name("BE-06 IT Cost Redaction Part")
                .category(category)
                .currentStock(10)
                .minimumStock(1)
                .reorderQuantity(5)
                .active(true)
                .retired(false)
                .build());

        PurchaseRequestAuthorization pra = praRepository.save(PurchaseRequestAuthorization.builder()
                .tenant(tenant)
                .status(PurchaseRequestAuthorizationStatus.ACTIVE)
                .build());

        PurchaseRequestAuthorizationItem praItem = praItemRepository.save(PurchaseRequestAuthorizationItem.builder()
                .purchaseRequestAuthorization(pra)
                .part(part)
                .authorizedQty(1)
                .maxValue(new BigDecimal("200000"))
                .build());

        Realisasi realisasi = realisasiRepository.save(Realisasi.builder()
                .tenant(tenant)
                .purchaseRequestAuthorization(pra)
                .externalOrderRef("BE06IT-" + UUID.randomUUID())
                .status(RealisasiStatus.APPROVED)
                .subtotal(new BigDecimal("100000"))
                .sellerDiscount(new BigDecimal("5000"))
                .platformVoucher(new BigDecimal("2000"))
                .shipping(new BigDecimal("15000"))
                .insurance(new BigDecimal("1000"))
                .serviceFee(new BigDecimal("500"))
                .totalCost(new BigDecimal("109500"))
                .build());

        realisasiItemRepository.save(RealisasiItem.builder()
                .realisasi(realisasi)
                .purchaseRequestAuthorizationItem(praItem)
                .part(part)
                .purchaseUom("paket")
                .conversionFactor(new BigDecimal("6"))
                .purchasedQty(1)
                .actualUnitPrice(new BigDecimal("100000"))
                .allocatedLandedCost(new BigDecimal("109500"))
                .build());

        realisasiId = realisasi.getId();

        TenantContext.setTenantId(tenant.getId().toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        ScopeContext.clear();
    }

    @Test
    void supervisorSeesNoCostFields() {
        assertCostFieldsRedacted(Scope.SUPERVISOR);
    }

    @Test
    void managerSeesNoCostFields() {
        assertCostFieldsRedacted(Scope.MANAGER);
    }

    @Test
    void ownerSeesCostFields() {
        assertCostFieldsVisible(Scope.OWNER);
    }

    @Test
    void principalSeesCostFields() {
        assertCostFieldsVisible(Scope.PRINCIPAL);
    }

    private void assertCostFieldsRedacted(Scope scope) {

        ScopeContext.setScope(scope.name());

        RealisasiResponse response = realisasiService.getById(realisasiId);

        assertNull(response.getSubtotal(), scope + " must not see subtotal");
        assertNull(response.getSellerDiscount(), scope + " must not see sellerDiscount");
        assertNull(response.getPlatformVoucher(), scope + " must not see platformVoucher");
        assertNull(response.getShipping(), scope + " must not see shipping");
        assertNull(response.getInsurance(), scope + " must not see insurance");
        assertNull(response.getServiceFee(), scope + " must not see serviceFee");
        assertNull(response.getTotalCost(), scope + " must not see totalCost");
        assertNull(response.getItems().get(0).getActualUnitPrice(), scope + " must not see actualUnitPrice");
        assertNull(response.getItems().get(0).getAllocatedLandedCost(), scope + " must not see allocatedLandedCost");

        // Redaction must not collapse the rest of the response.
        assertEquals("APPROVED", response.getStatus());
        assertEquals(1, response.getItems().get(0).getPurchasedQty());
    }

    private void assertCostFieldsVisible(Scope scope) {

        ScopeContext.setScope(scope.name());

        RealisasiResponse response = realisasiService.getById(realisasiId);

        assertEquals(0, new BigDecimal("100000").compareTo(response.getSubtotal()));
        assertEquals(0, new BigDecimal("109500").compareTo(response.getTotalCost()));
        assertEquals(0, new BigDecimal("100000").compareTo(response.getItems().get(0).getActualUnitPrice()));
        assertEquals(0, new BigDecimal("109500").compareTo(response.getItems().get(0).getAllocatedLandedCost()));
    }
}
