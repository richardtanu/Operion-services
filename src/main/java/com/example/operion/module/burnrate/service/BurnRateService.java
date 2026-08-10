package com.example.operion.module.burnrate.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.burnrate.dto.BurnRateResponse;
import com.example.operion.module.burnrate.enums.BurnRateSource;
import com.example.operion.module.maintenance.entity.MaintenancePolicy;
import com.example.operion.module.maintenance.repository.MaintenancePolicyRepository;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.partinstance.repository.PartInstanceRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import lombok.RequiredArgsConstructor;

/**
 * operion-burn-rate-spec.md. Computed on read, nothing stored — a stored
 * derived value drifts silently from its inputs (spec §5), same reasoning
 * as the PRA ceiling's "no remaining column" rule.
 */
@Service
@RequiredArgsConstructor
public class BurnRateService {

    private static final int DEFAULT_WINDOW_DAYS = 30;

    private static final int DEFAULT_MIN_OBSERVATION_DAYS = 21;

    private static final int DEFAULT_MIN_EVENTS = 10;

    private static final int DEFAULT_TARGET_DAYS = 14;

    private static final String CONSUMABLE_CATEGORY_NAME = "Consumable";

    private final PartRepository partRepository;

    private final PartInstanceRepository partInstanceRepository;

    private final MaintenancePolicyRepository maintenancePolicyRepository;

    private final TenantHierarchyService tenantHierarchyService;

    public List<BurnRateResponse> getAll() {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return computeAll(tenantId, tenantIds);
    }

    public BurnRateResponse getByPart(UUID partId) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return computeAll(tenantId, tenantIds).stream()
                .filter(r -> r.getPartId().equals(partId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Part not found"));
    }

    /**
     * One grouped query for takes, one for first-seen — not a per-part loop
     * (spec §6.1: the PR creation screen requests the whole catalog at once).
     */
    private List<BurnRateResponse> computeAll(UUID tenantId, List<UUID> tenantIds) {

        LocalDateTime now = LocalDateTime.now();

        MaintenancePolicy policy = maintenancePolicyRepository.findByTenantId(tenantId).orElse(null);

        int windowDays = intOrDefault(policy == null ? null : policy.getBurnRateWindowDays(), DEFAULT_WINDOW_DAYS);
        int minObservationDays = intOrDefault(
                policy == null ? null : policy.getBurnRateMinObservationDays(), DEFAULT_MIN_OBSERVATION_DAYS);
        int minEvents = intOrDefault(policy == null ? null : policy.getBurnRateMinEvents(), DEFAULT_MIN_EVENTS);
        int targetDays = intOrDefault(policy == null ? null : policy.getDaysOfCoverTarget(), DEFAULT_TARGET_DAYS);

        LocalDateTime windowStart = now.minusDays(windowDays);

        Map<UUID, Long> takesByPart = new HashMap<>();
        for (Object[] row : partInstanceRepository.countTakesGroupedByPart(tenantIds, windowStart)) {
            takesByPart.put((UUID) row[0], (Long) row[1]);
        }

        Map<UUID, LocalDateTime> firstSeenByPart = new HashMap<>();
        for (Object[] row : partInstanceRepository.findFirstTakenAtGroupedByPart(tenantIds)) {
            firstSeenByPart.put((UUID) row[0], (LocalDateTime) row[1]);
        }

        return partRepository.findByTenantIdInAndActiveTrue(tenantIds).stream()
                .map(part -> compute(
                        part, takesByPart, firstSeenByPart, now, windowDays, minObservationDays, minEvents,
                        targetDays))
                .toList();
    }

    private BurnRateResponse compute(
            Part part,
            Map<UUID, Long> takesByPart,
            Map<UUID, LocalDateTime> firstSeenByPart,
            LocalDateTime now,
            int windowDays,
            int minObservationDays,
            int minEvents,
            int targetDays) {

        long takes = takesByPart.getOrDefault(part.getId(), 0L);
        LocalDateTime firstSeen = firstSeenByPart.get(part.getId());

        // §2.1: clamped both ways — never beyond the window, never below
        // actual history. This is the calculation "the clamp test" (spec §8
        // case 2) exists to catch: skipping the lower clamp silently
        // understates the rate on anything added to the catalog mid-life.
        Integer observationDays = null;
        if (firstSeen != null) {
            long daysSinceFirstSeen = ChronoUnit.DAYS.between(firstSeen.toLocalDate(), now.toLocalDate());
            observationDays = (int) Math.max(1, Math.min(windowDays, daysSinceFirstSeen));
        }

        BurnRateSource source;
        BigDecimal burnRate = null;
        Integer eventCount = null;
        Integer reportedObservationDays = null;

        // spec §3: spareparts never get a computed rate at all, even if take()
        // volume happens to clear the thresholds — installed-on-failure items
        // stay on the manual path permanently (MANUAL_RATE/MANUAL_LEVEL/NONE).
        if (isConsumable(part) && observationDays != null && observationDays >= minObservationDays
                && takes >= minEvents) {

            int clampedObservationDays = observationDays;

            source = BurnRateSource.COMPUTED;
            burnRate = BigDecimal.valueOf(takes)
                    .divide(BigDecimal.valueOf(clampedObservationDays), 4, RoundingMode.HALF_UP);
            eventCount = (int) takes;
            reportedObservationDays = clampedObservationDays;

        } else if (part.getManualDailyUsage() != null) {

            // Permanent for items like spareparts with a rate but not enough
            // (or any) take history — not a cold-start fallback (spec §3).
            source = BurnRateSource.MANUAL_RATE;
            burnRate = part.getManualDailyUsage();
            eventCount = (int) takes;
            reportedObservationDays = observationDays;

        } else if (part.getManualReorderPoint() != null) {

            source = BurnRateSource.MANUAL_LEVEL;

        } else {

            // Must stay null, never 0 — 0 renders as maximum urgency and
            // would light up most of a fresh catalog (spec §3.1).
            source = BurnRateSource.NONE;
        }

        BigDecimal daysOfCover = null;
        if (burnRate != null && burnRate.compareTo(BigDecimal.ZERO) > 0) {
            daysOfCover = BigDecimal.valueOf(part.getCurrentStock()).divide(burnRate, 4, RoundingMode.HALF_UP);
        }

        Integer suggestedQty;
        Integer responseTargetDays = null;
        Integer manualReorderPoint = null;
        Boolean belowLevel = null;

        if (source == BurnRateSource.COMPUTED || source == BurnRateSource.MANUAL_RATE) {

            responseTargetDays = targetDays;
            suggestedQty = suggestedTopUpQty(part, targetDays, burnRate);

        } else if (source == BurnRateSource.MANUAL_LEVEL) {

            manualReorderPoint = part.getManualReorderPoint();
            belowLevel = part.getCurrentStock() <= manualReorderPoint;
            suggestedQty = belowLevel
                    ? (part.getReorderQuantity() != null
                            ? part.getReorderQuantity()
                            : Math.max(0, manualReorderPoint - part.getCurrentStock()))
                    : 0;

        } else {

            suggestedQty = 0;
        }

        return BurnRateResponse.builder()
                .partId(part.getId())
                .partName(part.getName())
                .currentStock(part.getCurrentStock())
                .burnRateSource(source)
                .burnRate(burnRate)
                .observationDays(reportedObservationDays)
                .eventCount(eventCount)
                .daysOfCover(daysOfCover)
                .targetDays(responseTargetDays)
                .suggestedQty(suggestedQty)
                .manualReorderPoint(manualReorderPoint)
                .belowLevel(belowLevel)
                .build();
    }

    /**
     * spec §2's top-up formula, max(0, ceil(target*rate - stock)) — but
     * overridden by Part.reorderQuantity when a reorder is actually being
     * suggested (spec §7.3: without this, a bulk-buy item like gas suggests
     * "13 cans" instead of the real ~100-can buying pattern). reorderQuantity
     * already exists on every Part (default 10, previously unused anywhere)
     * — reused here rather than adding a new column.
     */
    private Integer suggestedTopUpQty(Part part, int targetDays, BigDecimal burnRate) {

        BigDecimal topUp = BigDecimal.valueOf(targetDays)
                .multiply(burnRate)
                .subtract(BigDecimal.valueOf(part.getCurrentStock()));

        int topUpQty = Math.max(0, topUp.setScale(0, RoundingMode.CEILING).intValue());

        if (topUpQty > 0 && part.getReorderQuantity() != null) {
            return part.getReorderQuantity();
        }

        return topUpQty;
    }

    private int intOrDefault(Integer value, int defaultValue) {

        return value != null ? value : defaultValue;
    }

    private boolean isConsumable(Part part) {

        return part.getCategory() != null && CONSUMABLE_CATEGORY_NAME.equals(part.getCategory().getName());
    }
}
