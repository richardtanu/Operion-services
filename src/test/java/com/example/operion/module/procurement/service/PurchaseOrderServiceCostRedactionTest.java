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
import com.example.operion.module.procurement.dto.PurchaseOrderResponse;
import com.example.operion.module.procurement.entity.PurchaseOrder;
import com.example.operion.module.procurement.entity.PurchaseOrderItem;
import com.example.operion.module.procurement.repository.PurchaseOrderItemRepository;
import com.example.operion.module.procurement.repository.PurchaseOrderRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

/**
 * BE-11 (OPERION_BE_CHANGE_QUEUE.md): PurchaseOrder cost fields —
 * header {@code totalAmount} and per-item {@code price} — are redacted (null)
 * below OWNER scope, same rule as BE-06. Exercises the real service against a
 * real datasource, matching {@link RealisasiServiceCostRedactionTest}.
 *
 * Quantity is 6, not 1 (CLAUDE.md rule 6): with quantity 1 the item price and
 * the header total are the same number, so a test could pass while checking
 * the wrong field.
 */
@SpringBootTest
@Transactional
class PurchaseOrderServiceCostRedactionTest {

    @Autowired private PurchaseOrderService purchaseOrderService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private PartCategoryRepository partCategoryRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private PurchaseOrderItemRepository purchaseOrderItemRepository;

    private UUID purchaseOrderId;

    @BeforeEach
    void seedFixtures() {

        Tenant tenant = tenantRepository.findAll().stream()
                .filter(t -> !partCategoryRepository.findByTenantId(t.getId()).isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tenant with a category found"));

        PartCategory category = partCategoryRepository.findByTenantId(tenant.getId()).get(0);

        Part part = partRepository.save(Part.builder()
                .tenant(tenant)
                .name("BE-11 IT Cost Redaction Part")
                .category(category)
                .currentStock(10)
                .minimumStock(1)
                .reorderQuantity(5)
                .active(true)
                .retired(false)
                .build());

        PurchaseOrder po = purchaseOrderRepository.save(PurchaseOrder.builder()
                .tenant(tenant)
                .poNumber("BE11IT-" + UUID.randomUUID())
                .totalAmount(new BigDecimal("600000"))
                .build());

        purchaseOrderItemRepository.save(PurchaseOrderItem.builder()
                .purchaseOrder(po)
                .part(part)
                .quantity(6)
                .price(new BigDecimal("100000"))
                .build());

        purchaseOrderId = po.getId();

        TenantContext.setTenantId(tenant.getId().toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        ScopeContext.clear();
    }

    @Test
    void noScopeSeesNoCostFields() {
        assertCostFieldsRedacted(null);
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

    @Test
    void listIsRedactedToo() {

        ScopeContext.setScope(Scope.MANAGER.name());

        PurchaseOrderResponse fromList = purchaseOrderService.getAll().stream()
                .filter(r -> r.getId().equals(purchaseOrderId))
                .findFirst()
                .orElseThrow();

        assertNull(fromList.getTotalAmount());
        assertNull(fromList.getItems().get(0).getPrice());
    }

    private void assertCostFieldsRedacted(Scope scope) {

        ScopeContext.setScope(scope != null ? scope.name() : null);

        PurchaseOrderResponse response = purchaseOrderService.getById(purchaseOrderId);

        assertNull(response.getTotalAmount(), scope + " must not see totalAmount");
        assertNull(response.getItems().get(0).getPrice(), scope + " must not see item price");

        // Redaction must not collapse the rest of the response.
        assertEquals(6, response.getItems().get(0).getQuantity());
        assertEquals("DRAFT", response.getStatus());
    }

    private void assertCostFieldsVisible(Scope scope) {

        ScopeContext.setScope(scope.name());

        PurchaseOrderResponse response = purchaseOrderService.getById(purchaseOrderId);

        assertEquals(0, new BigDecimal("600000").compareTo(response.getTotalAmount()));
        assertEquals(0, new BigDecimal("100000").compareTo(response.getItems().get(0).getPrice()));
    }
}
