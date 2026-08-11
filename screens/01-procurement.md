# Module 1 — Procurement Screen Inventory

**Date:** 10 Aug 2026
**Status:** DRAFT — first 4 records only, per
`SESSION_BRIEF_A_BACKEND_INVENTORY.md` step 3 ("draft 3-4 records, then STOP").
**Built against:** `OPERION_UI_SCREEN_INVENTORY_PLAN.md` **v1.3** (the brief specifies
v1.4, which is not yet synced into this repo — see the note at the end of this file).
**Source read:** `module/procurement/controller/`, `module/procurement/dto/`,
`module/procurement/service/` — every controller in the module, response DTOs for every
field, and every scope/role check in the four services these screens touch. All backend
claims below are `[C]`, cited by file:line.

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

## Note on plan version and scope

Drafted against `OPERION_UI_SCREEN_INVENTORY_PLAN.md` **v1.3**. The session brief that
requested this file (`SESSION_BRIEF_A_BACKEND_INVENTORY.md`) specifies v1.4 and also
references `BE-10`, neither of which exist in this repo as of this draft. The §3 record
format and the four listed constraints were unchanged between the versions this session
has seen (v1.1→v1.3), so the risk of drafting against a stale format is judged low — but
this has not been confirmed against the actual v1.4 text. **If v1.4 changes the §3 record
format or the §4 gates, these four records may need rework before the module is
completed.**

**STOPPING HERE per the brief's step 3.** Not yet done: completing the remaining
module 1 screens (step 4), running gates 1-5 (step 5, including the two gates the brief
flags as needing fresh judgment — the reimbursement SoD pairing and Gate 4's tenant-
hierarchy premise), and the no-serving-endpoint gap list (step 6).
