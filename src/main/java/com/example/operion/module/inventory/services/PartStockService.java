package com.example.operion.module.inventory.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.CallerRoles;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.burnrate.dto.BurnRateResponse;
import com.example.operion.module.burnrate.service.BurnRateService;
import com.example.operion.module.inventory.dto.AdjustStockRequest;
import com.example.operion.module.inventory.dto.InventoryDashboardResponse;
import com.example.operion.module.inventory.dto.LowStockResponse;
import com.example.operion.module.inventory.dto.StockMovementResponse;
import com.example.operion.module.inventory.entity.StockMovement;
import com.example.operion.module.inventory.enums.InventoryStockStatus;
import com.example.operion.module.inventory.enums.LowBecause;
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

        private final UserRepository userRepository;

        private final BurnRateService burnRateService;

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

                // BE-27: alert once, when the item *becomes* low by the one rule (the
                // same burn-rate row judges before and after, so only the stock differs)
                if (quantity < 0) {

                        BurnRateResponse burnRate = burnRateService.forPart(part);

                        LowBecause wasLow = LowStockRule.evaluate(before, part.getMinimumStock(), burnRate);
                        LowBecause isLow = LowStockRule.evaluate(after, part.getMinimumStock(), burnRate);

                        if (wasLow == null && isLow != null) {
                                notificationService.notifyLowStock(part, isLow);
                        }
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
                                // BE-26: every movement names whose action wrote it
                                .actor(currentUserOrNull())
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

        /**
         * BE-25: a data-repair shortcut that skips the adjustment workflow, so
         * ADMIN only, with a reason. Normal changes go through an approved
         * stock adjustment, a goods receipt or a scan.
         */
        @Transactional
        public void manualAdjustment(
                        AdjustStockRequest request) {

                String note = dataRepairNote(request.getReason(), request.getNotes());

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
                                StockMovementType.ADJUSTMENT,
                                StockReferenceType.MANUAL,
                                null,
                                note);
        }

        /** BE-25: the other data-repair shortcut (stock in with no purchase behind it). ADMIN only, with a reason. */
        @Transactional
        public void receiveStock(
                        ReceiveStockRequest request) {

                String note = dataRepairNote(request.getReason(), request.getNotes());

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
                                note);

                // BE-25: the category's consumable flag, not its name (BE-04)
                if (!part.isConsumable()) {

                        partInstanceService.generateInstances(
                                        part,
                                        request.getQuantity(),
                                        note);
                }
        }

        /**
         * ADMIN check and the movement's note: who repaired it and why. The
         * movement has no actor column yet (BE-26 adds it), so the name goes in
         * the note.
         */
        private String dataRepairNote(String reason, String notes) {

                if (!CallerRoles.hasAny(Role.ADMIN)) {
                        throw new AccessDeniedException(
                                        "Only admins can set stock directly; request a stock adjustment instead");
                }

                if (reason == null || reason.isBlank()) {
                        throw new RuntimeException("A reason is required to set stock directly");
                }

                // BE-26: the admin is the movement's actor now, so the note says only why
                return "Data repair: " + reason.trim()
                                + (notes != null && !notes.isBlank() ? " (" + notes.trim() + ")" : "");
        }

        /** The signed-in user, or null when nobody is (a startup runner, a scheduled job). */
        private User currentUserOrNull() {

                var auth = SecurityContextHolder.getContext().getAuthentication();

                if (auth == null || auth.getName() == null) {
                        return null;
                }

                try {
                        return userRepository.findById(UUID.fromString(auth.getName())).orElse(null);
                } catch (IllegalArgumentException notAUserId) {
                        return null;
                }
        }

        /**
         * BE-26: one item's stock history, newest first, for anyone who can see
         * the item (the caller's tree). {@code limit} defaults to 50, at most 500.
         */
        public List<StockMovementResponse> movements(UUID partId, Integer limit) {

                List<UUID> tree = tenantHierarchyService.getEffectiveTenantIds(
                                UUID.fromString(TenantContext.getTenantId()));

                partRepository.findById(partId)
                                .filter(p -> tree.contains(p.getTenant().getId()))
                                .orElseThrow(() -> new RuntimeException("Part not found"));

                int max = limit == null ? 50 : Math.max(1, Math.min(limit, 500));

                return movementRepository.findByPartIdOrderByCreatedAtDesc(partId).stream()
                                .limit(max)
                                .map(this::map)
                                .toList();
        }

        private StockMovementResponse map(StockMovement movement) {

                return StockMovementResponse.builder()
                                .id(movement.getId())
                                .partId(movement.getPart().getId())
                                .partName(movement.getPart().getName())
                                .referenceType(movement.getReferenceType())
                                .referenceId(movement.getReferenceId())
                                .actorId(movement.getActor() != null ? movement.getActor().getId() : null)
                                .actorName(movement.getActor() != null ? movement.getActor().getFullName() : null)
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

                // BE-27: the one "low" rule, the same as the lists and the alert
                List<LowStockResponse> rows = rows(parts, tenantIds);

                int lowStock = (int) rows.stream().filter(r -> r.getLowBecause() != null).count();

                int outOfStock = (int) rows.stream()
                                .filter(r -> r.getStatus() == InventoryStockStatus.OUT_OF_STOCK)
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

                // BE-26: consumables taken off the shelf are used too, not only parts fitted in maintenance
                int usedToday = movementRepository
                                .findByTenantIdInAndMovementTypeInAndCreatedAtAfter(
                                                tenantIds,
                                                List.of(StockMovementType.MAINTENANCE_USAGE,
                                                                StockMovementType.CONSUMABLE_TAKE),
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

        /** {@code tenantId} (BE-26): one store's subtree inside the caller's tree; null = the whole tree. */
        public List<LowStockResponse> lowStock(UUID tenantId) {

                List<UUID> tenantIds = tenantHierarchyService.readScope(tenantId);

                // BE-27: low by the one rule, lowest stock first
                return rows(partRepository.findByTenantIdInOrderByCurrentStockAsc(tenantIds), tenantIds)
                                .stream()
                                .filter(r -> r.getLowBecause() != null)
                                .toList();
        }

        /** {@code tenantId} (BE-26): one store's subtree inside the caller's tree; null = the whole tree. */
        public List<LowStockResponse> stockSummary(UUID tenantId) {

                List<UUID> tenantIds = tenantHierarchyService.readScope(tenantId);

                return rows(partRepository.findByTenantIdIn(tenantIds), tenantIds);
        }

        /** One burn-rate pass for the whole scope, then the rule per item. */
        private List<LowStockResponse> rows(List<Part> parts, List<UUID> tenantIds) {

                Map<UUID, BurnRateResponse> burnRates = burnRateService.byPartIn(tenantIds);

                return parts.stream()
                                .map(part -> mapLowStock(part, burnRates.get(part.getId())))
                                .toList();
        }

        private LowStockResponse mapLowStock(
                        Part part,
                        BurnRateResponse burnRate) {

                int currentStock = getSafeStock(part.getCurrentStock());
                int minimumStock = getSafeStock(part.getMinimumStock());

                LowBecause lowBecause = LowStockRule.evaluate(currentStock, minimumStock, burnRate);

                InventoryStockStatus status = currentStock <= 0
                                ? InventoryStockStatus.OUT_OF_STOCK
                                : lowBecause != null
                                                ? InventoryStockStatus.LOW_STOCK
                                                : InventoryStockStatus.NORMAL;

                return LowStockResponse.builder()
                                .partId(part.getId())
                                .partName(part.getName())
                                .currentStock(currentStock)
                                .minimumStock(minimumStock)
                                .status(status)
                                .lowBecause(lowBecause)
                                .daysOfCover(LowStockRule.hasUsageData(burnRate) ? burnRate.getDaysOfCover() : null)
                                .targetDays(LowStockRule.hasUsageData(burnRate) ? burnRate.getTargetDays() : null)
                                .tenantId(part.getTenant().getId())
                                .tenantName(part.getTenant().getName())
                                .catalogPartId(part.getCatalogPart() != null ? part.getCatalogPart().getId() : null)
                                .build();
        }

        private int getSafeStock(Integer stock) {
                return stock == null ? 0 : stock;
        }
}
