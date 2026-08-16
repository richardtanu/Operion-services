# Module 1 — Procurement Screen Inventory

**Date:** 10 Aug 2026, completed 11 Aug 2026
**Status:** **COMPLETE** — 8 screens (PROC-01…08), gates 1-5 run, gap list produced.
**Built against:** `OPERION_UI_SCREEN_INVENTORY_PLAN.md` **v1.4** (see version note at
the end of this file).
**Source read:** `module/procurement/controller/`, `module/procurement/dto/`,
`module/procurement/service/` — every controller in the module (`PurchaseRequest`,
`PurchaseRequestAuthorization`, `Realisasi`, `GoodsReceipt`, `Supplier`,
`PurchaseOrder`), response DTOs for every field, and every scope/role check in all seven
services this module touches. All backend claims below are `[C]`, cited by file:line.

---

### PROC-01 — Purchase Request Create

**Purpose:** Requester creates a new PR for one or more parts.
**Platform:** Web console
**Scope tiers:** None enforced. `[C]` `PurchaseRequestService.create()`
(`PurchaseRequestService.java:56-97`) has no `ScopeContext` check at all — any
authenticated tenant user can create a PR.
**Functional roles:** None enforced anywhere in this module. `[C]` grepped
`module/procurement/` for `@PreAuthorize`, `Role.PROCUREMENT`, `Role.ADMIN` — zero
matches. The functional `Role` enum (`ADMIN/TECHNICIAN/PROCUREMENT/ACCOUNTING/OPERATOR`)
is not read anywhere in this module's service layer. If the product intends "only
PROCUREMENT/ADMIN can create a PR," that is not backend-enforced today — the screen
would be the only guard, or this is a gap for a BE task.
**Actions:**
  - Create PR — any authenticated user, no scope/role gate `[C]`
**Data read:** `Part` (for the item picker — no dedicated "browsable parts for PR" 
endpoint found in this module; likely reads from `module/part`, out of this sweep's scope)
**Entry points:** Procurement list / dashboard (not yet inventoried)
**Failure behaviour:** Blocking error, manual retry — PR creation is not a scan-loop
action and has no outbox path.
**Backend status:** `[C]` `POST /purchase-requests` exists.
Request: `CreatePurchaseRequestRequest` (not fully read this pass — item lines +
`requestedBy` confirmed via `PurchaseRequestService.java:57-68`).
Response: `PurchaseRequestResponse{id, status, requestedById, requestedByName,
purchaseOrderId, items[{id, partId, partName, quantity}], createdAt}`.
Status starts `PENDING` (`PurchaseRequestStatus`: `PENDING/APPROVED/REJECTED/ORDERED
/AUTHORIZED/CANCELLED` — six values, not two).
**Vocabulary flag:** No.
**Cost visibility:** N/A — PR carries no cost fields (`quantity` only).
**Deferred decisions:**
  - Whether PR creation should be scope/role-restricted is an open product question this
    screen surfaces, not something to assume from the entity name.
  - Burn rate (`suggested_qty`, `GET /burn-rate`) is the natural companion data source for
    this screen per plan §5.2 — not yet cross-referenced into this record's Data read;
    do so when this record leaves draft.

---

### PROC-02 — Purchase Request Approval Console

**Purpose:** Owner reviews PENDING PRs and approves, rejects, or cancels them.
**Platform:** Web console
**Scope tiers:** Approve/Reject — **OWNER or above only**. `[C]`
`PurchaseRequestService.approve()` line 102 and `.reject()` line 124 both call
`requireOwnerScope()` (line 199-203), which checks `ScopeContext.hasAtLeast(Scope.OWNER)`.
Cancel has **no scope check** `[C]` (`cancel()`, lines 144-162) — any authenticated user
can cancel a PENDING or APPROVED PR. This asymmetry (approve/reject gated, cancel not) is
real, not an oversight this record should silently smooth over.
**Functional roles:** None enforced (see PROC-01).
**Actions:**
  - View list / detail / history — any authenticated user : view
  - Approve — Owner : approve
  - Reject — Owner : approve
  - Cancel — any authenticated user : action (not scope-gated)
**Data read:** `PurchaseRequestResponse` (list + detail), `StatusHistoryResponse` (via
`GET /purchase-requests/{id}/history`)
**Entry points:** Procurement list; notification on `NEW_PURCHASE_REQUEST` (`[C]`
`00-backend-state.md` §H1 — this notification type exists and fires on PR creation, per
`NotificationService.notifyNewPurchaseRequest()` called at `PurchaseRequestService.java:94`)
**Failure behaviour:** Blocking error, manual retry. Approval/rejection should not be
outbox-queued — a queued approval that fails silently later is worse than a refusal now
(plan §3 field notes).
**Backend status:** `[C]` `PATCH /purchase-requests/{id}/approve`,
`/{id}/reject` (body: `RejectRequest` — reason), `/{id}/cancel`, `GET /{id}/history` all
exist and were read directly.
**Vocabulary flag:** No.
**Cost visibility:** N/A — no cost fields on `PurchaseRequestResponse`.
**Deferred decisions:**
  - The cancel/approve scope asymmetry above — flag for Blitz, not this session's call to
    resolve.

---

### PROC-03 — PRA Authorization

**Purpose:** Authorize per-line ceilings (qty + value) against one or more already-APPROVED
PRs. Creation *is* the authorization act — there is no separate approve step.
**Platform:** Web console
**Scope tiers:** **None enforced at all.** `[C]` grepped
`PurchaseRequestAuthorizationService.java` for `ScopeContext`/`hasAtLeast` — zero matches
in the whole file. `create()` and `cancel()` are both open to any authenticated tenant
user. This directly contradicts the template example in plan §3 (`Scope tiers: Owner,
Principal`) — that example was illustrative, not derived from code, and this record does
not repeat that claim without evidence.
**Functional roles:** None enforced (see PROC-01).
**Actions:**
  - Create (= authorize) — any authenticated user : action. **No Approve button** — `[C]`
    confirmed no approve endpoint exists (`00-backend-state.md` §A3); `approvedBy` is set
    to the creator inside `create()` (`PurchaseRequestAuthorizationService.java:69,90`),
    not a separate approver role.
  - Cancel — any authenticated user : action
  - View list / detail / history — any authenticated user : view
**Data read:** `PurchaseRequestAuthorizationResponse{id, status, approvedById,
approvedByName, approvedByScope, items[{id, partId, partName, authorizedQty, maxValue,
purchasedQty, purchasedValue, remainingQty, remainingValue}], purchaseRequestIds[],
createdAt}`. **PRA is one-to-many over PRs** — `create()` takes
`purchaseRequestIds: List<UUID>` (`CreatePurchaseRequestAuthorizationRequest.java:17`),
all validated `APPROVED` first; the screen shows a *set* of originating PRs, not one.
`remainingQty`/`remainingValue` are response-computed only, never stored (`00-backend-state.md`
§B3) — do not design as if there's a stored balance.
**Status values:** `[C]` (BE-09 finding, `00-backend-state.md` §C8) — four real, reachable
states: `ACTIVE`, `PARTIALLY_FULFILLED`, `FULFILLED`, `CANCELLED`, recomputed live by
`PurchaseRequestAuthorizationService.refreshStatus()` after every Realisasi transition
that touches this PRA. Needs a four-state badge, not a binary active/cancelled one.
**Entry points:** PROC-02 (PR Approval Console, once PRs are APPROVED); procurement list
**Failure behaviour:** Blocking error, manual retry. Authorization is a financial-ceiling
action — should not be outbox-queued even though nothing currently forces that choice.
**Backend status:** `[C]` `POST /purchase-authorizations`, `GET`, `GET /{id}`,
`PATCH /{id}/cancel`, `GET /{id}/history` all exist and were read directly.
**Vocabulary flag:** No.
**Cost visibility:** **Yes — `maxValue`, `purchasedValue`, `remainingValue` are cost
fields.** Per Gate 4 (`00-backend-state.md` §G5): scope is enforced only at the service
layer for *actions*; there is no read-side scope filter, so **every scope tier currently
sees these values through `GET /purchase-authorizations`**, including Supervisor.
Blueprint §2.1 says Supervisor should have no financial-reporting access. This screen is
exactly the kind of DTO Gate 4 flags for BE-06 (DTO-level redaction below OWNER).
**Deferred decisions:**
  - No header-level ceiling exists (per-line only) — whether to add one is an open
    product decision this screen is likely to surface concretely once drawn (plan §5.1).
  - The complete absence of scope/role gating on create/cancel — flag, don't invent a
    plausible-sounding restriction.

---

### PROC-04 — Realisasi Entry & Review

**Purpose:** Record a purchase that already happened against an active PRA line —
landed-cost entry, not a purchase order. Also handles the escalated-approval and
correction (supersede) paths.
**Platform:** Web console
**Scope tiers:** Create/Reject/Supersede — **none enforced**. Approve (escalated only) —
**OWNER or above**. `[C]` `RealisasiService.approveEscalated()` (line 215-236) calls
`requireOwnerScope()` (line 397-401); `create()` (56-212), `reject()` (238-261), and
`supersede()` (263-...) have no `ScopeContext` check. `create()` does enforce a
**segregation-of-duties** check instead (`requireSegregationFromPraApprover()`,
`00-backend-state.md` §D1-D2: PRA approver ≠ Realisasi creator, OWNER/PRINCIPAL exempt).
**Functional roles:** None enforced.
**Actions:**
  - Create — any authenticated user, subject to SoD check : action
  - Approve (escalated only, i.e. over-ceiling) — Owner : approve. Below-ceiling
    Realisasi auto-passes to `APPROVED` at creation (no action needed).
  - Reject (mark FAILED) — any authenticated user : action
  - Supersede (correction) — any authenticated user : action. **This is not an edit
    screen** — `POST /{id}/supersede` flips the original to `SUPERSEDED` and creates a
    new record via self-FK (`00-backend-state.md` §C3). Design as append-only.
**Data read:** `RealisasiResponse{id, purchaseRequestAuthorizationId, status,
varianceStatus, externalOrderRef, channel, supplierId, supplierName, paymentRef,
paymentMethod, reimbursementStatus, purchasedAt, subtotal, sellerDiscount,
platformVoucher, shipping, insurance, serviceFee, totalCost, attachments,
retroPurchaseFlag, createdById, createdByName, approvedById, approvedByName,
supersedesId, items[{id, purchaseRequestAuthorizationItemId, partId, partName,
purchaseUom, conversionFactor, purchasedQty, actualUnitPrice, allocatedLandedCost}],
createdAt}`.
**Two status dimensions, not one** (`00-backend-state.md` §J #1): `status` ∈
`PENDING_APPROVAL/APPROVED/FAILED/SUPERSEDED`; `varianceStatus` ∈
`WITHIN_CEILING/ESCALATED` is separate, set alongside `status=PENDING_APPROVAL` when over
ceiling. One status field on this screen misrepresents the state — needs both.
**`realisasis.pra_id` is NOT NULL at the DB level** (BE-09 sweep context) — there is no
ad-hoc/quick-purchase path without a PRA. A "quick purchase" screen is impossible without
a schema change; do not design one against this endpoint.
**Reimbursement (BE-09 finding, `00-backend-state.md` §C7):** `reimbursementStatus`
is real data (`PENDING` set at creation when `paymentMethod=PERSONAL_REIMBURSABLE`), but
`REIMBURSED` is never reachable — no endpoint transitions it. **A reimbursement queue
view has real data to show; a "mark as reimbursed" action on this screen would be an
orphan (Gate 1) — do not add that button.** Whether to build the missing endpoint is
Blitz's call, not this record's.
**Five quantity columns terminate here:** `purchasedQty` on this screen is in
**purchase UOM**; `conversionFactor` is per-line, not a `Part` constant
(`RealisasiItemResponse`). The next screen down the chain (goods receipt) converts to
**stock units** — this screen must label the unit or it reads as the same number as
receipt quantity, which it is not.
**Entry points:** PROC-03 (PRA Authorization, once ACTIVE/PARTIALLY_FULFILLED);
notification on `REALISASI_ESCALATED` (`[C]` `00-backend-state.md` §H1, fires per
`RealisasiService.java:206` when `escalate == true`)
**Failure behaviour:** Blocking error, manual retry for create/approve/reject/supersede —
all financial-record actions, should not queue silently.
**Backend status:** `[C]` `POST /realisasi`, `PATCH /{id}/approve` (escalated only, throws
if not `PENDING_APPROVAL`), `PATCH /{id}/reject`, `POST /{id}/supersede`, `GET`,
`GET /{id}`, `GET /{id}/history` all exist and were read directly.
**Vocabulary flag:** No.
**Cost visibility:** **Yes — the single highest-value cost screen in the module.** All
six components (`subtotal`, `sellerDiscount`, `platformVoucher`, `shipping`, `insurance`,
`serviceFee`) plus `totalCost` and per-item `allocatedLandedCost` are on
`RealisasiResponse`/`RealisasiItemResponse` with **no scope-based read filter** — every
tier sees full landed cost today. Six separate inputs on create, never one total (~17%
error on a real receipt if collapsed — `00-backend-state.md` §C2). `attachments` is a
plain text/URL field, no upload backing — URL input, not a file picker.
**Deferred decisions:**
  - Reimbursement completion (BE-09) — flagged above, not resolved here.
  - Cost-field redaction below OWNER (BE-06) — this screen is the primary driver of that
    task; its field list should feed BE-06's scope directly once gates are run.

---

### PROC-05 — Goods Receipt

**Purpose:** Record physical receipt of parts against either a Purchase Order (franchise
leg) or a Realisasi (direct purchase) — exactly one, never both.
**Platform:** Web console (outlet back-office, not the mobile scan loop — this is a
paperwork/reconciliation step, not a barcode action)
**Scope tiers:** **None enforced anywhere in this service.** `[C]` grepped
`GoodsReceiptService.java` for `ScopeContext`/`hasAtLeast` — zero matches. Any
authenticated tenant user can record a receipt against either path.
**Functional roles:** None enforced (consistent with the rest of this module).
**Actions:**
  - Create (receive) — any authenticated user : action, subject to the DL-11 check below
  - View by Purchase Order / by Realisasi — any authenticated user : view
**Data read:** `GoodsReceiptResponse{id, purchaseOrderId, poNumber, realisasiId,
externalOrderRef, receivedById, receivedByName, receivedDate, notes,
items[{id, partId, partName, realisasiItemId, quantity}]}`. `purchaseOrderId` XOR
`realisasiId` is set, never both — enforced server-side (`GoodsReceiptService.java:80-84`,
throws if both or neither are provided).
**Segregation of duties — real, and asymmetric between the two paths.** `[C]` The
Realisasi path enforces DL-11 unconditionally: receiver must differ from the Realisasi's
creator, **no exemption** (`receiveAgainstRealisasi()`, lines 242-248 — matches the rule
as stated in the plan §4 Gate 3 table). **The Purchase Order path has no such check at
all** — `receiveAgainstPurchaseOrder()` (lines 104-218) never compares `receivedBy`
against anything. Whoever created or approved the PO may also receive against it. This
asymmetry is real, not a gap this record invents — same shape as PROC-02's
approve/cancel asymmetry.
**Attachment gate (Realisasi path only):** receiving is blocked if
`realisasi.attachments` is null/blank (lines 254-259) — the marketplace receipt document
must be on file before goods receipt can affect stock. No equivalent check on the PO
path (no `attachments` field on `PurchaseOrder` at all).
**Side effects on save, not just a record:** stock is adjusted (`PartStockService
.adjustStock`, `StockMovementType.PURCHASE`) and, for non-Consumable parts,
`PartInstance` rows are auto-generated (`PartInstanceService.generateInstances`) —
**with per-unit landed cost stamped from the Realisasi line** on that path
(specific-identification costing, decision #5), but **no landed cost at all on the PO
path** (`generateInstances` called without a cost argument — POs carry no itemized cost
breakdown to allocate from). A screen showing `PartInstance.landedCost` after a
PO-sourced receipt should expect `null`, not a bug.
**Entry points:** PROC-03 → PROC-04 (Realisasi APPROVED) for the direct-purchase path;
a not-yet-inventoried PO list/detail screen for the franchise-leg path (out of this
sweep — `PurchaseOrder` screens are drafted below in PROC-07, but no PO **list** screen
is inventoried here; see the gap list).
**Failure behaviour:** Blocking error, manual retry. Not outbox-queued — this is a
back-office console action with immediate stock-side-effects, not a scan-loop step.
**Backend status:** `[C]` `POST /goods-receipts`,
`GET /goods-receipts/purchase-order/{poId}`, `GET /goods-receipts/realisasi/{realisasiId}`
all exist and were read directly. **No `GET /goods-receipts` list and no
`GET /goods-receipts/{id}`** (BE-08, confirmed again here directly against the
controller) — a "recent receipts across all POs/Realisasi" screen is not servable today.
**Vocabulary flag:** No.
**Cost visibility:** No cost fields on `GoodsReceiptResponse` itself (`quantity` only) —
BE-06 did not need to touch this DTO. The landed-cost consequence of a receipt surfaces
on `PartInstanceResponse` instead, which BE-06 already redacts.
**Deferred decisions:**
  - No notification fires when a receipt is recorded, on either path — `[C]` grepped
    `module/notification/` for any reference to `GoodsReceipt`, zero matches. Not
    necessarily a gap (nobody asked for one), but worth naming since PROC-01 through
    PROC-04 all have a notification tie-in and this screen is the one link in the chain
    that doesn't.
  - The PO-path DL-11 gap above — flag for Blitz, don't assume it should mirror the
    Realisasi path without being asked.

---

### PROC-06 — Supplier Management

**Purpose:** Create and list suppliers, shared across the whole tenant hierarchy.
**Platform:** Web console
**Scope tiers:** **None enforced** — `[C]` grepped `SupplierService.java` for
`ScopeContext`/`hasAtLeast`, zero matches. **But not open to everyone either**: create
and list are both gated to the **root tenant only** via
`tenantHierarchyService.getRootTenantId()` (`SupplierService.java:31-37, 52`) — a
non-root outlet gets a `RuntimeException` ("Suppliers are managed centrally") on create,
and `getAll()` always queries suppliers under the *root* tenant id regardless of which
tenant the caller belongs to. This is a **tenancy-hierarchy gate, not a scope gate** —
the first concrete evidence in this module that the hierarchy BE-10.1 found live
(`Franchise HQ` → `Outlet 2`) is already load-bearing business logic, not just a dormant
capability.
**Functional roles:** None enforced.
**Actions:**
  - Create — any authenticated user **at the root tenant** : action
  - View list — any authenticated user, sees the **root tenant's** suppliers regardless
    of caller's own tenant : view
**Data read:** `SupplierResponse{id, name, contact, createdAt}`. Deliberately thin — no
address, no payment terms, no tax ID. If the product needs those, they're a schema
addition, not a UI gap.
**Entry points:** Not yet inventoried elsewhere in this module — suppliers are read by
`PurchaseOrder.supplier`/`Realisasi.supplier` but nothing in the four PROC-01…04 records
links here. Likely a standalone settings/admin screen.
**Failure behaviour:** Blocking error, manual retry.
**Backend status:** `[C]` `POST /suppliers`, `GET /suppliers` exist and were read
directly. **No update, no delete, no `GET /{id}`** — a supplier once created cannot be
edited or deactivated through the API today.
**Vocabulary flag:** No.
**Cost visibility:** N/A — no cost fields on this DTO.
**Deferred decisions:**
  - Whether a non-root outlet should be able to *view* suppliers (currently: yes, but
    only the root's list, silently — not its own) versus being blocked entirely is worth
    surfacing to Blitz; the current behaviour is a side effect of `getRootTenantId()`
    being used uniformly, not a considered product decision documented anywhere.
  - No edit/deactivate path — flag if the product needs one; not assumed here.

---

### PROC-07 — Purchase Order (Franchise Leg)

**Purpose:** Outlet-to-center ordering — the legacy/parallel path alongside
PRA/Realisasi, per DL-08 and `CLAUDE.md`'s "two genuinely different transactions" table.
Not superseded, still live and fully wired.
**Platform:** Web console
**Scope tiers:** **None enforced anywhere.** `[C]` grepped `PurchaseOrderService.java`
for `ScopeContext`/`hasAtLeast` — zero matches, unlike PROC-02/PROC-04's owner-gated
approval steps. Any authenticated user can create, send, or cancel a PO.
**Functional roles:** None enforced.
**Actions:**
  - Create (as DRAFT, optionally from approved PRs via `purchaseRequestIds`) — any
    authenticated user : action
  - Send to supplier (DRAFT → ORDERED) — any authenticated user : action
  - Cancel — any authenticated user : action
  - View list / detail / history — any authenticated user : view
**Data read:** `PurchaseOrderResponse{id, poNumber, status, supplierId, supplierName,
orderDate, totalAmount, items[{id, partId, partName, quantity, price}],
purchaseRequestIds[], createdAt}`. Status: `DRAFT/ORDERED/PARTIALLY_RECEIVED/RECEIVED
/CANCELLED` — five values.
**🔴 Cost visibility — real gap, not previously flagged by BE-06.** `totalAmount`
(header) and `price` (per item on `PurchaseOrderItemResponse`) are cost fields with
**no scope-based redaction** — `[C]` confirmed neither field name matched BE-06's grep
pass (`landedCost|unitCost|unitPrice|sellerDiscount|platformVoucher|serviceFee`), and
`PurchaseOrderService`'s mapper was never touched by that fix. Same exposure shape as
the Realisasi/PartInstance fields BE-06 already redacted — a Supervisor can read full PO
pricing today through `GET /purchase-orders`. **This needs a BE-06 follow-up task**, not
a screen-level workaround.
**Entry points:** PROC-02 (PR Approval Console, once PRs are APPROVED) — `create()`
accepts `purchaseRequestIds` to convert approved PRs into a PO. Also standalone
(`supplierId` optional at creation, "for center's own restocking" per
`CreatePurchaseOrderRequest`'s own comment) — not gated on any PR at all.
**Failure behaviour:** Blocking error, manual retry.
**Backend status:** `[C]` `POST /purchase-orders`, `GET`, `GET /{id}`,
`PATCH /{id}/send`, `PATCH /{id}/cancel`, `GET /{id}/history` all exist and were read
directly. **This resolves the plan §5.1 endpoint table's listed `/purchase-orders`
row** — a list screen is fully servable, unlike goods receipts.
**Vocabulary flag:** No.
**Deferred decisions:**
  - The cost-visibility gap above — new finding, feed into the BE-06 follow-up.
  - No scope gating at all on this path, including "send to supplier" (arguably as
    consequential as PRA authorization) — flag, don't assume Owner-only was intended.

---

### PROC-08 — Reimbursement Queue

**Purpose:** Surface Realisasi records paid with personal funds
(`paymentMethod=PERSONAL_REIMBURSABLE`) awaiting reimbursement, so the person owed money
gets paid back. **Not one of the four originally-scoped screens** — added per plan
§5.1's "Undocumented in every prior doc" #1 (BE-01/BE-09 findings), included here because
skipping a screen nobody counted would be exactly the mistake that section exists to
prevent.
**Platform:** Web console
**Scope tiers:** N/A for viewing (same as PROC-04, no read-side scope filter exists in
this module). **No scope tier can perform the core action** — see Backend status.
**Functional roles:** None enforced.
**Actions:**
  - View queue (filter `reimbursementStatus=PENDING`) — any authenticated user : view.
    **Client-side filter, not a backend one** — `[C]` `RealisasiService.getAll()`
    (line 286-296) takes no filter parameter; the full tenant-hierarchy Realisasi list
    would need to be filtered by `reimbursementStatus` on the client. Not a blocker, just
    not optimized — a dedicated `GET /realisasi?reimbursementStatus=PENDING` would scale
    better once the list is large, but nothing requires that today.
  - Mark as reimbursed — **orphan action, no backing endpoint.** `[C]` (BE-09,
    `00-backend-state.md` §C7): `ReimbursementStatus.REIMBURSED` is declared and never
    set anywhere in the codebase. No `markReimbursed` endpoint, no service method, no
    scheduled job exists. **Do not add this button** — Gate 1 would immediately flag it
    as dead. Whether to build the endpoint is a product decision (plan §12 Q2 is
    downstream of this — the SoD question "may the purchaser mark their own
    reimbursement paid?" is unanswerable while the action itself doesn't exist).
**Data read:** Same `RealisasiResponse` as PROC-04, filtered to
`paymentMethod=PERSONAL_REIMBURSABLE` and `reimbursementStatus=PENDING` — no new DTO
needed. `reimbursedTo`/`reimbursedAt` exist on the `Realisasi` entity (per
`Realisasi.java`) but **are not exposed on `RealisasiResponse` at all** — `[C]` grepped
the DTO, no such fields. A screen wanting to show "who this is owed to" beyond the
creator already visible via `createdByName` would need a DTO change, not just a query.
**Entry points:** Not yet inventoried — likely a filtered view reached from a
procurement dashboard or PROC-04's list, not a standalone nav item.
**Failure behaviour:** N/A (view-only screen; the one action it might have doesn't
exist).
**Backend status:** `[C]` No dedicated endpoint or DTO — reuses `GET /realisasi` and
`RealisasiResponse` entirely, client-filtered. **This is this module's clearest example
of a screen with real data to show but zero purpose-built backend support** — everything
it needs already exists as a side effect of PROC-04's endpoint, except the one action
(mark reimbursed) that doesn't exist at all.
**Vocabulary flag:** No.
**Cost visibility:** Same as PROC-04 — `Realisasi`'s six cost components, already
redacted below OWNER by BE-06. A reimbursement amount is arguably not "cost" in the same
sense (it's money owed to an employee, not a supplier), but it rides on the same
`subtotal`/`totalCost` fields today — no separate reimbursement-amount field exists to
consider separately.
**Deferred decisions:**
  - Whether to build `markReimbursed` — product decision, explicitly not this session's
    to make (BE-09's own instruction).
  - The purchaser-marks-own-reimbursement SoD question (plan §12 Q2) — blocked on the
    above, not answerable yet.
  - `reimbursedTo`/`reimbursedAt` missing from the response DTO — worth a BE task once
    the mark-reimbursed decision is made, not before (building the field before the
    action exists would be premature).

---

## Gates (§4)

Run once across all eight screens (PROC-01 through PROC-08), per plan §4's instruction to
run at the end of the module, not per-screen.

### Gate 1 — Orphan check

Every screen needs ≥1 entry point and ≥1 role×scope that can open it.

- PROC-01 through PROC-05: entry points identified inline (dashboard, prior-screen
  chaining, notifications). **Pass.**
- PROC-06 (Supplier): no entry point identified from within this module — likely reached
  from a settings/admin area outside procurement. **Flagged, not failed** — this module's
  sweep can't inventory a screen outside its own scope; recorded as a cross-module
  dependency for the §7 cross-module scope pass.
- PROC-07 (Purchase Order): entry points identified (PROC-02, standalone). **Pass.**
- PROC-08 (Reimbursement Queue): entry point not concretely identified (likely dashboard
  or PROC-04 list) — same flag as PROC-06, not a failure, a cross-module note.
- Every screen has at least one role×scope that can open it — this module enforces
  almost no scope/role gating at all (see Gate 3 below for why that's notable), so "can
  anyone open this" is trivially yes everywhere. **No screen deleted by this gate.**

### Gate 2 — Dead-end check

Every screen completes a task or leads somewhere.

- PROC-01 → PROC-02 (create leads to approval). PROC-02 → PROC-03 (approved PR leads to
  authorization). PROC-03 → PROC-04 (active PRA leads to Realisasi). PROC-04 → PROC-05
  (approved Realisasi leads to goods receipt). PROC-05 is terminal — receiving goods
  completes the chain, which is correct, not a dead end.
- PROC-06 (Supplier) is terminal by nature (a management screen, not a workflow step) —
  not a dead end, a different screen category.
- PROC-07 (Purchase Order) parallels PROC-02→05 on the franchise leg: create → send →
  (PROC-05 goods receipt against the PO). Not a dead end.
- PROC-08 (Reimbursement Queue) is currently a **structural dead end for its one
  action** — "mark as reimbursed" has nowhere to go because the endpoint doesn't exist.
  The *screen* isn't a dead end (it's informational, same category as PROC-06), but this
  is worth naming explicitly since Gate 1/2 together are what the plan says should catch
  exactly this shape of problem. **No screen deleted — the queue view itself completes
  its purpose (surfacing PENDING claims) even with the action orphaned.**

### Gate 3 — Segregation of duties check

Two documented rules, plus the reimbursement pairing this sweep was asked to give fresh
judgment on (plan §12 Q2).

| Rule | Exemption | Enforced at | Screen(s) |
|---|---|---|---|
| PRA approver ≠ Realisasi creator | Yes — Owner/Principal | `RealisasiService.requireSegregationFromPraApprover()` | PROC-04 |
| Realisasi creator ≠ goods receipt receiver | None | `GoodsReceiptService.receiveAgainstRealisasi()`, lines 242-248 | PROC-05 (Realisasi path only) |
| **New, this sweep:** PO creator/sender vs. PO goods receiver | **No rule exists at all** | Nowhere — `[C]` confirmed no check in `receiveAgainstPurchaseOrder()` | PROC-05 (PO path) |
| Reimbursement: purchaser marks own claim paid | **Unanswerable — action doesn't exist** | N/A | PROC-08 |

**Fresh judgment requested by the brief, delivered:** the PO-path SoD gap is a genuine,
newly-surfaced asymmetry (row 3) — this sweep does not invent a rule for it, per the
plan's "flag, don't fix" discipline, but it is now a named, evidenced finding rather than
an unnoticed absence. The reimbursement SoD question (row 4) remains correctly
unanswerable, not avoided — BE-09 already established why, and nothing in this module
sweep changes that.

### Gate 4 — Cost visibility check

Re-run with the full module in view, not just the 4 screens the original v1.3 draft
covered.

| Screen | Cost fields | Redacted below OWNER? |
|---|---|---|
| PROC-01 (PR Create) | None | N/A |
| PROC-02 (PR Approval) | None | N/A |
| PROC-03 (PRA Authorization) | `maxValue`, `purchasedValue`, `remainingValue` | **Yes** — BE-06 |
| PROC-04 (Realisasi) | Six components, `totalCost`, `actualUnitPrice`, `allocatedLandedCost` | **Yes** — BE-06 |
| PROC-05 (Goods Receipt) | None on this DTO (landed cost surfaces via PartInstance) | **Yes**, indirectly — BE-06 covers `PartInstanceResponse.landedCost` |
| PROC-06 (Supplier) | None | N/A |
| PROC-07 (Purchase Order) | `totalAmount`, `price` | **🔴 No — new gap, see PROC-07** |
| PROC-08 (Reimbursement Queue) | Reuses PROC-04's fields | **Yes** — inherits BE-06 |

**Net result of running this gate on the completed module: one real, previously-unfound
redaction gap** (PROC-07 / `PurchaseOrderResponse`). This is exactly the value the plan
describes Gate 4 as having — it wasn't runnable at all until BE-06 existed to check
against, and completing the module past the first 4 screens is what surfaced the one
DTO BE-06's original grep pass missed.

### Gate 5 — Deferred-decision log

Consolidated from every screen record above, not repeated per-screen:

1. PR creation's scope/role restriction — open product question (PROC-01).
2. PR cancel/approve scope asymmetry — flag for Blitz (PROC-02).
3. PRA header-level cap — open product decision, plan §5.1/§12 Q5 (PROC-03).
4. PRA/Realisasi complete absence of scope gating on create/cancel — flag (PROC-03).
5. Reimbursement completion + its SoD question — blocked on a product decision, not
   answerable by this sweep (PROC-04, PROC-08, plan §12 Q2).
6. PO-path DL-11 gap (no creator≠receiver check) — flag, don't assume symmetry was
   intended (PROC-05).
7. Non-root outlet's silent read of the root's supplier list — flag as an
   unconsidered side effect, not a designed behaviour (PROC-06).
8. PO cost-visibility gap — **new BE task**, not just a deferred decision (PROC-07).
9. No scope gating anywhere on the PO path, including "send to supplier" (PROC-07).
10. `reimbursedTo`/`reimbursedAt` missing from `RealisasiResponse` — DTO gap once the
    mark-reimbursed decision is made (PROC-08).

---

## Screens with no serving endpoint (module's real output, per plan §10 step 7)

**None.** Every one of the eight screen records above maps to endpoints that exist and
were read directly. This is a genuinely different result from what the plan's framing
(§5.1: "the interesting result is not the screen list — it is the gaps") anticipated,
and it's worth stating plainly rather than manufacturing a gap to match the expected
shape: **the procurement API's coverage is complete for every screen this sweep
identified.** The real output of this module turned out to be gaps of a different kind
than "missing endpoint" — a missing **list** endpoint (BE-08, PROC-05), a missing
**redaction** (PROC-07's new cost-visibility gap), and missing **scope/SoD enforcement**
(PROC-02, PROC-03, PROC-05's PO path, PROC-07) — all real, all evidenced, none of them
"no endpoint serves this data."

---

## Note on plan version and scope

Drafted against `OPERION_UI_SCREEN_INVENTORY_PLAN.md` **v1.4** — synced into this repo
11 Aug 2026 (see `OPERION_BE_CHANGE_QUEUE.md` and the plan's own v1.4 changelog). The
first four records (PROC-01…04) were originally drafted against v1.3; re-checked against
v1.4's changes (the reimbursement flow, PRA four-state lifecycle, and tenant-hierarchy
findings) and found already consistent — those records already cited the BE-09/BE-10
findings v1.4 formalizes, since this repo had them before v1.4 existed as a document.
No rework was needed.

**Module 1 complete.** All four brief steps done: source read (step 2), 3-4 records
drafted and format-checked (step 3), the remaining module completed (step 4), gates 1-5
run with findings recorded including failures (step 5), and the no-serving-endpoint list
produced (step 6) — which turned out to be empty, with the module's real findings
documented above instead.
