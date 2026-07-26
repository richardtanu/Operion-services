package com.example.operion.module.procurement.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.ScopeContext;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.entity.User;
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

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow();

        User requestedBy = userRepository
                .findById(request.getRequestedBy())
                .orElseThrow(() -> new RuntimeException("Requesting user not found"));

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

        writeHistory(pr, null, PurchaseRequestStatus.PENDING, requestedBy, null);

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

        writeHistory(pr, previous, PurchaseRequestStatus.APPROVED, null, null);

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

        writeHistory(pr, previous, PurchaseRequestStatus.REJECTED, null, request.getReason());

        return map(pr);
    }

    @Transactional
    public PurchaseRequestResponse cancel(UUID id) {

        PurchaseRequest pr = findOwned(id);

        if (pr.getStatus() != PurchaseRequestStatus.PENDING
                && pr.getStatus() != PurchaseRequestStatus.APPROVED) {

            throw new RuntimeException(
                    "Only PENDING or APPROVED purchase requests can be cancelled.");
        }

        PurchaseRequestStatus previous = pr.getStatus();
        pr.setStatus(PurchaseRequestStatus.CANCELLED);
        pr = repository.save(pr);

        writeHistory(pr, previous, PurchaseRequestStatus.CANCELLED, null, null);

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

    public PurchaseRequestResponse getById(UUID id) {

        return map(findOwned(id));
    }

    public List<StatusHistoryResponse> getHistory(UUID id) {

        PurchaseRequest pr = findOwned(id);

        return historyRepository
                .findByPurchaseRequest_IdOrderByCreatedAtDesc(pr.getId())
                .stream()
                .map(h -> StatusHistoryResponse.builder()
                        .previousStatus(h.getPreviousStatus() != null ? h.getPreviousStatus().name() : null)
                        .newStatus(h.getNewStatus().name())
                        .changedByName(h.getChangedBy() != null ? h.getChangedBy().getFullName() : null)
                        .reason(h.getReason())
                        .createdAt(h.getCreatedAt())
                        .build())
                .toList();
    }

    private void requireOwnerScope() {

        if (!ScopeContext.hasAtLeast(Scope.OWNER)) {
            throw new RuntimeException(
                    "Only Owner-level users or higher can approve or reject purchase requests");
        }
    }

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
                .purchaseOrderId(pr.getPurchaseOrder() != null ? pr.getPurchaseOrder().getId() : null)
                .items(items)
                .createdAt(pr.getCreatedAt())
                .build();
    }
}
