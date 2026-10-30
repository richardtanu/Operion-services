package com.example.operion.module.procurement.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.CallerRoles;
import com.example.operion.common.security.ScopeContext;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.notification.service.NotificationService;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.procurement.dto.CreatePurchaseRequestRequest;
import com.example.operion.module.procurement.dto.PurchaseRequestItemRequest;
import com.example.operion.module.procurement.dto.PurchaseRequestItemResponse;
import com.example.operion.module.procurement.dto.PurchaseRequestResponse;
import com.example.operion.module.procurement.dto.RejectRequest;
import com.example.operion.module.procurement.dto.StatusHistoryResponse;
import com.example.operion.module.procurement.entity.PurchaseRequest;
import com.example.operion.module.procurement.entity.PurchaseRequestItem;
import com.example.operion.module.procurement.entity.PurchaseRequestStatusHistory;
import com.example.operion.module.procurement.enums.ActorKind;
import com.example.operion.module.procurement.enums.PurchaseRequestStatus;
import com.example.operion.module.procurement.repository.PurchaseRequestItemRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestStatusHistoryRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PurchaseRequestService {

    private final PurchaseRequestRepository repository;

    private final PurchaseRequestItemRepository itemRepository;

    private final PurchaseRequestStatusHistoryRepository historyRepository;

    private final PartRepository partRepository;

    private final UserRepository userRepository;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    private final NotificationService notificationService;

    @Transactional
    public PurchaseRequestResponse create(CreatePurchaseRequestRequest request) {

        // BE-14: the Store Manager (MANAGER+) files a store's PRs; HQ ADMIN and
        // PROCUREMENT staff may too. TECHNICIAN, OPERATOR and ACCOUNTING may not
        // (ACCOUNTING handles the money, so it doesn't also ask for purchases).
        if (!ScopeContext.hasAtLeast(Scope.MANAGER) && !CallerRoles.hasAny(Role.ADMIN, Role.PROCUREMENT)) {
            throw new AccessDeniedException(
                    "Only Manager-level users or higher, Admin or Procurement can create purchase requests");
        }

        // BE-14: HQ staff file for a store in their tree; the PR lives on that store
        UUID tenantId = tenantHierarchyService.resolveTargetTenant(
                request.getTenantId(),
                CallerRoles.hasAny(Role.ADMIN, Role.PROCUREMENT) || ScopeContext.hasAtLeast(Scope.OWNER));

        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow();

        User caller = currentUser();

        // The requester is the caller themself, or a user of the store the PR is
        // for (BE-13 rule, widened by BE-14 so HQ can be the requester for a store:
        // "Requested by Rina Wijaya for Outlet Bandung"). Anyone else answers
        // exactly like a missing id, so ids can't be probed.
        User requestedBy = userRepository
                .findById(request.getRequestedBy())
                .filter(u -> u.getId().equals(caller.getId()) || u.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new RuntimeException("Requesting user not found in your branch"))
                .requireActive();

        PurchaseRequest pr = PurchaseRequest.builder()
                .tenant(tenant)
                .requestedBy(requestedBy)
                .status(PurchaseRequestStatus.PENDING)
                .build();

        pr = repository.save(pr);

        for (PurchaseRequestItemRequest itemRequest : request.getItems()) {

            Part part = partRepository
                    .findByIdAndTenantId(itemRequest.getPartId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Part not found"));

            itemRepository.save(
                    PurchaseRequestItem.builder()
                            .purchaseRequest(pr)
                            .part(part)
                            .quantity(itemRequest.getQuantity())
                            .build());
        }

        // BE-07: history records who actually made the call, which can differ
        // from requestedBy (a colleague in the same branch filing on their behalf).
        writeHistory(pr, null, PurchaseRequestStatus.PENDING, caller, null);

        notificationService.notifyNewPurchaseRequest(pr);

        return map(pr);
    }

    @Transactional
    public PurchaseRequestResponse approve(UUID id) {

        requireOwnerScope();

        PurchaseRequest pr = findOwned(id);

        if (pr.getStatus() != PurchaseRequestStatus.PENDING) {

            throw new RuntimeException(
                    "Only PENDING purchase requests can be approved.");
        }

        PurchaseRequestStatus previous = pr.getStatus();
        pr.setStatus(PurchaseRequestStatus.APPROVED);
        pr = repository.save(pr);

        User actor = currentUser();

        writeHistory(pr, previous, PurchaseRequestStatus.APPROVED, actor, null);

        notificationService.notifyPurchaseRequestApproved(pr, actor);

        return map(pr);
    }

    @Transactional
    public PurchaseRequestResponse reject(UUID id, RejectRequest request) {

        requireOwnerScope();

        PurchaseRequest pr = findOwned(id);

        if (pr.getStatus() != PurchaseRequestStatus.PENDING) {

            throw new RuntimeException(
                    "Only PENDING purchase requests can be rejected.");
        }

        PurchaseRequestStatus previous = pr.getStatus();
        pr.setStatus(PurchaseRequestStatus.REJECTED);
        pr = repository.save(pr);

        User actor = currentUser();

        writeHistory(pr, previous, PurchaseRequestStatus.REJECTED, actor, request.getReason());

        notificationService.notifyPurchaseRequestRejected(pr, actor, request.getReason());

        return map(pr);
    }

    @Transactional
    /**
     * BE-15. Cancel stops spending, so the bar is lower than approve's, but a
     * needed request mustn't vanish silently:
     * <ul>
     * <li>PENDING: the PR's requester (fixing their own mistake by cancel-and-refile),
     *     or OWNER+.</li>
     * <li>APPROVED: OWNER+ only: undoing an Owner's decision takes an Owner.</li>
     * </ul>
     * Order: tenant containment, then who, then status. A reason is required and
     * goes on the history row (so it shows in the activity log); the requester is
     * told unless they cancelled it themselves.
     */
    public PurchaseRequestResponse cancel(UUID id, String reason) {

        // Looked up across the caller's tree, so an HQ person can withdraw a PR
        // they filed for a store (BE-14). An Owner's power stays within their
        // own store: stores are independent, HQ doesn't cancel a store's requests.
        PurchaseRequest pr = findInTree(id);

        User caller = currentUser();
        boolean isRequester = pr.getRequestedBy() != null && pr.getRequestedBy().getId().equals(caller.getId());
        boolean isOwnOwner = ScopeContext.hasAtLeast(Scope.OWNER)
                && pr.getTenant().getId().toString().equals(TenantContext.getTenantId());

        boolean allowed = isOwnOwner || (isRequester && pr.getStatus() == PurchaseRequestStatus.PENDING);

        if (!allowed) {
            throw new AccessDeniedException(pr.getStatus() == PurchaseRequestStatus.APPROVED
                    ? "Only Owner-level users or higher can cancel an approved purchase request"
                    : "Only the requester, or Owner-level users or higher, can cancel this purchase request");
        }

        if (pr.getStatus() != PurchaseRequestStatus.PENDING
                && pr.getStatus() != PurchaseRequestStatus.APPROVED) {

            throw new RuntimeException(
                    "Only PENDING or APPROVED purchase requests can be cancelled.");
        }

        PurchaseRequestStatus previous = pr.getStatus();
        pr.setStatus(PurchaseRequestStatus.CANCELLED);
        pr = repository.save(pr);

        writeHistory(pr, previous, PurchaseRequestStatus.CANCELLED, caller, reason);

        notificationService.notifyPurchaseRequestCancelled(pr, caller, reason);

        return map(pr);
    }

    public List<PurchaseRequestResponse> getAll() {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return repository.findByTenantIdIn(tenantIds)
                .stream()
                .map(this::map)
                .toList();
    }

    // Reads follow the caller's tree, like getAll(): HQ can open a PR it filed for a store
    public PurchaseRequestResponse getById(UUID id) {

        return map(findInTree(id));
    }

    public List<StatusHistoryResponse> getHistory(UUID id) {

        PurchaseRequest pr = findInTree(id);

        return historyRepository
                .findByPurchaseRequest_IdOrderByCreatedAtDesc(pr.getId())
                .stream()
                .map(h -> StatusHistoryResponse.builder()
                        .previousStatus(h.getPreviousStatus() != null ? h.getPreviousStatus().name() : null)
                        .newStatus(h.getNewStatus().name())
                        .changedByName(h.getChangedBy() != null ? h.getChangedBy().getFullName() : null)
                        .reason(h.getReason())
                        .actorKind(ActorKind.of(h.getChangedBy(), false))
                        .createdAt(h.getCreatedAt())
                        .build())
                .toList();
    }

    private void requireOwnerScope() {

        if (!ScopeContext.hasAtLeast(Scope.OWNER)) {
            throw new AccessDeniedException(
                    "Only Owner-level users or higher can approve or reject purchase requests");
        }
    }

    /** Own tenant + descendants; same "not found" answer as findOwned for anything else. */
    private PurchaseRequest findInTree(UUID id) {

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(
                UUID.fromString(TenantContext.getTenantId()));

        return repository
                .findByIdAndTenantIdIn(id, tenantIds)
                .orElseThrow(() -> new RuntimeException("Purchase request not found"));
    }

    /** Own tenant only: approve and reject are the store Owner's decisions. */
    PurchaseRequest findOwned(UUID id) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Purchase request not found"));
    }

    private void writeHistory(
            PurchaseRequest pr,
            PurchaseRequestStatus previous,
            PurchaseRequestStatus next,
            User changedBy,
            String reason) {

        historyRepository.save(
                PurchaseRequestStatusHistory.builder()
                        .purchaseRequest(pr)
                        .previousStatus(previous)
                        .newStatus(next)
                        .changedBy(changedBy)
                        .reason(reason)
                        .build());
    }

    private User currentUser() {

        UUID userId = UUID.fromString(
                SecurityContextHolder.getContext().getAuthentication().getName());

        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    private PurchaseRequestResponse map(PurchaseRequest pr) {

        List<PurchaseRequestItemResponse> items = itemRepository
                .findByPurchaseRequestId(pr.getId())
                .stream()
                .map(item -> PurchaseRequestItemResponse.builder()
                        .id(item.getId())
                        .partId(item.getPart().getId())
                        .partName(item.getPart().getName())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        return PurchaseRequestResponse.builder()
                .id(pr.getId())
                .status(pr.getStatus().name())
                .requestedById(pr.getRequestedBy() != null ? pr.getRequestedBy().getId() : null)
                .requestedByName(pr.getRequestedBy() != null ? pr.getRequestedBy().getFullName() : null)
                .tenantId(pr.getTenant().getId())
                .tenantName(pr.getTenant().getName())
                .purchaseOrderId(pr.getPurchaseOrder() != null ? pr.getPurchaseOrder().getId() : null)
                .items(items)
                .createdAt(pr.getCreatedAt())
                .build();
    }
}
