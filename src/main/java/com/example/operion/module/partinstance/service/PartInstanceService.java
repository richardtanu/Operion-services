package com.example.operion.module.partinstance.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.repository.UserRepository;
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
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PartInstanceService {

    private static final String CONSUMABLE_CATEGORY_NAME = "Consumable";

    private final PartInstanceRepository repository;

    private final PartRepository partRepository;

    private final UserRepository userRepository;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    /**
     * Auto-generates system-issued barcode instances for a non-consumable
     * part on stock receipt. Never call this for a Consumable-category part.
     */
    @Transactional
    public void generateInstances(Part part, int quantity, String notes) {

        for (int i = 0; i < quantity; i++) {

            repository.save(
                    PartInstance.builder()
                            .tenant(part.getTenant())
                            .part(part)
                            .barcode(generateBarcode())
                            .status(PartInstanceStatus.IN_STOCK)
                            .notes(notes)
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

        if (!isConsumable(part)) {

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

        return map(repository.save(instance));
    }

    @Transactional
    public PartInstanceResponse exhaust(ExhaustRequest request) {

        PartInstance instance = findOwnedByBarcode(request.getBarcode());

        if (!isConsumable(instance.getPart())) {

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

    private boolean isConsumable(Part part) {

        return part.getCategory() != null
                && CONSUMABLE_CATEGORY_NAME.equals(part.getCategory().getName());
    }

    private String generateBarcode() {

        return "PI-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
    }

    private PartInstanceResponse map(PartInstance instance) {

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
                .build();
    }
}
