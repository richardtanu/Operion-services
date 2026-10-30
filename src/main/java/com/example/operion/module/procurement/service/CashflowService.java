package com.example.operion.module.procurement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.MoneyAccess;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.expense.entity.Expense;
import com.example.operion.module.expense.enums.ExpensePaidBy;
import com.example.operion.module.expense.enums.ExpenseStatus;
import com.example.operion.module.expense.repository.ExpenseRepository;
import com.example.operion.module.procurement.dto.CashflowEventResponse;
import com.example.operion.module.procurement.dto.CashflowResponse;
import com.example.operion.module.procurement.entity.PurchaseOrder;
import com.example.operion.module.procurement.entity.Realisasi;
import com.example.operion.module.procurement.enums.CashflowDirection;
import com.example.operion.module.procurement.enums.CashflowKind;
import com.example.operion.module.procurement.enums.PaymentMethod;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.enums.ReimbursementStatus;
import com.example.operion.module.procurement.repository.PurchaseOrderRepository;
import com.example.operion.module.procurement.repository.RealisasiRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import lombok.RequiredArgsConstructor;

/**
 * BE-41: the store's money movements, for Accounting and the Owner. Computed
 * on read from the records, nothing stored (as BE-16's spending summary).
 * Every row is money OUT; the app records no income yet. A row is dated when
 * the store's money actually moves (product owner, 30 Oct 2026):
 *
 * <ul>
 * <li><b>Purchase</b> paid from the store's account: on its purchase date. A
 *     purchase records a buy that already happened (DL-08), so one still
 *     waiting for the Owner, or rejected, is listed too, with its status; a
 *     superseded one isn't (its correction is).</li>
 * <li><b>Own-money purchase or expense</b>: when the Owner pays the person
 *     back, not when they bought it (until then it's owed, not cash out).</li>
 * <li><b>Expense</b> paid by the store: on its date, unless voided.</li>
 * <li><b>Order to HQ</b>: when the Owner sends it (its order date), as Spent
 *     counts it; a cancelled order isn't listed.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CashflowService {

    static final Set<RealisasiStatus> PAID_PURCHASE = EnumSet.of(
            RealisasiStatus.APPROVED, RealisasiStatus.PENDING_APPROVAL, RealisasiStatus.FAILED);

    static final int MAX_DAYS = 366;

    private final RealisasiRepository realisasiRepository;

    private final PurchaseOrderRepository purchaseOrderRepository;

    private final ExpenseRepository expenseRepository;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    /**
     * {@code from}/{@code to} inclusive, default this calendar month. HQ gets
     * every store in its tree, or the one {@code tenantId} names (outside the
     * tree → 400 "Store not found").
     */
    public CashflowResponse getCashflow(LocalDate from, LocalDate to, UUID tenantId) {

        if (!MoneyAccess.canSee()) {
            throw new AccessDeniedException("Only Owner-level users or higher, or Accounting, can see the cashflow");
        }

        LocalDate today = LocalDate.now();
        LocalDate first = from != null ? from : today.withDayOfMonth(1);
        LocalDate last = to != null ? to : first.withDayOfMonth(first.lengthOfMonth());

        if (last.isBefore(first)) {
            throw new RuntimeException("'from' must be on or before 'to'");
        }

        if (ChronoUnit.DAYS.between(first, last) >= MAX_DAYS) {
            throw new RuntimeException("Ask for at most a year at a time");
        }

        LocalDate next = last.plusDays(1);

        List<UUID> ids = tenantId != null
                ? List.of(tenantHierarchyService.resolveTargetTenant(tenantId, true))
                : tenantHierarchyService.getEffectiveTenantIds(UUID.fromString(TenantContext.getTenantId()));

        Map<UUID, Tenant> byId = tenantRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));

        List<CashflowEventResponse> events = new ArrayList<>();

        ids.stream().map(byId::get).filter(Objects::nonNull).forEach(t -> {

            for (Realisasi r : realisasiRepository.findCompanyPaidBetween(t.getId(), PAID_PURCHASE,
                    PaymentMethod.PERSONAL_REIMBURSABLE, first.atStartOfDay(), next.atStartOfDay())) {
                events.add(row(t, r.getPurchasedAt().toLocalDate(), CashflowKind.PURCHASE, r.getTotalCost())
                        .reference(r.getExternalOrderRef())
                        .counterparty(sellerOf(r))
                        .recordType("REALISASI").recordId(r.getId()).recordStatus(r.getStatus().name())
                        .build());
            }

            for (Realisasi r : realisasiRepository.findPaidBackBetween(t.getId(), ReimbursementStatus.REIMBURSED,
                    first.atStartOfDay(), next.atStartOfDay())) {
                events.add(row(t, r.getReimbursedAt().toLocalDate(), CashflowKind.PURCHASE_PAYBACK, r.getTotalCost())
                        .reference(r.getExternalOrderRef())
                        .counterparty(r.getReimbursedTo() != null ? r.getReimbursedTo().getFullName()
                                : r.getCreatedBy() != null ? r.getCreatedBy().getFullName() : null)
                        .method(r.getReimbursementMethod() != null ? r.getReimbursementMethod().name() : null)
                        .recordType("REALISASI").recordId(r.getId()).recordStatus(r.getStatus().name())
                        .build());
            }

            for (Expense e : expenseRepository.findDatedBetween(t.getId(), ExpenseStatus.ACTIVE, first, next)) {
                if (e.getPaidBy() == ExpensePaidBy.STORE) {
                    events.add(row(t, e.getExpenseDate(), CashflowKind.EXPENSE, e.getAmount())
                            .reference(e.getDescription())
                            .recordType("EXPENSE").recordId(e.getId()).recordStatus(e.getStatus().name())
                            .build());
                }
            }

            for (Expense e : expenseRepository.findPaidBackBetween(t.getId(), ReimbursementStatus.REIMBURSED,
                    first.atStartOfDay(), next.atStartOfDay())) {
                events.add(row(t, e.getReimbursedAt().toLocalDate(), CashflowKind.EXPENSE_PAYBACK, e.getAmount())
                        .reference(e.getDescription())
                        .counterparty(e.getCreatedBy().getFullName())
                        .method(e.getReimbursementMethod() != null ? e.getReimbursementMethod().name() : null)
                        .recordType("EXPENSE").recordId(e.getId()).recordStatus(e.getStatus().name())
                        .build());
            }

            for (PurchaseOrder po : purchaseOrderRepository.findOrderedBetween(t.getId(), SpendingService.SENT, first, next)) {
                events.add(row(t, po.getOrderDate(), CashflowKind.ORDER_TO_HQ, po.getTotalAmount())
                        .reference(po.getPoNumber())
                        .counterparty(po.getSupplier() != null ? po.getSupplier().getName() : null)
                        .recordType("PURCHASE_ORDER").recordId(po.getId()).recordStatus(po.getStatus().name())
                        .build());
            }
        });

        events.sort(Comparator.comparing(CashflowEventResponse::getDate).reversed()
                .thenComparing(CashflowEventResponse::getKind)
                .thenComparing(CashflowEventResponse::getReference, Comparator.nullsLast(String::compareTo)));

        BigDecimal totalIn = sum(events, CashflowDirection.IN);
        BigDecimal totalOut = sum(events, CashflowDirection.OUT);

        return CashflowResponse.builder()
                .from(first)
                .to(last)
                .totalIn(totalIn)
                .totalOut(totalOut)
                .net(totalIn.subtract(totalOut))
                .events(events)
                .build();
    }

    private static CashflowEventResponse.CashflowEventResponseBuilder row(
            Tenant tenant, LocalDate date, CashflowKind kind, BigDecimal amount) {

        return CashflowEventResponse.builder()
                .date(date)
                .direction(CashflowDirection.OUT)
                .kind(kind)
                .amount(amount != null ? amount : BigDecimal.ZERO)
                .tenantId(tenant.getId())
                .tenantName(tenant.getName());
    }

    private static String sellerOf(Realisasi r) {

        if (r.getSellerName() != null && !r.getSellerName().isBlank()) {
            return r.getSellerName();
        }
        return r.getSupplier() != null ? r.getSupplier().getName() : null;
    }

    private static BigDecimal sum(List<CashflowEventResponse> events, CashflowDirection direction) {

        return events.stream()
                .filter(e -> e.getDirection() == direction)
                .map(CashflowEventResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
