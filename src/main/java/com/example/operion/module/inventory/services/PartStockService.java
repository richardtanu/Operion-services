package com.example.operion.module.inventory.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.inventory.dto.AdjustStockRequest;
import com.example.operion.module.inventory.dto.InventoryDashboardResponse;
import com.example.operion.module.inventory.dto.LowStockResponse;
import com.example.operion.module.inventory.dto.StockMovementResponse;
import com.example.operion.module.inventory.entity.StockMovement;
import com.example.operion.module.inventory.enums.InventoryStockStatus;
import com.example.operion.module.inventory.enums.StockMovementType;
import com.example.operion.module.inventory.enums.StockReferenceType;
import com.example.operion.module.inventory.repository.ReceiveStockRequest;
import com.example.operion.module.inventory.repository.StockMovementRepository;
import com.example.operion.module.notification.service.NotificationService;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.partinstance.service.PartInstanceService;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PartStockService {

        private final PartRepository partRepository;

        private final StockMovementRepository movementRepository;

        private final PartInstanceService partInstanceService;

        private final TenantHierarchyService tenantHierarchyService;

        private final NotificationService notificationService;

        public void adjustStock(
                        Part part,
                        int quantity,
                        StockMovementType movementType,
                        StockReferenceType referenceType,
                        UUID referenceId,
                        String notes) {

                int before = part.getCurrentStock();

                int after = before + quantity;

                if (after < 0) {
                        throw new RuntimeException(
                                        "Insufficient stock");
                }

                part.setCurrentStock(after);

                partRepository.save(part);

                Integer minimumStock = part.getMinimumStock();

                if (minimumStock != null && before > minimumStock && after <= minimumStock) {
                        notificationService.notifyLowStock(part);
                }

                StockMovement movement = StockMovement.builder()
                                .tenant(part.getTenant())
                                .part(part)
                                .movementType(movementType)
                                .referenceType(referenceType)
                                .referenceId(referenceId)
                                .quantity(quantity)
                                .beforeStock(before)
                                .afterStock(after)
                                .notes(notes)
                                .build();

                movementRepository.save(movement);
        }

        public void consumeStock(
                        Part part,
                        UUID workOrderId,
                        String notes) {

                adjustStock(
                                part,
                                -1,
                                StockMovementType.MAINTENANCE_USAGE,
                                StockReferenceType.WORK_ORDER,
                                workOrderId,
                                notes);
        }

        public void addStock(
                        Part part,
                        int quantity,
                        String notes) {

                adjustStock(
                                part,
                                quantity,
                                StockMovementType.PURCHASE,
                                StockReferenceType.PURCHASE,
                                null,
                                notes);
        }

        public void initialStock(
                        Part part,
                        int quantity) {

                adjustStock(
                                part,
                                quantity,
                                StockMovementType.INITIAL_STOCK,
                                StockReferenceType.MANUAL,
                                null,
                                "Initial stock");
        }

        public void manualAdjustment(
                        AdjustStockRequest request) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                Part part = partRepository
                                .findByIdAndTenantId(
                                                request.getPartId(),
                                                tenantId)
                                .orElseThrow();

                adjustStock(
                                part,
                                request.getQuantity(),
                                StockMovementType.ADJUSTMENT,
                                StockReferenceType.MANUAL,
                                null,
                                request.getNotes());
        }

        @Transactional
        public void receiveStock(
                        ReceiveStockRequest request) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                Part part = partRepository
                                .findByIdAndTenantId(
                                                request.getPartId(),
                                                tenantId)
                                .orElseThrow(() -> new RuntimeException("Part not found"));

                adjustStock(
                                part,
                                request.getQuantity(),
                                StockMovementType.PURCHASE,
                                StockReferenceType.MANUAL,
                                null,
                                request.getNotes());

                if (part.getCategory() == null
                                || !"Consumable".equals(part.getCategory().getName())) {

                        partInstanceService.generateInstances(
                                        part,
                                        request.getQuantity(),
                                        request.getNotes());
                }
        }

        private StockMovementResponse map(StockMovement movement) {

                return StockMovementResponse.builder()
                                .id(movement.getId())
                                .partName(movement.getPart().getName())
                                .movementType(movement.getMovementType())
                                .quantity(movement.getQuantity())
                                .beforeStock(movement.getBeforeStock())
                                .afterStock(movement.getAfterStock())
                                .notes(movement.getNotes())
                                .createdAt(movement.getCreatedAt())
                                .build();
        }

        public InventoryDashboardResponse dashboard() {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

                List<Part> parts = partRepository.findByTenantIdIn(tenantIds);

                int totalStock = parts.stream()
                                .mapToInt(Part::getCurrentStock)
                                .sum();

                List<Part> lowStockParts = getLowStockParts(tenantIds);

                int lowStock = lowStockParts.size();

                int outOfStock = (int) lowStockParts.stream()
                                .filter(part -> part.getCurrentStock() == 0)
                                .count();

                LocalDateTime today = LocalDate.now().atStartOfDay();

                int receivedToday = movementRepository
                                .findByTenantIdInAndMovementTypeAndCreatedAtAfter(
                                                tenantIds,
                                                StockMovementType.PURCHASE,
                                                today)
                                .stream()
                                .mapToInt(
                                                StockMovement::getQuantity)
                                .sum();

                int usedToday = movementRepository
                                .findByTenantIdInAndMovementTypeAndCreatedAtAfter(
                                                tenantIds,
                                                StockMovementType.MAINTENANCE_USAGE,
                                                today)
                                .stream()
                                .mapToInt(m -> Math.abs(m.getQuantity()))
                                .sum();

                return InventoryDashboardResponse.builder()
                                .totalParts(parts.size())
                                .totalStock(totalStock)
                                .lowStock(lowStock)
                                .outOfStock(outOfStock)
                                .stockReceivedToday(receivedToday)
                                .stockUsedToday(usedToday)
                                .build();
        }

        public List<LowStockResponse> lowStock() {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

                return getLowStockParts(tenantIds)
                                .stream()
                                .map(this::mapLowStock)
                                .toList();
        }

        public List<LowStockResponse> stockSummary() {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

                return partRepository.findByTenantIdIn(tenantIds)
                                .stream()
                                .map(this::mapLowStock)
                                .toList();
        }

        private List<Part> getLowStockParts(List<UUID> tenantIds) {

                return partRepository
                                .findByTenantIdInOrderByCurrentStockAsc(tenantIds)
                                .stream()
                                .filter(part -> getSafeStock(part.getCurrentStock()) <= getSafeStock(
                                                part.getMinimumStock()))
                                .toList();
        }

        private LowStockResponse mapLowStock(
                        Part part) {

                int currentStock = getSafeStock(part.getCurrentStock());
                int minimumStock = getSafeStock(part.getMinimumStock());

                InventoryStockStatus status = currentStock <= 0
                                ? InventoryStockStatus.OUT_OF_STOCK
                                : currentStock <= minimumStock
                                                ? InventoryStockStatus.LOW_STOCK
                                                : InventoryStockStatus.NORMAL;

                return LowStockResponse.builder()
                                .partId(part.getId())
                                .partName(part.getName())
                                .currentStock(currentStock)
                                .minimumStock(minimumStock)
                                .status(status)
                                .build();
        }

        private int getSafeStock(Integer stock) {
                return stock == null ? 0 : stock;
        }
}
