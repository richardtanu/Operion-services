package com.example.operion.module.procurement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.part.service.HqCatalogService;
import com.example.operion.module.procurement.dto.CreateRealisasiRequest;
import com.example.operion.module.procurement.dto.MarkReimbursedRequest;
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
import com.example.operion.module.procurement.enums.ActorKind;
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
import com.example.operion.module.upload.enums.UploadPurpose;
import com.example.operion.module.upload.service.UploadService;

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

    private final HqCatalogService hqCatalogService;

    private final UserRepository userRepository;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    private final NotificationService notificationService;

    private final UploadService uploadService;

    /** BE-39: receipt photos are attached to a purchase under this type. */
    static final String ATTACHED_TYPE = "REALISASI";

    @Transactional
    public RealisasiResponse create(CreateRealisasiRequest request) {

        // BE-17: the Store Manager (MANAGER+) mostly logs purchases; HQ
        // PROCUREMENT staff sometimes buy for a store. TECHNICIAN, OPERATOR,
        // ACCOUNTING, and ADMIN without MANAGER+ are refused. DL-08 still applies.
        if (!ScopeContext.hasAtLeast(Scope.MANAGER) && !CallerRoles.hasAny(Role.PROCUREMENT)) {
            throw new AccessDeniedException(
                    "Only Manager-level users or higher, or Procurement, can log a purchase");
        }

        // BE-17: HQ PROCUREMENT (or OWNER+) may log for a store in their tree;
        // the PRA is looked up in, and the purchase saved on, that store.
        UUID tenantId = tenantHierarchyService.resolveTargetTenant(
                request.getTenantId(),
                CallerRoles.hasAny(Role.PROCUREMENT) || ScopeContext.hasAtLeast(Scope.OWNER));

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

        // BE-21: items stores must buy from HQ can't be bought elsewhere. An
        // urgent buy of one, paid at HQ, is still logged here, with HQ (the
        // franchise-centre supplier) as the seller. Covers supersede too.
        boolean boughtFromHq = supplier != null && supplier.isFranchiseCentre();

        for (RealisasiItemRequest itemRequest : request.getItems()) {

            Part part = partRepository
                    .findByIdAndTenantId(itemRequest.getPartId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Part not found"));

            if (!boughtFromHq && hqCatalogService.isMandatoryFromHq(part)) {
                throw new RuntimeException("This item must be bought from HQ: " + part.getName());
            }
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
                .sellerName(request.getSellerName())
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

        // BE-39: in this transaction, so a refused photo saves no purchase
        if (request.getReceiptUploadIds() != null) {
            for (UUID uploadId : request.getReceiptUploadIds().stream().distinct().toList()) {
                uploadService.attach(uploadId, UploadPurpose.PURCHASE_RECEIPT, ATTACHED_TYPE, realisasi.getId());
            }
        }

        writeHistory(realisasi, null, status, createdBy,
                escalate ? "Exceeds PRA ceiling, escalated for Owner approval" : null);

        if (escalate) {
            notificationService.notifyRealisasiEscalated(realisasi);
        }

        praService.refreshStatus(pra.getId(), createdBy, realisasi.getExternalOrderRef() + " logged");

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

        praService.refreshStatus(realisasi.getPurchaseRequestAuthorization().getId(), currentUser(),
                realisasi.getExternalOrderRef() + " approved");

        return map(realisasi);
    }

    @Transactional
    public RealisasiResponse reject(UUID id, RejectRealisasiRequest request) {

        // BE-17: OWNER+, from PENDING_APPROVAL and from APPROVED alike. Failing an
        // approved purchase reopens spending room on its PRA, so it's the Owner's call.
        requireOwnerScope();

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
        praService.refreshStatus(realisasi.getPurchaseRequestAuthorization().getId(), currentUser(),
                realisasi.getExternalOrderRef() + " rejected");

        return map(realisasi);
    }

    @Transactional
    public RealisasiResponse supersede(UUID id, SupersedeRealisasiRequest request) {

        Realisasi original = findOwned(id);

        // BE-17: a correction is made by whoever logged the original, or OWNER+
        boolean isCreator = original.getCreatedBy() != null
                && original.getCreatedBy().getId().equals(currentUser().getId());

        if (!isCreator && !ScopeContext.hasAtLeast(Scope.OWNER)) {
            throw new AccessDeniedException(
                    "Only the person who logged this purchase, or Owner-level users or higher, can correct it");
        }

        // BE-17: only a live record can be corrected; a FAILED or already
        // SUPERSEDED one can't be superseded again
        if (original.getStatus() != RealisasiStatus.PENDING_APPROVAL
                && original.getStatus() != RealisasiStatus.APPROVED) {

            throw new RuntimeException("Only PENDING_APPROVAL or APPROVED realizations can be corrected.");
        }

        // The correction stays on the original's store
        request.getCorrection().setTenantId(original.getTenant().getId());

        RealisasiStatus previous = original.getStatus();
        original.setStatus(RealisasiStatus.SUPERSEDED);
        original.setSupersededReason(request.getReason());
        repository.save(original);

        writeHistory(original, previous, RealisasiStatus.SUPERSEDED, currentUser(), request.getReason());

        praService.refreshStatus(original.getPurchaseRequestAuthorization().getId(), currentUser(),
                original.getExternalOrderRef() + " corrected");

        RealisasiResponse corrected = create(request.getCorrection());

        Realisasi correctedEntity = repository.findById(corrected.getId()).orElseThrow();
        correctedEntity.setSupersedes(original);
        repository.save(correctedEntity);

        return map(correctedEntity);
    }

    /**
     * Tree-scoped list. BE-18: optional server-side filters so the
     * reimbursement screen (PROC-08) asks for exactly "owed" or "paid back"
     * instead of filtering on the device.
     */
    public List<RealisasiResponse> getAll(PaymentMethod paymentMethod, ReimbursementStatus reimbursementStatus) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return repository.findByTenantIdIn(tenantIds)
                .stream()
                .filter(r -> paymentMethod == null || r.getPaymentMethod() == paymentMethod)
                .filter(r -> reimbursementStatus == null || r.getReimbursementStatus() == reimbursementStatus)
                .map(this::map)
                .toList();
    }

    /**
     * BE-18: the store Owner pays the Store Manager back for purchases made with
     * their own money — one or several at once. Rules:
     * <ul>
     * <li>Only an OWNER+ of the purchases' own store (HQ can read, not mark: the
     *     store's money is the store's business), and never for money owed to
     *     themself (segregation of duties).</li>
     * <li>All-or-nothing: if any id isn't a pending personal reimbursement in the
     *     caller's store, nothing changes (400).</li>
     * <li>A FAILED (rejected) purchase is still owed: the money really was spent.</li>
     * </ul>
     * One "paid back" notification per person owed, per call.
     */
    @Transactional
    public List<RealisasiResponse> markReimbursed(MarkReimbursedRequest request) {

        requireOwnerScope();

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        User caller = currentUser();

        List<UUID> ids = request.getIds().stream().distinct().toList();
        List<UUID> tree = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        // outside the caller's tree counts as missing (no probing other roots)
        List<Realisasi> records = repository.findAllById(ids).stream()
                .filter(r -> tree.contains(r.getTenant().getId()))
                .toList();

        // a store's purchase seen from HQ: HQ can read it, but paying back is the store Owner's call
        if (records.stream().anyMatch(r -> !r.getTenant().getId().equals(tenantId))) {
            throw new AccessDeniedException("Only the store's own Owner can mark its purchases paid back");
        }

        boolean allPending = records.size() == ids.size() && records.stream().allMatch(r ->
                r.getPaymentMethod() == PaymentMethod.PERSONAL_REIMBURSABLE
                        && r.getReimbursementStatus() == ReimbursementStatus.PENDING);

        if (!allPending) {
            throw new RuntimeException(
                    "Every purchase must be a pending reimbursement in your store; nothing was changed");
        }

        if (records.stream().anyMatch(r -> r.getCreatedBy() != null && r.getCreatedBy().getId().equals(caller.getId()))) {
            throw new AccessDeniedException("You can't mark money owed to yourself as paid back");
        }

        LocalDateTime paidAt = request.getPaidAt().atStartOfDay();
        String note = "Paid back by " + request.getMethod().name().toLowerCase()
                + (request.getReference() != null && !request.getReference().isBlank()
                        ? " (" + request.getReference() + ")" : "");

        for (Realisasi r : records) {
            r.setReimbursementStatus(ReimbursementStatus.REIMBURSED);
            r.setReimbursedTo(r.getCreatedBy());
            r.setReimbursedAt(paidAt);
            r.setReimbursementMethod(request.getMethod());
            r.setReimbursementReference(request.getReference());
            repository.save(r);

            // The purchase's own status doesn't change; the history row records who
            // paid it back and how, so it shows in the activity log
            writeHistory(r, r.getStatus(), r.getStatus(), caller, note);
        }

        records.stream()
                .collect(java.util.stream.Collectors.groupingBy(Realisasi::getCreatedBy))
                .forEach((owed, theirs) -> notificationService.notifyReimbursementPaid(owed, theirs, caller));

        return records.stream().map(this::map).toList();
    }

    /** BE-18: undo a mark-paid (wrong record, wrong date). Same OWNER rule; reason required. */
    @Transactional
    public RealisasiResponse undoReimbursement(UUID id, String reason) {

        requireOwnerScope();

        Realisasi r = findOwned(id);

        if (r.getReimbursementStatus() != ReimbursementStatus.REIMBURSED) {
            throw new RuntimeException("Only a reimbursement marked paid back can be undone");
        }

        User caller = currentUser();

        if (r.getCreatedBy() != null && r.getCreatedBy().getId().equals(caller.getId())) {
            throw new AccessDeniedException("You can't change a reimbursement owed to yourself");
        }

        r.setReimbursementStatus(ReimbursementStatus.PENDING);
        r.setReimbursedTo(null);
        r.setReimbursedAt(null);
        r.setReimbursementMethod(null);
        r.setReimbursementReference(null);
        repository.save(r);

        writeHistory(r, r.getStatus(), r.getStatus(), caller, "Paid back undone: " + reason);

        return map(r);
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
                        .actorKind(ActorKind.of(h.getChangedBy(), false))
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
            throw new AccessDeniedException("Only Owner-level users or higher can approve, reject or settle a purchase");
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

        // BE-06: cost fields redacted below OWNER scope — see
        // OPERION_BE_CHANGE_QUEUE.md BE-06. Redacted (null), not omitted; a
        // sub-OWNER caller sees every other field on this response normally.
        boolean canSeeCost = ScopeContext.hasAtLeast(Scope.OWNER);

        // BE-18: the one exception — the person owed always sees the total of a
        // purchase they paid for with their own money. Only totalCost; every
        // other cost field stays redacted for them.
        boolean isOwedToCaller = realisasi.getPaymentMethod() == PaymentMethod.PERSONAL_REIMBURSABLE
                && realisasi.getCreatedBy() != null
                && realisasi.getCreatedBy().getId().toString().equals(
                        SecurityContextHolder.getContext().getAuthentication().getName());

        // BE-39: a receipt shows what was paid, so its photos follow the money
        // rule, plus whoever logged the purchase (they took the photo)
        boolean loggedByCaller = realisasi.getCreatedBy() != null
                && SecurityContextHolder.getContext().getAuthentication() != null
                && realisasi.getCreatedBy().getId().toString().equals(
                        SecurityContextHolder.getContext().getAuthentication().getName());

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
                        .actualUnitPrice(canSeeCost ? item.getActualUnitPrice() : null)
                        .allocatedLandedCost(canSeeCost ? item.getAllocatedLandedCost() : null)
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
                .sellerName(realisasi.getSellerName())
                .paymentRef(realisasi.getPaymentRef())
                .paymentMethod(realisasi.getPaymentMethod() != null ? realisasi.getPaymentMethod().name() : null)
                .reimbursementStatus(
                        realisasi.getReimbursementStatus() != null ? realisasi.getReimbursementStatus().name() : null)
                .reimbursedToName(realisasi.getReimbursedTo() != null ? realisasi.getReimbursedTo().getFullName() : null)
                .reimbursedAt(realisasi.getReimbursedAt())
                .reimbursementMethod(
                        realisasi.getReimbursementMethod() != null ? realisasi.getReimbursementMethod().name() : null)
                .reimbursementReference(realisasi.getReimbursementReference())
                .purchasedAt(realisasi.getPurchasedAt())
                .subtotal(canSeeCost ? realisasi.getSubtotal() : null)
                .sellerDiscount(canSeeCost ? realisasi.getSellerDiscount() : null)
                .platformVoucher(canSeeCost ? realisasi.getPlatformVoucher() : null)
                .shipping(canSeeCost ? realisasi.getShipping() : null)
                .insurance(canSeeCost ? realisasi.getInsurance() : null)
                .serviceFee(canSeeCost ? realisasi.getServiceFee() : null)
                .totalCost(canSeeCost || isOwedToCaller ? realisasi.getTotalCost() : null)
                .attachments(realisasi.getAttachments())
                .receiptUploadIds(canSeeCost || loggedByCaller
                        ? uploadService.attachedIds(ATTACHED_TYPE, realisasi.getId()) : null)
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
