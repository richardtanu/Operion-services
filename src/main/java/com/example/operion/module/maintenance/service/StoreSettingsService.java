package com.example.operion.module.maintenance.service;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.common.security.ScopeContext;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.burnrate.service.BurnRateService;
import com.example.operion.module.maintenance.dto.StoreSettingsRequest;
import com.example.operion.module.maintenance.dto.StoreSettingsResponse;
import com.example.operion.module.maintenance.entity.MaintenancePolicy;
import com.example.operion.module.maintenance.repository.MaintenancePolicyRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import lombok.RequiredArgsConstructor;

/**
 * BE-38: a store's settings ({@code maintenance_rule}): target days of cover,
 * how burn rate is measured, and when a unit needs maintenance. Read by
 * anyone at the store or above it; changed by the store's own Owner only,
 * like the budget (BE-16): HQ reads, can't set. A store that never saved any
 * reads the defaults; its first save creates the row.
 */
@Service
@RequiredArgsConstructor
public class StoreSettingsService {

    private final MaintenancePolicyRepository repository;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    public StoreSettingsResponse get(UUID tenantId) {

        tenantHierarchyService.requireWithinCallerTenantTree(tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Store not found"));

        return map(tenant, repository.findByTenantId(tenantId).orElse(null));
    }

    @Transactional
    public StoreSettingsResponse update(UUID tenantId, StoreSettingsRequest request) {

        tenantHierarchyService.requireWithinCallerTenantTree(tenantId);

        if (!tenantId.equals(UUID.fromString(TenantContext.getTenantId())) || !ScopeContext.hasAtLeast(Scope.OWNER)) {
            throw new AccessDeniedException("Only the store's own Owner can change its settings");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Store not found"));

        MaintenancePolicy policy = repository.findByTenantId(tenantId)
                .orElseGet(() -> MaintenancePolicy.builder().tenant(tenant).build());

        if (request.getDaysOfCoverTarget() != null) policy.setDaysOfCoverTarget(request.getDaysOfCoverTarget());
        if (request.getBurnRateWindowDays() != null) policy.setBurnRateWindowDays(request.getBurnRateWindowDays());
        if (request.getBurnRateMinObservationDays() != null) policy.setBurnRateMinObservationDays(request.getBurnRateMinObservationDays());
        if (request.getBurnRateMinEvents() != null) policy.setBurnRateMinEvents(request.getBurnRateMinEvents());
        if (request.getHealthThreshold() != null) policy.setHealthThreshold(request.getHealthThreshold());
        if (request.getMinimumHealthForOperation() != null) policy.setMinimumHealthForOperation(request.getMinimumHealthForOperation());
        if (request.getMaintenanceIntervalDays() != null) policy.setMaintenanceIntervalDays(request.getMaintenanceIntervalDays());
        if (request.getPartEndOfLifeWarningDays() != null) policy.setPartEndOfLifeWarningDays(request.getPartEndOfLifeWarningDays());
        if (request.getMaxWornParts() != null) policy.setMaxWornParts(request.getMaxWornParts());
        if (request.getMaxRefurbishedParts() != null) policy.setMaxRefurbishedParts(request.getMaxRefurbishedParts());
        if (request.getMaxRemovedParts() != null) policy.setMaxRemovedParts(request.getMaxRemovedParts());
        if (request.getAutoCreateWorkOrder() != null) policy.setAutoCreateWorkOrder(request.getAutoCreateWorkOrder());

        int window = or(policy.getBurnRateWindowDays(), BurnRateService.DEFAULT_WINDOW_DAYS);
        int minObservation = or(policy.getBurnRateMinObservationDays(), BurnRateService.DEFAULT_MIN_OBSERVATION_DAYS);

        if (minObservation > window) {
            throw new RuntimeException("Minimum observation can't be longer than the burn-rate window");
        }

        return map(tenant, repository.save(policy));
    }

    /** Effective values: the store's own, or the defaults where it has none. */
    private static StoreSettingsResponse map(Tenant tenant, MaintenancePolicy saved) {

        MaintenancePolicy p = saved != null ? saved : MaintenancePolicy.builder().build();

        return StoreSettingsResponse.builder()
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .customised(saved != null)
                .daysOfCoverTarget(or(p.getDaysOfCoverTarget(), BurnRateService.DEFAULT_TARGET_DAYS))
                .burnRateWindowDays(or(p.getBurnRateWindowDays(), BurnRateService.DEFAULT_WINDOW_DAYS))
                .burnRateMinObservationDays(or(p.getBurnRateMinObservationDays(), BurnRateService.DEFAULT_MIN_OBSERVATION_DAYS))
                .burnRateMinEvents(or(p.getBurnRateMinEvents(), BurnRateService.DEFAULT_MIN_EVENTS))
                .healthThreshold(p.getHealthThreshold())
                .minimumHealthForOperation(p.getMinimumHealthForOperation())
                .maintenanceIntervalDays(p.getMaintenanceIntervalDays())
                .partEndOfLifeWarningDays(p.getPartEndOfLifeWarningDays())
                .maxWornParts(p.getMaxWornParts())
                .maxRefurbishedParts(p.getMaxRefurbishedParts())
                .maxRemovedParts(p.getMaxRemovedParts())
                .autoCreateWorkOrder(Boolean.TRUE.equals(p.getAutoCreateWorkOrder()))
                .build();
    }

    private static int or(Integer value, int fallback) {

        return value != null ? value : fallback;
    }
}
