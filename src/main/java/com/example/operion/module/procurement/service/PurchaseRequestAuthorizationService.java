package com.example.operion.module.procurement.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.ScopeContext;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.notification.service.NotificationService;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.procurement.dto.CreatePurchaseRequestAuthorizationRequest;
import com.example.operion.module.procurement.dto.PurchaseRequestAuthorizationItemRequest;
import com.example.operion.module.procurement.dto.PurchaseRequestAuthorizationItemResponse;
import com.example.operion.module.procurement.dto.PurchaseRequestAuthorizationResponse;
import com.example.operion.module.procurement.dto.StatusHistoryResponse;
import com.example.operion.module.procurement.entity.PurchaseRequest;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorizationItem;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorizationStatusHistory;
import com.example.operion.module.procurement.entity.PurchaseRequestStatusHistory;
import com.example.operion.module.procurement.entity.RealisasiItem;
import com.example.operion.module.procurement.enums.ActorKind;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.procurement.enums.PurchaseRequestStatus;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationItemRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationStatusHistoryRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestStatusHistoryRepository;
import com.example.operion.module.procurement.repository.RealisasiItemRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PurchaseRequestAuthorizationService {

    private final PurchaseRequestAuthorizationRepository repository;

    private final PurchaseRequestAuthorizationItemRepository itemRepository;

    private final PurchaseRequestAuthorizationStatusHistoryRepository historyRepository;

    private final PurchaseRequestRepository purchaseRequestRepository;

    private final RealisasiItemRepository realisasiItemRepository;

    private final PartRepository partRepository;

    private final UserRepository userRepository;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    private final PurchaseRequestStatusHistoryRepository purchaseRequestHistoryRepository;

    private final NotificationService notificationService;

    @Transactional
    public PurchaseRequestAuthorizationResponse create(CreatePurchaseRequestAuthorizationRequest request) {

        requireOwnerScopeForLimits();

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        User approvedBy = currentUser();

        List<PurchaseRequest> linkedRequests = purchaseRequestRepository
                .findByIdInAndTenantId(request.getPurchaseRequestIds(), tenantId);

        if (linkedRequests.size() != request.getPurchaseRequestIds().size()) {

            throw new RuntimeException("One or more purchase requests were not found.");
        }

        for (PurchaseRequest pr : linkedRequests) {

            if (pr.getStatus() != PurchaseRequestStatus.APPROVED) {

                throw new RuntimeException(
                        "Purchase request " + pr.getId() + " must be APPROVED before it can be authorized.");
            }
        }

        PurchaseRequestAuthorization pra = PurchaseRequestAuthorization.builder()
                .tenant(tenant)
                .approvedBy(approvedBy)
                .status(PurchaseRequestAuthorizationStatus.ACTIVE)
                .build();

        pra = repository.save(pra);

        for (PurchaseRequestAuthorizationItemRequest itemRequest : request.getItems()) {

            Part part = partRepository
                    .findByIdAndTenantId(itemRequest.getPartId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Part not found"));

            itemRepository.save(
                    PurchaseRequestAuthorizationItem.builder()
                            .purchaseRequestAuthorization(pra)
                            .part(part)
                            .authorizedQty(itemRequest.getAuthorizedQty())
                            .maxValue(itemRequest.getMaxValue())
                            .build());
        }

        for (PurchaseRequest pr : linkedRequests) {

            PurchaseRequestStatus previous = pr.getStatus();
            pr.setPurchaseRequestAuthorization(pra);
            pr.setStatus(PurchaseRequestStatus.AUTHORIZED);
            purchaseRequestRepository.save(pr);

            // BE-07: this transition never reached the PR's own history before
            // (and needed V3 to widen the history CHECK constraint to allow it)
            purchaseRequestHistoryRepository.save(PurchaseRequestStatusHistory.builder()
                    .purchaseRequest(pr)
                    .previousStatus(previous)
                    .newStatus(PurchaseRequestStatus.AUTHORIZED)
                    .changedBy(approvedBy)
                    .build());

            notificationService.notifyPurchaseRequestAuthorized(pr, approvedBy);
        }

        writeHistory(pra, null, PurchaseRequestAuthorizationStatus.ACTIVE, approvedBy, null);

        notificationService.notifyPurchaseAuthorizationCreated(pra, approvedBy);

        return map(pra);
    }

    @Transactional
    public PurchaseRequestAuthorizationResponse cancel(UUID id) {

        requireOwnerScopeForLimits();

        PurchaseRequestAuthorization pra = findOwned(id);

        if (pra.getStatus() == PurchaseRequestAuthorizationStatus.CANCELLED) {

            throw new RuntimeException("Purchase request authorization is already cancelled.");
        }

        PurchaseRequestAuthorizationStatus previous = pra.getStatus();
        pra.setStatus(PurchaseRequestAuthorizationStatus.CANCELLED);
        pra = repository.save(pra);

        writeHistory(pra, previous, PurchaseRequestAuthorizationStatus.CANCELLED, currentUser(), null);

        return map(pra);
    }

    public List<PurchaseRequestAuthorizationResponse> getAll() {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return repository.findByTenantIdIn(tenantIds)
                .stream()
                .map(this::map)
                .toList();
    }

    public PurchaseRequestAuthorizationResponse getById(UUID id) {

        return map(findOwned(id));
    }

    public List<StatusHistoryResponse> getHistory(UUID id) {

        PurchaseRequestAuthorization pra = findOwned(id);

        return historyRepository
                .findByPurchaseRequestAuthorization_IdOrderByCreatedAtDesc(pra.getId())
                .stream()
                .map(h -> StatusHistoryResponse.builder()
                        .previousStatus(h.getPreviousStatus() != null ? h.getPreviousStatus().name() : null)
                        .newStatus(h.getNewStatus().name())
                        .changedByName(h.getChangedBy() != null ? h.getChangedBy().getFullName() : null)
                        .reason(h.getReason())
                        .actorKind(ActorKind.of(h.getChangedBy(), h.isAutomatic()))
                        .triggeredByName(h.getTriggeredBy() != null ? h.getTriggeredBy().getFullName() : null)
                        .triggerReference(h.getTriggerReference())
                        .createdAt(h.getCreatedAt())
                        .build())
                .toList();
    }

    PurchaseRequestAuthorization findOwned(UUID id) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Purchase request authorization not found"));
    }

    /**
     * Recomputes and persists this PRA's overall status from the aggregate
     * remaining qty across its items. Called by RealisasiService after every
     * Realisasi create/approve/reject so the header status stays accurate
     * without ever writing a mutable "remaining" column.
     *
     * BE-22: a change made here is automatic; its history row names whose
     * action caused it ({@code triggeredBy}) and what that was
     * ({@code triggerReference}, e.g. "SHP-123 approved").
     */
    @Transactional
    public void refreshStatus(UUID praId, User triggeredBy, String triggerReference) {

        PurchaseRequestAuthorization pra = repository.findById(praId).orElseThrow();

        if (pra.getStatus() == PurchaseRequestAuthorizationStatus.CANCELLED) {
            return;
        }

        List<PurchaseRequestAuthorizationItem> items = itemRepository
                .findByPurchaseRequestAuthorizationId(praId);

        boolean anyRemaining = false;
        boolean anyFulfilled = false;

        for (PurchaseRequestAuthorizationItem item : items) {

            int purchased = approvedPurchasedQtyInStockUnits(item.getId());

            if (purchased >= item.getAuthorizedQty()) {
                anyFulfilled = true;
            } else {
                anyRemaining = true;
            }
        }

        PurchaseRequestAuthorizationStatus previous = pra.getStatus();
        PurchaseRequestAuthorizationStatus next;

        if (!anyRemaining) {
            next = PurchaseRequestAuthorizationStatus.FULFILLED;
        } else if (anyFulfilled) {
            next = PurchaseRequestAuthorizationStatus.PARTIALLY_FULFILLED;
        } else {
            next = PurchaseRequestAuthorizationStatus.ACTIVE;
        }

        if (next != previous) {

            pra.setStatus(next);
            repository.save(pra);

            historyRepository.save(
                    PurchaseRequestAuthorizationStatusHistory.builder()
                            .purchaseRequestAuthorization(pra)
                            .previousStatus(previous)
                            .newStatus(next)
                            .reason("Recomputed from Realisasi fulfillment")
                            .automatic(true)
                            .triggeredBy(triggeredBy)
                            .triggerReference(triggerReference)
                            .build());
        }
    }

    /**
     * Stock-unit-equivalent quantity purchased so far against this PRA line,
     * counting only APPROVED Realisasi items (FAILED/SUPERSEDED/
     * PENDING_APPROVAL don't consume the ceiling — this is how "failed
     * doesn't kill the PRA" and partial fulfillment fall out with no
     * mutable remaining-qty column). purchasedQty is in purchase UOM, so
     * each line is converted via its own conversionFactor before summing.
     */
    int approvedPurchasedQtyInStockUnits(UUID praItemId) {

        return approvedRealisasiItems(praItemId).stream()
                .map(ri -> BigDecimal.valueOf(ri.getPurchasedQty()).multiply(ri.getConversionFactor()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .intValue();
    }

    BigDecimal approvedPurchasedValue(UUID praItemId) {

        return approvedRealisasiItems(praItemId).stream()
                .map(ri -> ri.getActualUnitPrice().multiply(BigDecimal.valueOf(ri.getPurchasedQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<RealisasiItem> approvedRealisasiItems(UUID praItemId) {

        return realisasiItemRepository
                .findByPurchaseRequestAuthorizationItemIdAndRealisasi_Status(praItemId, RealisasiStatus.APPROVED);
    }

    /**
     * Creating a PRA is the authorization act (sets the spending ceiling), so
     * it and its cancellation need OWNER scope or above. BE-13 (14 Oct) set
     * MANAGER+; BE-17 (19 Oct) raised it on the product owner's instruction:
     * the store Owner sets the limits and the Store Manager buys against them,
     * which also keeps the DL-08 rule (approver can't log their own PRA's
     * purchase unless OWNER+) from blocking the normal flow. A PRA is only ever
     * created on the caller's own store: HQ doesn't set a store's limits.
     * Checked before any lookup, so a random id answers 403 too.
     */
    private void requireOwnerScopeForLimits() {

        if (!ScopeContext.hasAtLeast(Scope.OWNER)) {
            throw new AccessDeniedException(
                    "Only Owner-level users or higher can create or cancel a purchase authorization");
        }
    }

    private User currentUser() {

        UUID userId = UUID.fromString(
                SecurityContextHolder.getContext().getAuthentication().getName());

        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    private void writeHistory(
            PurchaseRequestAuthorization pra,
            PurchaseRequestAuthorizationStatus previous,
            PurchaseRequestAuthorizationStatus next,
            User changedBy,
            String reason) {

        historyRepository.save(
                PurchaseRequestAuthorizationStatusHistory.builder()
                        .purchaseRequestAuthorization(pra)
                        .previousStatus(previous)
                        .newStatus(next)
                        .changedBy(changedBy)
                        .reason(reason)
                        .build());
    }

    private PurchaseRequestAuthorizationResponse map(PurchaseRequestAuthorization pra) {

        // Below OWNER: the buyer still sees maxValue (their spending limit) and
        // all quantities, but not purchasedValue — it is summed from the
        // Realisasi spend BE-06 hides. remainingValue goes too, because
        // maxValue − remainingValue would give purchasedValue straight back.
        // Over-ceiling buys are still caught server-side (escalation), so the
        // buyer doesn't need the remaining value to stay within the limit.
        boolean canSeeCost = ScopeContext.hasAtLeast(Scope.OWNER);

        List<PurchaseRequestAuthorizationItemResponse> items = itemRepository
                .findByPurchaseRequestAuthorizationId(pra.getId())
                .stream()
                .map(item -> {

                    int purchasedQty = approvedPurchasedQtyInStockUnits(item.getId());
                    BigDecimal purchasedValue = approvedPurchasedValue(item.getId());

                    return PurchaseRequestAuthorizationItemResponse.builder()
                            .id(item.getId())
                            .partId(item.getPart().getId())
                            .partName(item.getPart().getName())
                            .authorizedQty(item.getAuthorizedQty())
                            .maxValue(item.getMaxValue())
                            .purchasedQty(purchasedQty)
                            .purchasedValue(canSeeCost ? purchasedValue : null)
                            .remainingQty(item.getAuthorizedQty() - purchasedQty)
                            .remainingValue(canSeeCost ? item.getMaxValue().subtract(purchasedValue) : null)
                            .build();
                })
                .toList();

        List<UUID> purchaseRequestIds = purchaseRequestRepository
                .findByPurchaseRequestAuthorizationId(pra.getId())
                .stream()
                .map(PurchaseRequest::getId)
                .toList();

        return PurchaseRequestAuthorizationResponse.builder()
                .id(pra.getId())
                .status(pra.getStatus().name())
                .approvedById(pra.getApprovedBy() != null ? pra.getApprovedBy().getId() : null)
                .approvedByName(pra.getApprovedBy() != null ? pra.getApprovedBy().getFullName() : null)
                .approvedByScope(pra.getApprovedBy() != null && pra.getApprovedBy().getScope() != null
                        ? pra.getApprovedBy().getScope().name()
                        : null)
                .items(items)
                .purchaseRequestIds(purchaseRequestIds)
                .createdAt(pra.getCreatedAt())
                .build();
    }
}
