package com.example.operion.module.procurement.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.ScopeContext;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.procurement.dto.ProcurementActivityResponse;
import com.example.operion.module.procurement.enums.ActorKind;
import com.example.operion.module.procurement.enums.ProcurementEntityType;
import com.example.operion.module.procurement.repository.PurchaseOrderStatusHistoryRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationStatusHistoryRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestStatusHistoryRepository;
import com.example.operion.module.procurement.repository.RealisasiStatusHistoryRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * BE-07: one feed of who did what across procurement — every status change on
 * PRs, PRAs, Realisasi and POs, read from their existing *StatusHistory tables.
 * Nothing new is stored; the log is only as complete as those tables, which is
 * why BE-07 also started recording the actor on every PR and PO transition.
 *
 * Readable by ADMIN or PROCUREMENT role, or OWNER scope and above (decided
 * 14 Oct 2026). Covers the caller's tenant tree, so HQ sees its outlets.
 */
@Service
@RequiredArgsConstructor
public class ProcurementActivityService {

    static final int DEFAULT_LIMIT = 100;

    static final int MAX_LIMIT = 500;

    // BE-41: Accounting reads the log too
    private static final Set<String> ALLOWED_ROLES = Set.of("ROLE_ADMIN", "ROLE_PROCUREMENT", "ROLE_ACCOUNTING");

    private final PurchaseRequestStatusHistoryRepository purchaseRequestHistory;

    private final PurchaseRequestAuthorizationStatusHistoryRepository authorizationHistory;

    private final RealisasiStatusHistoryRepository realisasiHistory;

    private final PurchaseOrderStatusHistoryRepository purchaseOrderHistory;

    private final TenantHierarchyService tenantHierarchyService;

    /**
     * Newest first. All filters optional: {@code from}/{@code to} inclusive
     * dates, {@code tenantId} narrows to that tenant's subtree (must be inside
     * the caller's), {@code actorId} the user who made the change,
     * {@code type} one record type. {@code limit} defaults to 100, max 500.
     */
    @Transactional
    public List<ProcurementActivityResponse> getActivity(
            LocalDate from,
            LocalDate to,
            UUID tenantId,
            UUID actorId,
            ProcurementEntityType type,
            Integer limit) {

        requireLogAccess();

        UUID root = tenantId;

        if (root != null) {
            tenantHierarchyService.requireWithinCallerTenantTree(root);
        } else {
            root = UUID.fromString(TenantContext.getTenantId());
        }

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(root);

        LocalDateTime fromTime = from != null ? from.atStartOfDay() : null;
        LocalDateTime toTime = to != null ? to.plusDays(1).atStartOfDay() : null;

        int max = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));

        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt");

        List<ProcurementActivityResponse> entries = new ArrayList<>();

        // Each source returns at most `max` rows; the overall newest `max`
        // are always within the union of each source's newest `max`.

        if (type == null || type == ProcurementEntityType.PURCHASE_REQUEST) {
            purchaseRequestHistory
                    .findBy(filter("purchaseRequest", tenantIds, fromTime, toTime, actorId),
                            q -> q.sortBy(newestFirst).limit(max).all())
                    .forEach(h -> entries.add(entry(
                            ProcurementEntityType.PURCHASE_REQUEST,
                            h.getPurchaseRequest().getId(), null, h.getPurchaseRequest().getTenant(),
                            h.getChangedBy(), h.getPreviousStatus(), h.getNewStatus(), h.getReason(),
                            NO_TRIGGER,
                            h.getCreatedAt())));
        }

        if (type == null || type == ProcurementEntityType.PURCHASE_REQUEST_AUTHORIZATION) {
            authorizationHistory
                    .findBy(filter("purchaseRequestAuthorization", tenantIds, fromTime, toTime, actorId),
                            q -> q.sortBy(newestFirst).limit(max).all())
                    .forEach(h -> entries.add(entry(
                            ProcurementEntityType.PURCHASE_REQUEST_AUTHORIZATION,
                            h.getPurchaseRequestAuthorization().getId(), null,
                            h.getPurchaseRequestAuthorization().getTenant(),
                            h.getChangedBy(), h.getPreviousStatus(), h.getNewStatus(), h.getReason(),
                            new Trigger(h.isAutomatic(), h.getTriggeredBy(), h.getTriggerReference()),
                            h.getCreatedAt())));
        }

        if (type == null || type == ProcurementEntityType.REALISASI) {
            realisasiHistory
                    .findBy(filter("realisasi", tenantIds, fromTime, toTime, actorId),
                            q -> q.sortBy(newestFirst).limit(max).all())
                    .forEach(h -> entries.add(entry(
                            ProcurementEntityType.REALISASI,
                            h.getRealisasi().getId(), h.getRealisasi().getExternalOrderRef(),
                            h.getRealisasi().getTenant(),
                            h.getChangedBy(), h.getPreviousStatus(), h.getNewStatus(), h.getReason(),
                            NO_TRIGGER,
                            h.getCreatedAt())));
        }

        if (type == null || type == ProcurementEntityType.PURCHASE_ORDER) {
            purchaseOrderHistory
                    .findBy(filter("purchaseOrder", tenantIds, fromTime, toTime, actorId),
                            q -> q.sortBy(newestFirst).limit(max).all())
                    .forEach(h -> entries.add(entry(
                            ProcurementEntityType.PURCHASE_ORDER,
                            h.getPurchaseOrder().getId(), h.getPurchaseOrder().getPoNumber(),
                            h.getPurchaseOrder().getTenant(),
                            h.getChangedBy(), h.getPreviousStatus(), h.getNewStatus(), h.getReason(),
                            new Trigger(h.isAutomatic(), h.getTriggeredBy(), h.getTriggerReference()),
                            h.getCreatedAt())));
        }

        return entries.stream()
                .sorted(Comparator.comparing(ProcurementActivityResponse::getAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(max)
                .toList();
    }

    private void requireLogAccess() {

        boolean allowedRole = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ALLOWED_ROLES::contains);

        if (!allowedRole && !ScopeContext.hasAtLeast(Scope.OWNER)) {
            throw new AccessDeniedException(
                    "The procurement log is limited to ADMIN, PROCUREMENT, ACCOUNTING, or Owner-level users");
        }
    }

    /** Same filter for every history table; {@code parent} is the field holding the record. */
    private static <T> Specification<T> filter(
            String parent,
            List<UUID> tenantIds,
            LocalDateTime from,
            LocalDateTime to,
            UUID actorId) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(root.get(parent).get("tenant").get("id").in(tenantIds));

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), from));
            }

            if (to != null) {
                predicates.add(cb.lessThan(root.<LocalDateTime>get("createdAt"), to));
            }

            if (actorId != null) {
                predicates.add(cb.equal(root.get("changedBy").get("id"), actorId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** BE-22: an automatic row's cause. PR and Realisasi history are never automatic. */
    private record Trigger(boolean automatic, User by, String reference) {
    }

    private static final Trigger NO_TRIGGER = new Trigger(false, null, null);

    private static ProcurementActivityResponse entry(
            ProcurementEntityType type,
            UUID entityId,
            String reference,
            Tenant tenant,
            User actor,
            Enum<?> previous,
            Enum<?> next,
            String reason,
            Trigger trigger,
            LocalDateTime at) {

        return ProcurementActivityResponse.builder()
                .at(at)
                .entityType(type)
                .entityId(entityId)
                .reference(reference)
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .actorId(actor != null ? actor.getId() : null)
                .actorName(actor != null ? actor.getFullName() : null)
                .previousStatus(previous != null ? previous.name() : null)
                .newStatus(next != null ? next.name() : null)
                .reason(reason)
                .actorKind(ActorKind.of(actor, trigger.automatic()))
                .triggeredByName(trigger.by() != null ? trigger.by().getFullName() : null)
                .triggerReference(trigger.reference())
                .build();
    }
}
