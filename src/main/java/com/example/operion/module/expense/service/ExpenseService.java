package com.example.operion.module.expense.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.ScopeContext;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.expense.dto.CreateExpenseRequest;
import com.example.operion.module.expense.dto.ExpenseResponse;
import com.example.operion.module.expense.entity.Expense;
import com.example.operion.module.expense.enums.ExpenseCategory;
import com.example.operion.module.expense.enums.ExpensePaidBy;
import com.example.operion.module.expense.enums.ExpenseStatus;
import com.example.operion.module.expense.repository.ExpenseRepository;
import com.example.operion.module.notification.service.NotificationService;
import com.example.operion.module.procurement.dto.MarkReimbursedRequest;
import com.example.operion.module.procurement.enums.ReimbursementStatus;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;
import com.example.operion.module.upload.enums.UploadPurpose;
import com.example.operion.module.upload.service.UploadService;

import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * BE-19 (screen PROC-10): what a branch spends that isn't stock.
 *
 * <ul>
 * <li>Record: the store's MANAGER+; "paid by store" only OWNER+. Always on
 *     the caller's own store. "Paid by me" needs a receipt photo: it's what
 *     the Owner pays back against.</li>
 * <li>No edit: void with a reason (the creator or the store's OWNER+) and
 *     enter it again.</li>
 * <li>Paid personally → paid back by the store's own OWNER+, BE-18's rules
 *     exactly: one or several, all-or-nothing, undo with a reason, never
 *     money owed to yourself, the claimant notified.</li>
 * <li>Reads cover the caller's tree, so HQ sees every store. Amounts are
 *     OWNER+ only, plus the claimant for their own personal expense.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    public static final String ATTACHED_TYPE = "EXPENSE";

    static final String NOT_FOUND = "Expense not found";

    private final ExpenseRepository repository;

    private final UserRepository userRepository;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    private final UploadService uploadService;

    private final NotificationService notificationService;

    @Transactional
    public ExpenseResponse create(CreateExpenseRequest request) {

        if (!ScopeContext.hasAtLeast(Scope.MANAGER)) {
            throw new AccessDeniedException("Only Manager-level users or higher can record an expense");
        }

        if (request.getPaidBy() == ExpensePaidBy.STORE && !ScopeContext.hasAtLeast(Scope.OWNER)) {
            throw new AccessDeniedException("Only Owner-level users or higher can record an expense paid by the store");
        }

        List<UUID> receipts = request.getReceiptUploadIds() != null
                ? request.getReceiptUploadIds().stream().distinct().toList()
                : List.of();

        if (request.getPaidBy() == ExpensePaidBy.PERSONAL && receipts.isEmpty()) {
            throw new RuntimeException("Add a photo of the receipt: it's what you're paid back against");
        }

        Tenant store = tenantRepository.findById(UUID.fromString(TenantContext.getTenantId())).orElseThrow();

        Expense expense = repository.save(Expense.builder()
                .tenant(store)
                .description(request.getDescription().trim())
                .amount(request.getAmount())
                .expenseDate(request.getExpenseDate() != null ? request.getExpenseDate() : LocalDate.now())
                .category(request.getCategory())
                .paidBy(request.getPaidBy())
                .createdBy(currentUser())
                .reimbursementStatus(request.getPaidBy() == ExpensePaidBy.PERSONAL
                        ? ReimbursementStatus.PENDING
                        : ReimbursementStatus.NOT_APPLICABLE)
                .build());

        for (UUID uploadId : receipts) {
            uploadService.attach(uploadId, UploadPurpose.EXPENSE_RECEIPT, ATTACHED_TYPE, expense.getId());
        }

        return map(expense);
    }

    /** Newest first, across the caller's tree. Every filter optional; {@code from}/{@code to} inclusive. */
    public List<ExpenseResponse> getAll(
            ExpenseCategory category,
            ExpensePaidBy paidBy,
            ReimbursementStatus reimbursementStatus,
            ExpenseStatus status,
            LocalDate from,
            LocalDate to) {

        List<UUID> tree = tenantHierarchyService.getEffectiveTenantIds(UUID.fromString(TenantContext.getTenantId()));

        Specification<Expense> spec = (root, query, cb) -> {

            List<Predicate> p = new ArrayList<>();
            p.add(root.get("tenant").get("id").in(tree));

            if (category != null) {
                p.add(cb.equal(root.get("category"), category));
            }
            if (paidBy != null) {
                p.add(cb.equal(root.get("paidBy"), paidBy));
            }
            if (reimbursementStatus != null) {
                p.add(cb.equal(root.get("reimbursementStatus"), reimbursementStatus));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
            }

            return cb.and(p.toArray(new Predicate[0]));
        };

        return repository.findAll(spec, Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("createdAt")))
                .stream()
                .map(this::map)
                .toList();
    }

    public ExpenseResponse getById(UUID id) {

        return map(findInTree(id));
    }

    /** No edit: the creator or the store's own OWNER+ voids it, with a reason, and enters it again. */
    @Transactional
    public ExpenseResponse voidExpense(UUID id, String reason) {

        Expense expense = findInTree(id);
        User caller = currentUser();

        boolean ownStore = expense.getTenant().getId().equals(UUID.fromString(TenantContext.getTenantId()));
        boolean isCreator = expense.getCreatedBy().getId().equals(caller.getId());

        if (!ownStore || !(isCreator || ScopeContext.hasAtLeast(Scope.OWNER))) {
            throw new AccessDeniedException(
                    "Only the person who recorded this expense, or the store's Owner, can void it");
        }

        if (expense.getStatus() != ExpenseStatus.ACTIVE) {
            throw new RuntimeException("This expense is already voided");
        }

        if (expense.getReimbursementStatus() == ReimbursementStatus.REIMBURSED) {
            throw new RuntimeException("This expense was paid back: undo the pay-back before voiding it");
        }

        expense.setStatus(ExpenseStatus.VOIDED);
        expense.setVoidedBy(caller);
        expense.setVoidedAt(LocalDateTime.now());
        expense.setVoidReason(reason.trim());
        // a voided expense owes nothing, so it leaves the "Owed" list
        expense.setReimbursementStatus(ReimbursementStatus.NOT_APPLICABLE);

        return map(repository.save(expense));
    }

    /** BE-18's rules: the store's own OWNER+, one or several, all-or-nothing, never your own. */
    @Transactional
    public List<ExpenseResponse> markReimbursed(MarkReimbursedRequest request) {

        requireOwnerScope();

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        User caller = currentUser();

        List<UUID> ids = request.getIds().stream().distinct().toList();
        List<UUID> tree = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        List<Expense> expenses = repository.findAllById(ids).stream()
                .filter(e -> tree.contains(e.getTenant().getId()))
                .toList();

        if (expenses.stream().anyMatch(e -> !e.getTenant().getId().equals(tenantId))) {
            throw new AccessDeniedException("Only the store's own Owner can mark its expenses paid back");
        }

        boolean allPending = expenses.size() == ids.size() && expenses.stream().allMatch(e ->
                e.getStatus() == ExpenseStatus.ACTIVE
                        && e.getPaidBy() == ExpensePaidBy.PERSONAL
                        && e.getReimbursementStatus() == ReimbursementStatus.PENDING);

        if (!allPending) {
            throw new RuntimeException("Every expense must be owed to someone in your store; nothing was changed");
        }

        if (expenses.stream().anyMatch(e -> e.getCreatedBy().getId().equals(caller.getId()))) {
            throw new AccessDeniedException("You can't mark money owed to yourself as paid back");
        }

        LocalDateTime paidAt = request.getPaidAt().atStartOfDay();

        for (Expense e : expenses) {
            e.setReimbursementStatus(ReimbursementStatus.REIMBURSED);
            e.setReimbursedBy(caller);
            e.setReimbursedAt(paidAt);
            e.setReimbursementMethod(request.getMethod());
            e.setReimbursementReference(request.getReference());
            e.setReimbursementUndoReason(null);
            repository.save(e);
        }

        expenses.stream()
                .collect(Collectors.groupingBy(Expense::getCreatedBy))
                .forEach((owed, theirs) -> notificationService.notifyExpensesReimbursed(owed, theirs, caller));

        return expenses.stream().map(this::map).toList();
    }

    @Transactional
    public ExpenseResponse undoReimbursement(UUID id, String reason) {

        requireOwnerScope();

        Expense e = findInTree(id);
        User caller = currentUser();

        if (!e.getTenant().getId().equals(UUID.fromString(TenantContext.getTenantId()))) {
            throw new AccessDeniedException("Only the store's own Owner can change its pay-backs");
        }

        if (e.getReimbursementStatus() != ReimbursementStatus.REIMBURSED) {
            throw new RuntimeException("Only an expense marked paid back can be undone");
        }

        if (e.getCreatedBy().getId().equals(caller.getId())) {
            throw new AccessDeniedException("You can't change a reimbursement owed to yourself");
        }

        e.setReimbursementStatus(ReimbursementStatus.PENDING);
        e.setReimbursedBy(null);
        e.setReimbursedAt(null);
        e.setReimbursementMethod(null);
        e.setReimbursementReference(null);
        e.setReimbursementUndoReason(reason.trim());

        return map(repository.save(e));
    }

    private Expense findInTree(UUID id) {

        List<UUID> tree = tenantHierarchyService.getEffectiveTenantIds(UUID.fromString(TenantContext.getTenantId()));

        return repository.findById(id)
                .filter(e -> tree.contains(e.getTenant().getId()))
                .orElseThrow(() -> new RuntimeException(NOT_FOUND));
    }

    private void requireOwnerScope() {

        if (!ScopeContext.hasAtLeast(Scope.OWNER)) {
            throw new AccessDeniedException("Only Owner-level users or higher can settle an expense");
        }
    }

    private User currentUser() {

        UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());

        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    private ExpenseResponse map(Expense e) {

        String callerId = SecurityContextHolder.getContext().getAuthentication().getName();

        // money: OWNER+, plus the claimant for their own personal expense (BE-18's exception)
        boolean canSeeAmount = ScopeContext.hasAtLeast(Scope.OWNER)
                || (e.getPaidBy() == ExpensePaidBy.PERSONAL && e.getCreatedBy().getId().toString().equals(callerId));

        return ExpenseResponse.builder()
                .id(e.getId())
                .tenantId(e.getTenant().getId())
                .tenantName(e.getTenant().getName())
                .description(e.getDescription())
                .amount(canSeeAmount ? e.getAmount() : null)
                .expenseDate(e.getExpenseDate())
                .category(e.getCategory())
                .paidBy(e.getPaidBy())
                .status(e.getStatus())
                .createdById(e.getCreatedBy().getId())
                .createdByName(e.getCreatedBy().getFullName())
                .createdAt(e.getCreatedAt())
                .receiptUploadIds(uploadService.attachedIds(ATTACHED_TYPE, e.getId()))
                .voidedByName(e.getVoidedBy() != null ? e.getVoidedBy().getFullName() : null)
                .voidedAt(e.getVoidedAt())
                .voidReason(e.getVoidReason())
                .reimbursementStatus(e.getReimbursementStatus())
                .reimbursedByName(e.getReimbursedBy() != null ? e.getReimbursedBy().getFullName() : null)
                .reimbursedAt(e.getReimbursedAt())
                .reimbursementMethod(e.getReimbursementMethod())
                .reimbursementReference(e.getReimbursementReference())
                .reimbursementUndoReason(e.getReimbursementUndoReason())
                .build();
    }
}
