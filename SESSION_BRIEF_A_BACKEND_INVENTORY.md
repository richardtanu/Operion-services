# Session Brief A — Module 1 Procurement Inventory

**Send to:** the Claude Code session in the **backend repo** (`Operion-services`)
**Date:** 10 Aug 2026
**Reads:** `OPERION_UI_SCREEN_INVENTORY_PLAN.md` v1.4 · `screens/00-backend-state.md` ·
`OPERION_BE_CHANGE_QUEUE.md` · `CLAUDE.md` · `module/procurement/` source
**Produces:** `screens/01-procurement.md`

---

## Why this session and not the design project

Wireframing happens in a **separate project** that has no access to this repo. That
session cannot read `module/procurement/`, cannot read `screens/00-backend-state.md`, and
cannot verify a single claim about the backend.

That makes the split structural rather than a matter of discipline:

- **This session** produces the screen inventory. It is the only place `[C]` claims can be
  made, because it is the only place with the source.
- **The design session** receives the finished inventory and draws from it. It asserts
  nothing about the backend.

Everything below belongs to this session. Do not draw anything.

---

## Order of work

### 1. Run BE-09 first

`OPERION_BE_CHANGE_QUEUE.md` BE-09 — verify the reimbursement flow and PRA lifecycle
states. Roughly thirty minutes.

**Why it comes first.** `realisasis.payment_method` ∈ `{COMPANY_ACCOUNT,
PERSONAL_REIMBURSABLE}` and `reimbursement_status` ∈ `{NOT_APPLICABLE, PENDING,
REIMBURSED}` were found in a live CHECK-constraint dump (BE-01), not in code. Nobody has
confirmed that service code **writes** them. Same for
`purchase_request_authorizations.status` allowing `PARTIALLY_FULFILLED` and `FULFILLED`
when every narrative frames PRA as `ACTIVE`/`CANCELLED` only.

If nothing writes those fields, a reimbursement queue screen is an **orphan** and Gate 1
deletes it. Designing it first wastes the work; worse, it makes an aspirational column
look like a shipped feature.

BE-09 carries its own **do not**: if the flow is absent, that is a product decision about
whether it should exist — not a gap for you to close. Report and stop.

Row counts against the live DB settle "aspirational vs live" faster than reading code.

### 2. Read the source

`module/procurement/` — controllers first, then entities.

**Do not infer backend state from this brief, from
`OPERION_UI_SCREEN_INVENTORY_PLAN.md`, or from `CLAUDE.md`.** All three are `[D]`-tier
about anything they do not cite. v1.0 of the plan made four confident false claims by
reasoning from summaries; two of them described existing modules as missing.

List every endpoint with its response DTO fields. That list is what screens can actually
display, and it becomes the API contract baseline.

### 3. Draft 3–4 screen records, then STOP

Use the §3 record format in the plan, exactly. In particular:

- **`Actions × permission`, per action — not per screen.** A screen both Manager and Owner
  can open where only Owner may approve is the normal case, not an edge case.
- **`Failure behaviour`** — blocking error / manual retry / outbox-queued. Approval and
  authorization screens should be blocking; a queued approval that silently fails later is
  worse than one that refuses now.
- **Vocabulary flag** — any user-facing string containing `airsoft`, `unit`, `gun`, `part`.
  This feeds the `ServiceableUnit` rename.
- **Cost visibility** — every screen showing landed cost, unit cost, supplier pricing, or
  the six Realisasi components records which scope tiers see them.
- Tag every backend claim `[V]` / `[C]` / `[D]` / `[?]`. **`[D]` is not acceptable in a
  final record.**

**Then stop and show them.** This stop is deliberate and is the one instruction not to
optimise away. The first records establish the pattern for the module — drift caught at
record 3 costs minutes, caught at record 20 it costs a rewrite.

### 4. Complete the module

### 5. Run gates 1–5 from §4

Record findings inline, **including failures**. A gate with no findings recorded is a gate
that was not run.

**Gate 3 has a third pairing with no rule yet.** May the person who made a purchase mark
their own reimbursement paid? Neither documented SoD rule covers it, and it is the one
place in module 1 where money moves to an individual. Flag it — do not invent the answer.

**Gate 4's own premise is `[?]`.** `PartStockService` depends on a `tenantHierarchyService`
from commit `3894c5e`. If tenant hierarchy is being built by the parallel team, flat
tenancy is no longer true and the gate needs its row-level clause now. That is BE-10 — run
it if Gate 4 output would change materially either way.

### 6. List every screen whose Data read has no serving endpoint

**This is the module's highest-value output.** The procurement API already exists, so the
interesting result is not the screen list — it is the gaps. Known ones to expect: no goods
receipt list, no `GET /goods-receipts/{id}`, no PRA notification type (and note that
`notifications_reference_type_check` has no PRA value either, so that is two `ALTER
TABLE`s, not one). There will be others.

This list is the backend backlog, derived from real screens rather than guessed at.

---

## Constraints to carry into the records

All `[C]` unless marked. Cited in plan §5.1 and `screens/00-backend-state.md`. Each one is
something a screen gets wrong by default.

- **PRA has no Approve button.** Creation *is* the authorization act; `approvedBy` is set
  to the creator inside `create()`. No approve endpoint exists.
- **PRA is one-to-many over PRs.** `create()` takes `purchaseRequestIds: List<UUID>`. The
  PRA screen shows a *set* of originating PRs. Per-line ceilings span across them.
- **No header-level cap on PRA.** Per-line only. Whether to add a nullable one is an open
  product decision — designing this screen is likely how it gets settled. Note the
  interaction: ceilings spanning multiple PRs make "ten in-ceiling lines overrun a total
  nobody stated" harder for an Owner to hold in their head, not easier.
- **No stored `remaining` column.** `remainingQty`/`remainingValue` exist on the response
  DTO only, computed live from `APPROVED` `RealisasiItem` rows. Do not design a screen
  implying a stored balance.
- **Two status dimensions on Realisasi.** `RealisasiStatus` is `PENDING_APPROVAL /
  APPROVED / FAILED / SUPERSEDED`; `ESCALATED` belongs to a separate `VarianceStatus`
  (`WITHIN_CEILING / ESCALATED`). One status field misrepresents the state.
- **Correction is supersede, not edit.** `POST /realisasi/{id}/supersede` creates a new
  record via self-FK and flips the original to `SUPERSEDED`. Append-only, not an edit form.
- **Six separate cost inputs**, never one total: `subtotal`, `sellerDiscount`,
  `platformVoucher`, `shipping`, `insurance`, `serviceFee`. A single total produces roughly
  17% error on a real receipt.
- **`attachments` is a plain text/URL field**, no upload backing. URL input, not a file
  picker.
- **`realisasis.pra_id` is NOT NULL.** No ad-hoc purchase path exists at DB level. A "quick
  purchase" screen is impossible without a schema change.
- **Five quantity columns, none overwritten.** `suggested` (computed, not stored) →
  `requested` → `authorized` → `purchased` (**purchase UOM**) → `received` (**stock
  units**). `conversionFactor` is stored per Realisasi line, not as a `Part` constant. Any
  screen showing both purchased and received **must label the unit**.
- **Scope is an action gate, not a data filter.** Repositories filter by tenant, not scope,
  so a Supervisor can currently read landed cost through the API. Blueprint §2.1 says they
  should not. Every cost field gets a scope-visibility note; those notes become BE-06.

---

## Working rules

- Where this brief, the plan, or `CLAUDE.md` disagrees with the code, **the code is
  right.** Record the disagreement in the module file rather than silently correcting
  either side.
- Do not fix things you find along the way. Flag them. BE-04 correctly flagged a
  pre-existing `PartStockServiceTest` failure without touching it — same discipline.
- "No changes required" means no work happened. It is not confirmation that a requirement
  was met.
- Do not wireframe, sketch, or describe visual layout. That is the design session's job and
  it starts after the gates.
