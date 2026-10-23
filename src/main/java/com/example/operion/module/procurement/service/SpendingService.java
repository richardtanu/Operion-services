package com.example.operion.module.procurement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.ScopeContext;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.expense.entity.Expense;
import com.example.operion.module.expense.enums.ExpenseCategory;
import com.example.operion.module.expense.enums.ExpenseStatus;
import com.example.operion.module.expense.repository.ExpenseRepository;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.procurement.dto.SpendingPartResponse;
import com.example.operion.module.procurement.dto.StoreSpendingResponse;
import com.example.operion.module.procurement.entity.PurchaseOrder;
import com.example.operion.module.procurement.entity.PurchaseOrderItem;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorization;
import com.example.operion.module.procurement.entity.PurchaseRequestAuthorizationItem;
import com.example.operion.module.procurement.entity.Realisasi;
import com.example.operion.module.procurement.entity.RealisasiItem;
import com.example.operion.module.procurement.enums.PurchaseOrderStatus;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.repository.PurchaseOrderItemRepository;
import com.example.operion.module.procurement.repository.PurchaseOrderRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationItemRepository;
import com.example.operion.module.procurement.repository.PurchaseRequestAuthorizationRepository;
import com.example.operion.module.procurement.repository.RealisasiItemRepository;
import com.example.operion.module.procurement.repository.RealisasiRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import lombok.RequiredArgsConstructor;

/**
 * BE-16: what each store has spent and committed, against its optional
 * budget. Computed on read from the records themselves, nothing stored
 * (the same reasoning as the PRA ceiling: a stored total drifts from its
 * inputs). The words follow the change queue's Definitions exactly:
 *
 * <ul>
 * <li><b>Spent</b>, in the month of the purchase: APPROVED purchases
 *     ({@code totalCost}, month of {@code purchasedAt}) and orders sent to HQ
 *     (ORDERED / PARTIALLY_RECEIVED / RECEIVED, {@code totalAmount}, month of
 *     {@code orderDate}). "From the centre" = every order, plus purchases
 *     with the franchise-centre supplier; "outside" = every other purchase.</li>
 * <li><b>Committed</b>, now: per PRA line, the larger of its remaining value
 *     (live PRAs only, never below 0) and its PENDING_APPROVAL purchases —
 *     never the sum, since an approval moves the same money from one to the
 *     other.</li>
 * </ul>
 *
 * Months are calendar months of the stored timestamps, which carry no zone:
 * purchase dates come from the receipt, and server-stamped defaults are in
 * the server's zone, so the server should run in the stores' zone (WIB).
 */
@Service
@RequiredArgsConstructor
public class SpendingService {

    static final List<PurchaseOrderStatus> SENT = List.of(
            PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.PARTIALLY_RECEIVED, PurchaseOrderStatus.RECEIVED);

    static final Set<PurchaseRequestAuthorizationStatus> LIVE = EnumSet.of(
            PurchaseRequestAuthorizationStatus.ACTIVE, PurchaseRequestAuthorizationStatus.PARTIALLY_FULFILLED);

    static final int TOP_PARTS = 5;

    private final RealisasiRepository realisasiRepository;

    private final RealisasiItemRepository realisasiItemRepository;

    private final PurchaseOrderRepository purchaseOrderRepository;

    private final PurchaseOrderItemRepository purchaseOrderItemRepository;

    private final PurchaseRequestAuthorizationRepository praRepository;

    private final PurchaseRequestAuthorizationItemRepository praItemRepository;

    private final PurchaseRequestAuthorizationService praService;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    private final ExpenseRepository expenseRepository;

    /**
     * OWNER+ only. A store Owner gets their own store (and any below it); HQ
     * gets one row per store in its tree, or the one {@code tenantId} names
     * (outside the tree → 400 "Store not found").
     */
    public List<StoreSpendingResponse> getSpending(YearMonth month, UUID tenantId) {

        if (!ScopeContext.hasAtLeast(Scope.OWNER)) {
            throw new AccessDeniedException("Only Owner-level users or higher can see spending");
        }

        YearMonth m = month != null ? month : YearMonth.now();

        List<UUID> ids = tenantId != null
                ? List.of(tenantHierarchyService.resolveTargetTenant(tenantId, true))
                : tenantHierarchyService.getEffectiveTenantIds(UUID.fromString(TenantContext.getTenantId()));

        Map<UUID, Tenant> byId = tenantRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));

        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(t -> summarize(t, m))
                .toList();
    }

    private StoreSpendingResponse summarize(Tenant tenant, YearMonth month) {

        LocalDate first = month.atDay(1);
        LocalDate next = month.plusMonths(1).atDay(1);

        BigDecimal fromCentre = BigDecimal.ZERO;
        BigDecimal outside = BigDecimal.ZERO;
        Map<UUID, BigDecimal> byPart = new HashMap<>();
        Map<UUID, String> partNames = new HashMap<>();

        for (Realisasi r : realisasiRepository.findPurchasedBetween(
                tenant.getId(), RealisasiStatus.APPROVED, first.atStartOfDay(), next.atStartOfDay())) {

            BigDecimal cost = nz(r.getTotalCost());

            if (r.getSupplier() != null && r.getSupplier().isFranchiseCentre()) {
                fromCentre = fromCentre.add(cost);
            } else {
                outside = outside.add(cost);
            }

            for (RealisasiItem item : realisasiItemRepository.findByRealisasiId(r.getId())) {
                addTo(byPart, partNames, item.getPart(), nz(item.getAllocatedLandedCost()));
            }
        }

        for (PurchaseOrder po : purchaseOrderRepository.findOrderedBetween(tenant.getId(), SENT, first, next)) {

            fromCentre = fromCentre.add(nz(po.getTotalAmount()));

            for (PurchaseOrderItem item : purchaseOrderItemRepository.findByPurchaseOrderId(po.getId())) {
                addTo(byPart, partNames, item.getPart(),
                        nz(item.getPrice()).multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }

        BigDecimal spent = fromCentre.add(outside);
        BigDecimal committed = committedNow(tenant.getId());
        BigDecimal budget = tenant.getMonthlyPurchaseBudget();

        // BE-19: other expenses, by the month of their date; the branch pays both categories
        BigDecimal expensesHq = BigDecimal.ZERO;
        BigDecimal expensesBranch = BigDecimal.ZERO;

        for (Expense e : expenseRepository.findDatedBetween(tenant.getId(), ExpenseStatus.ACTIVE, first, next)) {
            if (e.getCategory() == ExpenseCategory.HQ) {
                expensesHq = expensesHq.add(e.getAmount());
            } else {
                expensesBranch = expensesBranch.add(e.getAmount());
            }
        }

        BigDecimal otherExpenses = expensesHq.add(expensesBranch);

        List<SpendingPartResponse> topParts = byPart.entrySet().stream()
                .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed()
                        .thenComparing(e -> partNames.get(e.getKey()), Comparator.nullsLast(String::compareTo)))
                .limit(TOP_PARTS)
                .map(e -> SpendingPartResponse.builder()
                        .partId(e.getKey())
                        .partName(partNames.get(e.getKey()))
                        .amount(e.getValue())
                        .build())
                .toList();

        return StoreSpendingResponse.builder()
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .month(month.toString())
                .spentFromCentre(fromCentre)
                .spentOutside(outside)
                .spent(spent)
                .committed(committed)
                .otherExpenses(otherExpenses)
                .otherExpensesHq(expensesHq)
                .otherExpensesBranch(expensesBranch)
                .budget(budget)
                .overBudget(budget != null ? spent.add(committed).add(otherExpenses).compareTo(budget) > 0 : null)
                .topParts(topParts)
                .build();
    }

    /**
     * Per PRA line: max(remaining value on a live PRA, pending purchases on
     * the line). Both are on the ceiling's basis (unit price × purchased qty),
     * so they compare like for like.
     */
    private BigDecimal committedNow(UUID tenantId) {

        BigDecimal committed = BigDecimal.ZERO;

        for (PurchaseRequestAuthorization pra : praRepository.findByTenantIdIn(List.of(tenantId))) {

            boolean live = LIVE.contains(pra.getStatus());

            for (PurchaseRequestAuthorizationItem line : praItemRepository.findByPurchaseRequestAuthorizationId(pra.getId())) {

                BigDecimal remaining = live
                        ? nz(line.getMaxValue()).subtract(praService.approvedPurchasedValue(line.getId())).max(BigDecimal.ZERO)
                        : BigDecimal.ZERO;

                BigDecimal pending = realisasiItemRepository
                        .findByPurchaseRequestAuthorizationItemIdAndRealisasi_Status(line.getId(), RealisasiStatus.PENDING_APPROVAL)
                        .stream()
                        .map(i -> nz(i.getActualUnitPrice()).multiply(BigDecimal.valueOf(i.getPurchasedQty())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                committed = committed.add(remaining.max(pending));
            }
        }

        return committed;
    }

    private static void addTo(Map<UUID, BigDecimal> byPart, Map<UUID, String> names, Part part, BigDecimal amount) {

        byPart.merge(part.getId(), amount, BigDecimal::add);
        names.putIfAbsent(part.getId(), part.getName());
    }

    private static BigDecimal nz(BigDecimal value) {

        return value != null ? value : BigDecimal.ZERO;
    }
}
