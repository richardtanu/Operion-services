package com.example.operion.module.procurement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.ScopeContext;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.notification.service.NotificationService;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.procurement.dto.CreateRealisasiRequest;
import com.example.operion.module.procurement.dto.RealisasiItemRequest;
import com.example.operion.module.procurement.dto.RealisasiItemResponse;
import com.example.operion.module.procurement.dto.RealisasiResponse;
import com.example.operion.module.procurement.dto.RejectRealisasiRequest;
import com.example.operion.module.procurement.dto.StatusHistoryResponse;
import com.example.operion.module.procurement.dto.SupersedeRealisasiRequest;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorizationItem;
import com.example.operion.module.procurement.entity.Realisasi;
import com.example.operion.module.procurement.entity.RealisasiItem;
import com.example.operion.module.procurement.entity.RealisasiStatusHistory;
import com.example.operion.module.procurement.entity.Supplier;
import com.example.operion.module.procurement.enums.PaymentMethod;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.procurement.enums.ReimbursementStatus;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.enums.VarianceStatus;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationItemRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationRepository;
import com.example.operion.module.procurement.repository.RealisasiItemRepository;
import com.example.operion.module.procurement.repository.RealisasiRepository;
import com.example.operion.module.procurement.repository.RealisasiStatusHistoryRepository;
import com.example.operion.module.procurement.repository.SupplierRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RealisasiService {

    private final RealisasiRepository repository;

    private final RealisasiItemRepository itemRepository;

    private final RealisasiStatusHistoryRepository historyRepository;

    private final PurchaseRequestAuthorizationRepository praRepository;

    private final PurchaseRequestAuthorizationItemRepository praItemRepository;

    private final PurchaseRequestAuthorizationService praService;

    private final SupplierRepository supplierRepository;

    private final PartRepository partRepository;

    private final UserRepository userRepository;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    private final NotificationService notificationService;

    @Transactional
    public RealisasiResponse create(CreateRealisasiRequest request) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        if (repository.existsByExternalOrderRef(request.getExternalOrderRef())) {

            throw new RuntimeException(
                    "External order ref is already registered: " + request.getExternalOrderRef());
        }

        PurchaseRequestAuthorization pra = praRepository
                .findByIdAndTenantId(request.getPurchaseRequestAuthorizationId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Purchase request authorization not found"));

        if (pra.getStatus() == PurchaseRequestAuthorizationStatus.CANCELLED) {

            throw new RuntimeException("Cannot realize a purchase against a cancelled authorization.");
        }

        User createdBy = currentUser();

        requireSegregationFromPraApprover(pra, createdBy);

        Supplier supplier = null;

        if (request.getSupplierId() != null) {

            UUID rootTenantId = tenantHierarchyService.getRootTenantId(tenantId);

            supplier = supplierRepository
                    .findByIdAndTenantId(request.getSupplierId(), rootTenantId)
                    .orElseThrow(() -> new RuntimeException("Supplier not found"));
        }

        BigDecimal subtotal = nz(request.getSubtotal());
        BigDecimal sellerDiscount = nz(request.getSellerDiscount());
        BigDecimal platformVoucher = nz(request.getPlatformVoucher());
        BigDecimal shipping = nz(request.getShipping());
        BigDecimal insurance = nz(request.getInsurance());
        BigDecimal serviceFee = nz(request.getServiceFee());

        BigDecimal totalCost = subtotal
                .subtract(sellerDiscount)
                .subtract(platformVoucher)
                .add(shipping)
                .add(insurance)
                .add(serviceFee);

        boolean escalate = false;

        for (RealisasiItemRequest itemRequest : request.getItems()) {

            PurchaseRequestAuthorizationItem praItem = praItemRepository
                    .findById(itemRequest.getPurchaseRequestAuthorizationItemId())
                    .orElseThrow(() -> new RuntimeException("Purchase request authorization item not found"));

            if (!praItem.getPurchaseRequestAuthorization().getId().equals(pra.getId())) {

                throw new RuntimeException(
                        "Authorization item " + praItem.getId() + " does not belong to the specified authorization.");
            }

            int alreadyQty = praService.approvedPurchasedQtyInStockUnits(praItem.getId());
            BigDecimal alreadyValue = praService.approvedPurchasedValue(praItem.getId());

            BigDecimal newQtyStockUnits = BigDecimal.valueOf(itemRequest.getPurchasedQty())
                    .multiply(itemRequest.getConversionFactor());

            BigDecimal newLineValue = itemRequest.getActualUnitPrice()
                    .multiply(BigDecimal.valueOf(itemRequest.getPurchasedQty()));

            int newTotalQty = BigDecimal.valueOf(alreadyQty)
                    .add(newQtyStockUnits)
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();

            BigDecimal newTotalValue = alreadyValue.add(newLineValue);

            if (newTotalQty > praItem.getAuthorizedQty()
                    || newTotalValue.compareTo(praItem.getMaxValue()) > 0) {

                escalate = true;
            }
        }

        RealisasiStatus status = escalate ? RealisasiStatus.PENDING_APPROVAL : RealisasiStatus.APPROVED;
        VarianceStatus varianceStatus = escalate ? VarianceStatus.ESCALATED : VarianceStatus.WITHIN_CEILING;

        ReimbursementStatus reimbursementStatus = request.getPaymentMethod() == PaymentMethod.PERSONAL_REIMBURSABLE
                ? ReimbursementStatus.PENDING
                : ReimbursementStatus.NOT_APPLICABLE;

        Realisasi realisasi = Realisasi.builder()
                .tenant(tenant)
                .purchaseRequestAuthorization(pra)
                .createdBy(createdBy)
                .externalOrderRef(request.getExternalOrderRef())
                .channel(request.getChannel())
                .supplier(supplier)
                .paymentRef(request.getPaymentRef())
                .paymentMethod(request.getPaymentMethod())
                .reimbursementStatus(reimbursementStatus)
                .purchasedAt(request.getPurchasedAt() != null ? request.getPurchasedAt() : LocalDateTime.now())
                .subtotal(subtotal)
                .sellerDiscount(sellerDiscount)
                .platformVoucher(platformVoucher)
                .shipping(shipping)
                .insurance(insurance)
                .serviceFee(serviceFee)
                .totalCost(totalCost)
                .attachments(request.getAttachments())
                .varianceStatus(varianceStatus)
                .retroPurchaseFlag(request.getRetroPurchaseFlag() != null ? request.getRetroPurchaseFlag() : false)
                .status(status)
                .build();

        realisasi = repository.save(realisasi);

        saveItems(realisasi, request, tenantId, subtotal, totalCost);

        writeHistory(realisasi, null, status, createdBy,
                escalate ? "Exceeds PRA ceiling, escalated for Owner approval" : null);

        if (escalate) {
            notificationService.notifyRealisasiEscalated(realisasi);
        }

        praService.refreshStatus(pra.getId());

        return map(realisasi);
    }

    @Transactional
    public RealisasiResponse approveEscalated(UUID id) {

        requireOwnerScope();

        Realisasi realisasi = findOwned(id);

        if (realisasi.getStatus() != RealisasiStatus.PENDING_APPROVAL) {

            throw new RuntimeException("Only PENDING_APPROVAL realizations can be approved.");
        }

        RealisasiStatus previous = realisasi.getStatus();
        realisasi.setStatus(RealisasiStatus.APPROVED);
        realisasi.setApprovedBy(currentUser());
        realisasi = repository.save(realisasi);

        writeHistory(realisasi, previous, RealisasiStatus.APPROVED, currentUser(), null);

        praService.refreshStatus(realisasi.getPurchaseRequestAuthorization().getId());

        return map(realisasi);
    }

    @Transactional
    public RealisasiResponse reject(UUID id, RejectRealisasiRequest request) {

        Realisasi realisasi = findOwned(id);

        if (realisasi.getStatus() != RealisasiStatus.PENDING_APPROVAL
                && realisasi.getStatus() != RealisasiStatus.APPROVED) {

            throw new RuntimeException("Only PENDING_APPROVAL or APPROVED realizations can be marked failed.");
        }

        RealisasiStatus previous = realisasi.getStatus();
        realisasi.setStatus(RealisasiStatus.FAILED);
        realisasi = repository.save(realisasi);

        writeHistory(realisasi, previous, RealisasiStatus.FAILED, currentUser(), request.getReason());

        // Failed realization does not kill the PRA — its quantity simply stops
        // counting toward fulfillment (see PurchaseRequestAuthorizationService's
        // APPROVED-only aggregation), so remaining ceiling reopens automatically.
        praService.refreshStatus(realisasi.getPurchaseRequestAuthorization().getId());

        return map(realisasi);
    }

    @Transactional
    public RealisasiResponse supersede(UUID id, SupersedeRealisasiRequest request) {

        Realisasi original = findOwned(id);

        RealisasiStatus previous = original.getStatus();
        original.setStatus(RealisasiStatus.SUPERSEDED);
        original.setSupersededReason(request.getReason());
        repository.save(original);

        writeHistory(original, previous, RealisasiStatus.SUPERSEDED, currentUser(), request.getReason());

        praService.refreshStatus(original.getPurchaseRequestAuthorization().getId());

        RealisasiResponse corrected = create(request.getCorrection());

        Realisasi correctedEntity = repository.findById(corrected.getId()).orElseThrow();
        correctedEntity.setSupersedes(original);
        repository.save(correctedEntity);

        return map(correctedEntity);
    }

    public List<RealisasiResponse> getAll() {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return repository.findByTenantIdIn(tenantIds)
                .stream()
                .map(this::map)
                .toList();
    }

    public RealisasiResponse getById(UUID id) {

        return map(findOwned(id));
    }

    public List<StatusHistoryResponse> getHistory(UUID id) {

        Realisasi realisasi = findOwned(id);

        return historyRepository
                .findByRealisasi_IdOrderByCreatedAtDesc(realisasi.getId())
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

    Realisasi findOwned(UUID id) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Realisasi not found"));
    }

    private void saveItems(
            Realisasi realisasi,
            CreateRealisasiRequest request,
            UUID tenantId,
            BigDecimal headerSubtotal,
            BigDecimal totalCost) {

        for (RealisasiItemRequest itemRequest : request.getItems()) {

            Part part = partRepository
                    .findByIdAndTenantId(itemRequest.getPartId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Part not found"));

            PurchaseRequestAuthorizationItem praItem = praItemRepository
                    .findById(itemRequest.getPurchaseRequestAuthorizationItemId())
                    .orElseThrow(() -> new RuntimeException("Purchase request authorization item not found"));

            BigDecimal lineSubtotal = itemRequest.getActualUnitPrice()
                    .multiply(BigDecimal.valueOf(itemRequest.getPurchasedQty()));

            // Allocate the header's total landed cost across lines by value —
            // each line's share of the header subtotal (per resolved decision #5).
            BigDecimal allocatedLandedCost = headerSubtotal.compareTo(BigDecimal.ZERO) > 0
                    ? totalCost.multiply(lineSubtotal)
                            .divide(headerSubtotal, 10, RoundingMode.HALF_UP)
                            .setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            itemRepository.save(
                    RealisasiItem.builder()
                            .realisasi(realisasi)
                            .purchaseRequestAuthorizationItem(praItem)
                            .part(part)
                            .purchaseUom(itemRequest.getPurchaseUom())
                            .conversionFactor(itemRequest.getConversionFactor())
                            .purchasedQty(itemRequest.getPurchasedQty())
                            .actualUnitPrice(itemRequest.getActualUnitPrice())
                            .allocatedLandedCost(allocatedLandedCost)
                            .build());
        }
    }

    /**
     * DL-08 segregation rule resolved this session: the same user may not
     * both approve a PRA and create its Realisasi, unless that user holds
     * OWNER scope or above. This checks the PRA approver's stored scope —
     * a historical fact — not ScopeContext, which reflects the current
     * request's actor.
     */
    private void requireSegregationFromPraApprover(PurchaseRequestAuthorization pra, User createdBy) {

        User approver = pra.getApprovedBy();

        if (approver == null || !approver.getId().equals(createdBy.getId())) {
            return;
        }

        Scope approverScope = approver.getScope();
        boolean exempt = approverScope != null && approverScope.ordinal() >= Scope.OWNER.ordinal();

        if (!exempt) {

            throw new RuntimeException(
                    "The user who approved this authorization cannot also create its Realisasi, "
                            + "unless they hold Owner scope or above.");
        }
    }

    private void requireOwnerScope() {

        if (!ScopeContext.hasAtLeast(Scope.OWNER)) {
            throw new RuntimeException("Only Owner-level users or higher can approve an escalated realisasi");
        }
    }

    private BigDecimal nz(BigDecimal value) {

        return value != null ? value : BigDecimal.ZERO;
    }

    private User currentUser() {

        UUID userId = UUID.fromString(
                SecurityContextHolder.getContext().getAuthentication().getName());

        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    private void writeHistory(
            Realisasi realisasi,
            RealisasiStatus previous,
            RealisasiStatus next,
            User changedBy,
            String reason) {

        historyRepository.save(
                RealisasiStatusHistory.builder()
                        .realisasi(realisasi)
                        .previousStatus(previous)
                        .newStatus(next)
                        .changedBy(changedBy)
                        .reason(reason)
                        .build());
    }

    private RealisasiResponse map(Realisasi realisasi) {

        List<RealisasiItemResponse> items = itemRepository
                .findByRealisasiId(realisasi.getId())
                .stream()
                .map(item -> RealisasiItemResponse.builder()
                        .id(item.getId())
                        .purchaseRequestAuthorizationItemId(
                                item.getPurchaseRequestAuthorizationItem().getId())
                        .partId(item.getPart().getId())
                        .partName(item.getPart().getName())
                        .purchaseUom(item.getPurchaseUom())
                        .conversionFactor(item.getConversionFactor())
                        .purchasedQty(item.getPurchasedQty())
                        .actualUnitPrice(item.getActualUnitPrice())
                        .allocatedLandedCost(item.getAllocatedLandedCost())
                        .build())
                .toList();

        return RealisasiResponse.builder()
                .id(realisasi.getId())
                .purchaseRequestAuthorizationId(realisasi.getPurchaseRequestAuthorization().getId())
                .status(realisasi.getStatus().name())
                .varianceStatus(realisasi.getVarianceStatus() != null ? realisasi.getVarianceStatus().name() : null)
                .externalOrderRef(realisasi.getExternalOrderRef())
                .channel(realisasi.getChannel())
                .supplierId(realisasi.getSupplier() != null ? realisasi.getSupplier().getId() : null)
                .supplierName(realisasi.getSupplier() != null ? realisasi.getSupplier().getName() : null)
                .paymentRef(realisasi.getPaymentRef())
                .paymentMethod(realisasi.getPaymentMethod() != null ? realisasi.getPaymentMethod().name() : null)
                .reimbursementStatus(
                        realisasi.getReimbursementStatus() != null ? realisasi.getReimbursementStatus().name() : null)
                .purchasedAt(realisasi.getPurchasedAt())
                .subtotal(realisasi.getSubtotal())
                .sellerDiscount(realisasi.getSellerDiscount())
                .platformVoucher(realisasi.getPlatformVoucher())
                .shipping(realisasi.getShipping())
                .insurance(realisasi.getInsurance())
                .serviceFee(realisasi.getServiceFee())
                .totalCost(realisasi.getTotalCost())
                .attachments(realisasi.getAttachments())
                .retroPurchaseFlag(realisasi.getRetroPurchaseFlag())
                .createdById(realisasi.getCreatedBy() != null ? realisasi.getCreatedBy().getId() : null)
                .createdByName(realisasi.getCreatedBy() != null ? realisasi.getCreatedBy().getFullName() : null)
                .approvedById(realisasi.getApprovedBy() != null ? realisasi.getApprovedBy().getId() : null)
                .approvedByName(realisasi.getApprovedBy() != null ? realisasi.getApprovedBy().getFullName() : null)
                .supersedesId(realisasi.getSupersedes() != null ? realisasi.getSupersedes().getId() : null)
                .items(items)
                .createdAt(realisasi.getCreatedAt())
                .build();
    }
}
