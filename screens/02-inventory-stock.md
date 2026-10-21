# Module 2 — Inventory & Stock Screen Inventory

**Date:** 21 Oct 2026
**Status:** **DRAFT — first 4 records, stopped for review** (plan §10 step 4). The rest of
the module, the gates and the gap list follow once the format and direction are confirmed.
**Built against:** `OPERION_UI_SCREEN_INVENTORY_PLAN.md` v1.4, plus the module 1
decisions of 16–21 Oct 2026 (`OPERION_BE_CHANGE_QUEUE.md` BE-14…BE-23).
**Source read:** `module/inventory/` (controller, DTOs, enums, `PartStockService`),
`module/stockadjustment/` (controller, DTOs, service), `module/part/dto/`
(`PartResponse`, create/update requests), `module/burnrate/` (controller, response,
service scoping), `NotificationService.notifyLowStock()` / `approversIn()`. All backend
claims are `[C]`, cited by file:line. `module/partinstance/` and `module/barcodeblock/`
(scan in / take / exhaust) belong to module 3 and are not inventoried here.

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
  - **Which "low" wins?** Product decision. Recommendation: burn rate when it has data
    (`COMPUTED` / `MANUAL_RATE`), `minimumStock` otherwise — the screen shows which one
    triggered ("runs out in ~3 days" vs "below minimum 20").
  - **The Store Manager isn't told.** `LOW_STOCK` goes to ADMIN or OWNER+ in the item's
    own store only (`NotificationService.java:193-206`, `approversIn` `:249-252`). The SM,
    who files the PR under BE-14, never gets it; neither does HQ, which wants it for
    restock recommendations. Candidate BE task.

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
  - **BE task:** add `tenantId`/`tenantName` (and, after BE-21, `catalogPartId`) to the
    stock and burn-rate responses, or a dedicated HQ endpoint grouped by catalog item.
  - **Burn-rate policy is read from the caller's tenant** (`BurnRateService.java:80`):
    HQ's `MaintenancePolicy` would be applied to every store's items in this view. Verify
    before drawing numbers HQ will act on.

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
  - **Who may request?** Recommendation, following module 1: the store's Manager+ (the
    SM counts the stock), approve OWNER+, and **requester ≠ approver** unless the store
    has nobody else (to decide).
  - **What to do with `/inventory/adjust` and `/inventory/receive`.** Recommendation:
    remove them, or restrict to ADMIN for data repair, logged. Stock should only change
    through an approved adjustment, a goods receipt, or a scan (module 3).
  - **Notify the Owner** when an adjustment is requested (new notification type, CHECK
    constraint migration).

---

## Findings so far (before the remaining records and the gates)

1. **Stock can be changed with no approval** (`/inventory/adjust`) and **added with no
   purchase** (`/inventory/receive`) — by anyone signed in.
2. **An Owner can approve their own stock adjustment.**
3. **No store on any row**: HQ can't tell stores apart in stock, low-stock, burn-rate or
   adjustment lists.
4. **The Store Manager isn't told about low stock**, and neither is HQ.
5. **Two definitions of "low"** (minimum stock vs burn rate) that can disagree.
6. **A missed string check** in `receiveStock` (`"Consumable"` by name, `:170`).
7. **Burn-rate policy comes from the caller's tenant**, so an HQ view would apply HQ's
   policy to every store's items.

## Still to draft (after review)

Item detail with movement history (no per-item movement endpoint found yet), item master
(create / edit / retire, categories and types), the inventory dashboard tiles, HQ catalog
management (BE-21), then gates 1–5 and the list of screens no endpoint can serve.
