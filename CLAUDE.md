# CLAUDE.md — Operion Backend

Context for Claude Code sessions. Loaded at session start.

**Repo:** `richardtanu/Operion-services` — Java Spring Boot + PostgreSQL, modular monolith
**Authoritative plan:** `OPERION_BE_PLAN.md` (currently v3.3, 28 Jul 2026) — this file is a summary, that file is the detail
**Decision rationale:** `Operion-Decision-Log-Tambahan.md` (Indonesian) — `DL-06` … `DL-11`

---

## What Operion is

A specialized ERP for shooting range businesses, adaptable to mechanical/technical service businesses (repair shops, rentals). Modules: inventory control, operational control (barcode-driven consumable tracking), efficiency analysis, item lifetime analysis, procurement, digital twin (per-airsoft-unit health/maintenance state).

The product owner is learning backend engineering while building. **Explain trade-offs and the "why" behind a recommendation, not just the directive.**

---

## Rules that have each cost real debugging time

These are not general best practices. Each one is here because it caused a specific bug in this project.

### 1. A summary is evidence tier `[D]`, not `[C]` or `[V]`

Three planning documents in a row made confident false claims by reasoning from a summary of the code rather than the code:

- v2.0 asserted the procurement module didn't exist. It had existed for four days.
- v2.0 asserted two role systems needed reconciling. Only one ever existed.
- v3.1 asserted `retroPurchaseFlag` was absent. It was present.
- v3.1 asserted the PRA value ceiling was header-level. It's per-line.

**Before asserting anything about this codebase, open the file.** Tiers used in the plan doc: `[V]` observed live, `[C]` read in source, `[D]` claimed in a summary, `[?]` unknown. Do not promote `[D]` to `[C]` without reading.

Corollary: **where a planning document and the code disagree, the code is right.** Record the disagreement rather than silently fixing the doc — the pattern of what these documents get wrong is itself useful.

### 2. Never test stock math or costing with `conversionFactor = 1`

Purchase UOM ≠ stock UOM (1 paket → 6 cans). With a factor of 1, the correct formula and several wrong ones produce identical output.

This hid a real bug through **two** rounds of "verified live": the landed-cost divisor used `purchasedQty` (purchase units) instead of `purchasedQty × conversionFactor` (stock units). Both tests happened to use factor 1. The `conversionFactor=6` test caught it immediately — 142,200 ÷ 1 = 142,200 per instance × 6 instances, versus the correct 23,700.

**Any test touching stock quantities or costing must use a factor other than 1.**

### 3. `ddl-auto=update` does not alter constraints on existing tables

It creates new tables and columns fine. It does **not** modify DB-level CHECK or UNIQUE constraints on tables that already exist.

Symptom when this bites: no compile error, no startup error, failure only at insert. Adding `REALISASI` to `NotificationReferenceType` and `REALISASI_ESCALATED` to `NotificationType` silently broke every insert into the pre-existing `notifications` table, because the old CHECK constraint kept rejecting the new values.

**Pre-flight: before adding a value to an enum used by an existing entity, check for a DB CHECK constraint on that column and plan the migration in the same change.** Same applies to adding `@Column(unique=true)` to an existing table.

Two bugs have come from this root cause. `ddl-auto=update` is not a migration strategy — Flyway or Liquibase is an open decision (`OPERION_BE_PLAN.md` §9 Q4), and the cost of adopting it rises with every table.

### 4. Verify at the service layer, not just controllers

An early review concluded `PreventiveMaintenanceService` and `MaintenancePolicy` didn't exist, having examined only controller files. **Absence of an endpoint is not absence of a feature.**

### 5. "No changes required" in a session note means no work happened that session

It is not confirmation that a requirement was met.

---

## Architecture — settled, do not re-litigate

Closed via formal SDLC docs plus live code verification. Reopen only with new information.

### Superseded — do NOT build these

| No longer valid | Replaced by | Ref |
| --- | --- | --- |
| Local Agent per outlet (mini-backend, local DB, local auth) | **Cloud-only.** One API, one database, no server-side sync layer, no conflict resolution, no per-outlet deployment. | DL-06 |
| Bidirectional sync engine | Offline tolerance is **client-side only**: outbox queue + pre-allocated barcode code blocks in the RN app | DL-06 |
| `PR → PO → Invoice` chain | `PR → PRA → Realisasi Pembelian` | DL-08 |
| PO as an instruction to buy | Realisasi records a purchase that **already happened**, built from the marketplace receipt | DL-08 |
| Failed purchase cancels PO, restart PR | Failed Realisasi releases its hold; PRA stays alive; retry permitted | DL-08 |

Note: there is no `invoices` table and never was. The chain that actually exists on the legacy path is `PR → PO → GoodsReceipt`.

### Procurement — two genuinely different transactions

Not two implementations of one thing. Documented as class-level Javadoc on `PurchaseOrder.java` and `GoodsReceipt.java`.

| Path | Entities | Purpose |
| --- | --- | --- |
| Franchise leg | `PurchaseOrder`, `GoodsReceipt.purchaseOrder` | outlet → center |
| Direct purchase | `PurchaseRequestAuthorization`, `Realisasi`, `GoodsReceipt.realisasi` | marketplace / supplier |

`PurchaseOrder.supplier` is **legacy** — supplier-based purchasing is Realisasi's job now. Convention, not runtime-enforced; `PurchaseOrder` behaviour was deliberately left untouched.

### PRA / Realisasi semantics

- **PRA is an authorization ceiling**, not an order. `authorizedQty` and `maxValue` are both **per-line** on `PurchaseRequestAuthorizationItem`. There is no header-level ceiling field.
- **PRA creation *is* the authorization act.** No approve endpoint exists. (`approvedBy` reads as though a step is missing — it isn't.)
- **No mutable `remaining` column anywhere.** Consumption is aggregated live from `RealisasiItem` rows counting **only `APPROVED`** status. This single choice is what makes failed-realisasi-releases-its-hold and partial fulfilment work with zero extra bookkeeping. **Do not add a `remaining` column** — it would silently break both.
- Below ceiling → auto-passes. Above → whole Realisasi goes `PENDING_APPROVAL`/`ESCALATED`, Owner+ notified, Owner-only approval. Hard limit, no tolerance band.
- `supersede()` is append-only correction: new record via self-FK, re-runs every check, flips the original to `SUPERSEDED`. Nothing is overwritten in place.
- Landed cost allocated **by value** (each line's share of header subtotal), and all six cost components are stored separately: `subtotal`, `sellerDiscount`, `platformVoucher`, `shipping`, `insurance`, `serviceFee`. List price is not cost — the error is ~17% on a real Tokopedia receipt.
- **Specific-identification costing**: `PartInstance.landedCost` is stamped per individual instance. Possible because instances are tracked individually; most ERPs can only approximate with FIFO/average.

### Five quantities per line — all stored, never overwritten

`suggested_qty` *(not built — see below)* → `requested_qty` (`PurchaseRequestItem.quantity`) → `authorized_qty` (`PurchaseRequestAuthorizationItem.authorizedQty`) → `purchased_qty` (`RealisasiItem.purchasedQty`, in purchase UOM) → `received_qty` (`GoodsReceiptItem.quantity`, in stock units)

Each divergence is a signal. Storing one mutable qty column destroys all of them.

### Segregation of duties — two rules, different exemptions

Do not collapse these into one helper.

| Rule | Exemption |
| --- | --- |
| PRA approver ≠ Realisasi creator | **Yes** — Owner/Principal may do both. Manager does not qualify. |
| Realisasi creator ≠ goods receipt receiver | **None** |

The scope check reads the approver's **stored** scope as a historical fact, not the current request's `ScopeContext`. Reading the live context would let a later promotion retroactively legalize a past pairing.

### RBAC

Dual dimension: **scope** (`Principal > Owner > Manager > Supervisor`, linear, nullable `scope` column on `users`, JWT-carried, `ScopeContext`) × **functional role** (`ADMIN / TECHNICIAN / PROCUREMENT / ACCOUNTING / OPERATOR`).

One `Role` enum only. There is no `roles`/`user_roles` table pair — earlier claims to the contrary were wrong.

---

## Module inventory

`airsoft` · `analytics` · `auth` (Role + Scope) · `barcodeblock` · `dashboard` · `inventory` · `maintenance` · `notification` · `part` · `parthistory` · `partinstance` · `procurement` · `serviceevent` · `stockadjustment` · `tenant` · `unitpart` · `workorder`

`procurement` contains: `Supplier`, `PurchaseRequest`, `PurchaseOrder`, `GoodsReceipt`, `PurchaseRequestAuthorization`, `Realisasi`.

---

## Current state

**Conformance audit: 35 checks, all closed** (`OPERION_BE_PLAN.md` §3).

**Next up — burn rate / `suggested_qty`** (§6.1). The last link in the five-quantity chain, and it blocks two finished UI designs (the PR creation screen's suggested basket, the days-of-cover gauge on both procurement screens).

`PartInstance` has both `takenAt` and `exhaustedAt`, so this is a computation problem, not a schema gap. Needs: manual reorder point per item as the cold-start path, burn-rate computation from completed take→exhaust cycles, switchover at 10 completed cycles, days-of-cover target configurable via `maintenance_rule` (do not build a second settings mechanism), and **the read path must report which source a figure came from** — a manual guess and 200 observed cycles are different claims and the UI currently renders them identically.

**Deliberately not built:**

- Barcode redemption path — offline-generated codes → real `PartInstance` rows on sync. The endpoint reserves number space only. Blocked on an unmeasured question: how often outlets actually lose connectivity.
- File-upload backing for Realisasi `attachments` (plain text/URL field, by design).
- A filter surfacing `retroPurchaseFlag=true` records for the Owner. Field exists; nothing queries it.

**Open decisions** (`OPERION_BE_PLAN.md` §9): burn-rate window (90 days recommended); Flyway/Liquibase; barcode redemption semantics; outlet connectivity measurement; whether to add a nullable **header-level** `maxValue` to PRA — per-line ceilings can't express "spend at most Rp 2.1M on this authorization", and it's cheap to add before production PRAs exist.

**Housekeeping:** test data from three verification sessions is still in the dev DB (`OPERION_BE_PLAN.md` §5.2 lists it, with FK deletion order). Delete whole chains — partial deletion changes ceiling consumption on any PRA left behind. Also outstanding: duplicate `part_categories` cleanup from the July seed session (§5.3).

---

## Working style

- **SSOT before implementation.** Decisions get captured in BRD/FRS/HLD/LLD/Decision Log before building.
- **Keep this file in sync as gaps close.** It loads at every session start; stale content here propagates directly into code. A memory-index drift on 28 Jul described an already-built module as unbuilt — exactly this failure mode.
- **DDL is confirmed only after executing cleanly against a live PostgreSQL instance.** Paper review does not count.
- A parallel dev team uses other AI tools; outputs are reconciled manually. Assume other sessions may have changed things — check before asserting.

**Client stack** (context; separate repo): React Native (Expo dev build, `react-native-vision-camera`) for the outlet app; React + Vite for the web console; shared `packages/core` for API client, generated types, and design tokens. UI components are not shared between them.
