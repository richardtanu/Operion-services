# Operion — UI Screen Inventory Plan

**Versi:** 1.1
**Tanggal:** 8 Agustus 2026 (v1.0), corrected 10 Agustus 2026 (v1.1)
**Status:** Plan — belum dieksekusi
**Owner:** Blitz (product owner)

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

#### ⚠️ Open question — retry behaviour on request failure

Cloud-only settles the architecture but not this: **an operator scans a barcode, the
connection drops mid-request. What happens?**

Two options:
- **Hard fail** — error state, manual retry button. Simplest. Every failed scan is
  visible to the operator and must be redone by hand.
- **Small in-app retry queue** — the request is held and retried silently. Still an
  outbox, but a far smaller one than the Local Agent implied.

This must be answered before the module 3 scan loop screens can be inventoried, because
it determines whether scan screens need a pending/queued state at all.

> **Note:** the React Native (over PWA) decision was justified by iOS outbox storage
> guarantees. If the answer is "hard fail, no queue," that rationale no longer applies.
> The stack choice stands regardless — Expo dev builds are settled and switching now
> costs more than it saves — but the recorded *reason* would be wrong, and a future
> session should not build on it.

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
- **Failure behaviour** replaces what would have been an "offline behaviour" field. Every
  screen is online-only under cloud-only architecture, so the interesting question is not
  *whether* it works offline but what the user sees when a call fails — a blocking error,
  a retry affordance, or a queued state. Pending the §1 open question, default to
  "blocking error, manual retry" and flag screens where that is operationally painful.
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
No single role can both create and approve the same document, except through the two
documented exemption patterns.

⚠️ **Do not skip this on modules that feel non-financial.** Stock adjustment (module 2)
is an approval workflow with real fraud surface. Procurement is module 1, so the
precedent gets set early either way.

### Gate 4 — Scope leak check
Does any tier see data from outside its scope? The classic failure is a Supervisor seeing
a cross-outlet aggregate on a dashboard tile.

Principal is **aggregation-layer-only** per blueprint §2.1 — no direct query into
per-outlet detail. That boundary must hold in the UI, not just in the service layer.

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

Rules the screens must reflect:
- PRA ceilings are **per-line**, no header cap
- Ceiling consumption derived from **APPROVED Realisasi only** — no stored remaining column
- Failed Realisasi releases its hold **without restarting the PR**
- **Partial fulfilment is the default case**, not an exception

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
  file per module (`inventory/01-procurement.md`, etc.).
- **Do not infer backend state from this document.** Open the source. See §6.
- Blueprint (`operionblueprint.pdf`, Draft 1, 10 Jul 2026) is **partially superseded** —
  §4 procurement chain is wrong (now PR → PRA → Realisasi), and §3 sync model is obsolete
  (no Local Agent, no eventual sync, cloud API only). Treat the Decision Log as
  authoritative where they conflict. The blueprint is overdue for a v2 rewrite.
- Blitz prefers tradeoff explanations alongside decisions, not directives.
