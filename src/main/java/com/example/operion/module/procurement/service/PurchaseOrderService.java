package com.example.operion.module.procurement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.notification.service.NotificationService;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.repository.PartRepository;
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

    private final PartRepository partRepository;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    private final NotificationService notificationService;

    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow();

        Supplier supplier = null;

        if (request.getSupplierId() != null) {

            UUID rootTenantId = tenantHierarchyService.getRootTenantId(tenantId);

            supplier = supplierRepository
                    .findByIdAndTenantId(request.getSupplierId(), rootTenantId)
                    .orElseThrow(() -> new RuntimeException("Supplier not found"));
        }

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

        BigDecimal totalAmount = request.getItems().stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PurchaseOrder po = PurchaseOrder.builder()
                .tenant(tenant)
                .supplier(supplier)
                .poNumber("PO-" + System.currentTimeMillis())
                .orderDate(LocalDate.now())
                .status(PurchaseOrderStatus.DRAFT)
                .totalAmount(totalAmount)
                .build();

        po = repository.save(po);

        for (PurchaseOrderItemRequest itemRequest : request.getItems()) {

            Part part = partRepository
                    .findByIdAndTenantId(itemRequest.getPartId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Part not found"));

            itemRepository.save(
                    PurchaseOrderItem.builder()
                            .purchaseOrder(po)
                            .part(part)
                            .quantity(itemRequest.getQuantity())
                            .price(itemRequest.getPrice())
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

        writeHistory(po, null, PurchaseOrderStatus.DRAFT, null);

        return map(po);
    }

    @Transactional
    public PurchaseOrderResponse sendToSupplier(UUID id) {

        PurchaseOrder po = findOwned(id);

        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {

            throw new RuntimeException(
                    "Only DRAFT purchase orders can be sent to supplier.");
        }

        if (po.getSupplier() == null) {

            throw new RuntimeException(
                    "A supplier must be set before sending this purchase order.");
        }

        if (itemRepository.findByPurchaseOrderId(po.getId()).isEmpty()) {

            throw new RuntimeException(
                    "Purchase order has no items.");
        }

        PurchaseOrderStatus previous = po.getStatus();
        po.setStatus(PurchaseOrderStatus.ORDERED);
        po = repository.save(po);

        writeHistory(po, previous, PurchaseOrderStatus.ORDERED, null);

        return map(po);
    }

    @Transactional
    public PurchaseOrderResponse cancel(UUID id) {

        PurchaseOrder po = findOwned(id);

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

        writeHistory(po, previous, PurchaseOrderStatus.CANCELLED, null);

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

        historyRepository.save(
                PurchaseOrderStatusHistory.builder()
                        .purchaseOrder(po)
                        .previousStatus(previous)
                        .newStatus(next)
                        .changedBy(changedBy)
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
                        .reason(reason)
                        .build());
    }

    private PurchaseOrderResponse map(PurchaseOrder po) {

        List<PurchaseOrderItemResponse> items = itemRepository
                .findByPurchaseOrderId(po.getId())
                .stream()
                .map(item -> PurchaseOrderItemResponse.builder()
                        .id(item.getId())
                        .partId(item.getPart().getId())
                        .partName(item.getPart().getName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
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
                .totalAmount(po.getTotalAmount())
                .items(items)
                .purchaseRequestIds(purchaseRequestIds)
                .createdAt(po.getCreatedAt())
                .build();
    }
}
