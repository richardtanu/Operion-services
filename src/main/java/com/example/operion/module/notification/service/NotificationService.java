package com.example.operion.module.notification.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.expense.entity.Expense;
import com.example.operion.module.notification.dto.NotificationResponse;
import com.example.operion.module.notification.entity.Notification;
import com.example.operion.module.notification.enums.NotificationReferenceType;
import com.example.operion.module.notification.enums.NotificationType;
import com.example.operion.module.notification.repository.NotificationRepository;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.procurement.entity.PurchaseRequest;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;
import com.example.operion.module.procurement.entity.Realisasi;
import com.example.operion.module.stockadjustment.entity.StockAdjustment;
import com.example.operion.module.tenant.entity.Tenant;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final List<Scope> OWNER_OR_ABOVE = Arrays.stream(Scope.values())
            .filter(s -> s.ordinal() >= Scope.OWNER.ordinal())
            .toList();

    private final NotificationRepository repository;

    private final UserRepository userRepository;

    /**
     * BE-24: store alerts stay in the store. Only that store's own Owner can
     * act on them (approve/reject look records up in the caller's own store),
     * so HQ isn't told; it reads the activity log and spending instead.
     */
    private static List<UUID> storeOnly(Tenant store) {

        return List.of(store.getId());
    }

    public void notifyNewPurchaseRequest(PurchaseRequest pr) {

        List<UUID> tenantIds = storeOnly(pr.getTenant());

        String requesterName = pr.getRequestedBy() != null ? pr.getRequestedBy().getFullName() : "Unknown";

        for (User target : approversIn(tenantIds)) {

            // BE-07: an approver filing their own PR isn't told about it
            if (pr.getRequestedBy() != null && target.getId().equals(pr.getRequestedBy().getId())) {
                continue;
            }

            create(
                    pr.getTenant(),
                    target,
                    NotificationType.NEW_PURCHASE_REQUEST,
                    "New Purchase Request",
                    "New purchase request submitted by " + requesterName + " at " + pr.getTenant().getName(),
                    NotificationReferenceType.PURCHASE_REQUEST,
                    pr.getId());
        }
    }

    public void notifyPurchaseRequestOrdered(PurchaseRequest pr) {

        if (pr.getRequestedBy() == null) {
            return;
        }

        create(
                pr.getTenant(),
                pr.getRequestedBy(),
                NotificationType.PURCHASE_REQUEST_ORDERED,
                "Purchase Request Ordered",
                "Your purchase request has been ordered from the supplier",
                NotificationReferenceType.PURCHASE_REQUEST,
                pr.getId());
    }

    // BE-07: the requester hears about every decision on their own PR. Nobody
    // is notified about their own action (an Owner deciding their own PR).

    public void notifyPurchaseRequestApproved(PurchaseRequest pr, User actor) {

        notifyRequester(pr, actor,
                NotificationType.PURCHASE_REQUEST_APPROVED,
                "Purchase Request Approved",
                "Your purchase request was approved by " + nameOf(actor));
    }

    public void notifyPurchaseRequestRejected(PurchaseRequest pr, User actor, String reason) {

        notifyRequester(pr, actor,
                NotificationType.PURCHASE_REQUEST_REJECTED,
                "Purchase Request Rejected",
                "Your purchase request was rejected by " + nameOf(actor)
                        + (reason != null && !reason.isBlank() ? ": " + reason : ""));
    }

    /**
     * BE-18: one "paid back" notification per person owed per mark-paid call,
     * listing the purchases ("Rp 1.234.000 paid back for SHP-1, SHP-2"). The
     * amount is theirs to see (BE-18's exception to cost redaction).
     */
    public void notifyReimbursementPaid(User owed, List<Realisasi> records, User actor) {

        if (owed == null || (actor != null && owed.getId().equals(actor.getId())) || records.isEmpty()) {
            return;
        }

        java.math.BigDecimal total = records.stream()
                .map(r -> r.getTotalCost() != null ? r.getTotalCost() : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        String refs = records.stream().map(Realisasi::getExternalOrderRef)
                .collect(java.util.stream.Collectors.joining(", "));

        create(records.get(0).getTenant(), owed,
                NotificationType.REIMBURSEMENT_PAID,
                "Paid Back",
                "Rp " + rupiah(total) + " paid back by " + nameOf(actor) + " for " + refs,
                NotificationReferenceType.REALISASI,
                records.get(0).getId());
    }

    /**
     * BE-19: the same "Paid back" notice for expenses the claimant paid
     * personally. One per call, with the total; never to the person who marked it.
     */
    public void notifyExpensesReimbursed(User owed, List<Expense> expenses, User actor) {

        if (owed == null || (actor != null && owed.getId().equals(actor.getId())) || expenses.isEmpty()) {
            return;
        }

        java.math.BigDecimal total = expenses.stream()
                .map(Expense::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        String what = expenses.stream().map(Expense::getDescription)
                .collect(java.util.stream.Collectors.joining(", "));

        create(expenses.get(0).getTenant(), owed,
                NotificationType.REIMBURSEMENT_PAID,
                "Paid Back",
                "Rp " + rupiah(total) + " paid back by " + nameOf(actor) + " for " + what,
                NotificationReferenceType.EXPENSE,
                expenses.get(0).getId());
    }

    private static String rupiah(java.math.BigDecimal amount) {

        return java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("id-ID")).format(amount);
    }

    /** BE-15: skipped when the requester cancelled their own PR (notifyRequester's rule). */
    public void notifyPurchaseRequestCancelled(PurchaseRequest pr, User actor, String reason) {

        notifyRequester(pr, actor,
                NotificationType.PURCHASE_REQUEST_CANCELLED,
                "Purchase Request Cancelled",
                "Your purchase request was cancelled by " + nameOf(actor)
                        + (reason != null && !reason.isBlank() ? ": " + reason : ""));
    }

    public void notifyPurchaseRequestAuthorized(PurchaseRequest pr, User actor) {

        notifyRequester(pr, actor,
                NotificationType.PURCHASE_REQUEST_AUTHORIZED,
                "Purchase Request Authorized",
                "Your purchase request was authorized for purchase by " + nameOf(actor));
    }

    /**
     * BE-07: PROCUREMENT users in the PRA's own tenant do the buying, so they
     * are told a new authorization is ready. Own tenant only — an outlet's
     * buyer, not HQ's — and never the PRA's creator.
     */
    public void notifyPurchaseAuthorizationCreated(PurchaseRequestAuthorization pra, User actor) {

        for (User target : userRepository.findByTenantIdAndRole(pra.getTenant().getId(), Role.PROCUREMENT)) {

            if (actor != null && target.getId().equals(actor.getId())) {
                continue;
            }

            create(
                    pra.getTenant(),
                    target,
                    NotificationType.PURCHASE_AUTHORIZATION_CREATED,
                    "New Purchase Authorization",
                    "A purchase authorization at " + pra.getTenant().getName()
                            + " is ready to buy against, authorized by " + nameOf(actor),
                    NotificationReferenceType.PURCHASE_REQUEST_AUTHORIZATION,
                    pra.getId());
        }
    }

    private void notifyRequester(
            PurchaseRequest pr,
            User actor,
            NotificationType type,
            String title,
            String message) {

        User requester = pr.getRequestedBy();

        if (requester == null || (actor != null && requester.getId().equals(actor.getId()))) {
            return;
        }

        create(pr.getTenant(), requester, type, title, message,
                NotificationReferenceType.PURCHASE_REQUEST, pr.getId());
    }

    private static String nameOf(User user) {

        return user != null && user.getFullName() != null ? user.getFullName() : "Unknown";
    }

    public void notifyRealisasiEscalated(Realisasi realisasi) {

        List<UUID> tenantIds = storeOnly(realisasi.getTenant());

        for (User target : approversIn(tenantIds)) {

            create(
                    realisasi.getTenant(),
                    target,
                    NotificationType.REALISASI_ESCALATED,
                    "Realisasi Pembelian Exceeds Authorization Ceiling",
                    "Realisasi " + realisasi.getExternalOrderRef()
                            + " exceeds its PRA ceiling and needs approval",
                    NotificationReferenceType.REALISASI,
                    realisasi.getId());
        }
    }

    /**
     * BE-20: the buyer received their own purchase (allowed with a goods
     * photo). Owner+ (and ADMINs) of that store are told, except the receiver;
     * not HQ (BE-24). HQ can list them with GET /goods-receipts?selfReceived=true.
     */
    public void notifyGoodsSelfReceived(Realisasi realisasi, User receiver) {

        List<UUID> tenantIds = storeOnly(realisasi.getTenant());

        for (User target : approversIn(tenantIds)) {

            if (receiver != null && target.getId().equals(receiver.getId())) {
                continue;
            }

            create(
                    realisasi.getTenant(),
                    target,
                    NotificationType.GOODS_SELF_RECEIVED,
                    "Buyer Received Their Own Purchase",
                    nameOf(receiver) + " received their own purchase "
                            + realisasi.getExternalOrderRef() + " at " + realisasi.getTenant().getName()
                            + ": goods photo attached",
                    NotificationReferenceType.REALISASI,
                    realisasi.getId());
        }
    }

    public void notifyPartEndOfLife(AirsoftUnit unit, String message) {

        for (User target : approversIn(List.of(unit.getTenant().getId()))) {

            create(
                    unit.getTenant(),
                    target,
                    NotificationType.PART_END_OF_LIFE,
                    "Sparepart Nearing End Of Life",
                    unit.getName() + ": " + message,
                    NotificationReferenceType.AIRSOFT_UNIT,
                    unit.getId());
        }
    }

    public void notifyLowStock(Part part) {

        for (User target : approversIn(List.of(part.getTenant().getId()))) {

            create(
                    part.getTenant(),
                    target,
                    NotificationType.LOW_STOCK,
                    "Low Stock",
                    part.getName() + " has dropped to or below its minimum stock level",
                    NotificationReferenceType.PART,
                    part.getId());
        }
    }

    public List<NotificationResponse> getMyNotifications() {

        return repository.findByRecipientUserId(currentUserId())
                .stream()
                .map(this::map)
                .toList();
    }

    /**
     * BE-25: a stock adjustment is waiting for the store's Owner+. Store-only
     * like BE-24, and never the person who asked for it.
     */
    public void notifyStockAdjustmentRequested(StockAdjustment adjustment, List<User> owners) {

        for (User target : owners) {

            if (adjustment.getRequestedBy() != null && target.getId().equals(adjustment.getRequestedBy().getId())) {
                continue;
            }

            int qty = adjustment.getQuantity();

            create(
                    adjustment.getTenant(),
                    target,
                    NotificationType.STOCK_ADJUSTMENT_REQUESTED,
                    "Stock Adjustment Waiting",
                    nameOf(adjustment.getRequestedBy()) + " asks to " + (qty < 0 ? "remove " + (-qty) : "add " + qty)
                            + " × " + adjustment.getPart().getName() + ": " + adjustment.getReason(),
                    NotificationReferenceType.STOCK_ADJUSTMENT,
                    adjustment.getId());
        }
    }

    public long getUnreadCount() {

        return repository.countUnreadByRecipientUserId(currentUserId());
    }

    @Transactional
    public NotificationResponse markRead(UUID id) {

        Notification notification = repository
                .findByIdAndRecipientUserId(id, currentUserId())
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());

        return map(repository.save(notification));
    }

    @Transactional
    public void markAllRead() {

        List<Notification> unread = repository.findUnreadByRecipientUserId(currentUserId());

        LocalDateTime now = LocalDateTime.now();

        for (Notification notification : unread) {
            notification.setRead(true);
            notification.setReadAt(now);
        }

        repository.saveAll(unread);
    }

    private List<User> approversIn(List<UUID> tenantIds) {

        return userRepository.findByTenantIdInAndRoleOrScopeIn(tenantIds, Role.ADMIN, OWNER_OR_ABOVE);
    }

    private void create(
            Tenant tenant,
            User recipient,
            NotificationType type,
            String title,
            String message,
            NotificationReferenceType referenceType,
            UUID referenceId) {

        repository.save(
                Notification.builder()
                        .tenant(tenant)
                        .recipientUser(recipient)
                        .type(type)
                        .title(title)
                        .message(message)
                        .referenceType(referenceType)
                        .referenceId(referenceId)
                        .read(false)
                        .build());
    }

    private UUID currentUserId() {

        return UUID.fromString(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private NotificationResponse map(Notification notification) {

        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType().name())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .referenceType(
                        notification.getReferenceType() != null ? notification.getReferenceType().name() : null)
                .referenceId(notification.getReferenceId())
                .read(notification.isRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
