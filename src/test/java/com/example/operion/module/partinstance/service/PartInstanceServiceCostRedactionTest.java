package com.example.operion.module.partinstance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
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
import com.example.operion.module.partinstance.dto.PartInstanceResponse;
import com.example.operion.module.partinstance.entity.PartInstance;
import com.example.operion.module.partinstance.enums.PartInstanceStatus;
import com.example.operion.module.partinstance.repository.PartInstanceRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

/**
 * BE-06 (OPERION_BE_CHANGE_QUEUE.md): {@link PartInstanceResponse#getLandedCost()}
 * must be redacted (null) below OWNER scope, same rule and same live exposure
 * (BE-10.1) as {@code RealisasiServiceCostRedactionTest}. Exercises the real
 * {@link PartInstanceService#getByPart(UUID)} path against a real datasource.
 */
@SpringBootTest
@Transactional
class PartInstanceServiceCostRedactionTest {

    @Autowired
    private PartInstanceService partInstanceService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PartCategoryRepository partCategoryRepository;

    @Autowired
    private PartRepository partRepository;

    @Autowired
    private PartInstanceRepository partInstanceRepository;

    private UUID partId;

    @BeforeEach
    void seedFixtures() {

        Tenant tenant = tenantRepository.findAll().stream()
                .filter(t -> !partCategoryRepository.findByTenantId(t.getId()).isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tenant with a category found"));

        PartCategory category = partCategoryRepository.findByTenantId(tenant.getId()).get(0);

        Part part = partRepository.save(Part.builder()
                .tenant(tenant)
                .name("BE-06 IT PartInstance Cost Redaction")
                .category(category)
                .currentStock(10)
                .minimumStock(1)
                .reorderQuantity(5)
                .active(true)
                .retired(false)
                .build());

        partInstanceRepository.save(PartInstance.builder()
                .tenant(tenant)
                .part(part)
                .barcode("BE06IT-" + UUID.randomUUID())
                .status(PartInstanceStatus.IN_STOCK)
                .landedCost(new BigDecimal("23700.00"))
                .build());

        partId = part.getId();

        TenantContext.setTenantId(tenant.getId().toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        ScopeContext.clear();
    }

    @Test
    void supervisorSeesNoLandedCost() {
        assertLandedCostRedacted(Scope.SUPERVISOR);
    }

    @Test
    void managerSeesNoLandedCost() {
        assertLandedCostRedacted(Scope.MANAGER);
    }

    @Test
    void ownerSeesLandedCost() {

        ScopeContext.setScope(Scope.OWNER.name());

        List<PartInstanceResponse> responses = partInstanceService.getByPart(partId);

        assertEquals(0, new BigDecimal("23700.00").compareTo(responses.get(0).getLandedCost()));
    }

    @Test
    void principalSeesLandedCost() {

        ScopeContext.setScope(Scope.PRINCIPAL.name());

        List<PartInstanceResponse> responses = partInstanceService.getByPart(partId);

        assertEquals(0, new BigDecimal("23700.00").compareTo(responses.get(0).getLandedCost()));
    }

    private void assertLandedCostRedacted(Scope scope) {

        ScopeContext.setScope(scope.name());

        List<PartInstanceResponse> responses = partInstanceService.getByPart(partId);

        assertNull(responses.get(0).getLandedCost(), scope + " must not see landedCost");
        // Redaction must not collapse the rest of the response.
        assertEquals("IN_STOCK", responses.get(0).getStatus());
    }
}
