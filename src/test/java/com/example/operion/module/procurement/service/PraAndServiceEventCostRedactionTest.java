package com.example.operion.module.procurement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.common.security.ScopeContext;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.enums.UnitStatus;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.procurement.dto.PurchaseRequestAuthorizationItemResponse;
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
import com.example.operion.module.serviceevent.dto.ServiceEventResponse;
import com.example.operion.module.serviceevent.entity.ServiceEvent;
import com.example.operion.module.serviceevent.enums.ServiceEventType;
import com.example.operion.module.serviceevent.repository.ServiceEventRepository;
import com.example.operion.module.serviceevent.service.ServiceEventService;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

/**
 * The two money fields BE-11's follow-up grep found unredacted:
 *
 * - PRA items: below OWNER the buyer keeps {@code maxValue} (their limit) and
 *   every quantity, but loses {@code purchasedValue} (summed from Realisasi
 *   spend that BE-06 hides) and {@code remainingValue} (limit − remaining
 *   would give the spend straight back).
 * - ServiceEvent {@code cost}: redacted below OWNER like every other cost.
 *
 * Fixture spend: 2 paket × conversionFactor 6 at Rp 300,000 per paket, against
 * a 12-unit / Rp 900,000 line. Factor 6 not 1 (CLAUDE.md rule 2), so spend
 * (600,000), limit (900,000) and remaining (300,000) are all different, and
 * the stock-unit quantity (12) differs from the purchase quantity (2).
 */
@SpringBootTest
@Transactional
class PraAndServiceEventCostRedactionTest {

    @Autowired private PurchaseRequestAuthorizationService praService;
    @Autowired private ServiceEventService serviceEventService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private PartCategoryRepository categoryRepository;
    @Autowired private PartRepository partRepository;
    @Autowired private PurchaseRequestAuthorizationRepository praRepository;
    @Autowired private PurchaseRequestAuthorizationItemRepository praItemRepository;
    @Autowired private RealisasiRepository realisasiRepository;
    @Autowired private RealisasiItemRepository realisasiItemRepository;
    @Autowired private AirsoftUnitRepository unitRepository;
    @Autowired private ServiceEventRepository serviceEventRepository;

    private UUID praId;
    private UUID serviceEventId;

    @BeforeEach
    void seedFixtures() {

        String s = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .code("COSTRED_" + s).name("Cost redaction " + s).active(true).build());

        PartCategory category = categoryRepository.save(PartCategory.builder()
                .tenant(tenant).name("Gas").consumable(true).build());

        Part part = partRepository.save(Part.builder()
                .tenant(tenant)
                .name("Green gas can")
                .category(category)
                .currentStock(0)
                .minimumStock(6)
                .reorderQuantity(12)
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
                .authorizedQty(12)
                .maxValue(new BigDecimal("900000"))
                .build());

        Realisasi realisasi = realisasiRepository.save(Realisasi.builder()
                .tenant(tenant)
                .purchaseRequestAuthorization(pra)
                .externalOrderRef("COSTRED-" + s)
                .status(RealisasiStatus.APPROVED)
                .build());

        realisasiItemRepository.save(RealisasiItem.builder()
                .realisasi(realisasi)
                .purchaseRequestAuthorizationItem(praItem)
                .part(part)
                .purchaseUom("paket")
                .conversionFactor(new BigDecimal("6"))
                .purchasedQty(2)
                .actualUnitPrice(new BigDecimal("300000"))
                .build());

        AirsoftUnit unit = unitRepository.save(AirsoftUnit.builder()
                .tenant(tenant)
                .serialNumber("COSTRED-" + s)
                .name("Test rifle")
                .status(UnitStatus.ACTIVE)
                .build());

        serviceEventId = serviceEventRepository.save(ServiceEvent.builder()
                .tenant(tenant)
                .airsoftUnit(unit)
                .eventType(ServiceEventType.REPAIR)
                .cost(new BigDecimal("175000"))
                .serviceDate(LocalDate.of(2026, 10, 14))
                .build()).getId();

        praId = pra.getId();

        TenantContext.setTenantId(tenant.getId().toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        ScopeContext.clear();
    }

    @Test
    void belowOwner_praShowsLimitAndQuantities_butNotSpend() {

        for (Scope scope : new Scope[] { null, Scope.SUPERVISOR, Scope.MANAGER }) {

            ScopeContext.setScope(scope != null ? scope.name() : null);

            PurchaseRequestAuthorizationItemResponse item = praService.getById(praId).getItems().get(0);

            assertEquals(0, new BigDecimal("900000").compareTo(item.getMaxValue()), scope + " keeps the limit");
            assertEquals(12, item.getAuthorizedQty());
            assertEquals(12, item.getPurchasedQty(), "2 paket x 6 = 12 stock units");
            assertEquals(0, item.getRemainingQty());
            assertNull(item.getPurchasedValue(), scope + " must not see purchasedValue");
            assertNull(item.getRemainingValue(), scope + " must not see remainingValue");
        }
    }

    @Test
    void ownerAndAbove_praShowsSpendAndRemaining() {

        for (Scope scope : new Scope[] { Scope.OWNER, Scope.PRINCIPAL }) {

            ScopeContext.setScope(scope.name());

            PurchaseRequestAuthorizationItemResponse item = praService.getById(praId).getItems().get(0);

            assertEquals(0, new BigDecimal("600000").compareTo(item.getPurchasedValue()), "2 x 300,000");
            assertEquals(0, new BigDecimal("300000").compareTo(item.getRemainingValue()), "900,000 - 600,000");
        }
    }

    @Test
    void serviceEventCost_redactedBelowOwner() {

        ScopeContext.setScope(Scope.MANAGER.name());
        assertNull(serviceEvent().getCost());
        assertEquals("REPAIR", serviceEvent().getEventType(), "rest of the response intact");

        ScopeContext.setScope(null);
        assertNull(serviceEvent().getCost());

        ScopeContext.setScope(Scope.OWNER.name());
        assertEquals(0, new BigDecimal("175000").compareTo(serviceEvent().getCost()));
    }

    private ServiceEventResponse serviceEvent() {

        return serviceEventService.getAll().stream()
                .filter(e -> e.getId().equals(serviceEventId))
                .findFirst()
                .orElseThrow();
    }
}
