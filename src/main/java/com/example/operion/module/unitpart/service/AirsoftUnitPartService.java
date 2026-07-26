package com.example.operion.module.unitpart.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.airsoft.service.UnitOperationalRuleService;
import com.example.operion.module.analytics.service.UnitHealthService;
import com.example.operion.module.inventory.dto.StockAdjustmentRequest;
import com.example.operion.module.inventory.enums.StockMovementType;
import com.example.operion.module.inventory.enums.StockReferenceType;
import com.example.operion.module.inventory.services.PartStockService;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.parthistory.entity.PartConditionHistory;
import com.example.operion.module.parthistory.repository.PartConditionHistoryRepository;
import com.example.operion.module.partinstance.entity.PartInstance;
import com.example.operion.module.partinstance.service.PartInstanceService;
import com.example.operion.module.serviceevent.entity.ServiceEvent;
import com.example.operion.module.serviceevent.enums.ServiceEventType;
import com.example.operion.module.serviceevent.repository.ServiceEventRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.unitpart.dto.AirsoftUnitPartResponse;
import com.example.operion.module.unitpart.dto.InstallPartRequest;
import com.example.operion.module.unitpart.dto.ReplacePartRequest;
import com.example.operion.module.unitpart.dto.UpdatePartConditionRequest;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.unitpart.enums.UnitPartStatus;
import com.example.operion.module.unitpart.repository.AirsoftUnitPartRepository;
import com.example.operion.module.workorder.dto.CreateWorkOrderRequest;
import com.example.operion.module.workorder.entity.WorkOrder;
import com.example.operion.module.workorder.enums.WorkOrderPriority;
import com.example.operion.module.workorder.enums.WorkOrderStatus;
import com.example.operion.module.workorder.repository.WorkOrderRepository;
import com.example.operion.module.workorder.service.WorkOrderService;

import jakarta.transaction.Transactional;

import com.example.operion.module.unitpart.enums.PartCondition;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AirsoftUnitPartService {

        private final UnitOperationalRuleService operationalRuleService;

        private final UnitHealthService unitHealthService;

        private final AirsoftUnitPartRepository repository;

        private final TenantRepository tenantRepository;

        private final AirsoftUnitRepository airsoftUnitRepository;

        private final PartRepository partRepository;

        private final PartConditionHistoryRepository historyRepository;

        private final WorkOrderService workOrderService;

        private final WorkOrderRepository workOrderRepository;

        private final ServiceEventRepository serviceEventRepository;

        private final PartStockService stockAdjustmentRequest;

        private final PartInstanceService partInstanceService;

        @Transactional
        public AirsoftUnitPartResponse installPart(
                        InstallPartRequest request) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                Tenant tenant = tenantRepository
                                .findById(tenantId)
                                .orElseThrow();

                AirsoftUnit unit = airsoftUnitRepository
                                .findByIdAndTenantId(request.getAirsoftUnitId(), tenantId)
                                .orElseThrow();

                Part part = partRepository
                                .findByIdAndTenantId(
                                                request.getPartId(),
                                                tenantId)
                                .orElseThrow(() -> new RuntimeException("Replacement part not found"));

                UUID partTypeId = part.getPartType().getId();

                boolean installedSomewhere = repository.existsByPartIdAndStatus(
                                part.getId(),
                                UnitPartStatus.INSTALLED);

                if (installedSomewhere) {
                        throw new RuntimeException(
                                        "This physical part is already installed on another unit.");
                }

                boolean alreadyInstalled = repository
                                .existsByAirsoftUnitIdAndPart_PartType_IdAndStatus(
                                                unit.getId(),
                                                partTypeId,
                                                UnitPartStatus.INSTALLED);

                if (alreadyInstalled) {
                        throw new RuntimeException(
                                        "Unit already has this part type installed: " + part.getPartType().getName());
                }

                PartInstance instance = null;

                if (request.getBarcode() != null && !request.getBarcode().isBlank()) {

                        instance = partInstanceService.findByBarcodeForInstall(
                                        request.getBarcode(),
                                        part.getId());
                }

                AirsoftUnitPart installedPart = AirsoftUnitPart.builder()
                                .tenant(tenant)
                                .airsoftUnit(unit)
                                .part(part)
                                .status(UnitPartStatus.INSTALLED)
                                .installedDate(
                                                request.getInstalledDate())
                                .notes(request.getNotes())
                                .build();

                AirsoftUnitPart saved = repository.save(installedPart);

                if (instance != null) {
                        partInstanceService.markInstalled(instance, saved);
                }

                stockAdjustmentRequest.adjustStock(
                                part,
                                -1,
                                StockMovementType.MAINTENANCE_USAGE,
                                StockReferenceType.UNIT_INSTALL,
                                saved.getId(),
                                "Installed on " + unit.getName());

                return map(saved);
        }

        @Transactional
        public AirsoftUnitPartResponse replacePart(
                        UUID installedPartId,
                        ReplacePartRequest request) {

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                /*
                 * FIND INSTALLED PART
                 */
                AirsoftUnitPart oldInstalledPart = repository
                                .findByIdAndTenantId(
                                                installedPartId,
                                                tenantId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Installed part not found"));

                /*
                 * VALIDATE INSTALLED STATUS
                 */
                if (oldInstalledPart.getStatus() != UnitPartStatus.INSTALLED) {

                        throw new RuntimeException(
                                        "Part is not currently installed");
                }

                /*
                 * STORE PREVIOUS CONDITION
                 */
                PartCondition previousCondition = oldInstalledPart.getCondition();

                /*
                 * FIND REPLACEMENT PART
                 */
                Part newPart = partRepository
                                .findById(request.getNewPartId())
                                .orElseThrow(() -> new RuntimeException(
                                                "Replacement part not found"));

                /*
                 * VALIDATE SAME PART TYPE
                 */
                if (!oldInstalledPart.getPart()
                                .getPartType()
                                .getId()
                                .equals(newPart.getPartType().getId())) {

                        throw new RuntimeException(
                                        "Replacement part must have the same Part Type.");
                }

                /*
                 * PREVENT REPLACING WITH SAME PART
                 */
                if (oldInstalledPart.getPart()
                                .getId()
                                .equals(newPart.getId())) {

                        throw new RuntimeException(
                                        "Cannot replace a part with itself.");
                }

                /*
                 * ENSURE REPLACEMENT PART IS NOT ALREADY INSTALLED
                 */
                boolean alreadyInstalled = repository.existsByPartIdAndStatus(
                                newPart.getId(),
                                UnitPartStatus.INSTALLED);

                AirsoftUnitPart installed = repository
                                .findByPartIdAndTenantIdAndStatus(
                                                newPart.getId(),
                                                tenantId,
                                                UnitPartStatus.INSTALLED)
                                .orElse(null);

                if (installed != null) {

                        throw new RuntimeException(
                                        "Replacement part is already installed on unit: "
                                                        + installed.getAirsoftUnit().getName());
                }

                /*
                 * REMOVE OLD PART
                 */
                oldInstalledPart.setStatus(
                                UnitPartStatus.REMOVED);

                oldInstalledPart.setRemovedDate(
                                request.getRemovedDate());

                oldInstalledPart.setCondition(
                                request.getRemovedPartCondition());

                oldInstalledPart.setTechnicianAssessment(
                                request.getTechnicianAssessment());

                repository.save(oldInstalledPart);

                /*
                 * SAVE CONDITION HISTORY
                 */
                PartConditionHistory history = PartConditionHistory.builder()
                                .airsoftUnitPart(oldInstalledPart)
                                .previousCondition(previousCondition)
                                .newCondition(request.getRemovedPartCondition())
                                .reason(request.getReason())
                                .technicianAssessment(request.getTechnicianAssessment())
                                .build();

                historyRepository.save(history);

                /*
                 * RESOLVE OPTIONAL BARCODE INSTANCE
                 */
                PartInstance instance = null;

                if (request.getBarcode() != null && !request.getBarcode().isBlank()) {

                        instance = partInstanceService.findByBarcodeForInstall(
                                        request.getBarcode(),
                                        newPart.getId());
                }

                /*
                 * INSTALL NEW PART
                 */
                AirsoftUnitPart replacement = AirsoftUnitPart.builder()
                                .tenant(oldInstalledPart.getTenant())
                                .airsoftUnit(oldInstalledPart.getAirsoftUnit())
                                .part(newPart)
                                .status(UnitPartStatus.INSTALLED)
                                .condition(PartCondition.GOOD)
                                .installedDate(request.getInstalledDate())
                                .notes(request.getNotes())
                                .build();

                AirsoftUnitPart saved = repository.save(replacement);

                if (instance != null) {
                        partInstanceService.markInstalled(instance, saved);
                }

                /*
                 * RESOLVE OPTIONAL WORK ORDER
                 */
                WorkOrder linkedWorkOrder = null;

                if (request.getWorkOrderId() != null) {

                        linkedWorkOrder = workOrderRepository
                                        .findByIdAndTenantId(
                                                        request.getWorkOrderId(),
                                                        oldInstalledPart.getTenant().getId())
                                        .orElseThrow(() -> new RuntimeException("Work order not found"));
                }

                /*
                 * CREATE SERVICE EVENT
                 */
                ServiceEvent serviceEvent = ServiceEvent.builder()
                                .tenant(oldInstalledPart.getTenant())
                                .airsoftUnit(oldInstalledPart.getAirsoftUnit())
                                .workOrder(linkedWorkOrder)
                                .eventType(ServiceEventType.REPAIR)
                                .issue("Part replacement")
                                .actionTaken(
                                                "Replaced "
                                                                + oldInstalledPart.getPart().getName()
                                                                + " with "
                                                                + newPart.getName())
                                .serviceDate(LocalDate.now())
                                .build();

                serviceEventRepository.save(serviceEvent);

                /*
                 * RECALCULATE UNIT HEALTH
                 */
                unitHealthService.recalculateHealth(
                                saved.getAirsoftUnit());

                /*
                 * RE-EVALUATE OPERATIONAL STATUS
                 */
                operationalRuleService.evaluate(
                                saved.getAirsoftUnit());

                /*
                 * AUTO COMPLETE WORK ORDER
                 */
                workOrderService.autoCompleteForPart(
                                oldInstalledPart.getId());

                /*
                 * CONSUME INVENTORY
                 */
                stockAdjustmentRequest.consumeStock(
                                newPart,
                                request.getWorkOrderId(),
                                "Replacement during maintenance");

                return map(saved);
        }

        public List<AirsoftUnitPartResponse> getInstalledParts(UUID unitId) {

                return repository
                                .findByAirsoftUnitIdAndStatus(
                                                unitId,
                                                UnitPartStatus.INSTALLED)
                                .stream()
                                .map(this::map)
                                .toList();
        }

        private AirsoftUnitPartResponse map(
                        AirsoftUnitPart item) {

                Long usageDays = null;
                String categoryName = null;
                UUID categoryId = null;

                String partTypeName = null;
                UUID partTypeId = null;

                if (item.getPart().getCategory() != null) {
                        categoryName = item.getPart().getCategory().getName();
                        categoryId = item.getPart().getCategory().getId();
                }

                if (item.getPart().getPartType() != null) {

                        partTypeName = item.getPart().getPartType().getName();
                        partTypeId = item.getPart().getPartType().getId();
                }

                if (item.getInstalledDate() != null &&
                                item.getRemovedDate() != null) {

                        usageDays = ChronoUnit.DAYS.between(
                                        item.getInstalledDate(),
                                        item.getRemovedDate());
                }

                return AirsoftUnitPartResponse.builder()
                                .airsoftUnitPartId(item.getId())
                                .unitName(item.getAirsoftUnit().getName())
                                .partName(item.getPart().getName())
                                .partTypeId(partTypeId)
                                .partTypeName(partTypeName)
                                .brand(
                                                item.getPart().getBrand())
                                .category(categoryName)
                                .categoryId(categoryId)
                                .categoryName(categoryName)
                                .status(item.getStatus().name())
                                .installedDate(
                                                item.getInstalledDate())
                                .removedDate(item.getRemovedDate())

                                .condition(
                                                item.getCondition() != null
                                                                ? item.getCondition().name()
                                                                : null)

                                .technicianAssessment(
                                                item.getTechnicianAssessment())
                                .notes(item.getNotes())
                                .usageDays(usageDays)

                                .build();
        }

        public List<AirsoftUnitPartResponse> getHistory(UUID unitId) {

                return repository
                                .findByAirsoftUnitIdOrderByInstalledDateDesc(
                                                unitId)
                                .stream()
                                .map(this::map)
                                .toList();
        }

        public AirsoftUnitPartResponse updateCondition(
                        UUID unitPartId,
                        UpdatePartConditionRequest request) {

                // AirsoftUnitPart item = repository.findById(unitPartId)
                // .orElseThrow();

                UUID tenantId = UUID.fromString(
                                TenantContext.getTenantId());

                AirsoftUnitPart item = repository
                                .findByIdAndTenantId(
                                                unitPartId,
                                                tenantId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Installed part not found"));

                PartCondition previousCondition = item.getCondition();

                item.setCondition(
                                request.getCondition());

                item.setTechnicianAssessment(
                                request.getTechnicianAssessment());

                AirsoftUnitPart saved = repository.save(item);

                /*
                 * SAVE HISTORY
                 */

                PartConditionHistory history = PartConditionHistory.builder()
                                .airsoftUnitPart(saved)
                                .previousCondition(
                                                previousCondition)
                                .newCondition(
                                                request.getCondition())
                                .reason(request.getReason())
                                .technicianAssessment(
                                                request.getTechnicianAssessment())
                                .build();

                historyRepository.save(history);

                /*
                 * RECALCULATE HEALTH
                 */

                unitHealthService.recalculateHealth(
                                saved.getAirsoftUnit());

                /*
                 * RE-EVALUATE OPERATIONAL STATUS
                 */

                operationalRuleService.evaluate(
                                saved.getAirsoftUnit());

                if (request.getCondition() == PartCondition.FAILED) {

                        UUID unitId = saved.getAirsoftUnit().getId();

                        boolean alreadyOpen = workOrderService.hasOpenWorkOrder(
                                        unitId);

                        if (!alreadyOpen) {

                                CreateWorkOrderRequest wo = new CreateWorkOrderRequest();

                                wo.setAirsoftUnitId(unitId);

                                wo.setPriority(WorkOrderPriority.CRITICAL);

                                wo.setTitle("Replace Failed Part");

                                wo.setDescription(saved.getPart().getName()
                                                + " marked as FAILED and requires replacement");

                                workOrderService.create(wo);
                        }
                }

                return map(saved);
        }
}