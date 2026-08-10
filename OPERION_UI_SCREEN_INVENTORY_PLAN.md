# Operion — UI Screen Inventory Plan

**Versi:** 1.2
**Tanggal:** 8 Agustus 2026 (v1.0), corrected 10 Agustus 2026 (v1.1, v1.2)
**Status:** Plan — belum dieksekusi
**Owner:** Blitz (product owner)

### Changelog — v1.2

Merge fix (v1.1 was edited from a stale copy of v1.0), plus the consequences of the
verification sweep that v1.1 recorded but did not yet act on:

- **Gate 4 rewritten.** Scope is an *action* gate, not a data filter — repositories scope
  by tenant, not scope (`00-backend-state.md` §G5). The old Gate 4 was unrunnable.
- **§1 connectivity** — outbox posture stated, with the K4 `[?]` caveat.
- **§5.1** — PRA/Realisasi rules written out, five quantity columns added, endpoint
  inventory added, three concrete gaps recorded.
- **§9** — output directory is `screens/`, not `inventory/`.
- **§10 added** — session prompt template for running a module.

### Changelog — v1.1

Backend state verification sweep (`screens/00-backend-state.md`, 10 Aug 2026, all
claims `[C]`-tier — read in source, not summarized) found several "Backend status"
claims below were stale. Corrected in place, marked `[C] 10 Aug`. See
`screens/00-backend-state.md` §J for full disagreement log. Nothing here was corrected
based on a summary — every change traces to a source-code citation in that file.

---

## 0. Purpose

Produce a complete screen inventory for Operion, organised **by module**, with **scope
validation gates** run continuously rather than as a single pass at the end.

Two outputs come out of this work:

1. **The RBAC matrix** — a mechanical read of the completed grid. This becomes the spec
   for the nullable `scope` column on `users`.
2. **The API contract draft** — the "Data read" column of each screen record is the
   raw material for blueprint §10 step 3 (realtime vs eventual sync contract).

Screen-by-screen design is the cheapest way to derive an API contract. You find out what
a screen needs to render in one round-trip, which tells you the shape of the response.
Designing endpoints first reliably produces endpoints that need three calls to paint one
screen.

### What this document is not

Not a design document. No layout, no visual design, no component decisions. This is an
inventory — what screens exist, who can open them, what they read, what they do.

---

## 1. Tech stack (settled — do not reopen)

| Layer | Choice | Notes |
|---|---|---|
| Backend | Java Spring Boot + PostgreSQL | Modular monolith |
| Repo | `richardtanu/Operion-services` | Private |
| Mobile (outlet app) | React Native + Expo (dev builds) | Outlet operator, scan loop |
| Web console | React + Vite | Owner / Principal / back-office |
| Shared layer | `packages/core` | API client, types, validation, design tokens |

### Why React Native over PWA

Outbox queue storage guarantees on iOS. Browser storage is subject to eviction, and the
outbox is the thing that must survive an app being backgrounded for a day. This was
decided against the PWA alternative deliberately — do not revisit without new evidence.

### Why `packages/core` excludes UI components

Mobile and web have genuinely different interaction models (scan loop vs. approval
console). Sharing types and validation is high-value and low-friction. Sharing components
forces both platforms toward the lowest common denominator. Share the contract, not the
chrome.

### Connectivity posture — SETTLED

**No Local Agent.** Cloud API only. Both clients talk directly to the cloud backend;
every write is synchronous against a single source of truth. There is no per-outlet
mini-backend, no local database, no eventual-sync reconciliation layer.

This supersedes blueprint §3 (sync model), which describes eventual sync for inventory,
operational, efficiency, and digital twin paths. **That table is obsolete** — all paths
are now realtime. Blueprint §3's principle that "outlet = source-of-truth for its own
stock" no longer holds; the cloud is the source of truth for everything.

The six data-model invariants (client-generated UUIDs, coordination-free barcode scheme,
dual timestamps, idempotency keys, `origin_node_id` + `revision`, clean module
boundaries) are **still worth keeping**. They cost little now and are what make a future
Local Agent reintroduction non-breaking if outlet count or connectivity ever forces it.

#### Offline tolerance is client-side only

Per DL-06, the RN app carries an **outbox queue** plus **pre-allocated barcode code
blocks**. There is no server-side sync layer and no conflict resolution — the client
holds writes and replays them; the server just receives them.

Backend-side evidence confirms the design: `BarcodeAllocationService.issue()` reserves a
`[rangeStart, rangeEnd]` block via a locked counter and creates no `PartInstance` rows
(`00-backend-state.md` §H5). That endpoint exists *only* to support offline code
generation.

> ⚠️ **`[?]` — the outbox itself is unverified.** It lives in the RN client repo, outside
> the backend sweep's scope (`00-backend-state.md` K4, §J #5). Confirm it exists and
> behaves as described before inventorying module 3. Everything below assumes it does.

**UI consequence:** scan screens need a **pending/queued state**. A scan that goes into
the outbox is not an error and must not render as one — the operator's job succeeded, the
transmission is just deferred. Screens writing through the outbox need three visual
states, not two: confirmed, queued, failed.

> ⚠️ **The barcode redemption path is deliberately not built** — offline-generated codes
> do not yet become real `PartInstance` rows on sync; the endpoint reserves number space
> only. Blocked on an unmeasured question: how often outlets actually lose connectivity.
> Inventory the scan screens as if redemption works, and flag every field that depends on
> it.

---

## 2. Vocabulary rename — runs in parallel

`AirsoftUnit` → **`ServiceableUnit`**

`Asset` was considered and rejected: it collides with accounting vocabulary in
Accurate/Jurnal, where "asset" means a depreciable fixed asset. Operion will eventually
talk to one of those ledgers. `ServiceableUnit` is clunkier but unambiguous.

**This runs in parallel with the screen inventory, not before it.** The rename is
mechanical, needs no product judgment, and is verifiable by compilation — good work to
hand to the dev team via Claude Code. The inventory needs Blitz specifically, because it
is role-scope and product judgment.

They only collide once UI *implementation* starts, which is weeks away.

**The inventory feeds the rename.** The `Vocabulary flag` column marks every user-facing
string containing domain vocabulary. A Java class name is a find-and-replace; a screen
titled "Airsoft Unit Detail" is a different problem — that string must become
tenant-configurable, because a repair shop tenant needs "Vehicle" and a rental tenant
needs "Equipment." You cannot design that taxonomy config well until you can see every
place the word surfaces.

---

## 3. The per-screen record

Every screen gets these fields. Scope fields are filled in **as you go** — they are what
make the gates in §4 possible, and retrofitting them is how the gates get skipped.

```markdown
### PROC-04 — PRA Authorization

**Purpose:** Owner authorizes per-line ceilings on an approved PR.
**Platform:** Web console
**Scope tiers:** Owner, Principal
**Functional roles:** PROCUREMENT, ADMIN
**Actions:**
  - View lines — Owner:view, Principal:view
  - Set line ceiling — Owner:approve
  - Reject — Owner:approve
**Data read:** PurchaseRequest, PurchaseRequestLine, PRA, PRALine, Part, Supplier
**Entry points:** PROC-03 (PR Approval Console), notification, procurement list
**Failure behaviour:** Blocking error, manual retry (all screens are online-only)
**Backend status:** [C] tables exist, zero API implementation
**Vocabulary flag:** No
**Deferred decisions:** —
```

### Field notes

- **Actions × permission, not screen × permission.** Approval buttons are where scope
  actually bites. A screen both Manager and Owner can open, where only Owner can approve,
  is the normal case — not an edge case.
- **Backend status** uses the provenance tiers (§6). Do not write `[C]` unless you have
  actually opened the source file.
- **Failure behaviour** replaces what would have been an "offline behaviour" field. The
  backend is cloud-only, but the RN app has a client-side outbox, so the question per
  screen is which of three paths applies: **blocking error** (screen cannot proceed),
  **manual retry** (error state with a retry affordance), or **outbox-queued** (write is
  held and replayed; screen shows a pending state and lets the operator continue).
  Approval and authorization screens should be blocking — a queued approval that silently
  fails later is worse than one that refuses now.
- **Cost visibility.** Any screen displaying landed cost, unit cost, supplier pricing, or
  the six Realisasi cost components must record which scope tiers see those fields. See
  Gate 4.
- **Deferred decisions** records blockers without letting them block the module.

---

## 4. Per-module gate

Run at the end of **each module**, before starting the next. 15–20 minutes once in
rhythm. Running these continuously rather than once at the end matters — the failures get
harder to fix the more screens accumulate around them.

### Gate 1 — Orphan check
Every screen has ≥1 entry point and ≥1 role×scope combination that can open it.
A screen with zero roles is deleted or justified in writing. No third option.

### Gate 2 — Dead-end check
Every screen either completes a task or leads somewhere. Terminal screens with no exit
are a navigation bug — cheaper to catch now than after the flow is built.

### Gate 3 — Segregation of duties check

Two rules. **They have different exemptions — do not collapse them.** Both are enforced
in service code, verified `[C]` 10 Aug (`00-backend-state.md` §D).

| Rule | Exemption | Enforced at |
|---|---|---|
| PRA approver ≠ Realisasi creator | **Yes** — OWNER/PRINCIPAL may do both; MANAGER does not qualify | `RealisasiService.requireSegregationFromPraApprover()` |
| Realisasi creator ≠ goods receipt receiver | **None** | `GoodsReceiptService.receiveAgainstRealisasi()` |

The first check reads the approver's **stored** scope from the `User` row referenced by
`pra.approvedBy` — a snapshot from PRA creation time, not the live request context. A
later promotion cannot retroactively legalize a past pairing.

**Screen consequence:** because both are enforced server-side, screens may offer the
action and surface the rejection. They do not need to pre-compute eligibility — but they
should explain the refusal in domain terms, not as a raw error.

⚠️ **Do not skip this on modules that feel non-financial.** Stock adjustment (module 2)
is an approval workflow with real fraud surface, scope-gated at OWNER.

### Gate 4 — Cost visibility check

**Rewritten in v1.2.** The original gate asked whether any tier sees data outside its
scope, and assumed the backend filtered rows by scope. It does not.

`[C]` 10 Aug (`00-backend-state.md` §G5): scope is enforced **only** at the service layer
via `ScopeContext.hasAtLeast(...)`, which gates *actions*. Repositories filter by
`TenantContext` — tenancy, not scope. A Supervisor's `GET /purchase-requests` returns the
same rows an Owner's does.

Two consequences:

1. **Row-level scope leak is not currently checkable**, because there is nothing to leak
   into — tenancy is flat, so tenant ≈ outlet, and cross-outlet data does not exist yet.
   Principal and Owner see the same rows today, differing only in approval rights. This
   gate gets a row-level clause when franchise/principal tenancy lands, not before.

2. **Cost visibility is checkable, and is a live requirement.** Blueprint §2.1 states a
   Supervisor has no access to financial reporting. Today a Supervisor can read landed
   cost, supplier pricing, and all six Realisasi cost components through the API. The
   code does not meet a requirement you already wrote down.

**The check:** for every screen, does it display cost data to a tier that should not see
it? Cost data means landed cost, unit cost, supplier pricing, and the six components
(`subtotal`, `sellerDiscount`, `platformVoucher`, `shipping`, `insurance`, `serviceFee`).

**Resolution direction (decided 10 Aug):** field-level redaction at the DTO layer below
OWNER scope — not repository-level row filtering. It targets the actual exposure, costs a
handful of DTO changes rather than every list endpoint, and defers row filtering until
the tiers mean something.

⚠️ **Hiding a field in the UI is not redaction.** The endpoint remains reachable. Every
screen this gate flags produces a DTO-layer backend task, not just a UI note.

### Gate 5 — Deferred-decision log
Any screen blocked on an unresolved input (burn rate target, notification engine) is
recorded in the module's deferred list. Record and move on; do not block the module.

---

## 5. Module order

| # | Module | Why here | Backend status |
|---|---|---|---|
| 1 | **Procurement** | Flow settled at DL-08, no open dependencies, two stale prototypes waiting | ~~Tables exist, zero API~~ **`[C]` 10 Aug: full API exists** — all 6 entities (`PurchaseRequest`, `PurchaseOrder`, `GoodsReceipt`, `PurchaseRequestAuthorization`, `Realisasi`, `Supplier`) have controllers with multiple endpoints each. See `00-backend-state.md` §A. |
| 2 | **Inventory & stock** | Realisasi lands stock here — natural next link | ~~Partial; adjustment approval is a known gap~~ **`[C]` 10 Aug: not a gap** — `StockAdjustment` has a full approval workflow (`PENDING/APPROVED/REJECTED`, approve/reject endpoints, scope-gated at OWNER). See `00-backend-state.md` §H3. |
| 3 | **Operational / scan loop** | Where burn rate surfaces | ~~Aggregate stock only — see §5.3~~ **`[C]` 10 Aug: per-instance tracking exists for consumables** — see corrected §5.3 below. Burn rate itself (`GET /burn-rate`) is fully implemented; see `00-backend-state.md` §F. |
| 4 | **Units & parts (digital twin)** | Most complete backend; mostly documenting what exists | Largely implemented — not independently re-verified in the 10 Aug sweep (out of scope; that sweep covered procurement + cross-cutting only) |
| 5 | **Analysis & reporting** | Depends on 3 and 4 producing data | ~~Depends on burn rate~~ **`[C]` 10 Aug: burn rate is implemented**, including the four-mode logic and `MaintenancePolicy` threshold fallback. One bug found in the sparepart gate — fixed, see `00-backend-state.md` §F5. |
| 6 | **Admin, tenancy, notifications** | Most gaps, least settled | ~~Notification engine does not exist~~ **`[C]` 10 Aug: it exists**, polling-based (`GET /notifications`, `/unread-count`, mark-read endpoints). See `00-backend-state.md` §H1–H2. |

### 5.1 Procurement — specific notes

Chain is **PR → PRA → Realisasi Pembelian** (DL-08). The old PR → PO → Invoice model in
blueprint §4 is **superseded** — the blueprint doc has not been updated.

**The API already exists.** All six entities have controllers (`00-backend-state.md` §A2).
This module is therefore a **gap-finding exercise**, not a design-into-vacuum: every
screen's "Data read" gets checked against a real endpoint, and the valuable output is
where no endpoint serves what a screen needs.

#### Existing endpoints — the contract baseline

| Resource | Endpoints |
|---|---|
| `/suppliers` | `POST`, `GET` |
| `/purchase-requests` | `POST`, `GET`, `GET /{id}`, `PATCH /{id}/approve`, `PATCH /{id}/reject`, `PATCH /{id}/cancel`, `GET /{id}/history` |
| `/purchase-orders` | `POST`, `GET`, `GET /{id}`, `PATCH /{id}/send`, `PATCH /{id}/cancel`, `GET /{id}/history` |
| `/purchase-authorizations` | `POST`, `GET`, `GET /{id}`, `PATCH /{id}/cancel`, `GET /{id}/history` |
| `/realisasi` | `POST`, `GET`, `GET /{id}`, `PATCH /{id}/approve`, `PATCH /{id}/reject`, `POST /{id}/supersede`, `GET /{id}/history` |
| `/goods-receipts` | `POST`, `GET /purchase-order/{poId}`, `GET /realisasi/{realisasiId}` |

#### Rules the screens must reflect

- **PRA creation *is* the authorization act.** There is no approve endpoint, verified
  `[C]` (§A3). `approvedBy` is set to the creator inside `create()` (§A4) — it is not a
  separate approver. **The PRA screen must have no Approve button.**
- PRA ceilings are **per-line** (`authorizedQty`, `maxValue` on
  `PurchaseRequestAuthorizationItem`). **No header-level cap exists** (§B2). Whether to
  add a nullable one is an **open decision** — per-line ceilings cannot express "spend at
  most Rp 2.1M on this authorization," and ten in-ceiling lines can overrun a total
  nobody stated. The PRA screen is where an Owner would feel that gap; designing it is
  probably how the decision gets made.
- Ceiling consumption is **computed live from APPROVED `RealisasiItem` rows only**
  (`PurchaseRequestAuthorizationService.approvedPurchasedQtyInStockUnits()`). No stored
  `remaining` column exists — `remainingQty`/`remainingValue` appear on the response DTO
  only (§B3). **Do not design a screen that implies a stored balance.**
- Failed Realisasi releases its hold **without restarting the PR**.
- **Partial fulfilment is the default case**, not an exception.
- **Correction is supersede, not edit** (§C3). `POST /realisasi/{id}/supersede` flips the
  original to `SUPERSEDED` and creates a new record via self-FK. Nothing is overwritten.
  This is an append-only correction screen, not an edit form.
- **Two status dimensions, not one** (§J #1). `RealisasiStatus` is
  `PENDING_APPROVAL / APPROVED / FAILED / SUPERSEDED`. `ESCALATED` belongs to a separate
  `VarianceStatus` enum (`WITHIN_CEILING / ESCALATED`), set alongside
  `status=PENDING_APPROVAL` when over ceiling. A screen showing one status field will
  misrepresent the state.
- **Six cost components, six inputs** (§C2). A single "total" field destroys landed-cost
  accuracy — roughly 17% error on a real receipt.
- **`attachments` is a plain text/URL field** with no upload backing, by design (§C5).
  The screen shows a URL input, not a file picker.

#### The five quantity columns

All stored, none overwritten. Each divergence is a signal, and every procurement screen
touches at least one.

| Quantity | Lives on | Unit |
|---|---|---|
| `suggested_qty` | computed on read by `BurnRateService` — **not stored** | stock |
| `requested_qty` | `PurchaseRequestItem.quantity` | stock |
| `authorized_qty` | `PurchaseRequestAuthorizationItem.authorizedQty` | stock |
| `purchased_qty` | `RealisasiItem.purchasedQty` | **purchase UOM** |
| `received_qty` | `GoodsReceiptItem.quantity` | **stock units** |

⚠️ `conversionFactor` is stored **per Realisasi line**, not as a `Part` constant (§E6).
Any screen showing both purchased and received quantities **must label the unit** — they
are in different UOMs and the mismatch is a live source of user error.

#### Gaps found by the 10 Aug sweep — inventory these

1. **No goods receipt list, no `GET /goods-receipts/{id}`.** Only by-PO and by-Realisasi
   lookups exist. A "recent receipts" screen is not currently servable.
2. **No PRA notification type.** `NotificationType` is `NEW_PURCHASE_REQUEST`,
   `PURCHASE_REQUEST_ORDERED`, `PART_END_OF_LIFE`, `LOW_STOCK`, `REALISASI_ESCALATED`
   (§H1). When a PRA is created, **nobody is told** — the person who must create the
   Realisasi gets no signal. This is a flow break in the middle of the happy path.
   ⚠️ Adding a value here hits `CLAUDE.md` rule 3 (`ddl-auto` does not alter CHECK
   constraints on existing tables) and `00-backend-state.md` I2 is still `[?]`. Resolve
   the constraint question before this becomes code.
3. **`retroPurchaseFlag` is written but never queried** (§C6). An Owner-facing filter is a
   *new* requirement, not an existing capability.
4. **Unverified:** does `PurchaseRequestAuthorization` hold an FK back to its
   `PurchaseRequest`? §B2's field list shows only `id`, `tenant`, `approvedBy`, `status`.
   If the FK is genuinely absent, PRA screens have no navigation back to the originating
   PR — the same defect blueprint §9 #3 flagged for `purchase_orders`. **Confirm before
   drawing PRA screens.**

Two existing prototypes (PR creation, PR approval console) are **stale** — drawn against
the old chain. Redrawing them is part of this module.

### 5.2 Burn rate screens (module 3) — designable now

The *shape* is settled and does not depend on the unverified numbers:
- Four modes: COMPUTED, MANUAL_RATE, MANUAL_LEVEL, NONE — all four confirmed
  implemented, `[C]` 10 Aug, see `00-backend-state.md` §F1, F6
- **NONE returns null days-of-cover, not zero** — the UI must render "no data," not "0
  days" — confirmed `[C]` 10 Aug, `00-backend-state.md` §F2
- ~~`preferredOrderQty` overrides the computed basket~~ **`[C]` 10 Aug: there is no
  `preferredOrderQty` field.** The code reuses `Part.reorderQuantity` (a pre-existing
  field) for this override. See `00-backend-state.md` §F4.
- **Spareparts and COMPUTED mode:** the spec's invariant that spareparts never receive a
  `COMPUTED` rate did not hold in code as of 10 Aug — `take()` timestamps applied to any
  part regardless of category, so a heavily-replaced sparepart could cross the
  thresholds. Fixed same day in `BurnRateService.java`. No screen impact, but worth
  knowing if you design around "spareparts are always MANUAL_LEVEL" — that is now true
  by construction, not by data volume. See `00-backend-state.md` §F5, §J #2.

What is unverified is seed values and window tuning — that is **config, not UI**.

⚠️ **Do not hardcode the 14-day days-of-cover target in any component.** It is pending
real-world verification and will change.

### 5.3 Scan loop (module 3) — corrected 10 Aug

~~No barcode item-instance entity exists for consumables — backend has aggregate stock
only. The existing scan loop prototype draws data the backend cannot currently
produce.~~

**`[C]` 10 Aug: this was wrong.** `PartInstance` exists specifically for the Consumable
category, with `POST /part-instances/scan-in`, `take`, and `exhaust` endpoints, each
timestamped (`takenAt`, etc.) per instance. Non-Consumable parts get instances
auto-generated on goods receipt instead of scan-in. See `00-backend-state.md` §H4.

The scan loop prototype's per-instance take/exhaust timestamps **are** producible from
the backend as it exists today — this is no longer a known constraint. One real gap
remains, unrelated to this correction: consumable `PartInstance` rows are never
auto-generated on receipt, so `POST /part-instances/scan-in` is a mandatory manual step
that nothing in the current receipt flow navigates the user to. That is a navigation gap
for this inventory to close, not a backend gap.

---

## 6. Provenance discipline

Planning documents have previously made confident false claims by reasoning from session
summaries rather than reading source code. v2.0 asserted the procurement module did not
exist when it had existed for days, and asserted two RBAC systems needed reconciling when
only one ever existed.

Tag every backend claim in this inventory:

| Tag | Meaning |
|---|---|
| `[V]` | Observed live — ran it, saw it work |
| `[C]` | Read in source — opened the file |
| `[D]` | Claimed in a summary — **not verified** |
| `[?]` | Unknown |

**A `[D]` claim is not evidence.** If a screen's design depends on a `[D]` claim, either
verify it to `[C]` or record it as a deferred decision.

**`screens/00-backend-state.md` is the `[C]` source of record** for procurement and
cross-cutting concerns as of 10 Aug 2026. Cite it by section rather than re-deriving.
Modules 2–6 need their own sweep before their screen records can claim `[C]` — the 10 Aug
sweep did not cover the digital twin, analytics, or tenancy modules.

Note what this process caught: four confident claims in v1.0 of this document were false,
and were only found because someone opened the source. Two of them (a "missing"
notification engine, a "missing" item-instance entity) would have produced screens
designed around capabilities that already existed.

---

## 7. Cross-module scope pass

After all six modules, walk **one full day per tier** through the finished grid:

- **Supervisor** — a shift, open to close
- **Manager** — a morning
- **Owner** — a week
- **Principal** — a month

You are checking whether the screens each person needs form a usable sequence, or whether
they are scattered across modules with no navigation connecting them.

This is fast because the per-module gates already removed the orphans. You are only
looking at flow.

**Module-first inventory hides the navigation problem** — that is its known weakness, and
this pass is the specific correction for it. Do not skip it.

---

## 8. Definition of done

- [ ] All six modules inventoried, every screen using the §3 record format
- [ ] All five gates passed per module, with findings recorded
- [ ] Cross-module scope pass complete, navigation gaps logged
- [ ] Every backend claim tagged `[V]`/`[C]`/`[D]`/`[?]`
- [ ] Vocabulary flag column complete → handed to the rename work
- [ ] RBAC matrix derived from the grid
- [ ] Deferred decisions consolidated into one list
- [ ] API contract draft extracted from the "Data read" columns

---

## 9. Working notes for Claude Code sessions

- This document is a **plan**, not a spec. The inventory output belongs in a separate
  file per module: `screens/01-procurement.md`, `screens/02-inventory-stock.md`, etc.
- **Directory is `screens/`, not `inventory/`** — deliberately. `inventory` is already a
  backend module name meaning stock control, and a root-level `inventory/` directory
  would read as backend code to every future session.
- **This plan is `[D]` about backend state except where marked `[C]`.** Claims carrying a
  `[C]` tag and an `00-backend-state.md` citation were read in source on 10 Aug.
  Everything else is summary. `CLAUDE.md` rule 1 applies to this file too.
- **Do not infer backend state from this document.** Open the source. See §6.
- Blueprint (`operionblueprint.pdf`, Draft 1, 10 Jul 2026) is **partially superseded** —
  §4 procurement chain is wrong (now PR → PRA → Realisasi), and §3 sync model is obsolete
  (no Local Agent, no eventual sync, cloud API only). Treat the Decision Log as
  authoritative where they conflict. The blueprint is overdue for a v2 rewrite.
- Blitz prefers tradeoff explanations alongside decisions, not directives.

---

## 10. Session prompt template

Paste this at the start of a Claude Code session, filling in the module. One module per
session — do not batch.

```
Module: [N] — [name]
Plan: OPERION_UI_SCREEN_INVENTORY_PLAN.md (read §3 record format, §4 gates, §5.[N])
Backend facts: screens/00-backend-state.md — [C]-tier, cite by section
Output: screens/[NN]-[name].md

Before writing any screen record:
1. Read the module source under module/[name]/ — controllers first, then entities.
2. List every endpoint with its response DTO fields. This is what screens can display.
3. Do NOT infer backend state from the plan or from CLAUDE.md. Open the source.
   Where they disagree with the code, the code wins — record the disagreement.

Then:
4. Draft 3-4 screen records in the §3 format. STOP and show them before continuing.
5. After I confirm the format, complete the module.
6. Run gates 1-5 from §4. Record findings inline, including failures.
7. List every screen whose Data read has no serving endpoint. This is the
   module's real output.

Rules:
- Tag every backend claim [V]/[C]/[D]/[?]. [D] is not acceptable in a final record.
- Actions get permissions, not screens.
- Flag every user-facing string containing domain vocabulary
  (airsoft, unit, gun, part) for the ServiceableUnit rename.
- Cost fields (landed cost, unit cost, supplier price, the six Realisasi
  components) get an explicit scope-visibility note. See Gate 4.
```

**Why step 4 stops.** The first three records establish the pattern for the module. If
the format drifts, catching it at record 3 costs minutes; catching it at record 20 costs
a rewrite.

**Why step 7 exists.** With the procurement API already built, screens that no endpoint
can serve are the highest-value output of this whole exercise. They are the backend
backlog, derived from real screens rather than guessed at.

---

## 11. Before starting module 1

- [ ] Resolve `00-backend-state.md` **I2** — `\d+ notifications` against live Postgres.
      Blocks the PRA notification type (§5.1 gap 2).
- [ ] Confirm whether `PurchaseRequestAuthorization` has a `purchaseRequest` FK
      (§5.1 gap 4).
- [ ] Runtime-verify the 10 Aug `BurnRateService` sparepart fix — a sparepart with enough
      takes to cross the thresholds should report `MANUAL_LEVEL`, not `COMPUTED`. A clean
      compile does not verify this (`CLAUDE.md` rule 7).
- [ ] Consider replacing the `getCategory().getName().equals("Consumable")` string check
      with a flag or enum on the category entity. It is duplicated across three services
      and fails silently if a category is renamed or localized — and per-tenant
      configurable taxonomies are already planned.
- [ ] Create `screens/` and commit `00-backend-state.md` into it.
