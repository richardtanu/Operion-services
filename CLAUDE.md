# CLAUDE.md — Operion Backend

Context for Claude Code sessions. Loaded at session start.

**Repo:** `richardtanu/Operion-services` — Java Spring Boot + PostgreSQL, modular monolith
**Baseline reference:** `OPERION_BE_BASELINE.md` (22 Aug 2026) — this file is a summary, that file is the detailed, source-verified module-by-module reference (entities, endpoints, business rules, consolidated gap list)
**Audit history:** `OPERION_BE_PLAN.md` (v3.4, 29 Jul 2026) — the historical round-by-round conformance audit; superseded as a "current state" reference by `OPERION_BE_BASELINE.md` above, but kept for the decision trail
**Decision rationale:** `Operion-Decision-Log-Tambahan.md` (Indonesian) — `DL-06` … `DL-11`

---

## What Operion is

A specialized ERP for shooting range businesses, adaptable to mechanical/technical service businesses (repair shops, rentals). Modules: inventory control, operational control (barcode-driven consumable tracking), efficiency analysis, item lifetime analysis, procurement, digital twin (per-airsoft-unit health/maintenance state).

The product owner is learning backend engineering while building. **Explain trade-offs and the "why" behind a recommendation, not just the directive.**

---

## Rules that have each cost real debugging time

These are not general best practices. Each one is here because it caused a specific bug in this project.

### 1. A summary is evidence tier `[D]`, not `[C]` or `[V]`

Planning documents made confident false claims by reasoning from a summary of the code rather than the code:

- v2.0 asserted the procurement module didn't exist. It had existed for four days.
- v2.0 asserted two role systems needed reconciling. Only one ever existed.
- v3.1 asserted `retroPurchaseFlag` was absent. It was present.
- v3.1 asserted the PRA value ceiling was header-level. It's per-line.

**Before asserting anything about this codebase, open the file.** Tiers used in the plan doc: `[V]` observed live, `[C]` read in source, `[D]` claimed in a summary, `[?]` unknown. Do not promote `[D]` to `[C]` without reading.

Corollary: **where a planning document and the code disagree, the code is right.** Record the disagreement rather than silently fixing the doc — the pattern of what these documents get wrong is itself useful.

### 2. Never test stock math or costing with `conversionFactor = 1`

Purchase UOM ≠ stock UOM (1 paket → 6 cans). With a factor of 1, the correct formula and several wrong ones produce identical output.

This hid a real bug through **two** rounds of "verified live": the landed-cost divisor used `purchasedQty` (purchase units) instead of `purchasedQty × conversionFactor` (stock units). Both tests happened to use factor 1. The `conversionFactor=6` test caught it immediately.

**Any test touching stock quantities or costing must use a factor other than 1.**

### 3. `ddl-auto=update` does not alter constraints on existing tables

It creates new tables and columns fine. It does **not** modify DB-level CHECK or UNIQUE constraints on tables that already exist.

Symptom: no compile error, no startup error, failure only at insert. Adding `REALISASI` to `NotificationReferenceType` and `REALISASI_ESCALATED` to `NotificationType` silently broke every insert into the pre-existing `notifications` table.

**Pre-flight: before adding a value to an enum used by an existing entity, check for a DB CHECK constraint on that column and plan the migration in the same change.** Same applies to adding `@Column(unique=true)` to an existing table.

Two bugs from this root cause. A third was caught by this pre-flight in BE-07: `PurchaseRequestStatus.AUTHORIZED` existed in Java for months but `purchase_request_status_history`'s CHECK never allowed it — invisible only because nothing had written that row yet. `ddl-auto=update` is not a migration strategy — Flyway/Liquibase is an open decision (`OPERION_BE_PLAN.md` §9 Q4).

### 4. Verify at the service layer, not just controllers

An early review concluded `PreventiveMaintenanceService` and `MaintenancePolicy` didn't exist, having examined only controller files. **Absence of an endpoint is not absence of a feature.**

### 5. "No changes required" in a session note means no work happened that session

It is not confirmation that a requirement was met.

### 6. A default value of 1 or 0 can hide a bug — vary it

Generalisation of rule 2, learned the same way. Any parameter whose neutral value makes several formulas agree — conversion factors, multipliers, quantities, offsets — must be tested at something other than neutral before the code is believed.

### 7. `@Lazy` on a Lombok `@RequiredArgsConstructor` field does nothing

Breaking a circular bean dependency needs `@Lazy` on the **constructor parameter**. Putting it on the field and letting `@RequiredArgsConstructor` generate the constructor fails identically to having no `@Lazy` at all — Lombok does not copy field annotations onto the generated constructor.

Hit wiring `PartInstanceService.take()` to call back into `PartStockService` (which already depends on `PartInstanceService`): `@Lazy` on the field compiled fine and failed at Spring startup with the same `UnsatisfiedDependencyException` as no annotation. Fix: drop `@RequiredArgsConstructor` for that class, write the constructor explicitly, put `@Lazy` on the parameter.

**When breaking a cycle this way, verify by actually starting the app** — a clean compile proves nothing here, Lombok's silent no-op only shows up at Spring container startup.

---

## Architecture — settled, do not re-litigate

Closed via formal SDLC docs plus live code verification. Reopen only with new information.

### Superseded — do NOT build these

| No longer valid                                             | Replaced by                                                                                                         | Ref   |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- | ----- |
| Local Agent per outlet (mini-backend, local DB, local auth) | **Cloud-only.** One API, one database, no server-side sync layer, no conflict resolution, no per-outlet deployment. | DL-06 |
| Bidirectional sync engine                                   | Offline tolerance is **client-side only**: outbox queue + pre-allocated barcode code blocks in the RN app           | DL-06 |
| `PR → PO → Invoice` chain                                   | `PR → PRA → Realisasi Pembelian`                                                                                    | DL-08 |
| PO as an instruction to buy                                 | Realisasi records a purchase that **already happened**, built from the marketplace receipt                          | DL-08 |
| Failed purchase cancels PO, restart PR                      | Failed Realisasi releases its hold; PRA stays alive; retry permitted                                                | DL-08 |

There is no `invoices` table and never was. The legacy chain is `PR → PO → GoodsReceipt`.

### Procurement — two genuinely different transactions

Not two implementations of one thing. Documented as class-level Javadoc on `PurchaseOrder.java` and `GoodsReceipt.java`.

| Path            | Entities                                                              | Purpose                |
| --------------- | --------------------------------------------------------------------- | ---------------------- |
| Franchise leg   | `PurchaseOrder`, `GoodsReceipt.purchaseOrder`                         | outlet → center        |
| Direct purchase | `PurchaseRequestAuthorization`, `Realisasi`, `GoodsReceipt.realisasi` | marketplace / supplier |

`PurchaseOrder.supplier` is **legacy** — supplier-based purchasing is Realisasi's job now. Since BE-21 it is always set to HQ's franchise-centre supplier on create; the request's `supplierId` is ignored.

### HQ catalog — two ways a store buys (BE-21)

- **Tenant tree:** `HQ_BANDUNG` ("HQ BANDUNG", V8) is the root; `BLITZ_BDG` is its first store. Suppliers are HQ's (V8 moved BLITZ_BDG's six). `FRANCHISE_HQ`/`OUTLET_2` are test data.
- **The HQ catalog is the root tenant's own `Part` rows** — no `CatalogItem` table. A store part points at the HQ row it is via `parts.catalog_part_id` (one per store, unique index). Stock and history stay on the store's row; `soldByHq` / `mandatoryFromHq` live once, on the HQ row (mandatory implies sold, DB CHECK). `HqCatalogService.catalogEntryOf` is the one place that answers "which HQ item is this?"; `PartResponse` carries the flags *read from HQ's row*.
- **Mandatory ≠ consumable.** Independent flags.
- **Flags and links are HQ's:** `PATCH /parts/{id}/hq-flags` and `PATCH /parts/{id}/catalog-link` need the ADMIN role or PRINCIPAL scope *signed in at the root*; a store's ADMIN/Owner gets 403. Exception (BE-25): the store's own MANAGER+ may *create* a link on their own unlinked item to an item HQ sells; only HQ re-links or unlinks (unlinking escapes the mandatory rule). Items counted differently (`isConsumable()`) can never be linked. `GET /catalog` is for anyone signed in.
- **Buy from HQ** (PO): only items HQ sells (400 otherwise). `partId` may be the HQ item or the store's linked part; the store's first order of an HQ item creates its part (category/type mapped by name, created if missing, stock 0). The supplier is the root's **franchise-centre supplier** (`suppliers.franchise_centre`, one per tenant); without one, orders to HQ are refused.
- **Buy elsewhere** (Realisasi): a mandatory item is refused ("This item must be bought from HQ") unless the supplier is the franchise centre (an urgent buy paid at HQ). Applies to `supersede()` too, since it goes through `create()`. `sellerName` is free text for outside sellers.

### Who changes stock (BE-25) and what the reads show (BE-26)

- `POST /inventory/adjust` and `/inventory/receive` are **data-repair shortcuts**: ADMIN only, reason required. Everything else changes stock through an approved stock adjustment, a goods receipt or a scan.
- **Stock adjustments:** requested by the store's MANAGER+, decided by the store's own OWNER+ (`findOwned`; HQ reads, can't decide). Your own request needs another of the store's Owner+ — unless you're its only one, then it's allowed and saved `selfApproved` (flagged, like BE-20's self-received). The store's Owner+ get STOCK_ADJUSTMENT_REQUESTED.
- **Items:** create/edit MANAGER+ or ADMIN; retire/restore OWNER+ or ADMIN. **Categories and types:** writes and seeds ADMIN only (`@PreAuthorize`).
- **Every stock movement records its actor** (BE-26, V14): `PartStockService.adjustStock` — the single writer — stamps the signed-in user (null when nobody is signed in, and on rows before 23 Oct 2026). An approval's movement carries the approver. `GET /parts/{id}/movements` is the item history.
- **Inventory rows say which store** (BE-26): `tenantId`/`tenantName` on parts, stock-summary, low-stock, burn-rate, stock-adjustment and PRA rows (plus `catalogPartId` on stock and burn-rate rows). List reads take `?tenantId=` through `TenantHierarchyService.readScope` (inside the caller's tree, else 400 "Store not found"). **Burn rate uses each item's own store's `MaintenancePolicy`**, not the caller's. "Used today" counts CONSUMABLE_TAKE as well as MAINTENANCE_USAGE.

**Orders to HQ** (BE-20): drafting a PO needs MANAGER+ scope or the PROCUREMENT role; **sending** it needs OWNER+ (sending commits the store's money); cancelling a DRAFT needs its creator (`PurchaseOrder.createdBy`, null on rows before 22 Oct 2026) or OWNER+, cancelling a sent one (ORDERED / PARTIALLY_RECEIVED) needs OWNER+ and a reason, written to history. PO numbers carry a random suffix (two drafts in the same millisecond used to collide on the unique column).

**Receiving goods** (BE-20): the receiver is always the signed-in user. `receivedBy` in the body is deprecated, still accepted for one release, 400 when it isn't the caller (it used to accept any user id from any tenant). Goods photos are `goodsPhotoUploadIds` (BE-23 uploads, purpose `GOODS_RECEIPT_PHOTO`), attached in the same transaction.

### PRA / Realisasi semantics

- **PRA is an authorization ceiling**, not an order. `authorizedQty` and `maxValue` are both **per-line** on `PurchaseRequestAuthorizationItem`. There is no header-level ceiling field. (Whether to add a nullable header cap is an open decision — per-line ceilings cannot express "spend at most Rp 2.1M on this authorization", and ten lines each individually within ceiling can still overrun a total that was never stated.)
- **PRA creation _is_ the authorization act.** No approve endpoint exists. (`approvedBy` reads as though a step is missing — it isn't.) Create and cancel need **OWNER scope or above** (BE-17, which raised BE-13's MANAGER+): the store Owner sets the limits, the Store Manager buys against them, an over-ceiling buy escalates back to the Owner. A PRA is only created on the caller's own store — HQ doesn't set a store's limits.
- **Logging a purchase** (`POST /realisasi`) needs MANAGER+ scope or the PROCUREMENT role (BE-17); **rejecting** one needs OWNER+ from PENDING_APPROVAL *and* APPROVED (failing an approved purchase reopens its limit); **correcting** (`supersede`) needs the original's creator or OWNER+, and only a PENDING_APPROVAL or APPROVED original can be corrected.
- **No mutable `remaining` column anywhere.** Consumption is aggregated live from `RealisasiItem` rows counting **only `APPROVED`** status. This single choice is what makes failed-realisasi-releases-its-hold and partial fulfilment work with zero extra bookkeeping. **Do not add a `remaining` column** — it would silently break both.
- Below ceiling → auto-passes. Above → whole Realisasi goes `PENDING_APPROVAL`/`ESCALATED`, Owner+ notified, Owner-only approval. Hard limit, no tolerance band.
- `supersede()` is append-only correction: new record via self-FK, re-runs every check, flips the original to `SUPERSEDED`. Nothing overwritten in place.
- Landed cost allocated **by value**; all six cost components stored separately (`subtotal`, `sellerDiscount`, `platformVoucher`, `shipping`, `insurance`, `serviceFee`). List price is not cost — ~17% error on a real receipt.
- **Specific-identification costing**: `PartInstance.landedCost` stamped per instance. Divisor is `allocatedLandedCost ÷ (purchasedQty × conversionFactor)` — see rule 2 for why that took two attempts.
- **Money is redacted (null, not omitted) below OWNER scope** in the response mapper: Realisasi + PartInstance (BE-06), PurchaseOrder (BE-11), PRA `purchasedValue`/`remainingValue` and ServiceEvent `cost` (BE-13), `Tenant.monthlyPurchaseBudget` (BE-16). Exceptions by design: PRA `maxValue` stays visible — it's the buyer's limit; and the person owed sees `totalCost` of their own PERSONAL_REIMBURSABLE purchase (BE-18). Any new money field on a response needs the same `canSeeCost` check.
- **Paying the Store Manager back** (BE-18): only the store's own OWNER+ marks purchases paid back (`PATCH /realisasi/reimbursements/mark-paid`, one or many, all-or-nothing) or undoes it (with a reason); HQ can read but not mark, and nobody marks money owed to themself. A rejected (FAILED) purchase is still owed.
- **Spending summary and budget** (BE-16): `GET /procurement/spending?month=yyyy-MM[&tenantId=]`, OWNER+ only, one row per store in the caller's tree. Computed on read, nothing stored. **Spent** (month of the purchase) = APPROVED Realisasi `totalCost` by `purchasedAt` + sent POs (ORDERED/PARTIALLY_RECEIVED/RECEIVED) `totalAmount` by `orderDate`, split *from the centre* (every PO + Realisasi with the franchise-centre supplier) vs *outside*. **Committed** (now) = per PRA line, `max(remaining value on a live PRA, PENDING_APPROVAL purchases on that line)`, never the sum, on the ceiling's basis (unit price × qty). The definitions live in the change queue's BE-16 entry; use those words exactly. `PATCH /tenants/{id}/purchase-budget` is the store's own OWNER+ only (HQ reads, can't set); `overBudget` warns, never blocks. Months are calendar months of the stored zone-less timestamps, so **the server should run in WIB** (Railway `TZ=Asia/Jakarta`) for server-stamped dates (`orderDate`, a defaulted `purchasedAt`) to land in the right month. Since BE-19 the summary also carries `otherExpenses` (+ HQ / Branch split), and `overBudget` = spent + committed + otherExpenses > budget.
- **Expenses** (BE-19, module `expense`, `/expenses`): branch spending that isn't stock — no request, no limits, no stock. Record: the store's MANAGER+; `paidBy` STORE only OWNER+; PERSONAL needs a receipt photo (`EXPENSE_RECEIPT` upload). No edit: void with a reason (creator or the store's OWNER+). PERSONAL ones are paid back under BE-18's rules exactly (store's own OWNER+, all-or-nothing, never your own). `category` HQ / BRANCH_SPECIFIC is a label only: the branch pays both and both count against its budget. Amount redacted below OWNER except the claimant's own PERSONAL one.
- **Who files PRs** (BE-14): MANAGER+ scope, or the ADMIN or PROCUREMENT role. TECHNICIAN, OPERATOR and ACCOUNTING are refused.
- **Cancelling a PR** (BE-15) needs a reason. PENDING: its requester (anywhere in their tree) or an OWNER+ of that store; APPROVED: that store's OWNER+. Approve/reject stay with the store's own Owner (`findOwned`, own tenant only); reads and cancel look PRs up across the caller's tree (`findInTree`).
- **HQ acting for a store** (BE-14, BE-17): an optional `tenantId` on PR create (ADMIN, PROCUREMENT or OWNER+) and on purchase logging (PROCUREMENT or OWNER+) puts the record on that store. It must be inside the caller's tree; outside, or a made-up id, answers 400 "Store not found" either way. One helper: `TenantHierarchyService.resolveTargetTenant`. Store picker: `GET /tenants/tree`.
- **A PR's `requestedBy`** must be the caller, or a user of the store the PR is for (BE-13, widened by BE-14 so HQ can be the requester: "Requested by Rina for Outlet Bandung").

### Five quantities per line — all stored, never overwritten

`suggested_qty` (`BurnRateService`, computed on read) → `requested_qty` (`PurchaseRequestItem.quantity`) → `authorized_qty` (`PurchaseRequestAuthorizationItem.authorizedQty`) → `purchased_qty` (`RealisasiItem.purchasedQty`, purchase UOM) → `received_qty` (`GoodsReceiptItem.quantity`, stock units)

Each divergence is a signal. One mutable qty column destroys all of them.

### Burn rate / `suggested_qty`

`module/burnrate` — `BurnRateService`, `GET /burn-rate`, `GET /burn-rate/{partId}`, computed on read, nothing stored (same reasoning as the PRA ceiling: a stored derived value drifts silently from its inputs). Full spec: `operion-burn-rate-spec.md`.

- **Counts takes, not completed take→exhaust cycles.** Stock depletes when an item leaves the shelf; completed cycles undercount (every open tag is invisible) and lag. Completed cycles are for _efficiency analysis_, a different measurement.
- **`observationDays` is clamped to actual history**, not the window — an item first seen 6 days ago with 12 takes reports `observationDays: 6`, not the window length.
- **Four modes:** `COMPUTED` / `MANUAL_RATE` / `MANUAL_LEVEL` / `NONE`. Spareparts never get a computed rate — the manual path is permanent for them, not a cold-start fallback. `NONE` returns **null** days of cover, never `0` — zero renders as maximum urgency and would light up most of a new catalog.
- **`windowDays` = 30**, not 90 — calibrated from 26 months of real sales data (`Data_Ops.xlsx`), see `operion-burn-rate-spec.md` §4.1.
- `Part.reorderQuantity` (pre-existing field, previously unused anywhere) doubles as the override for `suggestedQty` whenever a reorder is actually triggered — this is spec §7.3's `preferredOrderQty`, not a separate field.
- **Consumable `PartInstance` rows are never auto-generated on receipt.** A goods receipt only moves `currentStock` for them; someone still has to `POST /part-instances/scan-in` each unit. Burn rate is only as good as that scanning discipline.
- `PartInstanceService.take()` decrements stock, **Consumable-scoped only** — sparepart instances already get decremented at `AirsoftUnitPartService.installPart()`/`replacePart()`; decrementing at take too would double-count a taken-then-installed sparepart.
- No `MaintenancePolicyController` exists — the four thresholds (`burn_rate_window_days`, `burn_rate_min_observation_days`, `burn_rate_min_events`, `days_of_cover_target`) plus every other `maintenance_rule` field are only editable by direct DB edit. `BurnRateService` falls back to spec defaults (30/21/10/14) when a tenant's row has them `null`.

**Still open:** lead time is unmodelled (spec §7.1); seeding `manualDailyUsage` from the spreadsheet (§7.4) is a one-time data task, not code.

### Segregation of duties — two rules, different exemptions

Do not collapse these into one helper.

| Rule                                       | Exemption                                                        |
| ------------------------------------------ | ---------------------------------------------------------------- |
| PRA approver ≠ Realisasi creator           | **Yes** — Owner/Principal may do both. Manager does not qualify. |
| Realisasi creator ≠ goods receipt receiver | **Yes, with a goods photo** (BE-20, replaced DL-11's hard refusal): the buyer may receive their own purchase only with at least one photo; the receipt is flagged `selfReceived = true` (`GET /goods-receipts?selfReceived=true`) and the store's own Owner+ get `GOODS_SELF_RECEIVED` (not HQ, BE-24). Prevention became visibility, because at a store the SM usually buys and opens the package. |

The PO path has **no** creator ≠ receiver rule, deliberately (BE-20): the Owner sends the order, the SM receives it, and HQ knows what it shipped.

The scope check reads the approver's **stored** scope as a historical fact, not the current request's `ScopeContext`. Reading the live context would let a later promotion retroactively legalize a past pairing.

### RBAC

Dual dimension: **scope** (`Principal > Owner > Manager > Supervisor`, linear, nullable `scope` column on `users`, JWT-carried, `ScopeContext`) × **functional role** (`ADMIN / TECHNICIAN / PROCUREMENT / ACCOUNTING / OPERATOR`).

One `Role` enum only. No `roles`/`user_roles` table pair.

**Scope is granted, never self-assigned** (BE-12). `POST /auth/register` is ADMIN-only, has no `scope` field, and always creates an `OPERATOR`; scope comes only from ADMIN-gated `PATCH /users/{id}/scope`. Both — and all `/tenants` endpoints — are tenant-tree-contained via `TenantHierarchyService.requireWithinCallerTenantTree` (self + descendants, never ancestors). Root tenants need `PRINCIPAL`, so they arrive via Flyway seed. The only PRINCIPAL is the **HQ super admin** (V11: `tanugraha.richard@gmail.com`, ADMIN + PRINCIPAL at `HQ_BANDUNG`), created locked; its first password comes from `BOOTSTRAP_ADMIN_EMAIL`/`BOOTSTRAP_ADMIN_PASSWORD` at startup (`InitialPasswordBootstrap`, locked accounts only). There is no password-change endpoint yet. Authorization denials are **403** (`AccessDeniedException`); segregation-of-duties violations stay 400. Test setup: log in as an ADMIN → `register` → `user-update-scope` / `user-update-role`.

**Roles are settable but barely enforced** (BE-07). ADMIN-only `PATCH /users/{id}/role` (tree-contained, never your own). Role checks now in use: `PROCUREMENT` reads `GET /procurement/activity` (as may ADMIN and OWNER+), receives the PRA-created notification, files PRs and logs purchases, including for a store (BE-14/17); `ADMIN` files PRs, including for a store. TECHNICIAN and ACCOUNTING are still only checked by being refused. Role-**or**-scope checks live in the service (`CallerRoles.hasAny` + `ScopeContext.hasAtLeast`), because `@PreAuthorize` only sees roles.

**Approval alerts stay in the store** (BE-24): NEW_PURCHASE_REQUEST, REALISASI_ESCALATED and GOODS_SELF_RECEIVED go to that store's own ADMIN and OWNER+ users only, never HQ — HQ can't approve a store's records. **Procurement notifications** go to the PR requester (approved / rejected / authorized / cancelled by someone else) and to PROCUREMENT users in the PRA's own tenant (PRA created). Nobody is notified about their own action. Every PR and PO status-history row now records its actor; rows written before 14 Oct 2026 may have a null actor. **Automatic rows** (BE-22: the PRA status refresh, the PO auto-status on goods receipt) keep `changedBy` null and set `automatic`, `triggeredBy` (whose action caused it) and `triggerReference` ("SHP-123 approved", "PO-… goods received"); responses carry `actorKind` USER / AUTOMATIC / NOT_RECORDED so the app never guesses from an empty name. Any new server-derived status change must do the same.

---

## Module inventory

`airsoft` · `analytics` · `auth` (Role + Scope) · `barcodeblock` · `burnrate` · `dashboard` · `expense` · `inventory` · `maintenance` · `notification` · `part` · `parthistory` · `partinstance` · `procurement` · `serviceevent` · `stockadjustment` · `tenant` · `unitpart` · `workorder`

`procurement` contains: `Supplier`, `PurchaseRequest`, `PurchaseOrder`, `GoodsReceipt`, `PurchaseRequestAuthorization`, `Realisasi`.

---

## Deliberately not built

- **Barcode redemption path** — offline-generated codes → real `PartInstance` rows on sync. The endpoint reserves number space only. Blocked on an unmeasured question: how often outlets actually lose connectivity.
- Realisasi `attachments` is still a plain text/URL field. File uploads now exist (BE-23: Cloudflare R2, private bucket, presigned PUT/GET, `UploadService.attach` for records that take files); wiring them into Realisasi is a later task (`UploadPurpose.PURCHASE_RECEIPT` is reserved for it).
- A filter surfacing `retroPurchaseFlag=true` records for the Owner. Field exists; nothing queries it.

## Deployment

Live on Railway since 16 Aug 2026, project `imaginative-gratitude`, service `Operion-services`, public URL `https://operion-services-production.up.railway.app`. Separate `Postgres` service in the same project, connected via Railway's private network (`postgres.railway.internal`) — the app service's `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` env vars reference it as `${{Postgres.PGHOST}}` etc., not literal values. Public TCP access to Postgres was enabled once (to load data below) and should stay off otherwise — check `Postgres → Settings → Networking` if in doubt.

**Flyway migrations beyond what §5.1/Q4 of `OPERION_BE_PLAN.md` anticipated**, both required because the dev DB's schema predates Flyway (adopted BE-05, 11 Aug) and had no migration scripts of its own:

- `V1__baseline.sql` — schema-only (`pg_dump --schema-only`) snapshot of the dev DB, needed because `baseline-on-migrate` only baselines a schema that already has tables; a genuinely fresh database (any new Railway Postgres) had nothing to baseline and `ddl-auto=validate` would fail at boot with zero tables. Note: pg_dump 18's `\restrict`/`\unrestrict` meta-commands and the `flyway_schema_history` table itself both had to be stripped out of the dump by hand before Flyway would accept it as a migration.
- `V2__seed_default_tenant.sql` — inserts the `BLITZ_BDG` tenant. `AuthService.register()` requires an existing `Tenant` row matching `tenantCode` (default `"BLITZ_BDG"`), and every endpoint except `/auth/login`/`/auth/register` requires a JWT — so a database with zero tenants has no way to bootstrap its first user via the API at all. `MasterDataSyncRunner` only re-seeds *existing* tenants on boot, it never creates the first one.
- `V8__hq_bandung_and_catalog.sql` (BE-21) — inserts the `HQ_BANDUNG` root and makes `BLITZ_BDG` its child, moves BLITZ_BDG's suppliers to HQ, adds HQ's franchise-centre supplier and the catalog columns. HQ's part categories/types arrive from `MasterDataSyncRunner` on the next boot. It creates no user.
- `V11__hq_super_admin.sql` (BE-21) — the HQ super admin, locked (`password_hash = '!'`). Never a password in a migration; see RBAC above for how its first password is set.

**Prod DB currently holds an exact 1:1 copy of the dev DB's data** (loaded 17 Aug 2026, `pg_dump --data-only` → `psql`, verified row-count-identical across all 38 tables). This means every caveat in the paragraph below about dev DB test junk is now equally true of prod, not just dev.

`OPERION PROD.postman_collection.json` (repo root) — URLs point at the Railway public domain, not `localhost:8080`; `OPERION DEV.postman_collection.json` mirrors it for localhost. `register` now sends the ADMIN's `{{bearerToken}}` (BE-12). Both files are PowerShell-`ConvertTo-Json`-formatted and round-trip byte-identically through `ConvertFrom-Json | ConvertTo-Json -Depth 100` — edit them that way to keep diffs minimal.

**Open:** a 405 seen testing from an iPhone had a Cloudflare-shaped error envelope (`{success, result, errors: [{code, message}]}`), confirmed **not** to originate from `GlobalExceptionHandler`/`ApiResponse` (neither produces that shape) — so the request likely never reached the app. Root cause not yet identified; suspect a GET hitting a POST-only endpoint (e.g. `/part-instances/scan-in`) at the edge, but unconfirmed. Further evidence it never reached the app (12 Oct, `[C]`): before BE-12 the app's catch-all handler turned `HttpRequestMethodNotSupportedException` into **500**, so the app could not have emitted any 405 at all. Since BE-12 it correctly returns 405 with its own `{timestamp, error}` shape — so a *Cloudflare-shaped* 405 seen after deploy is still the edge.

## Housekeeping

Test data from four verification sessions is still in the dev DB, **and now in prod too** (see Deployment above) (`OPERION_BE_PLAN.md` §5.2 lists it with FK deletion order). Delete whole chains — partial deletion changes ceiling consumption on any PRA left behind. Also outstanding: duplicate `part_categories` cleanup from the July seed session (§5.3).

---

## Working style

- **SSOT before implementation.** Decisions get captured in BRD/FRS/HLD/LLD/Decision Log before building.
- **Keep this file in sync as gaps close.** It loads at every session start; stale content here propagates directly into code. A memory-index drift on 28 Jul described an already-built module as unbuilt — exactly this failure mode.
- **DDL is confirmed only after executing cleanly against a live PostgreSQL instance.** Paper review does not count.
- A parallel dev team uses other AI tools; outputs are reconciled manually. Assume other sessions may have changed things — check before asserting.
- **Branches: `feature/<name>`** (e.g. `feature/be-14-pr-create-gate`), cut from an up-to-date `main` (`git checkout main && git pull` first). One task per branch; merge to `main` with a merge commit when done. Product owner's rule, 20 Oct 2026.
- **After each backend task ships, update the shared status page** ("Operion Project Status", a claude.ai artifact the product owner keeps across the backend, mobile and design chats): move the task to shipped and flip its "Live?" entries.

**Client stack** (context; separate repo): React Native (Expo dev build, `react-native-vision-camera`) for the outlet app; React + Vite for the web console; shared `packages/core` for API client, generated types, and design tokens. UI components are not shared between them.
