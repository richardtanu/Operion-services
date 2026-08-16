# Session Brief B — Module 1 Procurement Wireframes

**Send to:** the design project (separate Pencil project, no access to this repo)
**Date:** 11 Aug 2026
**Reads:** Only this document. Nothing else in the backend repo is reachable from here.
**Produces:** Wireframes for the 8 procurement screens below (PROC-01…08)

---

## Why this session and not the backend repo

Per `OPERION_UI_SCREEN_INVENTORY_PLAN.md` §2.4 (two-project split): wireframing happens
in a project with no access to `Operion-services`. That project cannot read
`module/procurement/`, cannot read `screens/00-backend-state.md`, and cannot verify a
single claim about the backend on its own. The split is structural, not a matter of
discipline:

- **The backend repo session** produced the screen inventory below. It is the only place
  `[C]` (confirmed-by-reading-source) claims could be made, because it is the only place
  with the source.
- **This session** receives the finished inventory and draws from it. It asserts nothing
  new about the backend.

Everything you need to draw all 8 screens is in this document. It is a direct copy of
`screens/01-procurement.md` (backend repo, module 1, complete as of 11 Aug 2026) — same
`[C]`-tier evidence, same file:line citations, nothing summarized down for this handoff.

---

## Rules of engagement — read before drawing anything

- **Every backend claim below is `[C]`** — read from source, not summarized. Treat it as
  trustworthy; you have no way to independently verify it and shouldn't need to.
- **If a screen record is insufficient to draw from** — a field you need isn't listed, a
  data shape is ambiguous — **stop and report**. Do not invent a plausible-sounding field,
  endpoint, or rule to fill the gap. An invented rule that turns out wrong is more
  expensive to unwind than a paused wireframe.
- **Do not invent scope/role restrictions a record says don't exist.** Several screens
  here have *literally no backend scope gating* — PRA creation, Realisasi creation,
  goods receipt, the entire Purchase Order path. That is a real, evidenced finding (see
  Gate 3/4 below), not a documentation gap for you to quietly patch with an assumed
  "Owner only" rule. Draw it as open, and let the deferred-decisions list (below) carry
  the flag forward to the product owner.
- **No Approve button on PRA (PROC-03).** Creation *is* the authorization act.
- **Realisasi correction is supersede, not edit (PROC-04).** `POST /{id}/supersede`
  creates a new record and flips the original to `SUPERSEDED` — append-only. Design a
  correction flow, not an edit form.
- **Six separate cost inputs on Realisasi creation, never one total field** (PROC-04) —
  collapsing them produces a real ~17% costing error on receipts with discounts/fees.
- **Two status dimensions on Realisasi** (PROC-04): `status` (`PENDING_APPROVAL/
  APPROVED/FAILED/SUPERSEDED`) and `varianceStatus` (`WITHIN_CEILING/ESCALATED`) are
  independent fields. One status badge misrepresents the state — show both.
- **Four states on PRA** (PROC-03): `ACTIVE/PARTIALLY_FULFILLED/FULFILLED/CANCELLED`,
  not a binary active/cancelled toggle.
- **Do not add a "mark as reimbursed" button anywhere** (PROC-04, PROC-08) — confirmed
  orphan action, no backing endpoint exists. The view is real; that one action is not.
- **Label units wherever purchased and received quantities appear together**
  (PROC-04 → PROC-05): `purchasedQty` is in purchase UOM, `receivedQty` is in stock
  units, `conversionFactor` converts between them. Same-looking numbers, different units.
- **Cost fields render redacted (blank/masked) for SUPERVISOR and MANAGER, real values
  for OWNER and PRINCIPAL** — see the cost-visibility table below. This matches what the
  backend actually does today for every screen except PROC-07 (Purchase Order), where
  the backend hasn't shipped the fix yet (open task, BE-11). **Design PROC-07 with the
  same OWNER+-only rule as every other cost screen anyway** — draw the intended state,
  not today's unfixed exposure.

---

## Cost-visibility summary (Gate 4, run across the whole module)

| Screen | Cost fields | Redacted below OWNER? |
|---|---|---|
| PROC-01 (PR Create) | None | N/A |
| PROC-02 (PR Approval) | None | N/A |
| PROC-03 (PRA Authorization) | `maxValue`, `purchasedValue`, `remainingValue` | Yes |
| PROC-04 (Realisasi) | Six components, `totalCost`, `actualUnitPrice`, `allocatedLandedCost` | Yes |
| PROC-05 (Goods Receipt) | None on this DTO (cost surfaces via PartInstance) | Yes, indirectly |
| PROC-06 (Supplier) | None | N/A |
| PROC-07 (Purchase Order) | `totalAmount`, `price` | **Not yet on the backend (BE-11 open) — design as if redacted anyway** |
| PROC-08 (Reimbursement Queue) | Reuses PROC-04's fields | Yes, inherited |

---

## The eight screens

### PROC-01 — Purchase Request Create

**Purpose:** Requester creates a new PR for one or more parts.
**Platform:** Web console
**Scope tiers:** None enforced. `[C]` `PurchaseRequestService.create()`
(`PurchaseRequestService.java:56-97`) has no `ScopeContext` check at all — any
authenticated tenant user can create a PR.
**Functional roles:** None enforced anywhere in this module. `[C]` grepped
`module/procurement/` for `@PreAuthorize`, `Role.PROCUREMENT`, `Role.ADMIN` — zero
matches. If the product intends "only PROCUREMENT/ADMIN can create a PR," that is not
backend-enforced today — the screen would be the only guard, or this is a gap for a
backend task.
**Actions:**
  - Create PR — any authenticated user, no scope/role gate
**Data read:** `Part` (for the item picker — no dedicated "browsable parts for PR"
endpoint found in this module; likely reads from `module/part`, out of this sweep's
scope)
**Entry points:** Procurement list / dashboard (not yet inventoried)
**Failure behaviour:** Blocking error, manual retry — PR creation has no outbox path.
**Backend status:** `[C]` `POST /purchase-requests` exists.
Response: `PurchaseRequestResponse{id, status, requestedById, requestedByName,
purchaseOrderId, items[{id, partId, partName, quantity}], createdAt}`.
Status starts `PENDING` (`PurchaseRequestStatus`: `PENDING/APPROVED/REJECTED/ORDERED
/AUTHORIZED/CANCELLED` — six values, not two).
**Vocabulary flag:** No.
**Cost visibility:** N/A — PR carries no cost fields (`quantity` only).
**Deferred decisions:**
  - Whether PR creation should be scope/role-restricted is an open product question.
  - Burn rate (`suggested_qty`, `GET /burn-rate`) is the natural companion data source
    for this screen's item picker — not yet cross-referenced into this record.

---

### PROC-02 — Purchase Request Approval Console

**Purpose:** Owner reviews PENDING PRs and approves, rejects, or cancels them.
**Platform:** Web console
**Scope tiers:** Approve/Reject — **OWNER or above only**. Cancel has **no scope
check** — any authenticated user can cancel a PENDING or APPROVED PR. This asymmetry
(approve/reject gated, cancel not) is real — draw it as-is, don't smooth it over.
**Functional roles:** None enforced.
**Actions:**
  - View list / detail / history — any authenticated user : view
  - Approve — Owner : approve
  - Reject — Owner : approve
  - Cancel — any authenticated user : action (not scope-gated)
**Data read:** `PurchaseRequestResponse` (list + detail), `StatusHistoryResponse`
**Entry points:** Procurement list; notification on `NEW_PURCHASE_REQUEST` (fires on PR
creation)
**Failure behaviour:** Blocking error, manual retry. Approval/rejection should not be
outbox-queued — a queued approval that fails silently later is worse than a refusal now.
**Backend status:** `[C]` `PATCH /purchase-requests/{id}/approve`, `/{id}/reject` (body:
reason), `/{id}/cancel`, `GET /{id}/history` all exist.
**Vocabulary flag:** No.
**Cost visibility:** N/A — no cost fields on this DTO.
**Deferred decisions:**
  - The cancel/approve scope asymmetry — flag for the product owner, don't resolve it in
    the wireframe.

---

### PROC-03 — PRA Authorization

**Purpose:** Authorize per-line ceilings (qty + value) against one or more
already-APPROVED PRs. Creation *is* the authorization act — no separate approve step.
**Platform:** Web console
**Scope tiers:** **None enforced at all.** `create()` and `cancel()` are both open to
any authenticated tenant user.
**Functional roles:** None enforced.
**Actions:**
  - Create (= authorize) — any authenticated user : action. **No Approve button** —
    `approvedBy` is set to the creator inside `create()`, not a separate approver role.
  - Cancel — any authenticated user : action
  - View list / detail / history — any authenticated user : view
**Data read:** `PurchaseRequestAuthorizationResponse{id, status, approvedById,
approvedByName, approvedByScope, items[{id, partId, partName, authorizedQty, maxValue,
purchasedQty, purchasedValue, remainingQty, remainingValue}], purchaseRequestIds[],
createdAt}`. **PRA is one-to-many over PRs** — the screen shows a *set* of originating
PRs, not one. `remainingQty`/`remainingValue` are response-computed only, never
stored — do not design as if there's a stored balance.
**Status values:** Four real, reachable states — `ACTIVE`, `PARTIALLY_FULFILLED`,
`FULFILLED`, `CANCELLED` — recomputed live after every Realisasi transition that
touches this PRA. Needs a four-state badge.
**Entry points:** PROC-02 (once PRs are APPROVED); procurement list
**Failure behaviour:** Blocking error, manual retry. A financial-ceiling action —
should not be outbox-queued.
**Backend status:** `[C]` `POST /purchase-authorizations`, `GET`, `GET /{id}`,
`PATCH /{id}/cancel`, `GET /{id}/history` all exist.
**Vocabulary flag:** No.
**Cost visibility:** Yes — `maxValue`, `purchasedValue`, `remainingValue`. Redacted
below OWNER (backend fix shipped).
**Deferred decisions:**
  - No header-level ceiling exists (per-line only) — whether to add one is an open
    product decision this screen is likely to surface concretely once drawn.
  - The complete absence of scope/role gating on create/cancel — flag, don't invent a
    plausible-sounding restriction.

---

### PROC-04 — Realisasi Entry & Review

**Purpose:** Record a purchase that already happened against an active PRA line —
landed-cost entry, not a purchase order. Also handles the escalated-approval and
correction (supersede) paths.
**Platform:** Web console
**Scope tiers:** Create/Reject/Supersede — **none enforced**. Approve (escalated
only) — **OWNER or above**. Create instead enforces a **segregation-of-duties** check:
PRA approver ≠ Realisasi creator, OWNER/PRINCIPAL exempt.
**Functional roles:** None enforced.
**Actions:**
  - Create — any authenticated user, subject to the SoD check : action
  - Approve (escalated only, i.e. over-ceiling) — Owner : approve. Below-ceiling
    Realisasi auto-passes to `APPROVED` at creation — no action needed.
  - Reject (mark FAILED) — any authenticated user : action
  - Supersede (correction) — any authenticated user : action. **Not an edit screen** —
    flips the original to `SUPERSEDED`, creates a new record. Append-only.
**Data read:** `RealisasiResponse{id, purchaseRequestAuthorizationId, status,
varianceStatus, externalOrderRef, channel, supplierId, supplierName, paymentRef,
paymentMethod, reimbursementStatus, purchasedAt, subtotal, sellerDiscount,
platformVoucher, shipping, insurance, serviceFee, totalCost, attachments,
retroPurchaseFlag, createdById, createdByName, approvedById, approvedByName,
supersedesId, items[{id, purchaseRequestAuthorizationItemId, partId, partName,
purchaseUom, conversionFactor, purchasedQty, actualUnitPrice, allocatedLandedCost}],
createdAt}`.
**Two status dimensions, not one:** `status` ∈ `PENDING_APPROVAL/APPROVED/FAILED
/SUPERSEDED`; `varianceStatus` ∈ `WITHIN_CEILING/ESCALATED` is separate, set alongside
`status=PENDING_APPROVAL` when over ceiling.
**`realisasis.pra_id` is NOT NULL at the DB level** — there is no ad-hoc/quick-purchase
path without a PRA. Do not design a "quick purchase" screen against this endpoint.
**Reimbursement:** `reimbursementStatus` is real data (`PENDING` set at creation when
`paymentMethod=PERSONAL_REIMBURSABLE`), but `REIMBURSED` is never reachable — no
endpoint transitions it. A reimbursement queue view has real data to show; a "mark as
reimbursed" action on this screen would be an orphan — **do not add that button.**
**Five quantity columns terminate here:** `purchasedQty` on this screen is in
**purchase UOM**; the next screen down the chain (goods receipt) converts to **stock
units** — label the unit or the two numbers read as the same thing when they aren't.
**Entry points:** PROC-03 (once ACTIVE/PARTIALLY_FULFILLED); notification on
`REALISASI_ESCALATED`
**Failure behaviour:** Blocking error, manual retry for all actions — financial
records, should not queue silently.
**Backend status:** `[C]` `POST /realisasi`, `PATCH /{id}/approve` (escalated only),
`PATCH /{id}/reject`, `POST /{id}/supersede`, `GET`, `GET /{id}`, `GET /{id}/history`
all exist.
**Vocabulary flag:** No.
**Cost visibility:** Yes — the single highest-value cost screen in the module. All six
components plus `totalCost` and per-item `allocatedLandedCost`, redacted below OWNER
(backend fix shipped). Six separate inputs on create, never one total — collapsing them
produces ~17% error on a real receipt. `attachments` is a plain text/URL field, no
upload backing — URL input, not a file picker.
**Deferred decisions:**
  - Reimbursement completion — flagged above, not resolved here.

---

### PROC-05 — Goods Receipt

**Purpose:** Record physical receipt of parts against either a Purchase Order
(franchise leg) or a Realisasi (direct purchase) — exactly one, never both.
**Platform:** Web console (outlet back-office, not the mobile scan loop — this is a
paperwork/reconciliation step, not a barcode action)
**Scope tiers:** **None enforced anywhere.** Any authenticated tenant user can record
a receipt against either path.
**Functional roles:** None enforced.
**Actions:**
  - Create (receive) — any authenticated user : action, subject to the DL-11 check below
  - View by Purchase Order / by Realisasi — any authenticated user : view
**Data read:** `GoodsReceiptResponse{id, purchaseOrderId, poNumber, realisasiId,
externalOrderRef, receivedById, receivedByName, receivedDate, notes,
items[{id, partId, partName, realisasiItemId, quantity}]}`. `purchaseOrderId` XOR
`realisasiId` is set, never both — enforced server-side.
**Segregation of duties — real, and asymmetric between the two paths.** The Realisasi
path enforces DL-11 unconditionally: receiver must differ from the Realisasi's creator,
**no exemption**. **The Purchase Order path has no such check at all** — whoever
created or sent the PO may also receive against it. This asymmetry is real, not a gap
this record invents.
**Attachment gate (Realisasi path only):** receiving is blocked if the Realisasi has no
attachment on file — the marketplace receipt document must exist first. No equivalent
check on the PO path (no `attachments` field on `PurchaseOrder` at all).
**Side effects on save, not just a record:** stock is adjusted and, for non-Consumable
parts, `PartInstance` rows are auto-generated — **with per-unit landed cost stamped**
on the Realisasi path, but **no landed cost at all on the PO path** (POs carry no
itemized cost breakdown to allocate from). A screen showing landed cost after a
PO-sourced receipt should expect it to be empty, not treat that as a bug.
**Entry points:** PROC-03 → PROC-04 (Realisasi APPROVED) for the direct-purchase path;
a Purchase Order detail screen (PROC-07) for the franchise-leg path.
**Failure behaviour:** Blocking error, manual retry. Not outbox-queued — a back-office
console action with immediate stock side-effects, not a scan-loop step.
**Backend status:** `[C]` `POST /goods-receipts`, `GET /goods-receipts/purchase-order
/{poId}`, `GET /goods-receipts/realisasi/{realisasiId}` all exist. **No list endpoint
across all receipts and no `GET /goods-receipts/{id}`** — a "recent receipts" screen
spanning both paths is not servable today (backend task open, not blocking this
wireframe — draw the by-PO and by-Realisasi views, hold off on a cross-path list view
until that endpoint exists).
**Vocabulary flag:** No.
**Cost visibility:** No cost fields on this DTO directly — the landed-cost consequence
surfaces on the PartInstance side, already redacted below OWNER.
**Deferred decisions:**
  - No notification fires when a receipt is recorded, on either path — worth naming
    since every other screen in this chain has a notification tie-in and this one
    doesn't.
  - The PO-path segregation-of-duties gap above — flag, don't assume it should mirror
    the Realisasi path without being asked.

---

### PROC-06 — Supplier Management

**Purpose:** Create and list suppliers, shared across the whole tenant hierarchy.
**Platform:** Web console
**Scope tiers:** **None enforced** — but not open to everyone either: create and list
are both gated to the **root tenant only**. A non-root outlet gets an error on create,
and the list always shows the *root* tenant's suppliers regardless of which tenant the
caller belongs to. This is a **tenancy-hierarchy gate, not a scope gate**.
**Functional roles:** None enforced.
**Actions:**
  - Create — any authenticated user **at the root tenant** : action
  - View list — any authenticated user, sees the **root tenant's** suppliers
    regardless of caller's own tenant : view
**Data read:** `SupplierResponse{id, name, contact, createdAt}`. Deliberately thin —
no address, no payment terms, no tax ID.
**Entry points:** Not identified within this module — likely a standalone
settings/admin screen. **Propose a reasonable entry point in the wireframe, but label
it as a proposal, not confirmed navigation** — actual placement is a cross-module
decision.
**Failure behaviour:** Blocking error, manual retry.
**Backend status:** `[C]` `POST /suppliers`, `GET /suppliers` exist. **No update, no
delete, no `GET /{id}`** — a supplier once created cannot be edited or deactivated
through the API today. Don't design edit/deactivate controls against endpoints that
don't exist.
**Vocabulary flag:** No.
**Cost visibility:** N/A — no cost fields on this DTO.
**Deferred decisions:**
  - Whether a non-root outlet should be able to *view* suppliers at all (currently:
    yes, silently, only the root's list) versus being blocked entirely — flag, this is
    an unconsidered side effect, not a designed behaviour.
  - No edit/deactivate path — flag if the product needs one.

---

### PROC-07 — Purchase Order (Franchise Leg)

**Purpose:** Outlet-to-center ordering — the legacy/parallel path alongside
PRA/Realisasi. Not superseded, still live and fully wired.
**Platform:** Web console
**Scope tiers:** **None enforced anywhere.** Any authenticated user can create, send,
or cancel a PO.
**Functional roles:** None enforced.
**Actions:**
  - Create (as DRAFT, optionally from approved PRs) — any authenticated user : action
  - Send to supplier (DRAFT → ORDERED) — any authenticated user : action
  - Cancel — any authenticated user : action
  - View list / detail / history — any authenticated user : view
**Data read:** `PurchaseOrderResponse{id, poNumber, status, supplierId, supplierName,
orderDate, totalAmount, items[{id, partId, partName, quantity, price}],
purchaseRequestIds[], createdAt}`. Status: `DRAFT/ORDERED/PARTIALLY_RECEIVED/RECEIVED
/CANCELLED` — five values.
**Cost visibility — see the rules-of-engagement note above.** `totalAmount` and
per-item `price` are not yet redacted on the backend (open task) — **design this
screen with the same OWNER+-only cost visibility as every other cost screen anyway.**
**Entry points:** PROC-02 (once PRs are APPROVED) — converts approved PRs into a PO.
Also standalone (supplier optional at creation, for center's own restocking) — not
gated on any PR at all.
**Failure behaviour:** Blocking error, manual retry.
**Backend status:** `[C]` `POST /purchase-orders`, `GET`, `GET /{id}`,
`PATCH /{id}/send`, `PATCH /{id}/cancel`, `GET /{id}/history` all exist — a list and
detail screen are both fully servable.
**Vocabulary flag:** No.
**Deferred decisions:**
  - No scope gating at all on this path, including "send to supplier" (arguably as
    consequential as PRA authorization) — flag, don't assume Owner-only was intended.

---

### PROC-08 — Reimbursement Queue

**Purpose:** Surface Realisasi records paid with personal funds
(`paymentMethod=PERSONAL_REIMBURSABLE`) awaiting reimbursement, so the person owed
money gets paid back. **Not one of the originally-scoped screens** — added because a
prior verification sweep found the underlying data is real, and skipping a screen
nobody had counted would defeat the point of that finding.
**Platform:** Web console
**Scope tiers:** N/A for viewing (no read-side scope filter exists in this module).
**No scope tier can perform the core action** — see Backend status.
**Functional roles:** None enforced.
**Actions:**
  - View queue (filter `reimbursementStatus=PENDING`) — any authenticated user : view.
    **Client-side filter, not a backend one** — the list endpoint takes no filter
    parameter today; filter the full Realisasi list on the client. Not a blocker.
  - Mark as reimbursed — **orphan action, no backing endpoint.**
    `ReimbursementStatus.REIMBURSED` is declared and never set anywhere. No endpoint,
    no service method, no scheduled job. **Do not add this button** — it would have
    nowhere to go. Whether to build the endpoint is a product decision, separate from
    this wireframe.
**Data read:** Same `RealisasiResponse` as PROC-04, filtered to
`paymentMethod=PERSONAL_REIMBURSABLE` and `reimbursementStatus=PENDING` — no new DTO.
**`reimbursedTo`/`reimbursedAt` exist on the backend entity but are not exposed on the
response DTO at all** — a screen wanting to show "who this is owed to" beyond the
creator (already visible) would need a backend change, not just a query. Design around
what's actually in `RealisasiResponse` today.
**Entry points:** Not identified — likely a filtered view reached from a procurement
dashboard or PROC-04's list. **Propose a reasonable entry point, label it as a
proposal**, same caveat as PROC-06.
**Failure behaviour:** N/A (view-only screen; the one action it might have doesn't
exist).
**Backend status:** `[C]` No dedicated endpoint or DTO — reuses `GET /realisasi`
entirely, client-filtered. This screen has real data to show but zero purpose-built
backend support beyond what PROC-04 already provides.
**Vocabulary flag:** No.
**Cost visibility:** Same as PROC-04 — already redacted below OWNER.
**Deferred decisions:**
  - Whether to build the reimbursement-completion endpoint — product decision, not
    this brief's to make.
  - Whether a purchaser may mark their own reimbursement paid — unanswerable until the
    action above exists; don't design a segregation-of-duties control for an action
    that isn't there yet.

---

## Deferred / open product decisions — flag these, do not resolve them in the wireframe

1. PR creation's scope/role restriction — open product question (PROC-01).
2. PR cancel/approve scope asymmetry (PROC-02).
3. PRA header-level cap — open product decision; this screen is likely to surface it
   concretely once drawn (PROC-03).
4. PRA/Realisasi complete absence of scope gating on create/cancel (PROC-03, PROC-04).
5. Reimbursement completion + its segregation-of-duties question — blocked on a product
   decision (PROC-04, PROC-08).
6. PO-path goods-receipt segregation-of-duties gap — no creator≠receiver rule exists
   (PROC-05).
7. Non-root outlet's silent read of the root's supplier list (PROC-06).
8. PO cost-visibility gap — backend task already logged, not yours to fix, but the
   screen should still be drawn cost-safe per the rules-of-engagement note (PROC-07).
9. No scope gating anywhere on the PO path, including "send to supplier" (PROC-07).
10. `reimbursedTo`/`reimbursedAt` missing from the Realisasi response — a backend gap
    once the mark-reimbursed decision is made, not before (PROC-08).

If any of these turn out to materially change a screen's layout once you're drawing it
(most likely #3, the PRA header cap — see the record's own note that designing the
screen is probably how that decision gets made), that's expected and fine. Record the
resulting shape as a proposal, not a confirmed requirement, and note which open decision
it depends on.

---

## Working notes

- Stop and report rather than guess, on any record that turns out insufficient once you
  start drawing — same discipline the backend-repo sessions used producing this
  document.
- Every claim above is `[C]`, cited by file:line in the original `screens/01-procurement
  .md` in the backend repo. You don't need those citations to draw, but if a claim seems
  to contradict itself or another part of this document, say so — don't silently pick
  one version.
- This module's most consequential shared fact: **almost no screen in this module has
  backend scope or role gating.** Only PR approve/reject and Realisasi escalated-approve
  are Owner-gated; everything else — PRA creation/cancellation, Realisasi creation/
  reject/supersede, all of goods receipt, all of Purchase Order — is open to any
  authenticated user today. This is not something to design around by inventing gates;
  draw the screens as open, and let the deferred-decisions list carry it to the product
  owner as a real, evidenced finding.
