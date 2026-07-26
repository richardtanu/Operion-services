package com.example.operion.module.procurement.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.inventory.enums.StockMovementType;
import com.example.operion.module.inventory.enums.StockReferenceType;
import com.example.operion.module.inventory.services.PartStockService;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.partinstance.service.PartInstanceService;
import com.example.operion.module.procurement.dto.CreateGoodsReceiptRequest;
import com.example.operion.module.procurement.dto.GoodsReceiptItemRequest;
import com.example.operion.module.procurement.dto.GoodsReceiptItemResponse;
import com.example.operion.module.procurement.dto.GoodsReceiptResponse;
import com.example.operion.module.procurement.entity.GoodsReceipt;
import com.example.operion.module.procurement.entity.GoodsReceiptItem;
import com.example.operion.module.procurement.entity.PurchaseOrder;
import com.example.operion.module.procurement.entity.PurchaseOrderItem;
import com.example.operion.module.procurement.entity.PurchaseOrderStatusHistory;
import com.example.operion.module.procurement.enums.PurchaseOrderStatus;
import com.example.operion.module.procurement.repository.GoodsReceiptItemRepository;
import com.example.operion.module.procurement.repository.GoodsReceiptRepository;
import com.example.operion.module.procurement.repository.PurchaseOrderItemRepository;
import com.example.operion.module.procurement.repository.PurchaseOrderRepository;
import com.example.operion.module.procurement.repository.PurchaseOrderStatusHistoryRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GoodsReceiptService {

    private final GoodsReceiptRepository repository;

    private final GoodsReceiptItemRepository itemRepository;

    private final PurchaseOrderRepository purchaseOrderRepository;

    private final PurchaseOrderItemRepository purchaseOrderItemRepository;

    private final PurchaseOrderStatusHistoryRepository purchaseOrderHistoryRepository;

    private final PartStockService partStockService;

    private final PartInstanceService partInstanceService;

    private final PartRepository partRepository;

    private final UserRepository userRepository;

    private final TenantRepository tenantRepository;

    @Transactional
    public GoodsReceiptResponse receive(CreateGoodsReceiptRequest request) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow();

        PurchaseOrder po = purchaseOrderRepository
                .findByIdAndTenantId(request.getPurchaseOrderId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));

        if (po.getStatus() != PurchaseOrderStatus.ORDERED
                && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {

            throw new RuntimeException(
                    "Purchase order must be ORDERED or PARTIALLY_RECEIVED to receive goods.");
        }

        User receivedBy = userRepository
                .findById(request.getReceivedBy())
                .orElseThrow(() -> new RuntimeException("Receiving user not found"));

        Map<UUID, Integer> orderedByPart = purchaseOrderItemRepository
                .findByPurchaseOrderId(po.getId())
                .stream()
                .collect(Collectors.toMap(
                        i -> i.getPart().getId(),
                        PurchaseOrderItem::getQuantity,
                        Integer::sum));

        Map<UUID, Integer> alreadyReceivedByPart = new HashMap<>(
                itemRepository.findByGoodsReceipt_PurchaseOrder_Id(po.getId())
                        .stream()
                        .collect(Collectors.groupingBy(
                                i -> i.getPart().getId(),
                                Collectors.summingInt(GoodsReceiptItem::getQuantity))));

        for (GoodsReceiptItemRequest itemRequest : request.getItems()) {

            Integer ordered = orderedByPart.get(itemRequest.getPartId());

            if (ordered == null) {

                throw new RuntimeException(
                        "Part " + itemRequest.getPartId() + " was not ordered on this purchase order.");
            }

            int alreadyReceived = alreadyReceivedByPart.getOrDefault(itemRequest.getPartId(), 0);

            if (alreadyReceived + itemRequest.getQuantity() > ordered) {

                throw new RuntimeException(
                        "Received quantity for part " + itemRequest.getPartId()
                                + " exceeds the ordered quantity.");
            }
        }

        GoodsReceipt receipt = GoodsReceipt.builder()
                .tenant(tenant)
                .purchaseOrder(po)
                .receivedBy(receivedBy)
                .receivedDate(request.getReceivedDate())
                .notes(request.getNotes())
                .build();

        receipt = repository.save(receipt);

        for (GoodsReceiptItemRequest itemRequest : request.getItems()) {

            Part part = partRepository
                    .findByIdAndTenantId(itemRequest.getPartId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Part not found"));

            itemRepository.save(
                    GoodsReceiptItem.builder()
                            .goodsReceipt(receipt)
                            .part(part)
                            .quantity(itemRequest.getQuantity())
                            .build());

            partStockService.adjustStock(
                    part,
                    itemRequest.getQuantity(),
                    StockMovementType.PURCHASE,
                    StockReferenceType.PURCHASE,
                    receipt.getId(),
                    "Goods receipt for PO " + po.getPoNumber());

            if (part.getCategory() == null
                    || !"Consumable".equals(part.getCategory().getName())) {

                partInstanceService.generateInstances(
                        part,
                        itemRequest.getQuantity(),
                        "Goods receipt for PO " + po.getPoNumber());
            }

            alreadyReceivedByPart.merge(itemRequest.getPartId(), itemRequest.getQuantity(), Integer::sum);
        }

        boolean fullyReceived = orderedByPart.entrySet().stream()
                .allMatch(e -> alreadyReceivedByPart.getOrDefault(e.getKey(), 0) >= e.getValue());

        PurchaseOrderStatus previous = po.getStatus();
        PurchaseOrderStatus next = fullyReceived
                ? PurchaseOrderStatus.RECEIVED
                : PurchaseOrderStatus.PARTIALLY_RECEIVED;

        if (next != previous) {

            po.setStatus(next);
            purchaseOrderRepository.save(po);

            purchaseOrderHistoryRepository.save(
                    PurchaseOrderStatusHistory.builder()
                            .purchaseOrder(po)
                            .previousStatus(previous)
                            .newStatus(next)
                            .reason("Goods receipt " + receipt.getId())
                            .build());
        }

        return map(receipt);
    }

    public List<GoodsReceiptResponse> getByPurchaseOrder(UUID purchaseOrderId) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        purchaseOrderRepository
                .findByIdAndTenantId(purchaseOrderId, tenantId)
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));

        return repository.findByPurchaseOrderId(purchaseOrderId)
                .stream()
                .map(this::map)
                .toList();
    }

    private GoodsReceiptResponse map(GoodsReceipt receipt) {

        List<GoodsReceiptItemResponse> items = itemRepository
                .findByGoodsReceiptId(receipt.getId())
                .stream()
                .map(item -> GoodsReceiptItemResponse.builder()
                        .id(item.getId())
                        .partId(item.getPart().getId())
                        .partName(item.getPart().getName())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        return GoodsReceiptResponse.builder()
                .id(receipt.getId())
                .purchaseOrderId(receipt.getPurchaseOrder().getId())
                .poNumber(receipt.getPurchaseOrder().getPoNumber())
                .receivedById(receipt.getReceivedBy() != null ? receipt.getReceivedBy().getId() : null)
                .receivedByName(receipt.getReceivedBy() != null ? receipt.getReceivedBy().getFullName() : null)
                .receivedDate(receipt.getReceivedDate())
                .notes(receipt.getNotes())
                .items(items)
                .build();
    }
}
