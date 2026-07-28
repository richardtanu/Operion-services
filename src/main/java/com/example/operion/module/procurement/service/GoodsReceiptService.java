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
import com.example.operion.module.procurement.entity.Realisasi;
import com.example.operion.module.procurement.entity.RealisasiItem;
import com.example.operion.module.procurement.enums.PurchaseOrderStatus;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.repository.GoodsReceiptItemRepository;
import com.example.operion.module.procurement.repository.GoodsReceiptRepository;
import com.example.operion.module.procurement.repository.PurchaseOrderItemRepository;
import com.example.operion.module.procurement.repository.PurchaseOrderRepository;
import com.example.operion.module.procurement.repository.PurchaseOrderStatusHistoryRepository;
import com.example.operion.module.procurement.repository.RealisasiItemRepository;
import com.example.operion.module.procurement.repository.RealisasiRepository;
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

    private final RealisasiRepository realisasiRepository;

    private final RealisasiItemRepository realisasiItemRepository;

    private final PartStockService partStockService;

    private final PartInstanceService partInstanceService;

    private final PartRepository partRepository;

    private final UserRepository userRepository;

    private final TenantRepository tenantRepository;

    @Transactional
    public GoodsReceiptResponse receive(CreateGoodsReceiptRequest request) {

        boolean hasPo = request.getPurchaseOrderId() != null;
        boolean hasRealisasi = request.getRealisasiId() != null;

        if (hasPo == hasRealisasi) {

            throw new RuntimeException(
                    "Exactly one of purchaseOrderId or realisasiId must be provided.");
        }

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow();

        User receivedBy = userRepository
                .findById(request.getReceivedBy())
                .orElseThrow(() -> new RuntimeException("Receiving user not found"));

        if (hasRealisasi) {
            return receiveAgainstRealisasi(request, tenant, tenantId, receivedBy);
        }

        return receiveAgainstPurchaseOrder(request, tenant, tenantId, receivedBy);
    }

    private GoodsReceiptResponse receiveAgainstPurchaseOrder(
            CreateGoodsReceiptRequest request, Tenant tenant, UUID tenantId, User receivedBy) {

        PurchaseOrder po = purchaseOrderRepository
                .findByIdAndTenantId(request.getPurchaseOrderId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));

        if (po.getStatus() != PurchaseOrderStatus.ORDERED
                && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {

            throw new RuntimeException(
                    "Purchase order must be ORDERED or PARTIALLY_RECEIVED to receive goods.");
        }

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

    /**
     * Realisasi-based receiving path. Sources ordered/already-received
     * quantities from RealisasiItem (converted to stock-unit equivalence via
     * conversionFactor, since purchasedQty is in purchase UOM) instead of
     * PurchaseOrderItem, but reuses the exact same stock-adjustment +
     * PartInstance-generation calls as the PO path. Enforces DL-11 always:
     * the receiver must differ from whoever created the Realisasi — no scope
     * exemption here, unlike the PRA-approver/Realisasi-creator segregation
     * rule in RealisasiService, per §6's plain statement of the rule.
     */
    private GoodsReceiptResponse receiveAgainstRealisasi(
            CreateGoodsReceiptRequest request, Tenant tenant, UUID tenantId, User receivedBy) {

        Realisasi realisasi = realisasiRepository
                .findByIdAndTenantId(request.getRealisasiId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Realisasi not found"));

        if (realisasi.getStatus() != RealisasiStatus.APPROVED) {

            throw new RuntimeException("Realisasi must be APPROVED to receive goods.");
        }

        if (realisasi.getCreatedBy() != null
                && realisasi.getCreatedBy().getId().equals(receivedBy.getId())) {

            throw new RuntimeException(
                    "Goods receipt must be performed by a different user than the one who "
                            + "created the Realisasi (DL-11).");
        }

        // Attachment (the marketplace receipt) is optional when the Realisasi
        // is first recorded — logging an urgent/retro purchase shouldn't wait
        // on the receipt document arriving — but required before it can
        // affect real inventory via goods receipt.
        if (realisasi.getAttachments() == null || realisasi.getAttachments().isBlank()) {

            throw new RuntimeException(
                    "Realisasi " + realisasi.getExternalOrderRef()
                            + " has no attachment on file. Attach the marketplace receipt before receiving goods.");
        }

        GoodsReceipt receipt = GoodsReceipt.builder()
                .tenant(tenant)
                .realisasi(realisasi)
                .receivedBy(receivedBy)
                .receivedDate(request.getReceivedDate())
                .notes(request.getNotes())
                .build();

        receipt = repository.save(receipt);

        for (GoodsReceiptItemRequest itemRequest : request.getItems()) {

            if (itemRequest.getRealisasiItemId() == null) {

                throw new RuntimeException(
                        "realisasiItemId is required for each item on the Realisasi receiving path.");
            }

            RealisasiItem realisasiItem = realisasiItemRepository
                    .findById(itemRequest.getRealisasiItemId())
                    .orElseThrow(() -> new RuntimeException("Realisasi item not found"));

            if (!realisasiItem.getRealisasi().getId().equals(realisasi.getId())) {

                throw new RuntimeException(
                        "Realisasi item does not belong to the specified Realisasi.");
            }

            int purchasedStockUnits = java.math.BigDecimal.valueOf(realisasiItem.getPurchasedQty())
                    .multiply(realisasiItem.getConversionFactor())
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .intValue();

            int alreadyReceived = itemRepository
                    .findByRealisasiItemId(realisasiItem.getId())
                    .stream()
                    .mapToInt(GoodsReceiptItem::getQuantity)
                    .sum();

            if (alreadyReceived + itemRequest.getQuantity() > purchasedStockUnits) {

                throw new RuntimeException(
                        "Received quantity for part " + itemRequest.getPartId()
                                + " exceeds the purchased quantity on this Realisasi line.");
            }

            Part part = partRepository
                    .findByIdAndTenantId(itemRequest.getPartId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Part not found"));

            itemRepository.save(
                    GoodsReceiptItem.builder()
                            .goodsReceipt(receipt)
                            .part(part)
                            .realisasiItem(realisasiItem)
                            .quantity(itemRequest.getQuantity())
                            .build());

            partStockService.adjustStock(
                    part,
                    itemRequest.getQuantity(),
                    StockMovementType.PURCHASE,
                    StockReferenceType.PURCHASE,
                    receipt.getId(),
                    "Goods receipt for Realisasi " + realisasi.getExternalOrderRef());

            if (part.getCategory() == null
                    || !"Consumable".equals(part.getCategory().getName())) {

                // Per-unit landed cost (decision #5, specific-identification
                // costing): the line's allocated landed cost spread across
                // its purchased qty in STOCK units, not the qty received in
                // this one call (a line can be received across several
                // partial receipts) and not purchasedQty alone — purchasedQty
                // is in purchase UOM (see purchasedStockUnits above), so it
                // must be converted the same way the over-receipt check is.
                java.math.BigDecimal unitLandedCost = realisasiItem.getAllocatedLandedCost()
                        .divide(
                                java.math.BigDecimal.valueOf(purchasedStockUnits),
                                4,
                                java.math.RoundingMode.HALF_UP);

                partInstanceService.generateInstances(
                        part,
                        itemRequest.getQuantity(),
                        "Goods receipt for Realisasi " + realisasi.getExternalOrderRef(),
                        unitLandedCost);
            }
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

    public List<GoodsReceiptResponse> getByRealisasi(UUID realisasiId) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        realisasiRepository
                .findByIdAndTenantId(realisasiId, tenantId)
                .orElseThrow(() -> new RuntimeException("Realisasi not found"));

        return repository.findByRealisasiId(realisasiId)
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
                        .realisasiItemId(item.getRealisasiItem() != null ? item.getRealisasiItem().getId() : null)
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        return GoodsReceiptResponse.builder()
                .id(receipt.getId())
                .purchaseOrderId(receipt.getPurchaseOrder() != null ? receipt.getPurchaseOrder().getId() : null)
                .poNumber(receipt.getPurchaseOrder() != null ? receipt.getPurchaseOrder().getPoNumber() : null)
                .realisasiId(receipt.getRealisasi() != null ? receipt.getRealisasi().getId() : null)
                .externalOrderRef(
                        receipt.getRealisasi() != null ? receipt.getRealisasi().getExternalOrderRef() : null)
                .receivedById(receipt.getReceivedBy() != null ? receipt.getReceivedBy().getId() : null)
                .receivedByName(receipt.getReceivedBy() != null ? receipt.getReceivedBy().getFullName() : null)
                .receivedDate(receipt.getReceivedDate())
                .notes(receipt.getNotes())
                .items(items)
                .build();
    }
}
