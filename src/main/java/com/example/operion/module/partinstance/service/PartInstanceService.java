package com.example.operion.module.partinstance.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.ScopeContext;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.inventory.enums.StockMovementType;
import com.example.operion.module.inventory.enums.StockReferenceType;
import com.example.operion.module.inventory.services.PartStockService;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.partinstance.dto.ExhaustRequest;
import com.example.operion.module.partinstance.dto.PartInstanceResponse;
import com.example.operion.module.partinstance.dto.ScanInRequest;
import com.example.operion.module.partinstance.dto.TakeRequest;
import com.example.operion.module.partinstance.entity.PartInstance;
import com.example.operion.module.partinstance.enums.PartInstanceStatus;
import com.example.operion.module.partinstance.repository.PartInstanceRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;

import jakarta.transaction.Transactional;

@Service
public class PartInstanceService {

    private final PartInstanceRepository repository;

    private final PartRepository partRepository;

    private final UserRepository userRepository;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    private final PartStockService partStockService;

    /**
     * Explicit constructor (not @RequiredArgsConstructor) because @Lazy on
     * partStockService below needs to land on the constructor parameter to
     * take effect — Lombok does not copy field annotations onto the
     * generated constructor by default. Lazy breaks a real cycle:
     * PartStockService already depends on PartInstanceService (for
     * generateInstances on receipt), and take() needs to call back into
     * PartStockService. Safe — take() is never invoked during context
     * startup.
     */
    public PartInstanceService(
            PartInstanceRepository repository,
            PartRepository partRepository,
            UserRepository userRepository,
            TenantRepository tenantRepository,
            TenantHierarchyService tenantHierarchyService,
            @Lazy PartStockService partStockService) {

        this.repository = repository;
        this.partRepository = partRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.tenantHierarchyService = tenantHierarchyService;
        this.partStockService = partStockService;
    }

    /**
     * Auto-generates system-issued barcode instances for a non-consumable
     * part on stock receipt. Never call this for a Consumable-category part.
     */
    @Transactional
    public void generateInstances(Part part, int quantity, String notes) {

        generateInstances(part, quantity, notes, null);
    }

    /**
     * Same as {@link #generateInstances(Part, int, String)}, but also stamps
     * each instance with a per-unit landed cost (decision #5, specific-
     * identification costing). Pass null when no itemized cost data exists
     * for this receipt (manual receipt, legacy PO-based receipt).
     */
    @Transactional
    public void generateInstances(Part part, int quantity, String notes, BigDecimal landedCost) {

        for (int i = 0; i < quantity; i++) {

            repository.save(
                    PartInstance.builder()
                            .tenant(part.getTenant())
                            .part(part)
                            .barcode(generateBarcode())
                            .status(PartInstanceStatus.IN_STOCK)
                            .notes(notes)
                            .landedCost(landedCost)
                            .build());
        }
    }

    @Transactional
    public PartInstanceResponse scanIn(ScanInRequest request) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow();

        Part part = partRepository
                .findByIdAndTenantId(request.getPartId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Part not found"));

        if (!part.isConsumable()) {

            throw new RuntimeException(
                    "Only Consumable-category parts can be scanned in; "
                            + "instances for other parts are auto-generated on receipt.");
        }

        if (repository.existsByBarcode(request.getBarcode())) {

            throw new RuntimeException(
                    "Barcode is already registered.");
        }

        PartInstance instance = PartInstance.builder()
                .tenant(tenant)
                .part(part)
                .barcode(request.getBarcode())
                .status(PartInstanceStatus.IN_STOCK)
                .notes(request.getNotes())
                .build();

        return map(repository.save(instance));
    }

    @Transactional
    public PartInstanceResponse take(TakeRequest request) {

        PartInstance instance = findOwnedByBarcode(request.getBarcode());

        if (instance.getStatus() != PartInstanceStatus.IN_STOCK) {

            throw new RuntimeException(
                    "Only IN_STOCK instances can be taken.");
        }

        User takenBy = userRepository
                .findById(request.getTakenBy())
                .orElseThrow(() -> new RuntimeException("User not found"));

        instance.setStatus(PartInstanceStatus.TAKEN);
        instance.setTakenAt(LocalDateTime.now());
        instance.setTakenBy(takenBy);

        PartInstance saved = repository.save(instance);

        /*
         * Only Consumable takes decrement stock here. Non-Consumable
         * (sparepart) instances are never exhausted (see exhaust() below) —
         * their stock effect happens once, at AirsoftUnitPartService.install/
         * replacePart, regardless of whether the instance was taken first.
         * Decrementing here too would double-count that unit.
         */
        if (instance.getPart().isConsumable()) {

            partStockService.adjustStock(
                    instance.getPart(),
                    -1,
                    StockMovementType.CONSUMABLE_TAKE,
                    StockReferenceType.PART_INSTANCE,
                    instance.getId(),
                    "Taken via barcode " + instance.getBarcode());
        }

        return map(saved);
    }

    @Transactional
    public PartInstanceResponse exhaust(ExhaustRequest request) {

        PartInstance instance = findOwnedByBarcode(request.getBarcode());

        if (!instance.getPart().isConsumable()) {

            throw new RuntimeException(
                    "Only Consumable-category instances can be exhausted; "
                            + "spareparts transition to INSTALLED instead.");
        }

        if (instance.getStatus() != PartInstanceStatus.TAKEN) {

            throw new RuntimeException(
                    "Only TAKEN instances can be exhausted.");
        }

        instance.setStatus(PartInstanceStatus.EXHAUSTED);
        instance.setExhaustedAt(LocalDateTime.now());

        if (request.getNotes() != null) {
            instance.setNotes(request.getNotes());
        }

        return map(repository.save(instance));
    }

    /**
     * Links a specific barcode instance to an AirsoftUnitPart once it's
     * installed. Called from AirsoftUnitPartService when a barcode is
     * supplied on install/replace.
     */
    @Transactional
    public PartInstance markInstalled(PartInstance instance, AirsoftUnitPart unitPart) {

        if (instance.getStatus() != PartInstanceStatus.IN_STOCK
                && instance.getStatus() != PartInstanceStatus.TAKEN) {

            throw new RuntimeException(
                    "Only IN_STOCK or TAKEN instances can be installed.");
        }

        instance.setStatus(PartInstanceStatus.INSTALLED);
        instance.setInstalledUnitPart(unitPart);

        return repository.save(instance);
    }

    public PartInstance findByBarcodeForInstall(String barcode, UUID partId) {

        PartInstance instance = findOwnedByBarcode(barcode);

        if (!instance.getPart().getId().equals(partId)) {

            throw new RuntimeException(
                    "Barcode does not belong to the part being installed.");
        }

        return instance;
    }

    public List<PartInstanceResponse> getByPart(UUID partId) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return repository.findByPartIdAndTenantIdIn(partId, tenantIds)
                .stream()
                .map(this::map)
                .toList();
    }

    private PartInstance findOwnedByBarcode(String barcode) {

        UUID tenantId = UUID.fromString(
                TenantContext.getTenantId());

        return repository
                .findByBarcodeAndTenantId(barcode, tenantId)
                .orElseThrow(() -> new RuntimeException("Part instance not found for barcode"));
    }


    private String generateBarcode() {

        return "PI-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
    }

    private PartInstanceResponse map(PartInstance instance) {

        // BE-06: landed cost redacted below OWNER scope — see
        // OPERION_BE_CHANGE_QUEUE.md BE-06. Redacted (null), not omitted.
        boolean canSeeCost = ScopeContext.hasAtLeast(Scope.OWNER);

        return PartInstanceResponse.builder()
                .id(instance.getId())
                .partId(instance.getPart().getId())
                .partName(instance.getPart().getName())
                .barcode(instance.getBarcode())
                .status(instance.getStatus().name())
                .receivedAt(instance.getReceivedAt())
                .takenAt(instance.getTakenAt())
                .takenByName(instance.getTakenBy() != null ? instance.getTakenBy().getFullName() : null)
                .exhaustedAt(instance.getExhaustedAt())
                .installedUnitPartId(instance.getInstalledUnitPart() != null
                        ? instance.getInstalledUnitPart().getId()
                        : null)
                .notes(instance.getNotes())
                .landedCost(canSeeCost ? instance.getLandedCost() : null)
                .build();
    }
}
