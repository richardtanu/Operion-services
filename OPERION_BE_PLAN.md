# Operion — Backend Plan & Conformance Audit

**Version:** 3.4 · 29 Jul 2026
**Replaces:** v3.3 (28 Jul), v3.2 (28 Jul), v3.1 (28 Jul), v3.0 (28 Jul), v2.0 (26 Jul), v1.0 (15 Jul) — all archived, see §0.1
**Repo:** `richardtanu/Operion-services` — Java Spring Boot + PostgreSQL, modular monolith
**Companion:** `Operion-Decision-Log-Tambahan.md` (Indonesian) — rationale for `DL-06` … `DL-11`

**What changed from v3.3:** burn rate / `suggested_qty` (§6.1) is built — the last link in the five-quantity chain. Full detail in the new §6.1. Highlights: `operion-burn-rate-spec.md`'s own build-order step 1 ("confirm stock decrements at take time") turned up a case the spec didn't anticipate — it decremented at **neither** take nor exhaust, a real pre-existing gap, not a spec error. Resolved by wiring `PartInstanceService.take()` to decrement stock (Consumable-scoped, to avoid double-counting the sparepart install path, which already decrements separately). That wiring created a genuine circular bean dependency (`PartStockService` ↔ `PartInstanceService`), fixed with `@Lazy` on an explicit constructor parameter — see new CLAUDE.md rule 7, `@Lazy` on a Lombok `@RequiredArgsConstructor` field does **not** work, Lombok doesn't copy the annotation onto the generated constructor. Spec §7.3's flagged gap (`preferredOrderQty`) turned out to already exist under a different name (`Part.reorderQuantity`, present since the original build, never read anywhere) — reused rather than adding a column. All four spec §8 test cases relevant to non-trivial logic (2, 5, 9, 10) verified live, including the clamp test's exact numbers (`observationDays: 6, burnRate: 2.0000, daysOfCover: 2.0000`, not the wrong-implementation `0.13`/`30`).

**What changed from v3.2 → v3.3:** the one remaining open item (#20, `conversionFactor`) was live-tested with `conversionFactor=6` — and it caught a real bug, not a non-issue. The landed-cost divisor in `GoodsReceiptService.receiveAgainstRealisasi` used `purchasedQty` (purchase UOM) instead of `purchasedQty × conversionFactor` (stock units), so #19's "FIXED" from the previous round was only correct by coincidence (that live test happened to use `conversionFactor=1`). **Fixed and re-verified live**, see §3.3 #19/#20. **The audit is now fully closed — zero open items.**

**What changed from v3.1 → v3.2:** every open item from v3.1's audit was resolved that round — most by reading the actual source directly rather than working from a summary. Two of v3.1's own findings turned out to be wrong and were corrected (§3.2 #11, §3.3 #23; also §3.7 #35). One item was closed empirically with a real concurrency test rather than argued (§3.5 #31). Two genuine gaps were found and fixed (§3.3 #19 — later found incomplete, see above; §3.7 #34). Three blocking decisions were made and implemented (§3.3 #21, §3.6 #36, §6.2).

---

## 0. How to use this file

- **§1–§4 — audit.** Fully closed (§4 is now empty). Read §3 for what was found and how.
- **§5–§7 — build.** What to do next, now that the audit is clean.

**Recording convention.** `PASS` / `PARTIAL` / `FAIL` / `N/A` / `FIXED`, with the file path where it was found or changed.

### 0.1 Provenance — evidence tiers

| Tier    | Meaning                                                                                   |
| ------- | ------------------------------------------------------------------------------------------ |
| **[V]** | Verified live against a running instance with observed behaviour                          |
| **[C]** | Confirmed by reading the actual source code directly — stronger than a summary claim      |
| **[D]** | Documented in a summary — a claim about the code, not an independent observation           |
| **[?]** | Neither. Open.                                                                             |

**v3.1 of this plan was itself wrong on two claims** (§3.2 #11: asserted the value ceiling lives on the PRA header — it's per-line; §3.3 #23: asserted `retroPurchaseFlag` is absent — it's present and has been since 27 Jul). Both were produced by reasoning from a code-change *summary* rather than the code itself — the same failure mode v3.1 itself diagnosed in v2.0. The fix applied this round: go back to the actual files for every open or asserted item, not the document describing them.

**So: where any planning document and the code disagree, the code is right.** Record the disagreement rather than silently correcting it.

### 0.2 Standing rules

- **Verify at the service layer, not just controllers.**
- **"No changes required" in a session note means no work happened that session.**
- **DDL is confirmed only when it has executed cleanly against a live PostgreSQL instance.**
- **New: a claim sourced from a summary document is [D], not [C] or [V].** Promote it only after reading the actual file or observing the actual behaviour. This is the specific rule that would have caught both of v3.1's errors before they were written down.

---

## 1. Corrections to prior planning documents

| v2.0/v3.1 claimed                                                    | Reality                                                                                                             |
| ---------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Procurement API: zero implementation                                    | Full module existed before this session even started — `PurchaseRequest`, `PurchaseOrder`, `GoodsReceipt` (22 Jul)     |
| RBAC: two role systems need reconciliation                              | One `Role` enum only. `scope`/`Scope`/`ScopeContext` present (23 Jul)                                                    |
| `retroPurchaseFlag` absent (v3.1 §3.3 #23)                              | Present since the original 27 Jul build — `Realisasi.retroPurchaseFlag`, `Boolean`, defaults false                      |
| Value ceiling lives on the PRA header (v3.1 §3.2 #11)                   | Both `authorizedQty` and `maxValue` are per-line on `PurchaseRequestAuthorizationItem`. No header-level ceiling exists   |
| Landed cost per line "OPEN" (v3.1 §3.7 #35)                             | Was already PASS — `RealisasiItemResponse.allocatedLandedCost` existed since 27 Jul                                     |

**Retained unchanged:** DL-06 (cloud-only) and DL-08 (PR → PRA → Realisasi) remain operative.

---

## 2. Architecture baseline

### 2.1 Superseded — do NOT build

| No longer valid                                             | Replaced by                                                                        | Ref   |
| ------------------------------------------------------------ | ------------------------------------------------------------------------------------ | ----- |
| Local Agent per outlet (mini-backend, local DB, local auth) | Cloud-only. One API, one database. No server-side sync layer.                      | DL-06 |
| Bidirectional sync engine; sync-mode-per-data-path          | Offline tolerance is client-side only: outbox queue + pre-allocated barcode blocks | DL-06 |
| `PR → PO → Invoice` as the operative chain                  | `PR → PRA → Realisasi Pembelian`                                                   | DL-08 |
| PO as an instruction to buy                                 | Realisasi records a purchase that **already happened**, built from the receipt     | DL-08 |
| Failed purchase cancels PO and restarts PR                  | Failed Realisasi releases its hold; PRA stays alive; retry permitted               | DL-08 |

### 2.2 Still operative — do not re-litigate

- **RBAC:** scope (`Principal > Owner > Manager > Supervisor`) × functional role (`ADMIN / TECHNICIAN / PROCUREMENT / ACCOUNTING / OPERATOR`).
- **Outlet is source of truth for its own physical stock; center aggregates.**
- **Two transactions, not one** — the franchise path (outlet↔center) and direct marketplace purchase are genuinely different kinds of purchase, not two implementations of the same thing. As of 28 Jul this is now explicit in code: `PurchaseOrder`/`GoodsReceipt.purchaseOrder` = outlet→center only; `PurchaseRequestAuthorization`/`Realisasi`/`GoodsReceipt.realisasi` = direct marketplace/supplier purchase. See §6.2.
- `part_condition_history` as the lifetime-analysis foundation.
- Digital twin: `airsoft_units`, `airsoft_unit_parts`, `service_events`.

---

## 3. Conformance audit — results

**Summary: 35 closed, 0 open (down from 12 open in v3.1).**

### 3.1 DL-08 · Chain structure — all closed

| #   | Expected                                                             | Tier | Result                                                  |
| --- | ---------------------------------------------------------------------- | ---- | ---------------------------------------------------------- |
| 1   | `PurchaseRequestAuthorization` (+Item, +StatusHistory) exists        | [C]  | **PASS**                                                |
| 2   | `Realisasi` (+Item, +StatusHistory) exists                           | [C]  | **PASS**                                                |
| 3   | PRA creation **is** the authorization act — no separate approve step | [C]  | **PASS** — no approve endpoint exists; see §3.6 #37     |
| 4   | PRA status recomputed, never a stored mutable figure                 | [V]  | **PASS** — reached `FULFILLED` by recomputation in test |
| 5   | No `remaining_qty` / `remaining_value` column anywhere               | [C]  | **PASS**                                                |
| 6   | Ceiling consumption counts **only APPROVED** lines                   | [C]  | **PASS**                                                |
| 7   | `FAILED` Realisasi releases its hold; retry succeeds                 | [V]  | **PASS** — observed end to end                          |
| 8   | Partial fulfilment leaves remainder authorized                       | [V]  | **PASS**                                                |
| 9   | `supersede()` re-runs all checks, flips original to `SUPERSEDED`     | [V]  | **PASS**                                                |
| 10  | Legacy `PurchaseOrder` path untouched and functional                 | [C]  | **PASS** — parallel, not renamed; scope now documented, see §6.2 |

### 3.2 DL-09 · Ceiling semantics — all closed

| #   | Expected                                                          | Tier | Result                                                                                                                                       |
| --- | ------------------------------------------------------------------ | ---- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| 11  | Max qty **per line**, max value **on header**                     | [C]  | **PASS, corrected** — both `authorizedQty` and `maxValue` are per-line on `PurchaseRequestAuthorizationItem.java`. No header-level ceiling field exists on `PurchaseRequestAuthorization` at all. v3.1's "on header" claim was wrong. |
| 12  | Hard limit — no percentage tolerance band                         | [C]  | **PASS** — read `RealisasiService.create()` directly: `newTotalQty > authorizedQty` and `newTotalValue.compareTo(maxValue) > 0`, strict comparisons only, no tolerance percentage anywhere in the codebase |
| 13  | Any breach → **whole** Realisasi `PENDING_APPROVAL` / `ESCALATED` | [C]  | **PASS**                                                                                                                                     |
| 14  | `approveEscalated()` gated to Owner scope                         | [V]  | **PASS** — non-Owner blocked, Owner succeeded                                                                                                |
| 15  | Owner+ notified via `notifyRealisasiEscalated`                    | [V]  | **PASS** — delivery observed                                                                                                                |

### 3.3 DL-10 · Marketplace purchase & landed cost — all closed

| #   | Expected                                                          | Tier | Result                                                                                                          |
| --- | -------------------------------------------------------------------- | ---- | ------------------------------------------------------------------------------------------------------------------ |
| 16  | `externalOrderRef` unique at **DB level**, not only service check | [V]  | **PASS** — watched Hibernate execute `ALTER TABLE realisasis ADD CONSTRAINT ... UNIQUE (external_order_ref)` live on table creation. (The failure mode originally flagged — `@Column(unique=true)` not being applied by `ddl-auto=update` — only affects *already-existing* tables gaining a new constraint, e.g. §5.1's CHECK-constraint bug; `realisasis` was a brand-new table this session, so it was applied correctly.) |
| 17  | Six cost components stored separately → computed `totalCost`      | [C]  | **PASS** — `subtotal`, `sellerDiscount`, `platformVoucher`, `shipping`, `insurance`, `serviceFee` as six separate `BigDecimal` columns on `Realisasi`; `totalCost = subtotal − discounts + shipping + insurance + fee` |
| 18  | Landed cost allocated **by value** (decision #3)                  | [C]  | **PASS**                                                                                                        |
| 19  | All components enter inventory cost, written to `PartInstance` (not list price) | [V]  | **FIXED 28 Jul, corrected again same day — see #20.** First fix: added nullable `PartInstance.landedCost` (+ exposed on `PartInstanceResponse`); new overload `generateInstances(part, qty, notes, landedCost)` — the 3-arg version delegates with `null`, so the other two call sites (`PartStockService` manual receipt, `GoodsReceiptService`'s legacy PO-based receipt) are unchanged and correctly keep `landedCost=null` since neither has itemized cost data. That fix's divisor was `allocatedLandedCost ÷ purchasedQty` — **which is only correct when `conversionFactor=1`**, as the first live test happened to use. The `conversionFactor=6` test (see #20) exposed this: divisor is now `allocatedLandedCost ÷ (purchasedQty × conversionFactor)`, i.e. `realisasiItem`'s already-computed `purchasedStockUnits`. **Verified live (final):** subtotal 142,200 / 1 paket / conversionFactor 6 → 6 `PartInstance` rows, each `landedCost: 23,700.00` — sums back to the real 142,200. (Superseded verification: the original 120,000/2-unit test used `conversionFactor=1`, where both the buggy and fixed formulas coincide — that's why it passed without catching the bug.) |
| 20  | `conversionFactor` applied in **every** stock comparison          | [V]  | **PASS — closed empirically 28 Jul, caught a real bug.** Ran a full live scenario with `conversionFactor=6` (1 paket → 6 cans): PR → approve → PRA (`authorizedQty=6`) → Realisasi (`purchasedQty=1`, `conversionFactor=6`, `actualUnitPrice=142200`) → goods receipt of 6 stock units. The ceiling check and over-receipt check (both already multiplied by `conversionFactor`) worked correctly. The landed-cost divisor did **not** — see #19. Fixed in `GoodsReceiptService.receiveAgainstRealisasi` (`src/main/java/com/example/operion/module/procurement/service/GoodsReceiptService.java`), re-ran the same scenario, confirmed correct. **This closes the plan's last open item — see §4.** |
| 21  | Attachment not required to record, required to close              | [V]  | **FIXED 28 Jul** (decision made this session — no "close" step exists, so the rule was implemented as "required before goods receipt" instead). `GoodsReceiptService.receiveAgainstRealisasi` now blocks with a clear error if `realisasi.getAttachments()` is null/blank, before any stock/instance side effects. **Verified live:** no-attachment → blocked; with-attachment → succeeds. |
| 22  | `paymentMethod` + `reimbursementStatus` on the record             | [C]  | **PASS** — both enums present and wired (auto-sets `PENDING` for `PERSONAL_REIMBURSABLE`, `NOT_APPLICABLE` otherwise) |
| 23  | Retro-purchase flag                                               | [C]  | **PASS, corrected** — v3.1 claimed ABSENT. Wrong: `Realisasi.retroPurchaseFlag` is a real `Boolean` field, defaults `false`, settable on create, returned in the response. Present since the original 27 Jul build. Unused by any query/filter yet — a "show retro purchases" view for Owner is legitimate future UI/query work, not a missing field (see §6.4). |

### 3.4 DL-11 · Segregation of duties — all closed

| #   | Rule                                                               | Tier | Result                              |
| --- | ------------------------------------------------------------------- | ---- | ------------------------------------ |
| 24  | PRA approver ≠ Realisasi creator, **Owner+ exempt**                | [V]  | **PASS** — all three cases observed |
| 25  | Realisasi creator ≠ goods receiver, **no exemption**               | [V]  | **PASS**                            |
| 26  | #24 reads the approver's **stored** scope, not live `ScopeContext` | [C]  | **PASS**                            |

### 3.5 DL-06 · Cloud-only & barcode blocks — all closed

| #   | Expected                                                | Tier | Result                            |
| --- | ---------------------------------------------------------- | ---- | ---------------------------------- |
| 27  | No sync/agent/offline/replication scaffolding           | [V]  | **PASS**                          |
| 28  | `BarcodeCounter` via pessimistic-write lock, no raw SQL | [C]  | **PASS**                          |
| 29  | `BarcodeAllocation` records every issued block          | [C]  | **PASS**                          |
| 30  | `POST /barcode-blocks` contract as specified            | [V]  | **PASS**                          |
| 31  | Ranges never overlap under **concurrent** requests      | [V]  | **PASS — closed empirically 28 Jul.** Fired 10 truly *simultaneous* (backgrounded, not sequential) `POST /barcode-blocks` requests against a live instance. Result: perfectly contiguous, zero-overlap ranges (521–620, 621–720, … 1421–1520). The pessimistic-write lock genuinely holds under real concurrency, not just sequential calls. |
| 32  | Redemption path: offline codes → `PartInstance`         | [C]  | **ABSENT** — deliberate; see §6.3 |

### 3.6 Findings from v3.1 — resolved

| #   | Finding                                              | Result              |
| --- | ------------------------------------------------------- | -------------------- |
| 36  | `PurchaseRequest` holds a nullable FK **to** the PRA | **DECIDED 28 Jul: kept as-is.** One PR → one PRA. If purchasing ever needs partial authorization (urgent lines now, rest after a cashflow review), the PIC splits the PR into two requests first — already possible today, no schema change needed. Revisit only if that workflow turns out to be common enough that pre-splitting becomes real friction. |
| 37  | PRA has `approvedBy` but no approve endpoint         | **Acknowledged, not actioned.** Cosmetic — creation *is* authorization (#3), the field name just reads as though a step is missing. Low priority; revisit (comment or rename to `authorizedBy`) if it causes actual confusion. |

### 3.7 API surface for the client — all closed

| #   | Needed                                                                                          | Result                                                                                                                                                  |
| --- | ----------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 33  | PRA read returns per-line `consumedQty`/`remainingQty`, header `consumedValue`/`remainingValue` | **PASS, under different field names.** `PurchaseRequestAuthorizationItemResponse` exposes `purchasedQty`/`purchasedValue`/`remainingQty`/`remainingValue` per line. No header-level rollup exists, because — per corrected #11 — `maxValue` itself is per-line, not header; there is nothing to roll up. |
| 34  | PRA read returns `approvedBy` **and** `approvedByScope`                                         | **FIXED 28 Jul.** `PurchaseRequestAuthorizationResponse` previously exposed `approvedById`/`approvedByName` only. Added `approvedByScope`, sourced from the already-existing `User.scope`. **Verified live:** correctly returned `"MANAGER"` for a manager-approved PRA. |
| 35  | Realisasi read returns allocated landed cost **per line**                                       | **PASS, corrected.** v3.1 marked this OPEN — wrong. `RealisasiItemResponse.allocatedLandedCost` was already exposed since the original 27 Jul build. |

---

## 4. Open checks — consolidated

None. The last open item (#20, `conversionFactor` live test) closed 28 Jul — see §3.3 #19/#20 for what it found (a real landed-cost divisor bug, now fixed and re-verified).

---

## 5. Housekeeping

### 5.1 Enum CHECK constraint — standing pre-flight

Adding a value to an enum used by an **already-existing** entity breaks inserts at runtime. `ddl-auto=update` creates new tables and columns but does **not** alter existing DB-level CHECK constraints. No compile error, no startup error — failure only at insert.

Occurred with `REALISASI` (`NotificationReferenceType`) and `REALISASI_ESCALATED` (`NotificationType`); fixed by manually dropping and recreating both constraints.

**Rule for `CLAUDE.md`:** _before adding a value to an existing enum, check for a DB CHECK constraint on that column and plan the migration in the same change._

`ddl-auto=update` is not a migration strategy — adopting Flyway or Liquibase would make this class of problem structural rather than recurring, and the cost rises with every table.

### 5.2 Test data in the dev database

From the 27 Jul session: users `barcodetest@test.com`, `pra_requester@test.com`, `pra_owner@test.com`, `pra_manager@test.com`, `pra_receiver@test.com`; 3 PRAs; ~8 Realisasi; 1 goods receipt; 2 barcode allocations.

**Added 28 Jul** (this verification/fix round): Realisasi records `LANDEDCOST-TEST`, `NOATTACH-TEST`, `WITHATTACH-TEST`; 10 barcode allocations (`concurrent-device-1` … `concurrent-device-10`, ranges 521–1520); a handful of associated goods receipts and `PartInstance` rows.

**Added 28 Jul, later pass** (the `conversionFactor=6` test that closed #20): users `conv_owner2@test.com`, `conv_creator2@test.com`, `conv_receiver2@test.com`; 1 PR/PRA/Realisasi (`externalOrderRef` prefixed `CONVTEST-6-`) against part "AK Hop-Up Chamber Standard Nylon"; 1 goods receipt; 6 `PartInstance` rows (`landedCost: 23700.00` each, correctly summing to the 142,200 subtotal).

**Added 29 Jul** (burn rate build/verification): users `burnratetest1@test.com`, `burnratetest2@test.com`, `case2test@test.com`, `case5test@test.com`, `case9_owner@test.com`, `case9_creator@test.com`, `case9_receiver@test.com`; parts "Green Gas Test Can", "Case2 Clamp Test", "Case5 No Data Test", "Case9 Gas ConvFactor" (all category Consumable, tenant `a720623c-...`); ~20 `PartInstance` rows (scanned-in + taken, mix of manually-adjusted stock); 1 PR/PRA/Realisasi/goods-receipt chain (`externalOrderRef` prefixed `CASE9-`). The Case2 part's 12 takes were deliberately backdated to `now() - 6 days` via a one-off JDBC connection — if inspecting `part_instances` directly, don't mistake that for real historical data.

No DB CLI on this machine. **Delete whole chains, in FK order:** goods receipt items → goods receipts → Realisasi items → Realisasi → PRA items → PRA → users. Barcode allocations are independent. Partial deletion changes ceiling consumption on any PRA left behind.

### 5.3a Memory-index drift (28 Jul, later pass) — doc unaffected

The Claude Code memory index (`operion_project_overview.md`, outside this repo) had drifted from this document: it still described the barcode-block endpoint as "confirmed not yet built" and the v2.0 §8 decisions as "still open," both leftover from the *pre-build* state earlier the same day the build actually happened (27 Jul). Checked this document against those two claims — both were already correct here (§3.5 #30, §3.1/§1). **No change was needed to this file.** Fixed at the source: the memory file. Noted here only because a doc/memory mismatch is exactly the failure mode §0.1/§0.2 exist to catch, even when this document turns out to be the one that was right.

### 5.3 Outstanding from the July seed session

Working tenant UUID: `a720623c-28d9-4d6b-b06b-059397718d18`

1. **Duplicate `part_categories`** — keep **Consumable**, remove the other new broad categories, re-map any `part_type` rows pointing at duplicates **before** deletion. Dependency-check query output was never captured.
2. **`Gearbox TESTING`** — probable test data. Confirm and remove.
3. **Null `created_date` on raw-SQL rows** — add DB-level `DEFAULT now()` for anything seedable, then backfill.

---

## 6. Build plan

### 6.1 Burn rate / `suggested_qty` — **DONE 29 Jul 2026**

Completes the five-quantity chain:

`suggested_qty` **(built)** → `requested_qty` (`PurchaseRequestItem.quantity`) → `authorized_qty` (`PurchaseRequestAuthorizationItem.authorizedQty`) → `purchased_qty` (`RealisasiItem.purchasedQty`) → `received_qty` (`GoodsReceiptItem.quantity`)

Built per `operion-burn-rate-spec.md`, computed on read (no stored burn-rate column, same reasoning as the PRA ceiling), new `module/burnrate` (`BurnRateService`, `BurnRateController`: `GET /burn-rate`, `GET /burn-rate/{partId}`), four modes (`COMPUTED`/`MANUAL_RATE`/`MANUAL_LEVEL`/`NONE`), thresholds sourced from four new nullable `maintenance_rule` columns with code-level fallback to spec defaults (30/21/10/14 — nullable rather than `NOT NULL`, since the table already has a row per tenant and Hibernate can't `ALTER ... NOT NULL` a populated table).

**A pre-flight check (spec's own §9 step 1) found a real gap the spec didn't anticipate.** "Confirm stock decrements at take time, not exhaust" — grepped every `adjustStock`/`setCurrentStock` call site: `PartInstanceService.take()`/`.exhaust()` called **neither**. Taking or exhausting a barcoded item had zero effect on `Part.currentStock`. Decided with the user: wire `take()` to decrement stock via the existing `adjustStock(-1, ...)` pattern, **scoped to Consumable-category parts only** — non-Consumable (sparepart) instances already get their stock decrement at `AirsoftUnitPartService.installPart()`/`replacePart()`, unconditionally, regardless of whether the instance was taken first (`markInstalled` explicitly allows both `IN_STOCK` and `TAKEN` as valid prior states); decrementing at take too would have double-counted every taken-then-installed sparepart. `exhaust()` is unchanged — pure state transition, the stock effect already happened at take.

**That wiring created a real circular bean dependency**, not just a design smell: `PartStockService` already injects `PartInstanceService` (for `generateInstances` on receipt); having `PartInstanceService` call back into `PartStockService` fails at Spring startup (`UnsatisfiedDependencyException`, constructor cycle). First fix attempt — `@Lazy` on the field with `@RequiredArgsConstructor` still generating the constructor — **did not work and still failed the same way**: Lombok does not copy field annotations onto its generated constructor parameters. Fixed by replacing `@RequiredArgsConstructor` with an explicit constructor and `@Lazy` directly on the parameter. New standing rule, `CLAUDE.md` §Rules #7.

Two new enum values on the already-existing `stock_movements` table (`StockMovementType.CONSUMABLE_TAKE`, `StockReferenceType.PART_INSTANCE`) — pre-flighted per rule 3, but this time the insert succeeded immediately with no CHECK-constraint fix needed (confirmed live, not assumed): `stock_movements` apparently never had an explicit CHECK constraint on these columns to begin with, unlike `notifications` in the 27 Jul incident. Worth remembering the rule 3 gotcha is a *risk to check*, not a guaranteed failure every time.

**Spec §7.3's flagged gap resolved for free.** The spec asks for a `preferredOrderQty` per part so `suggestedQty` doesn't degenerate to "buy 13 cans" on bulk-buy items. `Part.reorderQuantity` already existed (default `10`, wired through Create/Update/Response since the original build) but was never read by any computation anywhere in the codebase — reused instead of adding a column. `suggestedQty` now returns `reorderQuantity` whenever the computed top-up figure would be `> 0`; `0` (no reorder needed) is returned unmodified. Caveat: `reorderQuantity` is `NOT NULL` defaulting to `10` for every part, so a part nobody has configured suggests exactly `10` regardless of its real consumption pattern — sane default, but not the same as a deliberately-set value.

**Verified live**, all four spec §8 cases with non-trivial logic:
- **Case 2 (the clamp test)** — 12 takes backdated to 6 days ago (via a one-off JDBC connection, no `psql` on this machine, same pattern as the 27 Jul CHECK-constraint fixes), stock 4, `burn_rate_min_observation_days` temporarily lowered to 1 to let the item clear the `COMPUTED` threshold. Result: `observationDays: 6, burnRate: 2.0000, daysOfCover: 2.0000` — exactly the spec's expected numbers, not the wrong-implementation `0.13`/`30` an unclamped `observationDays` would produce. Threshold restored to `null` (default) afterward.
- **Case 5** — brand-new part, zero takes, no manual values: `burnRateSource: NONE, burnRate: null, daysOfCover: null` — no divide-by-zero, no false `0`.
- **Cases 9/10** — a `conversionFactor=6` Realisasi/goods-receipt (reusing the exact scenario from §3.3 #20), then 6 cans manually scanned in and taken (Consumable parts don't auto-generate `PartInstance` rows on receipt, confirmed — receiving only moves `currentStock`; a real outlet would scan each can as it's shelved, same as this test). Result: `eventCount: 6`, not `1` (the take count, not the purchase-line count) — and unaffected by zero `exhaustedAt` values, confirming burn rate counts takes, not completed cycles, per decision #6's correction in the spec.
- **Stock decrement** — confirmed live: a taken Consumable instance drops `Part.currentStock` by exactly 1 and logs a `StockMovement` (`CONSUMABLE_TAKE`/`PART_INSTANCE`).
- **Single grouped query (§6.1 performance)** — confirmed via the Hibernate SQL log: `GET /burn-rate` issues exactly one `count(...) group by part_id` query and one `min(taken_at) group by part_id` query for the whole catalog, plus one `parts` query — no per-part N+1 pattern.

**Still open, not built this round:** `windowDays`/threshold values are only configurable by direct DB edit — no `MaintenancePolicyController` exists at all (pre-existing gap, predates this feature, out of scope here). Seeding `manualDailyUsage` from `Data_Ops.xlsx` (spec §7.4) is a one-time data task, not code. Lead time (spec §7.1) remains unmodelled.

**Unblocks the two UI designs** (PR creation's suggested basket, the days-of-cover gauge) — frontend work, not tracked here.

### 6.2 Legacy PO path — **decided and documented 28 Jul**

`PurchaseOrder`/`GoodsReceipt`-only flow runs parallel to PRA/Realisasi, and `GoodsReceiptService.receive()` branches between them.

**Decision:** keep both, scope narrowed. `PurchaseOrder`/`GoodsReceipt.purchaseOrder` = outlet→center leg only. `PurchaseRequestAuthorization`/`Realisasi`/`GoodsReceipt.realisasi` = direct marketplace/supplier purchase. This is now documented as class-level Javadoc on `PurchaseOrder.java` and `GoodsReceipt.java` — `PurchaseOrder.supplier` explicitly flagged as legacy, since supplier-based purchasing is now Realisasi's job. Not enforced in code (no validation blocks setting `PurchaseOrder.supplier`), since `PurchaseOrder` was deliberately left untouched behavior-wise earlier in this project — this is a documentation/convention decision, not a runtime constraint.

### 6.3 Barcode redemption path — still open

The gap between "codes can be generated offline" and "offline work lands in the system." Needs deciding: what happens when a redeemed code arrives for an item whose stock has since changed, and whether a code issued to a device that's been reset can be reclaimed.

### 6.4 Retro-purchase flag — already built, correcting the build plan

v3.1 listed this as work to do. It isn't — see corrected §3.3 #23. The field exists and works. What's actually still missing, if wanted: a filter on the Owner's console surfacing all `retroPurchaseFlag=true` records, so frequency is visible. Small, low priority, query/UI work only.

### 6.5 Client-side, not backend

Outbox queue and offline code consumption are React Native work. Only backend counterpart is §6.3.

---

## 7. Document sync — still not done

- [x] `CLAUDE.md` — cloud-only (DL-06), PRA/Realisasi chain (DL-08), current module inventory, the §5.1 enum pre-flight rule, the §0.2 "summary vs source" rule. **Done** — confirmed present as of the 10 Aug UI-planning sync (rules 1–7, superseded/procurement/PRA tables, module inventory all in current `CLAUDE.md`). Loads at the start of every Claude Code session; stale content here propagates directly into code, so re-check this box if `CLAUDE.md` drifts again.
- [ ] BRD / FRS / HLD / LLD — amendment checklist in `Operion-Decision-Log-Tambahan.md` §"Dampak ke dokumen" still unexecuted. HLD §3.2 still describes the Local Agent as resolved.
- [ ] Decision Log — record #36 (kept as-is) and the §6.2 PO-scope decision.

---

## 8. Design artifacts

Unchanged from v3.1 — no design-artifact work happened this round.

| Prototype                                      | Status                                                                       |
| ------------------------------------------------ | ------------------------------------------------------------------------------ |
| Outlet scan loop (take → print → board → scan) | Current. Needs RN rebuild.                                                   |
| PR creation (mobile)                           | Layout current; **chain stale** (shows PR → PO → Invoice). Backend for §6.1 no longer blocks it — needs a frontend pass. |
| PR approval console (web)                      | Layout current; **chain stale** — issues a PO, should produce a PRA ceiling. |
| Realisasi entry (web)                          | Current. Matches DL-08/09/10/11 as built.                                   |

---

## 9. Open questions

1. ~~PRA↔PR cardinality~~ — **resolved 28 Jul**, kept as-is (§3.6 #36)
2. ~~Legacy PO path~~ — **resolved 28 Jul**, kept + scope documented (§6.2)
3. ~~Burn-rate window~~ — **resolved 28 Jul, built 29 Jul**: 30 days, calibrated from real sales data, not the earlier 90-day guess (§6.1)
4. Flyway/Liquibase before the schema grows further (§5.1) — two bugs from this root cause so far
5. Barcode redemption semantics (§6.3)
6. Outlet connectivity — still unmeasured; determines whether the outbox queue is urgent or deferrable (DL-06)
7. **New:** no `MaintenancePolicyController` exists — `maintenance_rule` thresholds (including the four new burn-rate ones) are only editable by direct DB edit. Pre-existing gap, surfaced while building §6.1, not caused by it.
8. **New:** should PRA carry a header-level `maxValue` cap in addition to per-line ceilings? (carried over from v3.2/v3.3, still unresolved — see §3.2 #11's note)
