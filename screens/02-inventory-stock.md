# Module 2 — Inventory & Stock Screen Inventory

**Date:** 21 Oct 2026, completed 23 Oct 2026
**Status:** **COMPLETE** — 9 screens (INV-01…09), gates 1–5 run, gap list produced. Five
backend tasks came out of it (BE-25…BE-29).
**Built against:** `OPERION_UI_SCREEN_INVENTORY_PLAN.md` v1.4, plus the module 1
decisions of 16–21 Oct 2026 (`OPERION_BE_CHANGE_QUEUE.md` BE-14…BE-23).
**Source read:** `module/inventory/` (controller, DTOs, enums, `PartStockService`),
`module/stockadjustment/` (controller, DTOs, service), `module/part/dto/`
(`PartResponse`, create/update requests), `module/burnrate/` (controller, response,
service scoping), `NotificationService.notifyLowStock()` / `approversIn()`. All backend
claims are `[C]`, cited by file:line. `module/partinstance/` and `module/barcodeblock/`
(scan in / take / exhaust) belong to module 3 and are not inventoried here.

**Product owner's decisions, 23 Oct 2026** (on the draft's three questions, "as suggested"):
1. **Direction confirmed:** mobile-first; the Store Manager is the main user; HQ sees every
   store and only advises.
2. **One definition of "low":** "runs out in X days" (burn rate, `daysOfCover` below
   `targetDays`) when the item has usage data (`COMPUTED` / `MANUAL_RATE`); otherwise
   "below minimum stock". Screens say which one triggered.
3. **The two stock shortcuts** (`POST /inventory/adjust`, `POST /inventory/receive`):
   ADMIN only, for data repair, with a required reason and every use logged. Normal stock
   changes go through an approved adjustment, a goods receipt, or a scan (module 3).

**Context that changed since the plan was written:** the product is mobile-first (Store
Manager on one store phone; HQ staff on their own phones), the tenant tree is real (HQ
BANDUNG above `BLITZ_BDG`, BE-21), every store is independent, and HQ reads everything to
advise. Platform below is therefore **Mobile** unless stated.

---

## Endpoints — the contract baseline `[C]`

| Resource | Endpoint | Returns | Scope / role check | Tenant reach |
|---|---|---|---|---|
| Stock | `GET /inventory/stock-summary` | `LowStockResponse[]` — `{partId, partName, currentStock, minimumStock, status}` | none | **whole tree** (`getEffectiveTenantIds`, `PartStockService.java:258-263`) |
| Stock | `GET /inventory/low-stock` | same, only `currentStock ≤ minimumStock`, lowest first | none | whole tree (`:245-250`) |
| Stock | `GET /inventory/dashboard` | `{totalParts, totalStock, lowStock, outOfStock, stockReceivedToday, stockUsedToday}` | none | whole tree (`:193-198`) |
| Stock | `POST /inventory/adjust` | `"OK"` — `{partId, quantity, notes}`, changes stock **immediately** | **none** | caller's own store (`:127-147`) |
| Stock | `POST /inventory/receive` | `"OK"` — `{partId, quantity, notes}`, adds stock as `PURCHASE` | **none** | caller's own store (`:149-178`) |
| Adjustments | `POST /stock-adjustments` | `StockAdjustmentResponse{id, partId, partName, quantity, reason, status, requestedById, requestedByName, createdAt}` | none | caller's own store (`StockAdjustmentService.java:55-80`) |
| Adjustments | `GET /stock-adjustments`, `GET /stock-adjustments/pending` | list of the above | none | whole tree (`:135-157`) |
| Adjustments | `PATCH /stock-adjustments/{id}/approve` · `/reject {reason}` | the above | **OWNER+** (`requireOwnerScope`, `:86`, `:116`, `:176-182`) | whole tree (`findInHierarchy`, `:184-192`) |
| Adjustments | `GET /stock-adjustments/{id}/history` | `StatusHistoryResponse[]` | none | whole tree |
| Burn rate | `GET /burn-rate`, `GET /burn-rate/{partId}` | `BurnRateResponse{partId, partName, currentStock, burnRateSource, burnRate, observationDays, eventCount, daysOfCover, targetDays, suggestedQty, manualReorderPoint, belowLevel}` | none | whole tree (`BurnRateService.java:51-64`); policy read from the **caller's** tenant only (`:80`) |
| Parts | `GET /parts`, `GET /parts/grouped-by-category`, `POST`, `PUT /{id}`, `PATCH /{id}/retire`, `/restore` | `PartResponse{id, name, brand, categoryId, categoryName, partTypeId, partTypeName, expectedLifespanDays, notes, currentStock, minimumStock, reorderQuantity, manualDailyUsage, manualReorderPoint, retired, retiredAt}` | none | list: whole tree |

**No response in this module carries a tenant (store) id or name.** Every "whole tree"
list above returns HQ a flat mix of every store's rows with nothing saying which store a
row belongs to. That single fact shapes the HQ screens below.

---

### INV-01 — Stock Overview (store)

**Purpose:** The Store Manager sees every item the store holds, how much is left, and
what is running low, at a glance.
**Platform:** Mobile — the store phone (Store Manager), Store Owner on their own phone.
**Scope tiers:** View — any authenticated user; nothing enforced `[C]`
(`InventoryController.java:73`, `PartStockService.java:258`, no `ScopeContext`).
**Functional roles:** None enforced `[C]`.
**Actions:**
  - View the stock list, filter All / Low / Out — any user
  - Open an item → item detail (INV-0x, not yet drafted)
  - **"Request more"** on a low or out item → opens PROC-01 for this store with the item
    and the burn-rate `suggestedQty` prefilled — whoever may create a PR (BE-14:
    Manager+, ADMIN, PROCUREMENT)
**Data read:** `GET /inventory/stock-summary` (status per item) **and** `GET /burn-rate`
(days of cover, suggested quantity) — two calls joined on `partId` on the device.
**Entry points:** Dashboard (stock tiles), the `LOW_STOCK` notification, the Procurement
menu.
**Failure behaviour:** Manual retry — read-only list.
**Backend status:** `[C]` both endpoints exist. Status is computed on read:
`OUT_OF_STOCK` at ≤ 0, `LOW_STOCK` at ≤ `minimumStock`, else `NORMAL`
(`PartStockService.java:283-298`). Days of cover is **null, not 0,** for `MANUAL_LEVEL`
and `NONE` (`BurnRateResponse`, plan §5.2) — render "no usage data", never "0 days".
**Vocabulary flag:** Yes — "part(s)" appears in titles and empty states.
**Cost visibility:** N/A — neither response carries cost.
**Deferred decisions:**
  - **Two round trips for one list.** Stock status and burn rate live on different
    endpoints with the same `partId`. One `stock-summary` response carrying
    `daysOfCover`, `suggestedQty` and `burnRateSource` would serve this screen in one
    call. Candidate BE task, not a blocker.
  - Don't hardcode the 14-day `targetDays` (plan §5.2); read it from the response.

---

### INV-02 — Restock Suggestions (store)

**Purpose:** Turn "running low" into a request in one tap: what to reorder, how much,
and whether it must come from HQ.
**Platform:** Mobile — Store Manager (who files the PR), Store Owner (who approves it).
**Scope tiers:** View — any user `[C]`. Creating the request — BE-14's rule.
**Functional roles:** None enforced `[C]`.
**Actions:**
  - View items below their reorder point, worst first — any user
  - **"Request these"**: select several → one PROC-01 PR with all lines prefilled — PR
    creators (BE-14)
  - Mandatory items (BE-21 `mandatoryFromHq`) are marked **"From HQ"**: the restock goes
    through "Buy from HQ" (PROC-07), not "Buy elsewhere"
**Data read:** `GET /inventory/low-stock` + `GET /burn-rate`; after BE-21, the HQ
catalog flags per item.
**Entry points:** INV-01 ("Low" filter), Dashboard, `LOW_STOCK` notification.
**Failure behaviour:** Manual retry for the list; the PR it creates follows PROC-01.
**Backend status:** `[C]` `GET /inventory/low-stock` returns parts with
`currentStock ≤ minimumStock` ordered by stock ascending (`PartStockService.java:270-277`).
**Two definitions of "low" exist and can disagree:** this one (`minimumStock`) and burn
rate's (`daysOfCover < targetDays`, or `belowLevel` against `manualReorderPoint` for
`MANUAL_LEVEL`). An item can be "fine" here and "runs out in 2 days" there.
**Vocabulary flag:** Yes — "part(s)".
**Cost visibility:** N/A on this screen. The PR it opens carries none either.
**Deferred decisions:**
  - ~~Which "low" wins?~~ **Decided 23 Oct:** burn rate when it has data (`COMPUTED` /
    `MANUAL_RATE`), `minimumStock` otherwise — the screen shows which one triggered ("runs
    out in ~3 days" vs "below minimum 20"). The server applies the same rule: **BE-27**.
  - **The Store Manager isn't told.** `LOW_STOCK` goes to ADMIN or OWNER+ in the item's
    own store only (`NotificationService.java:193-206`, `approversIn` `:249-252`). The SM,
    who files the PR under BE-14, never gets it. **BE-27** adds the store's Manager+ and
    keeps it in the store, as BE-24 did for approval alerts: HQ follows stock through
    INV-03, not notifications.

---

### INV-03 — Stock Across Stores (HQ)

**Purpose:** HQ sees, per item, which stores are low or out — the product owner's "this
part is out at this outlet, and also at that one" — and advises the store Owner.
Advisory only: HQ doesn't restock a store.
**Platform:** Mobile — HQ Principal, HQ Admin, HQ Procurement on their own phones.
**Scope tiers:** View — HQ (PRINCIPAL scope, or ADMIN / PROCUREMENT at the root tenant).
Nothing enforces this today `[C]`; the endpoints are open to anyone, but only a caller at
the root *sees* other stores, through the tree walk.
**Functional roles:** None enforced `[C]`.
**Actions:**
  - View items grouped by item, worst store first: "6mm BBs 0.25g — Bandung runs out in
    ~3 days · Jakarta in ~9 days · Surabaya fine" — HQ
  - Filter: one store, mandatory items only, out of stock only — HQ
  - **"Create request for this store"** → PROC-01 with the store preselected (BE-14's HQ
    store picker) — HQ ADMIN / PROCUREMENT
**Data read:** stock + burn rate **per store**, plus the HQ catalog link (BE-21) to group
each store's copy of an item under one HQ item.
**Entry points:** HQ dashboard (not yet inventoried; pairs with BE-16's spending view).
**Failure behaviour:** Manual retry — read-only.
**Backend status:** **Not servable today.** `[C]` `stock-summary`, `low-stock` and
`burn-rate` already walk the whole tree for an HQ caller, but `LowStockResponse` and
`BurnRateResponse` carry **no tenant id or name** — HQ gets every store's rows mixed
together with no way to tell them apart. And with no catalog link yet (BE-21), the same
item at two stores is two unrelated parts.
**Vocabulary flag:** Yes — "part(s)", "outlet".
**Cost visibility:** N/A — stock quantities only. (Stock *value* would be cost data;
OWNER+ — HQ qualifies.)
**Deferred decisions:**
  - **BE-26:** `tenantId`/`tenantName` and `catalogPartId` on the stock and burn-rate
    rows (BE-21 has shipped, so the catalog link exists), so HQ can group each store's copy
    under one HQ item.
  - **Burn-rate policy is read from the caller's tenant** (`BurnRateService.java:80`):
    HQ's `MaintenancePolicy` would be applied to every store's items in this view. BE-26
    reads each part's own store policy instead.

---

### INV-04 — Stock Adjustment (request and approve)

**Purpose:** Correct the stock count when reality differs (a broken box, a miscount, a
loss), with the Owner approving before the number changes.
**Platform:** Mobile — Store Manager requests, Store Owner approves.
**Scope tiers:** Request — any user `[C]` (`StockAdjustmentService.java:55`, no check).
Approve / reject — **OWNER+** `[C]` (`:86`, `:116`).
**Functional roles:** None enforced `[C]`.
**Actions:**
  - Request an adjustment: item, quantity (+ or −), reason — any user today
  - Approve → stock changes, movement `ADJUSTMENT` with reference `STOCK_ADJUSTMENT`
    (`:96-104`) — OWNER+
  - Reject with reason — OWNER+
  - View pending list and history — any user
**Data read:** `GET /stock-adjustments/pending`, `GET /stock-adjustments`,
`GET /stock-adjustments/{id}/history`; `GET /parts` for the item picker.
**Entry points:** INV-01 item detail ("Adjust count"), Dashboard pending count. No
notification exists for a new adjustment request `[C]` (no adjustment type in
`NotificationType`) — the Owner finds out by looking.
**Failure behaviour:** Request — manual retry. Approve/reject — blocking (an approval must
not be queued, plan §3).
**Backend status:** `[C]` full workflow exists, with three findings that matter more than
the screen:
  1. **The approval can be skipped entirely.** `POST /inventory/adjust` changes stock
     immediately with **no approval and no scope check** (`PartStockService.java:127-147`).
     Anyone signed in can set any item's count, bypassing this screen's whole purpose.
  2. **An Owner can approve their own request.** `approve()` checks OWNER+ but not
     requester ≠ approver (`:84-112`). Gate 3 — the plan already warned this module has
     "real fraud surface" (plan §4 Gate 3).
  3. **`POST /inventory/receive` adds stock with no purchase behind it** — no goods
     receipt, no Realisasi, no PO, no check (`:149-178`). It also still decides
     "consumable" by the category *name* (`:170`), the string check BE-04 removed
     everywhere else, so a renamed consumable category would get a part instance per
     unit.
**Vocabulary flag:** Yes — "part".
**Cost visibility:** N/A today (quantities only). If adjustments ever show stock value
written off, that's cost data: OWNER+.
**Deferred decisions:**
  - **Who may request, who approves — BE-25.** Request: the store's Manager+ (the SM counts
    the stock). Approve/reject: the **store's own** OWNER+ — today `findInHierarchy`
    (`:184-192`) also lets HQ's Principal approve a store's adjustment, against "every
    store is independent". ~~Requester ≠ approver~~ **Decided 23 Oct:** another of the
    store's Owners approves; a store's only Owner may approve their own, saved and shown
    as "self-approved" — the same visibility-not-blocking choice as self-received goods
    (BE-20). **BE-25.**
  - ~~What to do with `/inventory/adjust` and `/inventory/receive`~~ **Decided 23 Oct:**
    ADMIN only, for data repair, reason required, every use logged — **BE-25**.
  - **Notify the Owner** when an adjustment is requested (new notification type, CHECK
    constraint migration) — **BE-25**.

---

### INV-05 — Item Detail and Movement History (store)

**Purpose:** One item's whole story: how much there is, how fast it goes, and every stock
change with who and why — the place an Owner checks before approving an adjustment.
**Platform:** Mobile — Store Manager, Store Owner.
**Scope tiers:** View — any user `[C]` (no check on any read in this module).
**Functional roles:** None enforced `[C]`.
**Actions:**
  - View stock, minimum, reorder quantity, burn rate (source, days of cover, suggested
    quantity), HQ link and "mandatory from HQ" (BE-21) — any user
  - View movement history: type, quantity, before → after, notes, when — any user
  - **Adjust count** → INV-04; **Request more** → PROC-01 (or PROC-07 for an HQ item);
    **Edit item** → INV-06
**Data read:** `GET /parts` (one row — no `GET /parts/{id}` exists `[C]`,
`PartController.java:28-130`), `GET /burn-rate/{partId}`, and the item's stock movements.
**Entry points:** INV-01, INV-02, INV-03 (HQ), the `LOW_STOCK` notification.
**Failure behaviour:** Manual retry — read-only.
**Backend status:** **History not servable.** `StockMovementRepository
.findByPartIdOrderByCreatedAtDesc` exists `[C]` but **no endpoint calls it** — the only
`StockMovementResponse` endpoint is `GET /part-history/unit-part/{unitPartId}`, which is
per installed unit part, not per stock item. `StockMovementResponse` also carries no actor
(`{id, partName, movementType, quantity, beforeStock, afterStock, notes, createdAt}`), so
even when exposed it can't say who changed the stock. **BE-26.**
**Vocabulary flag:** Yes — "part".
**Cost visibility:** None shown. If unit or landed cost is ever added here: OWNER+, and the
store's own purchases only.
**Deferred decisions:** —

---

### INV-06 — Items: list, add, edit, retire (store)

**Purpose:** The store's own item list — what it stocks, its minimum and reorder quantity
(which drive "low"), and retiring items it no longer carries.
**Platform:** Mobile — Store Manager (day to day), Store Owner.
**Scope tiers:** **None enforced** `[C]`: `POST /parts`, `PUT /parts/{id}`,
`PATCH /parts/{id}/retire` and `/restore` have no scope or role check
(`PartService.java` create `:54`, update `:282`, retire `:403-426`, restore). Only `hq-flags` and
`catalog-link` are gated (`requireHqCatalogAdmin`, `:218`, `:251`).
**Functional roles:** None enforced `[C]`.
**Actions:**
  - List items by category, with stock and status — any user
  - Add an item: name, brand, category, type, expected lifespan, minimum stock, reorder
    quantity, manual daily usage, manual reorder point, notes — **recommended Manager+**
  - Edit the same fields — **recommended Manager+**. `minimumStock` decides "low" for
    items without usage data, so whoever edits it controls the alarm.
  - Retire (reason; refused while stock > 0 or the item is installed `[C]` `:413-421`) /
    restore — **recommended Owner+**
  - For an item HQ sells: **"Use HQ's item"** instead of adding a duplicate — links the
    store's item to the HQ catalog (`PATCH /parts/{id}/catalog-link`, HQ admin only today)
**Data read:** `GET /parts/grouped-by-category`, `GET /part-categories`,
`GET /part-types/category/{categoryId}`.
**Entry points:** Inventory menu (doesn't exist in the app yet — see Gate 1), INV-05.
**Failure behaviour:** Manual retry; edits are blocking (not queued).
**Backend status:** `[C]` all endpoints exist; no permission checks. A store can create
its own "6mm BBs" while HQ sells one — the first order from HQ then creates or links a
second copy (BE-21's linking exists for exactly this, but only HQ can link). **BE-25**
adds the gates.
**Vocabulary flag:** Yes — "part", "installed", "unit".
**Cost visibility:** None on the item master.
**Deferred decisions:**
  - Should a store Manager be able to link their own item to HQ's (today HQ admin only)?
    **Decided 23 Oct (BE-25 item 6):** yes, but one way only. The
    store's Manager+ may link one of its own **unlinked** items to an item HQ sells — they
    know which box is which, and the natural moment is the first order from HQ ("you
    already stock this?"). Changing or removing a link stays HQ's: the link decides
    whether the mandatory-from-HQ rule applies (`HqCatalogService.isMandatoryFromHq`), so
    unlinking is the way around it. Refuse a link when the two items are counted
    differently (consumable vs per unit), or when the store already has an item linked to
    that HQ item.

---

### INV-07 — Categories and Types (admin)

**Purpose:** The taxonomy items hang on. The category's **`consumable`** flag decides
whether stock is tracked per unit (part instances) and whether burn rate applies (BE-04).
**Platform:** Mobile — HQ Admin; rare.
**Scope tiers:** **None enforced** `[C]` on `POST/PUT/DELETE /part-categories` and
`/part-types`, nor on `POST …/seed`.
**Functional roles:** None enforced `[C]`.
**Actions:**
  - List categories and types — any user
  - Add / rename / delete a category or type, set `consumable` — **recommended ADMIN only**
  - Seed defaults — ADMIN only
**Data read:** `GET /part-categories`, `GET /part-types`.
**Entry points:** Settings (doesn't exist yet — Gate 1).
**Failure behaviour:** Blocking.
**Backend status:** `[C]` CRUD exists, per store (master data is copied into each store,
`MasterDataInitializer`, BE-21 report). Flipping `consumable` on a category silently
changes how every item in it is counted from then on. **BE-25** gates it; **BE-29**
makes it one list at HQ.
**Vocabulary flag:** Yes — category and type names are domain vocabulary; tenant-
configurable by nature.
**Cost visibility:** N/A.
**Deferred decisions:**
  - Per-store copies of categories drift (the open duplicate-`part_categories` item,
    `OPERION_BE_PLAN.md` §5.3). **Decided 23 Oct (BE-29):** one
    list, owned by HQ, read by every store, moved **before go-live** while the production
    database is wiped anyway. Why: the first order of an HQ item matches the store's
    category by name and creates a copy if it's missing (`HqCatalogService.newStorePart`),
    copying HQ's `consumable` flag once — after that the two can differ, and the same gas
    would be counted per unit in one store and by quantity in another. HQ's cross-store
    numbers by category need one list too, and BE-25 makes categories ADMIN-only anyway,
    so stores lose nothing they'd use.

---

### INV-08 — Inventory Tiles on the Dashboard (store)

**Purpose:** Five numbers at a glance on the store dashboard: items, total stock, low, out,
received today, used today — each tapping through to INV-01 filtered.
**Platform:** Mobile — Store Manager.
**Scope tiers:** View — any user `[C]`.
**Functional roles:** None enforced `[C]`.
**Actions:** View; tap a tile → INV-01 with that filter — any user.
**Data read:** `GET /inventory/dashboard`.
**Entry points:** Dashboard.
**Failure behaviour:** Silent — tiles hide on failure, the dashboard still loads.
**Backend status:** `[C]` exists, two problems:
  1. **"Used today" counts `MAINTENANCE_USAGE` only** (`PartStockService.java:226-232`),
     not `CONSUMABLE_TAKE` — so BBs and gas taken from stock, the store's biggest daily
     use, never show. **BE-26.**
  2. **"Low" and "out" use minimum stock only**, not decision 2's rule. **BE-27.**
  Also: for an HQ caller it sums every store together — HQ uses INV-03 instead.
**Vocabulary flag:** Yes — "parts".
**Cost visibility:** N/A.
**Deferred decisions:** —

---

### INV-09 — HQ Catalog (HQ)

**Purpose:** HQ manages what it sells: which of its items are sold to stores, which are
mandatory, and which store item is the same thing as an HQ item.
**Platform:** Mobile — HQ Admin, HQ Principal.
**Scope tiers:** Flags and linking — ADMIN role or PRINCIPAL scope **signed in at the
root** `[C]` (`requireHqCatalogAdmin`, BE-21). Reading the catalog — any user
(`GET /catalog`).
**Functional roles:** ADMIN, as above.
**Actions:**
  - List HQ's items with "sold" and "mandatory" — HQ
  - Toggle **Sold by HQ** / **Mandatory from HQ** (mandatory implies sold) — HQ admin
  - Add an HQ item — `POST /parts` signed in at HQ (ungated today; BE-25)
  - **Link store items**: for one HQ item, pick each store's matching item — HQ admin
**Data read:** `GET /catalog` (sold items only), `GET /parts` at HQ (all of HQ's items,
sold or not, plus every store's), `PATCH /parts/{id}/hq-flags`, `/catalog-link`.
**Entry points:** HQ dashboard / settings.
**Failure behaviour:** Blocking.
**Backend status:** `[C]` flags, linking and the catalog read exist (BE-21). **Linking
can't be laid out:** `GET /parts` at HQ returns HQ's and every store's items with **no
store on each row** (`PartResponse` has no tenant field), so the screen can't show "Outlet
Bandung's items" to link from. **BE-26.**
**Vocabulary flag:** Yes — "part".
**Cost visibility:** None today. HQ's catalog has no selling price, so a store types the
price on every order to HQ (PROC-07). **Decided 23 Oct:** HQ sets a price per item;
orders to HQ take it, and stores stop typing one. The price is cost data: OWNER+, like
other prices. **BE-28.**
**Deferred decisions:**
  - ~~A catalog price set by HQ~~ **Decided 23 Oct:** yes — **BE-28**.

---

## Gates

### Gate 1 — Orphan check
Every screen has a role that can open it. **Entry points are the problem:** the app has no
Inventory menu and no settings screen, so INV-01/02/05/06 are reachable today only from a
`LOW_STOCK` notification, and INV-07/09 from nowhere. **Finding:** the app needs an
Inventory menu (store) and an HQ menu (INV-03, INV-09, spending). INV-03 and INV-09 are HQ
screens and also need the HQ dashboard the product owner asked for (status page, "New: HQ
dashboard").

### Gate 2 — Dead-end check
No dead ends. INV-02 and INV-05 hand off to PROC-01/PROC-07; INV-04 returns to INV-05;
INV-03 hands off to PROC-01 with the store preselected.

### Gate 3 — Segregation of duties
| Pairing | Today `[C]` | Decided / recommended |
|---|---|---|
| Stock adjustment: requester ≠ approver | **No rule** — an Owner approves their own (`StockAdjustmentService.java:84-112`) | Rule; the store's only Owner may approve their own, flagged "self-approved" (decided, BE-25) |
| Stock adjustment: approver is the store's own | **No** — HQ can approve via `findInHierarchy` | Store's own OWNER+ (BE-25) |
| Direct stock change with no approval | **Anyone** (`/inventory/adjust`) | ADMIN only, reason, logged (decided, BE-25) |
| Stock in with no purchase | **Anyone** (`/inventory/receive`) | ADMIN only, reason, logged (decided, BE-25) |
| Who sets `minimumStock` (which raises "low") | Anyone | Manager+ (BE-25) |

### Gate 4 — Cost visibility
Module 2 screens show **no cost fields** today `[C]` (`PartResponse`,
`LowStockResponse`, `StockAdjustmentResponse`, `BurnRateResponse`,
`InventoryDashboardResponse` carry none). Passes. If stock value, unit cost or write-off
value is ever added (INV-04, INV-05, INV-08): OWNER+, matching BE-06/BE-11.

### Gate 5 — Deferred-decision log
1. ~~Requester ≠ approver for stock adjustments when the store has one Owner~~ **Decided
   23 Oct:** allowed for the store's only Owner, flagged "self-approved" (INV-04, BE-25).
2. ~~May a Store Manager link their own item to HQ's catalog item?~~ **Decided 23 Oct:**
   link yes; change or remove a link, HQ only (INV-06, BE-25 item 6).
3. ~~Should HQ's catalog carry a selling price?~~ **Decided 23 Oct:** yes (INV-09, BE-28).
4. ~~One shared category list from HQ instead of per-store copies?~~ **Decided 23 Oct:**
   yes, before go-live (INV-07, BE-29).

No open questions remain in this module.

---

## Screens no endpoint can serve — the module's real output

| Screen | Missing | Task |
|---|---|---|
| INV-03 Stock across stores | Store (and catalog link) on stock and burn-rate rows; burn-rate policy per part's own store | BE-26 |
| INV-05 Item detail | Per-item movement history endpoint, with the actor on each movement | BE-26 |
| INV-09 HQ catalog linking | Store on `PartResponse` | BE-26 |
| INV-08 Dashboard tiles | "Used today" includes consumable takes | BE-26 |
| INV-02, INV-08 | One definition of "low" on the server; `LOW_STOCK` to the store's Manager+ | BE-27 |
| INV-04, INV-06, INV-07 | Permission checks on adjustments, items, categories; the two shortcuts ADMIN-only | BE-25 |
| INV-01 | Stock and burn rate in one call (nice to have) | BE-26 (optional) |
| INV-09, PROC-07 | HQ's price on catalog items; orders to HQ take it instead of a typed price | BE-28 |
| INV-06, PROC-07 | Store Manager may link their own item to HQ's (create only) | BE-25 item 6 |
| INV-07 | One category and type list, at HQ | BE-29 |

The store-on-every-row fix in BE-26 also closes the two gaps the app works around in module
1 (`API_CONTRACT.md`): HQ picking parts and PRAs for a chosen store.
