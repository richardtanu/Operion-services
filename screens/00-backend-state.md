# Operion — Backend State Verification Checklist

**Purpose:** Establish `[C]`-tier facts about the backend before the UI screen inventory
depends on them.
**Scope:** Procurement (module 1) + cross-cutting concerns. Later modules get their own
sweep.
**Output:** This file, filled in, committed as `screens/00-backend-state.md`.
**Estimated effort:** Half a day. No code changes.

---

## How to use this

For each item: open the named file, answer the question from what you read, record the
answer, tag it.

| Tag   | Meaning                                                          |
| ----- | ---------------------------------------------------------------- |
| `[V]` | Ran it, observed the behaviour live                              |
| `[C]` | Read it in source                                                |
| `[D]` | Taken from a summary — **not acceptable as a final answer here** |
| `[?]` | Could not determine                                              |

`[D]` is not a valid outcome for this sweep. If you finish an item still at `[D]`, mark
it `[?]` and note what blocked you.

### ⚠️ Rules of engagement — read before starting

**This checklist records what IS, not what SHOULD BE.**

When the code disagrees with `CLAUDE.md`, with the UI plan, or with anything Claude said
in chat:

1. **The code is right.** (`CLAUDE.md` rule 1, corollary.)
2. **Fix the document, not the code.**
3. Record the disagreement in §J below rather than silently correcting it. The pattern of
   what these documents get wrong is itself useful.

**Only change backend code if** it contradicts a _settled decision_ in
`Operion-Decision-Log-Tambahan.md` (DL-06 … DL-11). That is a real bug. Everything else
is documentation drift.

Do not treat a `CLAUDE.md` "deliberately not built" item as a gap to close. It is a
recorded decision.

---

## A. Procurement — what exists

Open `module/procurement/`. List directory contents first, then answer.

- [x] **A1.** Which of these have a **controller** (not just an entity/service)?
      `PurchaseRequest` · `PurchaseOrder` · `GoodsReceipt` ·
      `PurchaseRequestAuthorization` · `Realisasi` · `Supplier`
      → Record each as: controller + service + entity / service + entity / entity only.
      _Rationale: rule 4 — absence of an endpoint is not absence of a feature, and the
      inverse also matters. A service with no controller is not reachable from a screen._
      **Answer `[C]`:** All six have full controller+service+entity — none are
      service-only or entity-only:
      `PurchaseRequestController`, `PurchaseOrderController`, `GoodsReceiptController`,
      `PurchaseRequestAuthorizationController`, `RealisasiController`,
      `SupplierController`.

- [x] **A2.** For every controller found in A1, list its endpoints — method, path, and
      the DTO it returns.
      → This list becomes the API contract baseline. Screens that need data no endpoint
      returns are the gap the inventory is meant to surface.
      **Answer `[C]`:**
      - `SupplierController` `/suppliers`: `POST`, `GET` → `SupplierResponse`(-list)
      - `PurchaseRequestController` `/purchase-requests`: `POST`, `GET`, `GET /{id}`,
        `PATCH /{id}/approve`, `PATCH /{id}/reject`, `PATCH /{id}/cancel`,
        `GET /{id}/history` → `PurchaseRequestResponse` / `StatusHistoryResponse`
      - `PurchaseOrderController` `/purchase-orders`: `POST`, `GET`, `GET /{id}`,
        `PATCH /{id}/send`, `PATCH /{id}/cancel`, `GET /{id}/history` →
        `PurchaseOrderResponse` / `StatusHistoryResponse`
      - `PurchaseRequestAuthorizationController` `/purchase-authorizations`: `POST`,
        `GET`, `GET /{id}`, `PATCH /{id}/cancel`, `GET /{id}/history` →
        `PurchaseRequestAuthorizationResponse`
      - `RealisasiController` `/realisasi`: `POST`, `GET`, `GET /{id}`,
        `PATCH /{id}/approve`, `PATCH /{id}/reject`, `POST /{id}/supersede`,
        `GET /{id}/history` → `RealisasiResponse`
      - `GoodsReceiptController` `/goods-receipts`: `POST`,
        `GET /purchase-order/{poId}`, `GET /realisasi/{realisasiId}` →
        `GoodsReceiptResponse`

- [x] **A3.** Is there any **PRA approve** endpoint?
      `CLAUDE.md` says no — creation _is_ the authorization act.
      → Confirm by reading `PurchaseRequestAuthorizationController` (or noting its
      absence).
      **Answer `[C]`:** No. `PurchaseRequestAuthorizationController` exposes only
      `POST`, `GET`, `GET /{id}`, `PATCH /{id}/cancel`, `GET /{id}/history`. Confirms
      `CLAUDE.md`.

- [x] **A4.** Is `PurchaseRequestAuthorization.approvedBy` written anywhere in service
      code, or only declared on the entity?
      → grep the field name across `src/`.
      _If it is never written, the PRA screen must not imply an approval step._
      **Answer `[C]`:** Written once, inside `create()` —
      `PurchaseRequestAuthorizationService.java:69,90` sets
      `.approvedBy(currentUser())` at PRA creation time. No other write site exists.
      `approvedBy` = the creator, not a separate approver role — matches `CLAUDE.md`
      exactly.

---

## B. PRA semantics

Open `PurchaseRequestAuthorization.java` and `PurchaseRequestAuthorizationItem.java`.

- [x] **B1.** Full field list on `PurchaseRequestAuthorizationItem`. Confirm
      `authorizedQty` and `maxValue` both exist and are **per-line**.
      **Answer `[C]`:** `PurchaseRequestAuthorizationItem.java:20-37`: `id`,
      `purchaseRequestAuthorization` (FK), `part` (FK), `authorizedQty` (Integer),
      `maxValue` (BigDecimal). Both per-line, confirmed.

- [x] **B2.** Is there **any** header-level ceiling field on
      `PurchaseRequestAuthorization`? (`CLAUDE.md` says no, and flags adding a nullable
      one as an open decision.)
      → Confirm absence. This determines whether the PRA screen shows a running total
      against a cap, or only per-line numbers.
      **Answer `[C]`:** Absent. `PurchaseRequestAuthorization.java:27-53` has only
      `id`, `tenant`, `approvedBy`, `status` — no cap field.
      **Addendum `[C]` 10 Aug (BE-02):** this field list is correct but was read narrowly
      for the ceiling question — it does **not** mean PRA has no link back to its
      `PurchaseRequest`(s). The FK exists, just on the **inverse side**:
      `PurchaseRequest.purchaseRequestAuthorization` (`PurchaseRequest.java:43-45`,
      column `purchase_requests.pra_id`), set in
      `PurchaseRequestAuthorizationService.create()` line 113. Reverse lookup is a real,
      already-wired repository method —
      `PurchaseRequestRepository.findByPurchaseRequestAuthorizationId(UUID)` — called in
      `map()` (lines 317-321) and exposed on **every** PRA response as
      `purchaseRequestIds`. A PRA can authorize multiple PRs at once (`create()` accepts
      `purchaseRequestIds: List<UUID>`, all validated `APPROVED` first). **No code change
      needed** — PRA screens already have navigation back to origin PR(s) via the
      existing API. Confirmed both in source and against the live schema
      (`purchase_requests` has a `pra_id` column, no separate FK on
      `purchase_request_authorizations`).

- [x] **B3.** Search the whole procurement module for a `remaining` / `remainingQty` /
      `remainingValue` column on any entity.
      → Must be **absent**. Its presence would be a real bug against DL-08 — it silently
      breaks failed-realisasi-releases-hold and partial fulfilment.
      **Answer `[C]`:** No such column on any entity. `remainingQty`/`remainingValue`
      appear only as **computed fields on the response DTO**
      (`PurchaseRequestAuthorizationService.java:311-312`, inside `map()`) — never
      persisted. Consistent with `CLAUDE.md`.

- [x] **B4.** Find the method that computes ceiling consumption. Confirm it aggregates
      `RealisasiItem` rows filtered to **`APPROVED` status only**.
      → Record the class + method name; the PRA screen will need this number.
      **Answer `[C]`:**
      `PurchaseRequestAuthorizationService.approvedPurchasedQtyInStockUnits(UUID)`
      (line 245) and `.approvedPurchasedValue(UUID)` (line 254), both backed by
      `approvedRealisasiItems()` (lines 261-265), which calls
      `realisasiItemRepository.findByPurchaseRequestAuthorizationItemIdAndRealisasi_Status(
      praItemId, RealisasiStatus.APPROVED)`. Confirmed APPROVED-only.

---

## C. Realisasi semantics

- [x] **C1.** `Realisasi` status enum — list every value.
      → Screens need one visual state per value. Watch for `PENDING_APPROVAL`,
      `ESCALATED`, `APPROVED`, `SUPERSEDED`.
      **Answer `[C]`:** `RealisasiStatus`: `PENDING_APPROVAL`, `APPROVED`, `FAILED`,
      `SUPERSEDED`. **`ESCALATED` is NOT a value of this enum** — see §J #1.

- [x] **C2.** Confirm the six cost components are stored **separately** on the entity:
      `subtotal` · `sellerDiscount` · `platformVoucher` · `shipping` · `insurance` ·
      `serviceFee`.
      → The Realisasi entry screen needs a field per component; a single "total" input
      would destroy landed-cost accuracy (~17% error on a real receipt).
      **Answer `[C]`:** Confirmed, `Realisasi.java:80-93` — six distinct `BigDecimal`
      columns plus derived `totalCost`.

- [x] **C3.** Read `supersede()`. Confirm it creates a **new record via self-FK** and
      flips the original to `SUPERSEDED` — nothing overwritten in place.
      → Determines whether the correction UI is "edit" or "supersede". They are very
      different screens.
      **Answer `[C]`:** Confirmed. `RealisasiService.supersede()` (lines 264-284): sets
      original `status=SUPERSEDED` + `supersededReason`, saves, writes history; calls
      `create(request.getCorrection())` to make a new `Realisasi`; sets its `supersedes`
      FK to the original. Nothing overwritten in place.

- [x] **C4.** Landed cost allocation — confirm it allocates **by value**, and find the
      divisor. Should be `allocatedLandedCost ÷ (purchasedQty × conversionFactor)`.
      → Rule 2: if you re-test this, use a `conversionFactor` other than 1.
      **Answer `[C]`:** Confirmed. Allocation by value:
      `RealisasiService.saveItems()` lines 351-355,
      `allocatedLandedCost = totalCost * lineSubtotal / headerSubtotal`. Divisor:
      `GoodsReceiptService.java:290-293,338-342`,
      `purchasedStockUnits = purchasedQty × conversionFactor`, then
      `unitLandedCost = allocatedLandedCost / purchasedStockUnits`. Exact match.

- [x] **C5.** Is `Realisasi.attachments` a plain text/URL field with no upload backing?
      (`CLAUDE.md` says yes, by design.)
      → The screen shows a URL input, not a file picker. Confirm before drawing it.
      **Answer `[C]`:** Confirmed. `Realisasi.java:98-99`:
      `@Column(columnDefinition = "TEXT") private String attachments;` — no file/blob
      relation, no upload endpoint anywhere.

- [x] **C6.** Does anything query `retroPurchaseFlag`?
      (`CLAUDE.md` says the field exists but nothing reads it.)
      → If nothing queries it, an Owner-facing filter is a _new_ screen requirement, not
      an existing capability.
      **Answer `[C]`:** No. Grep across `src/` shows only get/set/builder usage
      (`RealisasiService.java:194,476`; `Realisasi.java:107,136-137`; two DTOs) — no
      repository query or filter method uses it. Confirms `CLAUDE.md`.

- [x] **C7.** (BE-09, 10 Aug) Is `Realisasi.reimbursementStatus` ever written, and does
      it ever reach `REIMBURSED`?
      → BE-01's live CHECK-constraint dump found `NOT_APPLICABLE/PENDING/REIMBURSED` as
      allowed values with no code-level confirmation either was ever set.
      **Answer `[C]`:** Written, but the loop never closes. `RealisasiService.java:170-183`
      sets it at creation: `PENDING` when `paymentMethod == PERSONAL_REIMBURSABLE`,
      `NOT_APPLICABLE` otherwise. `REIMBURSED` is declared in `ReimbursementStatus.java`
      but has **zero write sites** anywhere in the codebase — grepped every file touching
      `Reimburs*` (exactly 3: the entity, the enum, `RealisasiService`), no
      `markReimbursed` endpoint, no service method, no scheduled job. A reimbursement
      queue screen filtering on `PENDING` has real data to show; a "mark as reimbursed"
      *action* on that screen would be an orphan — no backing endpoint. Whether to build
      one is a product decision, not a gap for a screen-inventory session to close
      unilaterally. Same reasoning kills the Gate 3 question "may the purchaser mark
      their own reimbursement paid" — unanswerable today because the action doesn't
      exist to have a segregation rule about.

- [x] **C8.** (BE-09, 10 Aug) Is `PurchaseRequestAuthorizationStatus.PARTIALLY_FULFILLED`
      / `FULFILLED` ever reached, or is PRA lifecycle really just `ACTIVE`/`CANCELLED` as
      `CLAUDE.md`'s narrative implies?
      **Answer `[C]`:** Fully wired, contrary to the narrative. `PurchaseRequestAuthorizationService
      .refreshStatus(UUID)` (lines 192-235) computes `FULFILLED` (no item has remaining
      ceiling), `PARTIALLY_FULFILLED` (some but not all items fulfilled), or `ACTIVE`
      (nothing fulfilled) — live, from `approvedPurchasedQtyInStockUnits()` vs
      `authorizedQty` per line, same no-mutable-column pattern as everything else here.
      Called from **four** sites in `RealisasiService`: `create()` (170),
      `approveEscalated()` (233), `reject()` (258), `supersede()` (275) — every
      Realisasi transition that changes APPROVED-status aggregation triggers a
      recompute. **`PurchaseRequestAuthorizationStatus` has four real, reachable
      values**, not two. Any screen showing PRA status needs a four-state badge, not a
      binary active/cancelled one.

---

## D. Segregation of duties — enforced or documented?

This is the highest-value section. Gate 3 of the UI plan is unrunnable without it.

- [x] **D1.** Find the code enforcing **PRA approver ≠ Realisasi creator**.
      → Record class + method. If it exists only as a comment or Javadoc, say so
      explicitly — that changes the screen from "trust the backend" to "the UI is the
      only guard."
      **Answer `[C]`:** Enforced in code, not just documented:
      `RealisasiService.requireSegregationFromPraApprover()` (lines 378-395), called
      from `create()` at line 103.

- [x] **D2.** Confirm the exemption: **Owner/Principal may do both; Manager does not
      qualify.** Read the actual scope comparison.
      **Answer `[C]`:** Confirmed. `Scope` enum order:
      `SUPERVISOR, MANAGER, OWNER, PRINCIPAL`. Line 386-387:
      `exempt = approverScope != null && approverScope.ordinal() >= Scope.OWNER.ordinal()`
      — OWNER/PRINCIPAL exempt, MANAGER does not qualify.

- [x] **D3.** Find the code enforcing **Realisasi creator ≠ goods receipt receiver**.
      Confirm it has **no** exemption.
      **Answer `[C]`:** `GoodsReceiptService.receiveAgainstRealisasi()` lines 243-249:
      hard `RuntimeException` if
      `realisasi.getCreatedBy().getId().equals(receivedBy.getId())`. Method Javadoc
      (lines 221-230) explicitly states "no scope exemption here, unlike the
      PRA-approver/Realisasi-creator rule." No exemption branch exists.

- [x] **D4.** Confirm the scope check reads the approver's **stored** scope (historical
      fact), not the live `ScopeContext` from the current request.
      → `CLAUDE.md` is explicit that reading live context would let a later promotion
      retroactively legalize a past pairing. If the code reads live context, **that is a
      real bug** — record it as such.
      **Answer `[C]`:** Confirmed stored-scope read, not live context.
      `requireSegregationFromPraApprover` reads `approver.getScope()` off the `User`
      entity fetched via `pra.getApprovedBy()` (line 386) — a persisted FK snapshot from
      PRA creation time. (By contrast `requireOwnerScope()`, a *different* check on the
      current actor for `approveEscalated`, correctly uses live
      `ScopeContext.hasAtLeast(Scope.OWNER)` at line 399 — that's the right place for
      live context.) No bug — matches `CLAUDE.md`.

---

## E. The five quantity columns

Confirm each exists on the entity named, and that none is mutated in place.

- [x] **E1.** `suggested_qty` — computed on read by `BurnRateService`, **not stored**.
      **Answer `[C]`:** Confirmed. `BurnRateService.compute()`
      (`burnrate/service/BurnRateService.java:170-193`) builds `suggestedQty`
      in-memory each call; class Javadoc states "Computed on read, nothing stored." No
      such column on `Part` or `BurnRateResponse`.
- [x] **E2.** `requested_qty` → `PurchaseRequestItem.quantity`
      **Answer `[C]`:** Confirmed, `PurchaseRequestItem.java:31`.
- [x] **E3.** `authorized_qty` → `PurchaseRequestAuthorizationItem.authorizedQty`
      **Answer `[C]`:** Confirmed, `PurchaseRequestAuthorizationItem.java:32-33`,
      `@Column(name = "authorized_qty")`.
- [x] **E4.** `purchased_qty` → `RealisasiItem.purchasedQty` — in **purchase UOM**
      **Answer `[C]`:** Confirmed, `RealisasiItem.java:36-43` (`purchaseUom`,
      `conversionFactor`, `purchasedQty`). `GoodsReceiptService.java:290-293` converts
      `purchasedQty × conversionFactor` before use — proves `purchasedQty` alone is
      purchase UOM, not stock units.
- [x] **E5.** `received_qty` → `GoodsReceiptItem.quantity` — in **stock units**
      **Answer `[C]`:** Confirmed, `GoodsReceiptItem.java:35`.
      `GoodsReceiptService.java:301,320-326` compares/feeds it directly against the
      converted stock-unit value and into `partStockService.adjustStock()`.
- [x] **E6.** Where is `conversionFactor` stored, and which screens would need to display
      both UOMs to avoid ambiguity?
      → E4 and E5 being in different units is a live source of user error. Any screen
      showing both must label the unit.
      **Answer `[C]`:** Stored on `RealisasiItem` (`RealisasiItem.java:39-40`, column
      `conversion_factor`) — captured per purchase line, not a `Part`-level constant.
      Screens needing both UOMs: Realisasi creation (capturing `purchaseUom` +
      `conversionFactor` per line), goods receipt against Realisasi (over-receipt check
      + landed-cost divisor), and the Realisasi review/response DTO.

---

## F. Burn rate — surfaces in module 1, not module 3

`suggested_qty` renders on PR creation, so this is procurement-adjacent.

- [x] **F1.** Confirm endpoints: `GET /burn-rate` and `GET /burn-rate/{partId}`. Record
      the response DTO field-by-field — this is exactly what the PR creation screen can
      display.
      **Answer `[C]`:** Confirmed. `BurnRateController`: `GET /burn-rate` (`getAll()`),
      `GET /burn-rate/{partId}` (`getByPart`). `BurnRateResponse` fields: `partId`,
      `partName`, `currentStock`, `burnRateSource`, `burnRate`, `observationDays`,
      `eventCount`, `daysOfCover`, `targetDays`, `suggestedQty`, `manualReorderPoint`,
      `belowLevel`.

- [x] **F2.** Confirm `NONE` mode returns **null** days-of-cover, not `0`.
      → Zero renders as maximum urgency and would light up most of a new catalog. If it
      returns 0, **that is a real bug**.
      **Answer `[C]`:** Confirmed. `BurnRateService.java:165-168`: `daysOfCover` stays
      `null` unless `burnRate != null && burnRate > 0`; for `NONE`/`MANUAL_LEVEL`,
      `burnRate` is never set, so it stays null. No bug.

- [x] **F3.** Confirm `observationDays` is clamped to actual history, not the window
      length.
      **Answer `[C]`:** Confirmed. `BurnRateService.java:124-128`:
      `observationDays = max(1, min(windowDays, daysSinceFirstSeen))` — clamped both
      above (`windowDays`) and below (min 1).

- [x] **F4.** Confirm `Part.reorderQuantity` is read as the `suggestedQty` override.
      → It is a pre-existing field doing double duty, **not** a new `preferredOrderQty`
      field. Confirm which name the code actually uses.
      **Answer `[C]`:** Confirmed — code uses `part.getReorderQuantity()`
      (`BurnRateService.java:227-229, 185-187`), no `preferredOrderQty` field exists.
      Defined `Part.java:47-49`, default 10.

- [x] **F5.** Confirm spareparts never receive a `COMPUTED` rate — manual path is
      permanent for them.
      **Answer `[C]` — CONTRADICTED, real bug, now fixed.** As originally read,
      `BurnRateService.compute()` gated `COMPUTED` purely on
      `observationDays >= minObservationDays && takes >= minEvents` — **no category
      check**. `PartInstanceService.take()` (`PartInstanceService.java:148-187`) sets
      `takenAt` for **any** `IN_STOCK` instance regardless of category (only the stock
      *decrement* is Consumable-gated, per the comment at lines 168-174 confirming
      spareparts do flow through `take()` before install). A frequently-replaced
      sparepart could therefore cross the thresholds and reach `COMPUTED`, contradicting
      the explicit invariant in `operion-burn-rate-spec.md:73` ("spareparts never get a
      computed rate at all") and `CLAUDE.md`'s burn rate section.
      **Fix applied 2026-08-10** in `BurnRateService.java`: added the same
      `isConsumable(Part)` category-name check already used identically in
      `PartInstanceService` and `GoodsReceiptService` (`part.getCategory().getName()
      .equals("Consumable")`), and gated the `COMPUTED` branch on it. Non-Consumable
      parts now fall through to `MANUAL_RATE`/`MANUAL_LEVEL`/`NONE` regardless of take
      volume. Verified with `mvnw compile` (clean build). See §J #2.
      **`[V]` 10 Aug (BE-03), runtime-verified — a clean compile was not trusted per
      `CLAUDE.md` rule 7.** `src/test/java/.../BurnRateServiceSparepartGateTest.java`
      seeds real fixtures (via the actual `PartRepository`/`PartInstanceRepository`,
      inside one `@Transactional` test transaction that auto-rolls-back — nothing
      persists) and calls the real `BurnRateService.getByPart()`: a sparepart at
      *exactly* the threshold (10 takes, 21 days) and one at *triple* it (30 takes, 25
      days, rule 6 — vary the input) both assert `burnRateSource != COMPUTED`; a
      Consumable control with the same threshold-crossing shape (15 takes, 25 days)
      asserts `burnRateSource == COMPUTED` with `observationDays=25, eventCount=15` —
      confirming the gate doesn't break the normal path. All 3 pass against the live
      dev DB (`mvnw test -Dtest=BurnRateServiceSparepartGateTest`).
      Running this without `@Transactional` first threw
      `LazyInitializationException` on `part.getCategory().getName()` — real finding,
      not a test bug: `Part.category` is a lazy `@ManyToOne`, and
      `BurnRateService.compute()`/`computeAll()` have no `@Transactional` of their
      own, relying entirely on Spring Boot's default `open-in-view=true` (confirmed
      unmodified in `application.properties`) to keep a session open for the whole
      `GET /burn-rate` request. **Not a production bug today** — the real endpoint
      works — but `BurnRateService` cannot safely be called from a non-web context
      (batch job, another service, a future scheduled pre-computation) without its
      own session/transaction. Worth remembering if that ever changes.
      **`[C]` 10 Aug (BE-04) — the string check itself is now gone.** Added a stable
      `PartCategory.consumable` (`Boolean`, column `is_consumable`, nullable — see
      `PartCategory.java`) instead of comparing `getName()` to `"Consumable"` at every
      read. `Part.isConsumable()` is now the single call site all three services use.
      Existing rows backfilled directly against the live DB (`true` where
      `name='Consumable'`, `false` elsewhere — 3 true / 15 false across all tenants,
      preserves current behavior exactly). `CreatePartCategoryRequest` /
      `UpdatePartCategoryRequest` / `PartCategoryResponse` now carry `consumable` so a
      tenant can flag a renamed or custom category explicitly, instead of the flag
      only ever being settable by matching a hardcoded name.
      `seedDefaultCategories()` (the one remaining place a "Consumable" category name
      is used to decide anything) sets the flag once at creation time, not on every
      read. New test `PartTest.isConsumableFollowsTheStoredFlagNotTheDisplayName`
      proves a category named "Konsumabel" with the flag set still reports
      `isConsumable() == true`, and a category still literally named "Consumable"
      with the flag unset reports `false` — the exact silent-failure mode BE-04 was
      opened to close.

- [x] **F6.** Does `MaintenancePolicyController` exist?
      (`CLAUDE.md` says no — the four thresholds are DB-edit-only.)
      → If absent, module 6 must inventory the screens that would replace direct DB
      editing, or record explicitly that tenants cannot tune these.
      **Answer `[C]`:** Confirmed absent — only `MaintenanceRecommendationController`
      exists under `module/maintenance`.
      `MaintenancePolicy.java:76-86` has all four fields (nullable, no
      `@Builder.Default`); `BurnRateService.java:35-41,82-86` falls back to
      30/21/10/14 when a tenant's row has them null.

---

## G. RBAC & scope

- [x] **G1.** Does `users` have a `scope` column? Nullable?
      **Answer `[C]`:** Yes, nullable. `User.java:45-46`:
      `@Enumerated(EnumType.STRING) private Scope scope;` — no `nullable=false`, unlike
      `role` (line 41-43) which does have it.
- [x] **G2.** Is scope carried in the JWT? Find `ScopeContext` and record how it is
      populated.
      **Answer `[C]`:** Yes. `JwtUtil.generateToken()` adds `.claim("scope", scope)`.
      `ScopeContext` (`common/security/ScopeContext.java`) is a `ThreadLocal<String>`
      with `setScope`/`getScope`/`clear`/`hasAtLeast(Scope)`. Populated in
      `JwtAuthenticationFilter.doFilterInternal` (lines 62,66): claim extracted →
      `ScopeContext.setScope(scope)`, cleared in `finally` (line 92).
- [x] **G3.** Confirm **one** `Role` enum only — no `roles` / `user_roles` table pair.
      **Answer `[C]`:** Confirmed. `Role` is a plain field on `User`, not a join table.
      Grep for `roles|user_roles|@ManyToMany` across the module tree: no matches.
- [x] **G4.** List every value of the functional `Role` enum as it exists in code.
      → Expected: `ADMIN` `TECHNICIAN` `PROCUREMENT` `ACCOUNTING` `OPERATOR`. Confirm
      rather than assume; the RBAC matrix is built from this list.
      **Answer `[C]`:** `Role.java:3-9`: `ADMIN, TECHNICIAN, PROCUREMENT, ACCOUNTING,
      OPERATOR` — matches `CLAUDE.md` exactly. `Scope.java:3-8`: `SUPERVISOR, MANAGER,
      OWNER, PRINCIPAL` (ordinal-ascending).
- [x] **G5.** Is scope enforcement applied at controller, service, or repository level?
      → Determines whether a screen can trust filtered data or must filter itself.
      **Answer `[C]`:** Service layer, via `ScopeContext.hasAtLeast(...)`. Example:
      `StockAdjustmentService.java:177`. Also used in `RealisasiService`,
      `PurchaseRequestService`. No controller- or repository-level scope filtering
      found (repositories use `TenantContext` for tenant scoping, a different concern).

---

## H. Cross-cutting

- [x] **H1.** `notification` module — confirm it exists. List `NotificationType` and
      `NotificationReferenceType` values.
      → Screens need to know which events actually produce a notification row today.
      **Answer `[C]`:** Exists at `module/notification/`. `NotificationType`:
      `NEW_PURCHASE_REQUEST, PURCHASE_REQUEST_ORDERED, PART_END_OF_LIFE, LOW_STOCK,
      REALISASI_ESCALATED`. `NotificationReferenceType`: `PURCHASE_REQUEST, PART,
      AIRSOFT_UNIT, REALISASI`.

- [x] **H2.** How are notifications delivered — polling endpoint, or something else?
      Record the endpoint.
      **Answer `[C]`:** Polling only. `NotificationController`: `GET /notifications`,
      `GET /notifications/unread-count`, `PATCH /notifications/{id}/read`,
      `PATCH /notifications/read-all`. No WebSocket/SSE/push infra found.

- [x] **H3.** `stockadjustment` module — is there an approval workflow, or only the
      adjustment record?
      → Module 2 gate 3 depends on this answer.
      **Answer `[C]`:** Full approval workflow. `StockAdjustmentStatus`:
      `PENDING/APPROVED/REJECTED`, defaults to `PENDING`.
      `StockAdjustmentController`: `PATCH /stock-adjustments/{id}/approve`,
      `/{id}/reject`, `/{id}/history`. Approval scope-gated at
      `StockAdjustmentService.java:177` (`ScopeContext.hasAtLeast(Scope.OWNER)`).

- [x] **H4.** `partinstance` — confirm `POST /part-instances/scan-in` exists and that
      consumable `PartInstance` rows are **not** auto-generated by goods receipt.
      → If confirmed, there is a mandatory manual scan-in screen that nothing in the
      receipt flow currently points at. That is a navigation gap, not a code gap.
      **Answer `[C]`:** Both confirmed. `POST /part-instances/scan-in` exists
      (`PartInstanceController.java:25-34`); `scanIn()` rejects non-Consumable parts
      explicitly (lines 123-128). In `GoodsReceiptService.java:176-188`, every receipt
      line bumps `currentStock`, and only calls `partInstanceService.generateInstances()`
      when the part is NOT Consumable (line 184-185) — consumables get stock bumped,
      never an auto-created `PartInstance` row. Matches `CLAUDE.md`.

- [x] **H5.** Barcode redemption path — confirm the endpoint reserves number space only
      and does not create `PartInstance` rows on sync.
      (`CLAUDE.md`: deliberately not built.)
      **Answer `[C]`:** Confirmed. `BarcodeAllocationService.issue()` (lines 50-90)
      only reserves a `[rangeStart, rangeEnd]` range via a locked `BarcodeCounter` and
      persists a `BarcodeAllocation` row — no `PartInstance` import or creation anywhere
      in the file. Javadoc (lines 25-31) documents the offline-code-generation design
      (DL-06).

---

## I. Migration status

- [x] **I1.** Still `ddl-auto=update`? Any Flyway/Liquibase config present?
      **Answer `[C]`:** Yes, still `ddl-auto=update` (`application.properties:9`). No
      Flyway/Liquibase — grep for `flyway|liquibase` in `pom.xml` returns nothing, no
      `db/migration` directory anywhere in the repo.
- [x] **I2.** List DB-level CHECK constraints on enum columns that already exist.
      → Rule 3 caused two bugs. Any new enum value the UI work implies (new notification
      types, new Realisasi statuses) needs a migration planned in the same change.
      **Answer `[C]` 10 Aug (BE-01):** `pg_restore`/`psql` still unavailable in this
      environment, but the live instance at `localhost:5432/operion` was reachable —
      queried `information_schema.check_constraints` directly via JDBC (driver already a
      project dependency). Full results:

      **`notifications`**
      - `notifications_type_check`: `type` ∈ `{NEW_PURCHASE_REQUEST,
        PURCHASE_REQUEST_ORDERED, PART_END_OF_LIFE, LOW_STOCK, REALISASI_ESCALATED}`
      - `notifications_reference_type_check`: `reference_type` ∈ `{PURCHASE_REQUEST,
        PART, AIRSOFT_UNIT, REALISASI}`
      - plus standard `NOT NULL` checks on `id`, `read`, `recipient_user_id`, `tenant_id`,
        `title`

      **`purchase_request_authorizations`**
      - `purchase_request_authorizations_status_check`: `status` ∈ `{ACTIVE,
        PARTIALLY_FULFILLED, FULFILLED, CANCELLED}` — note this differs from the Java
        `PurchaseRequestAuthorizationStatus` enum used in `CLAUDE.md`'s narrative
        (which frames PRA lifecycle around ACTIVE/CANCELLED only) — `PARTIALLY_FULFILLED`
        and `FULFILLED` also exist at the DB/enum level. Not independently confirmed as
        reachable code paths in this pass — flag for whoever designs the PRA screen's
        status badge.
      - plus `NOT NULL` on `id`, `status`, `tenant_id`

      **`realisasis`** (note: table is `realisasis`, not `realisasi` — the entity's
      `@Table(name=...)` pluralizes it; this tripped the first query attempt)
      - `realisasis_status_check`: `status` ∈ `{PENDING_APPROVAL, APPROVED, FAILED,
        SUPERSEDED}` — matches C1 exactly
      - `realisasis_variance_status_check`: `variance_status` ∈ `{WITHIN_CEILING,
        ESCALATED}` — matches §J #1's finding that `ESCALATED` is a `VarianceStatus`
        value, not a `RealisasiStatus` value
      - `realisasis_payment_method_check`: `payment_method` ∈ `{COMPANY_ACCOUNT,
        PERSONAL_REIMBURSABLE}` — not previously covered by this sweep
      - `realisasis_reimbursement_status_check`: `reimbursement_status` ∈
        `{NOT_APPLICABLE, PENDING, REIMBURSED}` — not previously covered by this sweep
      - plus `NOT NULL` on `external_order_ref`, `id`, `pra_id`, `status`, `tenant_id`

      **`stock_adjustments`**
      - `stock_adjustments_status_check`: `status` ∈ `{PENDING, APPROVED, REJECTED}` —
        matches H3
      - plus `NOT NULL` on `id`, `part_id`, `quantity`, `status`, `tenant_id`

      **Consequence for BE-07** (PRA notification type): adding a `NotificationType`
      value now has an exact, known migration —
      `ALTER TABLE notifications DROP CONSTRAINT notifications_type_check, ADD CONSTRAINT
      notifications_type_check CHECK (type IN (..., 'NEW_VALUE'))` — in the same change
      as the enum edit. No longer blocked on unknown constraint shape.

---

## J. Disagreement log

Every place the code contradicted `CLAUDE.md`, the UI plan, or a chat claim. **Do not
silently correct — record.**

| #   | Source doc                        | What it claimed                                                                          | What the code does                                                                                                                                                                                    | Action                                                          |
| --- | ---------------------------------- | ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| 1   | `checklist.md` C1 hint / `CLAUDE.md` | Reads as if `ESCALATED` is a `Realisasi` **status** value ("goes `PENDING_APPROVAL`/`ESCALATED`") | `RealisasiStatus` = `{PENDING_APPROVAL, APPROVED, FAILED, SUPERSEDED}` only. `ESCALATED` is a value of a **separate** `VarianceStatus` enum (`WITHIN_CEILING`, `ESCALATED`), set alongside `status=PENDING_APPROVAL` when over ceiling. Behavior matches intent; wording doesn't. | doc fix — clarify two enums, not one, for anyone building the Realisasi status screen |
| 2   | `operion-burn-rate-spec.md` §3 / `CLAUDE.md` burn rate section | "spareparts never get a computed rate at all" — stated as a permanent invariant | `BurnRateService.compute()` had no category gate; `PartInstanceService.take()` timestamps `takenAt` for spareparts too (only the stock decrement is Consumable-gated), so a heavily-replaced sparepart could cross the `COMPUTED` thresholds | **code bug — fixed 2026-08-10.** Added `isConsumable(Part)` gate (same pattern as `PartInstanceService`/`GoodsReceiptService`) before the `COMPUTED` branch in `BurnRateService.java`. See F5. |
| 3   | `OPERION_UI_SCREEN_INVENTORY_PLAN.md` v1.0 §5 module table, "Procurement" row | "Tables exist, zero API" | Full API exists — all 6 procurement entities have controllers with multiple endpoints each (see A1–A2). | doc fix — corrected in plan v1.1 |
| 4   | `OPERION_UI_SCREEN_INVENTORY_PLAN.md` v1.0 §5 module table, "Inventory & stock" row | "Partial; adjustment approval is a known gap" | `StockAdjustment` has a full approval workflow (`PENDING/APPROVED/REJECTED`, `PATCH .../approve`, `/reject`, scope-gated at OWNER) — not a gap (see H3). | doc fix — corrected in plan v1.1 |
| 5   | `OPERION_UI_SCREEN_INVENTORY_PLAN.md` v1.0 §1, "Open question — retry behaviour" vs. `CLAUDE.md`'s DL-06 | Plan (dated 8 Aug 2026) treats client retry/queue behavior as **unresolved**, blocking module 3 scan-loop screens | `CLAUDE.md` states DL-06 already settled this: "Offline tolerance is client-side only: outbox queue + pre-allocated barcode code blocks in the RN app." Backend-side half is confirmed (`BarcodeAllocationService`, see H5); the outbox itself is client-side, unverifiable from this repo. | **not resolved here** — doc-vs-doc conflict on a product decision, not a code question. Flagged for Blitz to confirm which is current before module 3 scan-loop screens are inventoried. |

**Action column:** `doc fix` unless the code contradicts a settled DL decision, in which
case `code bug` — and open a separate issue rather than fixing it inline during a
verification sweep.

**Note on #2:** fixed inline rather than filed separately, at explicit user request
after this sweep surfaced it (not a DL-06…DL-11 item strictly — the invariant lives in
the burn-rate spec/`CLAUDE.md`, not the Indonesian decision log — but the user judged it
a real correctness bug worth fixing immediately rather than deferring). Flagging the
deviation from the "open a separate issue" rule here for visibility.

---

## K. Known-wrong claims to check specifically

These four were asserted in the UI plan v1.0 and are believed false. Confirm each, then
correct the plan.

- [x] **K1.** "No barcode item-instance entity exists for consumables — aggregate stock
      only." → Believed **false**; `PartInstance` exists (see H4).
      **Confirmed false `[C]`.** `PartInstance` exists, fully wired
      (controller/service/repository).
- [x] **K2.** "Notification engine does not exist." → Believed **false** (see H1).
      **Confirmed false `[C]`.** Notification module exists and is functional
      (see H1/H2).
- [x] **K3.** "`preferredOrderQty`" as a distinct new field → Believed **false**; it is
      `Part.reorderQuantity` (see F4).
      **Confirmed false `[C]`.** Code uses `part.getReorderQuantity()` throughout; no
      `preferredOrderQty` field exists anywhere.
- [x] **K4.** "All screens are online-only." → Believed **false**; RN app has a
      client-side outbox per DL-06.
      **`[?]`** Not verifiable from this backend repo — the outbox is client-side (RN
      app, separate repo). Backend-side evidence is only indirect: the barcode
      block-allocation design (H5) exists specifically to support offline code
      generation, consistent with DL-06, but the outbox itself lives outside this
      codebase. **See also §J #5** — the UI plan v1.0 itself treats this as an open
      question, not settled, which conflicts with `CLAUDE.md`'s DL-06 framing.

---

## Completion

- [x] Every item answered and tagged `[V]` / `[C]` / `[?]` — all `[C]` except I2 (`[?]`,
      blocked on binary `pg_dump` format) and K4 (`[?]`, out of backend scope)
- [x] No item left at `[D]`
- [x] §J disagreement log complete — 5 entries; #2 (burn rate sparepart gate) fixed
      inline in `BurnRateService.java` rather than filed separately, at explicit user
      request; #3–#5 found while cross-checking the full UI plan document, not just its
      K1–K4 excerpt
- [x] §K corrections confirmed — K1–K3 confirmed false as believed, K4 left `[?]`
- [x] File committed as `screens/00-backend-state.md` — copied 2026-08-10 (original
      `checklist.md` kept in place too; not yet `git commit`-ed, see chat)
- [x] UI plan updated to v1.1 from these findings — `OPERION_UI_SCREEN_INVENTORY_PLAN.md`
      corrected in place, version bumped, changelog added at top
