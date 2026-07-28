package com.example.operion.module.procurement.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.repository.UserRepository;
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
import com.example.operion.module.procurement.entity.RealisasiItem;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.procurement.enums.PurchaseRequestStatus;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationItemRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationStatusHistoryRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestRepository;
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

    @Transactional
    public PurchaseRequestAuthorizationResponse create(CreatePurchaseRequestAuthorizationRequest request) {

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

            pr.setPurchaseRequestAuthorization(pra);
            pr.setStatus(PurchaseRequestStatus.AUTHORIZED);
            purchaseRequestRepository.save(pr);
        }

        writeHistory(pra, null, PurchaseRequestAuthorizationStatus.ACTIVE, approvedBy, null);

        return map(pra);
    }

    @Transactional
    public PurchaseRequestAuthorizationResponse cancel(UUID id) {

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
     */
    @Transactional
    public void refreshStatus(UUID praId) {

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

            writeHistory(pra, previous, next, null, "Recomputed from Realisasi fulfillment");
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
                            .purchasedValue(purchasedValue)
                            .remainingQty(item.getAuthorizedQty() - purchasedQty)
                            .remainingValue(item.getMaxValue().subtract(purchasedValue))
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
