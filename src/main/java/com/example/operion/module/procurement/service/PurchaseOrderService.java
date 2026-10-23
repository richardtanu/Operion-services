package com.example.operion.module.procurement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import com.example.operion.module.part.service.HqCatalogService;
import com.example.operion.module.procurement.dto.CreatePurchaseOrderRequest;
import com.example.operion.module.procurement.dto.PurchaseOrderItemRequest;
import com.example.operion.module.procurement.dto.PurchaseOrderItemResponse;
import com.example.operion.module.procurement.dto.PurchaseOrderResponse;
import com.example.operion.module.procurement.dto.StatusHistoryResponse;
import com.example.operion.module.procurement.entity.PurchaseOrder;
import com.example.operion.module.procurement.entity.PurchaseOrderItem;
import com.example.operion.module.procurement.entity.PurchaseOrderStatusHistory;
import com.example.operion.module.procurement.entity.PurchaseRequest;
import com.example.operion.module.procurement.entity.PurchaseRequestStatusHistory;
import com.example.operion.module.procurement.entity.Supplier;
import com.example.operion.module.procurement.enums.ActorKind;
import com.example.operion.module.procurement.enums.PurchaseOrderStatus;
import com.example.operion.module.procurement.enums.PurchaseRequestStatus;
import com.example.operion.module.procurement.repository.PurchaseOrderItemRepository;
import com.example.operion.module.procurement.repository.PurchaseOrderRepository;
import com.example.operion.module.procurement.repository.PurchaseOrderStatusHistoryRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestStatusHistoryRepository;
import com.example.operion.module.procurement.repository.SupplierRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository repository;

    private final PurchaseOrderItemRepository itemRepository;

    private final PurchaseOrderStatusHistoryRepository historyRepository;

    private final PurchaseRequestRepository purchaseRequestRepository;

    private final PurchaseRequestStatusHistoryRepository purchaseRequestHistoryRepository;

    private final SupplierRepository supplierRepository;

    private final HqCatalogService hqCatalogService;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    private final NotificationService notificationService;

    private final UserRepository userRepository;

    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request) {

        // BE-20: the Store Manager (MANAGER+) or HQ PROCUREMENT drafts; the Owner sends
        if (!ScopeContext.hasAtLeast(Scope.MANAGER) && !CallerRoles.hasAny(Role.PROCUREMENT)) {
            throw new AccessDeniedException(
                    "Only Manager-level users or higher, or Procurement, can draft a purchase order");
        }

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow();

        // BE-21: an order is an order to HQ, so its supplier is HQ itself (the
        // franchise-centre supplier), set here. The legacy supplierId is ignored.
        Supplier supplier = supplierRepository
                .findFranchiseCentre(tenantHierarchyService.getRootTenantId(tenantId))
                .orElseThrow(() -> new RuntimeException(
                        "HQ has no franchise-centre supplier set up, so orders to HQ can't be placed yet"));

        // BE-21: only items HQ sells; the store's own part is created on its first order
        List<Part> lineParts = request.getItems().stream()
                .map(i -> hqCatalogService.storePartForHqOrder(i.getPartId(), tenantId))
                .toList();

        List<PurchaseRequest> linkedRequests = List.of();

        if (request.getPurchaseRequestIds() != null && !request.getPurchaseRequestIds().isEmpty()) {

            linkedRequests = purchaseRequestRepository.findByIdInAndTenantId(
                    request.getPurchaseRequestIds(), tenantId);

            if (linkedRequests.size() != request.getPurchaseRequestIds().size()) {

                throw new RuntimeException(
                        "One or more purchase requests were not found.");
            }

            for (PurchaseRequest pr : linkedRequests) {

                if (pr.getStatus() != PurchaseRequestStatus.APPROVED) {

                    throw new RuntimeException(
                            "Purchase request " + pr.getId() + " must be APPROVED before it can be ordered.");
                }
            }
        }

        // BE-28: HQ sets the price; the drafter's typed price is ignored
        List<BigDecimal> linePrices = lineParts.stream().map(this::hqPriceOf).toList();

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < request.getItems().size(); i++) {
            totalAmount = totalAmount.add(
                    linePrices.get(i).multiply(BigDecimal.valueOf(request.getItems().get(i).getQuantity())));
        }

        PurchaseOrder po = PurchaseOrder.builder()
                .tenant(tenant)
                .supplier(supplier)
                // millis alone collided when two orders landed in the same ms
                // (baseline §5.2); the random suffix makes that practically impossible
                .poNumber("PO-" + System.currentTimeMillis() + "-"
                        + Integer.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextInt(0x10000, 0xfffff)))
                .orderDate(LocalDate.now())
                .status(PurchaseOrderStatus.DRAFT)
                .totalAmount(totalAmount)
                .createdBy(currentUser())
                .build();

        po = repository.save(po);

        for (int i = 0; i < request.getItems().size(); i++) {

            PurchaseOrderItemRequest itemRequest = request.getItems().get(i);
            Part part = lineParts.get(i);

            itemRepository.save(
                    PurchaseOrderItem.builder()
                            .purchaseOrder(po)
                            .part(part)
                            .quantity(itemRequest.getQuantity())
                            .price(linePrices.get(i))
                            .build());
        }

        for (PurchaseRequest pr : linkedRequests) {

            PurchaseRequestStatus previous = pr.getStatus();
            pr.setPurchaseOrder(po);
            pr.setStatus(PurchaseRequestStatus.ORDERED);
            purchaseRequestRepository.save(pr);

            writePurchaseRequestHistory(pr, previous, PurchaseRequestStatus.ORDERED,
                    "Ordered via purchase order " + po.getPoNumber());

            notificationService.notifyPurchaseRequestOrdered(pr);
        }

        writeHistory(po, null, PurchaseOrderStatus.DRAFT, currentUser());

        return map(po);
    }

    @Transactional
    public PurchaseOrderResponse sendToSupplier(UUID id) {

        // BE-20: sending commits the store's money, so it's the Owner's act
        if (!ScopeContext.hasAtLeast(Scope.OWNER)) {
            throw new AccessDeniedException("Only Owner-level users or higher can send a purchase order");
        }

        PurchaseOrder po = findOwned(id);

        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {

            throw new RuntimeException(
                    "Only DRAFT purchase orders can be sent to supplier.");
        }

        if (po.getSupplier() == null) {

            throw new RuntimeException(
                    "A supplier must be set before sending this purchase order.");
        }

        List<PurchaseOrderItem> lines = itemRepository.findByPurchaseOrderId(po.getId());

        if (lines.isEmpty()) {

            throw new RuntimeException(
                    "Purchase order has no items.");
        }

        // BE-28: sending commits the store's money, so the lines take HQ's price
        // as it is now; after this they're frozen (nothing re-prices a sent order)
        BigDecimal total = BigDecimal.ZERO;

        for (PurchaseOrderItem line : lines) {
            line.setPrice(hqPriceOf(line.getPart()));
            itemRepository.save(line);
            total = total.add(line.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
        }

        po.setTotalAmount(total);

        PurchaseOrderStatus previous = po.getStatus();
        po.setStatus(PurchaseOrderStatus.ORDERED);
        po = repository.save(po);

        writeHistory(po, previous, PurchaseOrderStatus.ORDERED, currentUser());

        return map(po);
    }

    /** BE-28: the current HQ price of the HQ item a line is for; none set → 400. */
    private BigDecimal hqPriceOf(Part part) {

        Part item = hqCatalogService.catalogEntryOf(part);
        BigDecimal price = item != null ? item.getHqPrice() : null;

        if (price == null) {
            throw new RuntimeException("HQ hasn't set a price for " + part.getName() + " yet");
        }

        return price;
    }

    /**
     * BE-20. A DRAFT costs nothing yet, so its creator may drop it (or OWNER+).
     * Once sent (ORDERED / PARTIALLY_RECEIVED) the store has committed money:
     * OWNER+ only, with a reason that goes on the history row.
     */
    @Transactional
    public PurchaseOrderResponse cancel(UUID id, String reason) {

        PurchaseOrder po = findOwned(id);

        User caller = currentUser();
        boolean isCreator = po.getCreatedBy() != null && po.getCreatedBy().getId().equals(caller.getId());
        boolean isOwnerPlus = ScopeContext.hasAtLeast(Scope.OWNER);

        if (!isOwnerPlus && !(isCreator && po.getStatus() == PurchaseOrderStatus.DRAFT)) {
            throw new AccessDeniedException(po.getStatus() == PurchaseOrderStatus.DRAFT
                    ? "Only the person who drafted this order, or Owner-level users or higher, can cancel it"
                    : "Only Owner-level users or higher can cancel a purchase order that has been sent");
        }

        if (po.getStatus() != PurchaseOrderStatus.DRAFT
                && po.getStatus() != PurchaseOrderStatus.RECEIVED
                && po.getStatus() != PurchaseOrderStatus.CANCELLED
                && (reason == null || reason.isBlank())) {

            throw new RuntimeException("A reason is required to cancel a purchase order that has been sent");
        }

        if (po.getStatus() == PurchaseOrderStatus.RECEIVED) {

            throw new RuntimeException(
                    "Cannot cancel a fully received purchase order.");
        }

        if (po.getStatus() == PurchaseOrderStatus.CANCELLED) {

            throw new RuntimeException(
                    "Purchase order is already cancelled.");
        }

        PurchaseOrderStatus previous = po.getStatus();
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        po = repository.save(po);

        writeHistory(po, previous, PurchaseOrderStatus.CANCELLED, caller, reason);

        for (PurchaseRequest pr : purchaseRequestRepository.findByPurchaseOrderId(po.getId())) {

            if (pr.getStatus() != PurchaseRequestStatus.ORDERED) {
                continue;
            }

            pr.setPurchaseOrder(null);
            pr.setStatus(PurchaseRequestStatus.APPROVED);
            purchaseRequestRepository.save(pr);

            writePurchaseRequestHistory(pr, PurchaseRequestStatus.ORDERED, PurchaseRequestStatus.APPROVED,
                    "Reverted: purchase order " + po.getPoNumber() + " was cancelled");
        }

        return map(po);
    }

    public List<PurchaseOrderResponse> getAll() {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return repository.findByTenantIdIn(tenantIds)
                .stream()
                .map(this::map)
                .toList();
    }

    public PurchaseOrderResponse getById(UUID id) {

        return map(findOwned(id));
    }

    public List<StatusHistoryResponse> getHistory(UUID id) {

        PurchaseOrder po = findOwned(id);

        return historyRepository
                .findByPurchaseOrder_IdOrderByCreatedAtDesc(po.getId())
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

    PurchaseOrder findOwned(UUID id) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));
    }

    private void writeHistory(
            PurchaseOrder po,
            PurchaseOrderStatus previous,
            PurchaseOrderStatus next,
            User changedBy) {

        writeHistory(po, previous, next, changedBy, null);
    }

    private void writeHistory(
            PurchaseOrder po,
            PurchaseOrderStatus previous,
            PurchaseOrderStatus next,
            User changedBy,
            String reason) {

        historyRepository.save(
                PurchaseOrderStatusHistory.builder()
                        .purchaseOrder(po)
                        .previousStatus(previous)
                        .newStatus(next)
                        .changedBy(changedBy)
                        .reason(reason)
                        .build());
    }

    private void writePurchaseRequestHistory(
            PurchaseRequest pr,
            PurchaseRequestStatus previous,
            PurchaseRequestStatus next,
            String reason) {

        purchaseRequestHistoryRepository.save(
                PurchaseRequestStatusHistory.builder()
                        .purchaseRequest(pr)
                        .previousStatus(previous)
                        .newStatus(next)
                        .changedBy(currentUser())
                        .reason(reason)
                        .build());
    }

    private User currentUser() {

        UUID userId = UUID.fromString(
                SecurityContextHolder.getContext().getAuthentication().getName());

        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    private PurchaseOrderResponse map(PurchaseOrder po) {

        // BE-11: same rule as BE-06 on Realisasi/PartInstance — cost fields
        // (header totalAmount, per-item price) redacted to null below OWNER
        // scope. Redacted, not omitted; every other field is unchanged.
        boolean canSeeCost = ScopeContext.hasAtLeast(Scope.OWNER);

        List<PurchaseOrderItemResponse> items = itemRepository
                .findByPurchaseOrderId(po.getId())
                .stream()
                .map(item -> PurchaseOrderItemResponse.builder()
                        .id(item.getId())
                        .partId(item.getPart().getId())
                        .partName(item.getPart().getName())
                        .quantity(item.getQuantity())
                        .price(canSeeCost ? item.getPrice() : null)
                        .build())
                .toList();

        List<UUID> purchaseRequestIds = purchaseRequestRepository
                .findByPurchaseOrderId(po.getId())
                .stream()
                .map(PurchaseRequest::getId)
                .toList();

        return PurchaseOrderResponse.builder()
                .id(po.getId())
                .poNumber(po.getPoNumber())
                .status(po.getStatus().name())
                .supplierId(po.getSupplier() != null ? po.getSupplier().getId() : null)
                .supplierName(po.getSupplier() != null ? po.getSupplier().getName() : null)
                .orderDate(po.getOrderDate())
                .totalAmount(canSeeCost ? po.getTotalAmount() : null)
                .items(items)
                .purchaseRequestIds(purchaseRequestIds)
                .createdById(po.getCreatedBy() != null ? po.getCreatedBy().getId() : null)
                .createdByName(po.getCreatedBy() != null ? po.getCreatedBy().getFullName() : null)
                .createdAt(po.getCreatedAt())
                .build();
    }
}
