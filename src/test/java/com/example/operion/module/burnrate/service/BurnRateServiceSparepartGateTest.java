package com.example.operion.module.burnrate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.burnrate.dto.BurnRateResponse;
import com.example.operion.module.burnrate.enums.BurnRateSource;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.repository.PartCategoryRepository;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.partinstance.entity.PartInstance;
import com.example.operion.module.partinstance.enums.PartInstanceStatus;
import com.example.operion.module.partinstance.repository.PartInstanceRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

/**
 * BE-03 (OPERION_BE_CHANGE_QUEUE.md): runtime-verify the 10 Aug isConsumable()
 * gate against real PartInstance rows, not just a clean compile (CLAUDE.md
 * rule 7 — a compile proves nothing about a Lombok/Spring-style runtime bug,
 * and the same caution applies to a category check that could silently not
 * fire).
 *
 * Fixtures are created and torn down inside the same test transaction
 * ({@code @Transactional} rolls the whole test back — nothing is left in the
 * dev DB), against the first tenant found rather than a hardcoded id, so this
 * is safe to re-run.
 *
 * {@code @Transactional} also stands in for the request-scoped Hibernate
 * session that Spring Boot's default open-in-view provides on the real
 * {@code GET /burn-rate} call. Without it, {@code Part.isConsumable()}'s lazy
 * {@code category} association throws LazyInitializationException when
 * called outside a session — confirmed by first running this without the
 * annotation, before BE-04's refactor even landed. Not a production bug
 * (open-in-view is on, unmodified from the Spring Boot default — checked
 * application.properties), but it means BurnRateService cannot safely be
 * called from a non-web context (batch job, another service) without its own
 * session.
 *
 * Written against the pre-BE-04 string check; still valid after BE-04
 * (OPERION_BE_CHANGE_QUEUE.md) replaced {@code isConsumable()}'s body with a
 * read of {@code PartCategory.consumable} — the seeded fixture categories
 * keep the name "Consumable" AND the flag true, so this test doesn't
 * distinguish the two implementations. {@link com.example.operion.module.part.entity.PartTest}
 * covers that distinction directly.
 */
@SpringBootTest
@Transactional
class BurnRateServiceSparepartGateTest {

    @Autowired
    private BurnRateService burnRateService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PartCategoryRepository partCategoryRepository;

    @Autowired
    private PartRepository partRepository;

    @Autowired
    private PartInstanceRepository partInstanceRepository;

    private UUID tenantId;

    private UUID sparepartThresholdId;

    private UUID sparepartTripleId;

    private UUID consumableId;

    @BeforeEach
    void seedFixtures() {

        Tenant tenant = tenantRepository.findAll().stream()
                .filter(t -> partCategoryRepository.findByTenantIdAndName(t.getId(), "Consumable").isPresent())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tenant with a 'Consumable' category found"));
        tenantId = tenant.getId();

        PartCategory consumableCategory = partCategoryRepository
                .findByTenantIdAndName(tenantId, "Consumable")
                .orElseThrow();

        PartCategory sparepartCategory = partCategoryRepository.findByTenantId(tenantId).stream()
                .filter(c -> !c.getName().equals("Consumable"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No non-Consumable category found for tenant"));

        sparepartThresholdId = seedPart(tenant, sparepartCategory, "BE-03 IT Sparepart Threshold", 21, 10);
        sparepartTripleId = seedPart(tenant, sparepartCategory, "BE-03 IT Sparepart Triple", 25, 30);
        consumableId = seedPart(tenant, consumableCategory, "BE-03 IT Consumable Regression", 25, 15);

        TenantContext.setTenantId(tenantId.toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void sparepartAtExactThresholdNeverReturnsComputed() {

        BurnRateResponse response = burnRateService.getByPart(sparepartThresholdId);

        assertNotEquals(BurnRateSource.COMPUTED, response.getBurnRateSource(),
                "sparepart at exactly minEvents/minObservationDays must not compute a rate");
    }

    @Test
    void sparepartAtTripleThresholdNeverReturnsComputed() {

        BurnRateResponse response = burnRateService.getByPart(sparepartTripleId);

        assertNotEquals(BurnRateSource.COMPUTED, response.getBurnRateSource(),
                "sparepart at triple the threshold must not compute a rate either");
    }

    @Test
    void consumableAtSameThresholdShapeStillReturnsComputed() {

        BurnRateResponse response = burnRateService.getByPart(consumableId);

        assertEquals(BurnRateSource.COMPUTED, response.getBurnRateSource(),
                "regression check: the isConsumable() gate must not block Consumable parts");
        assertEquals(25, response.getObservationDays());
        assertEquals(15, response.getEventCount());
    }

    private UUID seedPart(Tenant tenant, PartCategory category, String name, int firstSeenDaysAgo, int takeCount) {

        Part part = partRepository.save(Part.builder()
                .tenant(tenant)
                .name(name)
                .category(category)
                .currentStock(50)
                .minimumStock(5)
                .reorderQuantity(10)
                .active(true)
                .retired(false)
                .build());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime firstSeen = now.minusDays(firstSeenDaysAgo);
        long totalMinutes = Duration.between(firstSeen, now).toMinutes();

        for (int i = 0; i < takeCount; i++) {

            double frac = takeCount == 1 ? 0.0 : (double) i / (takeCount - 1);
            LocalDateTime takenAt = firstSeen.plusMinutes((long) (frac * totalMinutes));

            partInstanceRepository.save(PartInstance.builder()
                    .tenant(tenant)
                    .part(part)
                    .barcode("BE03IT-" + part.getId() + "-" + i)
                    .status(PartInstanceStatus.TAKEN)
                    .takenAt(takenAt)
                    .build());
        }

        return part.getId();
    }
}
