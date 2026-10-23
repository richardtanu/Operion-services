package com.example.operion.module.stockadjustment.service;

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
import com.example.operion.module.inventory.enums.StockMovementType;
import com.example.operion.module.inventory.enums.StockReferenceType;
import com.example.operion.module.inventory.services.PartStockService;
import com.example.operion.module.notification.service.NotificationService;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.stockadjustment.dto.CreateStockAdjustmentRequest;
import com.example.operion.module.stockadjustment.dto.RejectStockAdjustmentRequest;
import com.example.operion.module.stockadjustment.dto.StatusHistoryResponse;
import com.example.operion.module.stockadjustment.dto.StockAdjustmentResponse;
import com.example.operion.module.stockadjustment.entity.StockAdjustment;
import com.example.operion.module.stockadjustment.entity.StockAdjustmentStatusHistory;
import com.example.operion.module.stockadjustment.enums.StockAdjustmentStatus;
import com.example.operion.module.stockadjustment.repository.StockAdjustmentRepository;
import com.example.operion.module.stockadjustment.repository.StockAdjustmentStatusHistoryRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockAdjustmentService {

    private final StockAdjustmentRepository repository;

    private final StockAdjustmentStatusHistoryRepository historyRepository;

    private final PartRepository partRepository;

    private final UserRepository userRepository;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    private final PartStockService partStockService;

    private final NotificationService notificationService;

    /** BE-25: the store's MANAGER+ ask; the store's Owner+ are told. */
    @Transactional
    public StockAdjustmentResponse create(CreateStockAdjustmentRequest request) {

        if (!ScopeContext.hasAtLeast(Scope.MANAGER)) {
            throw new AccessDeniedException("Only Manager-level users or higher can request a stock adjustment");
        }

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        Part part = partRepository
                .findByIdAndTenantId(request.getPartId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Part not found"));

        User requestedBy = currentUser();

        StockAdjustment adjustment = StockAdjustment.builder()
                .tenant(tenant)
                .part(part)
                .requestedBy(requestedBy)
                .quantity(request.getQuantity())
                .reason(request.getReason())
                .status(StockAdjustmentStatus.PENDING)
                .build();

        adjustment = repository.save(adjustment);

        writeHistory(adjustment, null, StockAdjustmentStatus.PENDING, requestedBy, null);

        notificationService.notifyStockAdjustmentRequested(adjustment, ownersOf(tenantId));

        return map(adjustment);
    }

    /**
     * BE-25: the store's own OWNER+ (HQ can't decide a store's stock). Not
     * your own request while the store has another Owner+; its only Owner+
     * may, and that approval is saved {@code selfApproved}.
     */
    @Transactional
    public StockAdjustmentResponse approve(UUID id) {

        requireOwnerScope();

        StockAdjustment adjustment = findOwned(id);

        if (adjustment.getStatus() != StockAdjustmentStatus.PENDING) {
            throw new RuntimeException("Only PENDING stock adjustments can be approved.");
        }

        User approver = currentUser();

        boolean ownRequest = adjustment.getRequestedBy() != null
                && adjustment.getRequestedBy().getId().equals(approver.getId());

        if (ownRequest) {

            boolean anotherOwner = ownersOf(adjustment.getTenant().getId()).stream()
                    .anyMatch(u -> !u.getId().equals(approver.getId()));

            if (anotherOwner) {
                throw new AccessDeniedException("Another Owner must approve your own adjustment");
            }

            adjustment.setSelfApproved(true);
        }

        partStockService.adjustStock(
                adjustment.getPart(),
                adjustment.getQuantity(),
                StockMovementType.ADJUSTMENT,
                StockReferenceType.STOCK_ADJUSTMENT,
                adjustment.getId(),
                adjustment.getReason());

        StockAdjustmentStatus previous = adjustment.getStatus();
        adjustment.setStatus(StockAdjustmentStatus.APPROVED);
        adjustment = repository.save(adjustment);

        writeHistory(adjustment, previous, StockAdjustmentStatus.APPROVED, approver, null);

        return map(adjustment);
    }

    @Transactional
    public StockAdjustmentResponse reject(UUID id, RejectStockAdjustmentRequest request) {

        requireOwnerScope();

        StockAdjustment adjustment = findOwned(id);

        if (adjustment.getStatus() != StockAdjustmentStatus.PENDING) {
            throw new RuntimeException("Only PENDING stock adjustments can be rejected.");
        }

        User approver = currentUser();

        StockAdjustmentStatus previous = adjustment.getStatus();
        adjustment.setStatus(StockAdjustmentStatus.REJECTED);
        adjustment = repository.save(adjustment);

        writeHistory(adjustment, previous, StockAdjustmentStatus.REJECTED, approver, request.getReason());

        return map(adjustment);
    }

    /** {@code selfApproved}: BE-25 filter, e.g. {@code ?selfApproved=true}. */
    public List<StockAdjustmentResponse> getAll(Boolean selfApproved) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return repository.findByTenantIdIn(tenantIds)
                .stream()
                .filter(a -> selfApproved == null || a.isSelfApproved() == selfApproved)
                .map(this::map)
                .toList();
    }

    public List<StockAdjustmentResponse> getPending() {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return repository.findByTenantIdInAndStatus(tenantIds, StockAdjustmentStatus.PENDING)
                .stream()
                .map(this::map)
                .toList();
    }

    public List<StatusHistoryResponse> getHistory(UUID id) {

        StockAdjustment adjustment = findInHierarchy(id);

        return historyRepository
                .findByStockAdjustment_IdOrderByCreatedAtDesc(adjustment.getId())
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
            throw new AccessDeniedException(
                    "Only Owner-level users or higher can approve or reject stock adjustments");
        }
    }

    /** BE-25: decisions are the store's own; HQ can read (findInHierarchy) but not decide. */
    private StockAdjustment findOwned(UUID id) {

        return repository
                .findByIdAndTenantId(id, UUID.fromString(TenantContext.getTenantId()))
                .orElseThrow(() -> new RuntimeException("Stock adjustment not found"));
    }

    /** The store's own Owner+ users (HQ's don't count). Users have no "active" flag yet. */
    private List<User> ownersOf(UUID storeId) {

        return userRepository.findByTenantIdAndScopeIn(storeId, List.of(Scope.OWNER, Scope.PRINCIPAL));
    }

    private StockAdjustment findInHierarchy(UUID id) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return repository
                .findByIdAndTenantIdIn(id, tenantIds)
                .orElseThrow(() -> new RuntimeException("Stock adjustment not found"));
    }

    private User currentUser() {

        UUID userId = UUID.fromString(
                SecurityContextHolder.getContext().getAuthentication().getName());

        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    private void writeHistory(
            StockAdjustment adjustment,
            StockAdjustmentStatus previous,
            StockAdjustmentStatus next,
            User changedBy,
            String reason) {

        historyRepository.save(
                StockAdjustmentStatusHistory.builder()
                        .stockAdjustment(adjustment)
                        .previousStatus(previous)
                        .newStatus(next)
                        .changedBy(changedBy)
                        .reason(reason)
                        .build());
    }

    private StockAdjustmentResponse map(StockAdjustment adjustment) {

        return StockAdjustmentResponse.builder()
                .id(adjustment.getId())
                .partId(adjustment.getPart().getId())
                .partName(adjustment.getPart().getName())
                .quantity(adjustment.getQuantity())
                .reason(adjustment.getReason())
                .status(adjustment.getStatus().name())
                .requestedById(adjustment.getRequestedBy() != null ? adjustment.getRequestedBy().getId() : null)
                .requestedByName(
                        adjustment.getRequestedBy() != null ? adjustment.getRequestedBy().getFullName() : null)
                .selfApproved(adjustment.isSelfApproved())
                .tenantId(adjustment.getTenant().getId())
                .tenantName(adjustment.getTenant().getName())
                .createdAt(adjustment.getCreatedAt())
                .build();
    }
}
