# Operion — Backend Change Queue

**Tanggal:** 10 Agustus 2026
**Source:** `screens/00-backend-state.md` (verification sweep, all `[C]`) +
`OPERION_UI_SCREEN_INVENTORY_PLAN.md` v1.2 (now v1.4 — this queue's §11 items and BE-09/
BE-10 are what moved it there)
**Status:** BE-01, BE-02, BE-03, BE-04, BE-05, BE-06, BE-09, BE-10 done. Flyway is live
(`ddl-auto=validate`, confirmed zero schema drift). BE-10's live check found real tenant
hierarchy (`Franchise HQ` → `Outlet 2`), which is why BE-06 (cost redaction) got done
immediately instead of waiting on module 1. **Module 1 (`screens/01-procurement.md`) is
now complete** — BE-07 and BE-08 are unblocked and ready to pick up; BE-11 (`PurchaseOrder`
cost redaction, same shape as BE-06) is a new gap module 1 surfaced. BE-07, BE-08, BE-11
open.

---

## How to use this file

Each task is self-contained. Do **one per session**. Do not batch BE-01…BE-04 into a
single change — BE-02 and BE-03 are investigations whose outcome may change the others.

Before starting any task:

1. **Open the source. Do not infer state from this file or from `CLAUDE.md`.**
   This queue is `[D]`-tier about anything it does not cite. Where it disagrees with the
   code, the code wins — record the disagreement, do not silently adjust either one.
2. Check whether the task is still needed. A parallel dev team works on this repo.
3. If the task turns out to be already done, say so and stop. Do not invent adjacent work.

### Already verified as DONE — do not rebuild

`[C]` 10 Aug. These appeared on older gap lists and are closed:

- Nullable `scope` column on `users`, JWT-carried, `ScopeContext` populated in
  `JwtAuthenticationFilter` — **exists** (`00-backend-state.md` §G1–G2)
- Full procurement API, all six entities with controllers — **exists** (§A1–A2)
- `StockAdjustment` approval workflow, scope-gated at OWNER — **exists** (§H3)
- Notification module, polling-based — **exists** (§H1–H2)
- `PartInstance` per-instance tracking for consumables — **exists** (§H4)
- Burn rate service, four modes, `MaintenancePolicy` fallback — **exists** (§F1–F6)

---

## BE-01 — Resolve I2: enum CHECK constraints on existing tables

**Status: DONE, 10 Aug 2026.**

**Priority:** Do first. Blocks BE-07.
**Type:** Investigation, no code change.
**Effort:** ~5 minutes.

### Why

`CLAUDE.md` rule 3: `ddl-auto=update` does **not** alter DB-level CHECK constraints on
tables that already exist. Adding a value to an enum used by an existing entity compiles
clean, starts clean, and fails at insert. This has already caused two bugs in this
project.

`00-backend-state.md` I2 was `[?]` — the sweep could not determine which CHECK
constraints exist, because the only schema artifact is a binary `pg_dump` and
`pg_restore` was not available.

### What was done

No `psql`/`pg_restore` in the environment, but the live instance at
`localhost:5432/operion` was reachable. Queried `information_schema.check_constraints`
directly over JDBC (the postgresql driver is already a project dependency — no new
tooling installed) instead of the `\d+` commands originally specified.

Full constraint list recorded in `00-backend-state.md` I2, now `[C]`. Headline findings:

- `notifications_type_check` and `notifications_reference_type_check` — exact allowed
  values captured; unblocks BE-07 with a known `ALTER TABLE` shape.
- `purchase_request_authorizations_status_check` allows `PARTIALLY_FULFILLED` and
  `FULFILLED` in addition to `ACTIVE`/`CANCELLED` — not previously documented anywhere.
  Not yet confirmed as reachable code paths.
- `realisasis` (table name is plural — tripped the first query attempt) has two
  additional CHECK-constrained columns not previously covered:
  `realisasis_payment_method_check` (`COMPANY_ACCOUNT`/`PERSONAL_REIMBURSABLE`) and
  `realisasis_reimbursement_status_check` (`NOT_APPLICABLE`/`PENDING`/`REIMBURSED`).

### Done when

`00-backend-state.md` I2 is updated from `[?]` to `[C]` with the constraint list, and
committed. **Met.**

---

## BE-02 — Confirm the PRA → PurchaseRequest link

**Status: DONE, 10 Aug 2026. Outcome: the queue's premise was wrong — no gap exists.**

**Priority:** Do early. Blocks PRA screen design in module 1.
**Type:** Investigation; may become a schema change.
**Effort:** ~10 minutes to verify.

### Why

`00-backend-state.md` §B2 lists `PurchaseRequestAuthorization` fields as `id`, `tenant`,
`approvedBy`, `status` — with no FK back to `PurchaseRequest`. That answer was given to a
question about header-level ceilings, so the list may simply be abbreviated.

### What was found

The FK exists — on the **inverse side**. `PurchaseRequest.java:43-45` has
`purchaseRequestAuthorization` (column `purchase_requests.pra_id`), set in
`PurchaseRequestAuthorizationService.create()` line 113. A PRA can authorize multiple PRs
at once (`create()` takes `purchaseRequestIds: List<UUID>`, all validated `APPROVED`
first).

Reverse lookup is a real, already-wired repository method —
`PurchaseRequestRepository.findByPurchaseRequestAuthorizationId(UUID)` — called inside
`PurchaseRequestAuthorizationService.map()` (lines 317-321) and exposed on **every** PRA
API response as `purchaseRequestIds`.

Confirmed both in source and against the live schema (`purchase_requests` has a `pra_id`
column; `purchase_request_authorizations` correctly has none).

### Done when

Either: FK confirmed present, `00-backend-state.md` §B2 corrected, done. **Met — no code
change needed.**

Or: FK confirmed absent → stop and report. **Not the outcome; this branch did not apply.**

---

## BE-03 — Runtime-verify the burn rate sparepart gate

**Status: DONE, 10 Aug 2026.**

**Priority:** Do before trusting the 10 Aug fix.
**Type:** Verification.
**Effort:** ~20 minutes.

### Why

The `isConsumable()` gate added to `BurnRateService` on 10 Aug (`00-backend-state.md` §F5,
§J #2) was verified with `mvnw compile` only.

`CLAUDE.md` rule 7 is explicit that a clean compile proves nothing about runtime — that
lesson came from a `@Lazy`/Lombok bug that compiled fine and failed at Spring startup.
The same reasoning applies here: a category check that compiles is not a category check
that fires.

### Task

1. Find or create a **sparepart** (non-Consumable category) with enough `PartInstance`
   rows carrying `takenAt` to cross both thresholds: `observationDays >=
   minObservationDays` (default 21) and `takes >= minEvents` (default 10).
2. Call `GET /burn-rate/{partId}` for it.
3. Assert `burnRateSource` is **`MANUAL_RATE` / `MANUAL_LEVEL` / `NONE`** — never
   `COMPUTED`.
4. Regression check: a Consumable part meeting the same thresholds **must** still return
   `COMPUTED`. The gate must not have broken the normal path.

### Note

Rule 6 applies: vary the inputs. A test where the sparepart has exactly the threshold
count and one where it has triple is more informative than either alone.

### Done when

Both assertions pass against a running instance, recorded in `00-backend-state.md` §F5 as
`[V]`. **Met.**

### What was done

`src/test/java/.../burnrate/service/BurnRateServiceSparepartGateTest.java`. Not an HTTP
call through auth — `@SpringBootTest` autowiring the real `BurnRateService` against the
real datasource, with fixtures created via the real `PartRepository`/
`PartInstanceRepository` inside one `@Transactional` test method (auto-rolled-back, so
nothing persists — safe to re-run, no manual cleanup needed).

Three cases, per rule 6 (vary the input, don't just hit the boundary once):
- Sparepart at exactly `minEvents`(10)/`minObservationDays`(21) → `burnRateSource !=
  COMPUTED`. Pass.
- Sparepart at triple that (30 takes, 25 days) → `burnRateSource != COMPUTED`. Pass.
- Consumable control, same threshold-crossing shape (15 takes, 25 days) → `burnRateSource
  == COMPUTED`, `observationDays=25`, `eventCount=15`. Pass — the gate doesn't break the
  normal path.

**Real finding, not a test artifact:** the first run (without `@Transactional`) threw
`LazyInitializationException` on `part.getCategory().getName()`. `Part.category` is a
lazy `@ManyToOne`; `BurnRateService` has no `@Transactional` of its own and relies on
Spring Boot's default `open-in-view=true` (confirmed unmodified in
`application.properties`) to keep a session open for the real `GET /burn-rate` request.
Not a production bug today, but `BurnRateService` cannot safely be called outside a web
request (batch job, another service) without its own session.

---

## BE-04 — Replace the `"Consumable"` string check

**Status: DONE, 10 Aug 2026.**

**Priority:** Do this week.
**Type:** Refactor. Behaviour-preserving.
**Effort:** ~30 minutes.

### Why

Three services currently identify consumables by comparing a **user-editable category
name** to a string literal:

```java
part.getCategory().getName().equals("Consumable")
```

Call sites: `PartInstanceService`, `GoodsReceiptService`, `BurnRateService` (added 10 Aug).

This fails **silently** if a tenant renames the category, localizes it ("Konsumabel"),
creates "Consumables" plural, or introduces their own taxonomy. No exception, no log —
consumables simply stop being treated as consumables. Burn rate degrades, instances stop
generating correctly, and nothing errors.

Per-tenant configurable taxonomies are already on the roadmap, which makes this a
scheduled failure rather than a hypothetical one.

It is the same shape as the `conversionFactor=1` bug: wrong behaviour that looks correct.

### Task

1. Add a stable discriminator to the category entity — a boolean `isConsumable`, or a
   `CategoryKind` enum. **Do not key off the display name.**
2. Replace all three call sites with a single shared helper.
3. Backfill existing rows so current behaviour is preserved exactly.
4. Add a test asserting that a category renamed away from `"Consumable"` still behaves as
   a consumable.

### Blocked-by check

If the discriminator is an **enum column on an existing table**, BE-01 applies — check for
a CHECK constraint and plan the migration in the same change. A boolean avoids this
entirely and is the safer choice under `ddl-auto=update`.

### Do not

Change any consumable/sparepart behaviour. This refactor must be a no-op on current data.
If it changes any output, something was already wrong — stop and report it.

### What was done

Chose the boolean (`PartCategory.consumable`, column `is_consumable`, nullable — avoids
BE-01's CHECK-constraint concern entirely, as the task itself suggested).

1. `PartCategory.java` — new `Boolean consumable` field. `Part.java` — new
   `isConsumable()` reading `category != null && Boolean.TRUE.equals(category
   .getConsumable())`. This is the one shared helper; all three call sites
   (`PartInstanceService` ×3, `GoodsReceiptService` ×2, `BurnRateService` ×1) now call
   `part.isConsumable()` instead of comparing `getCategory().getName()`. The old private
   `isConsumable(Part)` methods and the `CONSUMABLE_CATEGORY_NAME` constants are deleted,
   not left as dead code.
2. Column added and backfilled directly against the live DB (no `pg_restore` needed for
   this — it's a new column, not a constraint change, so `ALTER TABLE ADD COLUMN` +
   `UPDATE` was sufficient): `true` where `name = 'Consumable'`, `false` everywhere else.
   Verified: 3 rows true, 15 false, across all 3 tenants — matches the pre-refactor
   string-match behaviour exactly.
3. `CreatePartCategoryRequest`, `UpdatePartCategoryRequest`, `PartCategoryResponse` gained
   a `consumable` field, so the flag is settable through the API — a tenant renaming
   "Consumable" or adding a second consumable-like category now has a real way to say so,
   rather than the flag only ever being reachable by matching a hardcoded name.
   `PartCategoryService.seedDefaultCategories()` — the one place code still creates a
   "Consumable" category — sets the flag explicitly at creation time. That's now the only
   remaining place the string "Consumable" and the concept "is consumable" are linked;
   every other reader goes through the stored flag.
4. Test added: `PartTest.isConsumableFollowsTheStoredFlagNotTheDisplayName` — a category
   named "Konsumabel" with the flag set reports `isConsumable() == true`; a sibling test
   proves a category still literally named "Consumable" with the flag *unset* reports
   `false`. Together they prove the check no longer depends on the name at all, in either
   direction.

Verified with `mvnw compile` (clean) and `mvnw test` — every test this touched passes,
including the BE-03 fixture test re-run after the refactor (unaffected, since its
fixtures use real categories named "Consumable" with the flag true — see that test's own
Javadoc for why it doesn't distinguish pre- vs post-refactor behaviour, and why
`PartTest` is the one that does).

**Unrelated pre-existing failure found while running the full suite, not touched:**
`PartStockServiceTest.lowStockShouldReturnLowStockForPartsBelowMinimum` NPEs on an
unmocked `tenantHierarchyService` — `PartStockService` gained that dependency in an
earlier commit (`3894c5e`, not this session; confirmed via `git diff`/`git log` on that
file, which BE-04 never touched) and its Mockito test was never updated to mock it. Per
this queue's own rule 2 ("check whether the task is still needed... a parallel dev team
works on this repo") and rule 3 ("if already broken, say so and stop — do not invent
adjacent work"), this is flagged, not fixed, here.

---

## BE-05 — Flyway or Liquibase migration

**Priority:** High structurally. Do before BE-07.
**Type:** Infrastructure.
**Effort:** Half a day to a day.
**Status: DONE, 11 Aug 2026.** All four steps complete, all `[V]` against the live
instance. Confirmation was sought before step 3 specifically (`ddl-auto=validate`
changes boot behavior for anyone running against this DB, unlike BE-06's pure-code
change) — approved, then applied and verified. See "What was done" below.

### What was done

1. **Picked Flyway.** `flyway-core` + `flyway-database-postgresql` added to `pom.xml`
   (no explicit version — managed by `spring-boot-starter-parent` 3.5.14's dependency
   management; resolved to 11.7.2). Lighter fit than Liquibase for plain-SQL migrations
   on a modular monolith, per this task's original note.
2. **Baselined without a DDL dump.** The only existing schema artifact
   (`dump-operion-202605152101.sql`) is binary `pg_dump` custom format, and `pg_dump`/
   `pg_restore`/`psql` are still unavailable in this environment (same constraint BE-01
   and BE-10.1 worked around via direct JDBC). Rather than hand-reconstructing full DDL
   from `information_schema`, used Flyway's own answer to "adopt Flyway on a non-empty
   database": `spring.flyway.baseline-on-migrate=true` +
   `spring.flyway.baseline-version=1`. **Verified live**: booted the app with
   `ddl-auto` still `update` — `flyway_schema_history` table created, baseline recorded
   at V1, zero errors. Confirmed via full log, not assumed from a clean exit code.
   `db/migration` is currently empty (no migration files yet); the next schema change
   that needs one (candidates: BE-07's `NotificationType`/`NotificationReferenceType`
   values) will be `V2__...sql`.
3. **Hibernate/JPA confirmed compatible with Flyway active.** Same boot log:
   `EntityManagerFactory` initialized cleanly after Flyway's baseline ran, under the
   still-active `ddl-auto=update`. No schema-validation errors, no exceptions from
   either engine touching the same DB in sequence.

4. **Switched `ddl-auto` to `validate`, confirmed after go-ahead.** `application.properties:9`.
   **Verified live, twice:** `./mvnw spring-boot:run` (alternate port, to avoid an
   unrelated already-running instance on 8080) booted clean — `Started
   OperionApplication in 9.659 seconds`, zero `SchemaManagementException` /
   schema-validation errors in the full log, not just a non-zero exit code check. Then
   the full test suite re-run under `validate` (every `@SpringBootTest` now boots the
   full context through Flyway + `validate`, not just `update`): 17 tests, same single
   pre-existing `PartStockServiceTest` failure as before this task touched anything, zero
   new failures. **Current entity mappings have zero drift from the live schema** — this
   result is itself useful signal, not just a pass/fail gate: it means nothing here
   quietly relied on `ddl-auto=update`'s silent tolerance.

### What this means going forward

Any future column/constraint change to an existing table now needs a real
`db/migration/V*.sql` file — `validate` will refuse to boot on drift instead of
`update`'s silent no-op. This is what makes BE-07 (adding a `NotificationType` +
`NotificationReferenceType` value to the pre-existing `notifications` table) safe to do
by the book: write `V2__add_pra_notification_types.sql` with the `ALTER TABLE ... DROP
CONSTRAINT ... ADD CONSTRAINT ...` shape BE-01 already worked out, rather than hand-editing
the live DB and hoping `ddl-auto` doesn't need to touch it.

### Why

`00-backend-state.md` I1 `[C]`: still `ddl-auto=update` (`application.properties:9`), no
Flyway or Liquibase in `pom.xml`, no `db/migration` directory.

This has already caused **two** production-class bugs (`CLAUDE.md` rule 3). BE-06 and
BE-07 both require schema changes to existing tables — precisely the case where
`ddl-auto` fails silently. Doing those first invites a third instance of the same bug.

### Task

1. Pick Flyway or Liquibase. Flyway is the lighter fit for a modular monolith with
   plain-SQL migrations.
2. Baseline the current schema against the live instance.
3. Switch `ddl-auto` to `validate`.
4. Verify: a clean boot against the existing DB must produce no schema drift errors.

### Note

The existing `dump-operion-202605152101.sql` is a **binary `pg_dump` custom-format file**,
not plain SQL. Baselining will need `pg_restore` or a fresh `pg_dump --format=plain`
against the live instance. (10 Aug: `pg_restore`/`pg_dump`/`psql` CLI tools remain
unavailable in this environment — BE-01 worked around this via direct JDBC queries, but
that approach does not substitute for a proper Flyway baseline. This task still needs DB
tooling.)

---

## BE-06 — DTO-level cost redaction below OWNER scope

**Status: DONE, 11 Aug 2026.**

**Priority:** Was "after module 1 inventory" — done early instead, once BE-10.1 showed
the exposure is live between real tenants (`Franchise HQ` → `Outlet 2`), not deferred.
**Type:** Requirement gap.
**Blocked by:** ~~Module 1 Gate 4 output; BE-05 if any schema change is involved.~~ Turned
out to need neither — no schema change (DTO/service layer only), and Gate 4's field list
was already fully specified in this task's own "Cost fields at issue" line below;
module 1 wasn't needed to enumerate them.

### Why

`00-backend-state.md` §G5 `[C]`: scope is enforced only at the service layer via
`ScopeContext.hasAtLeast(...)`, which gates **actions**. Repositories filter by
`TenantContext` — tenancy, not scope. A Supervisor's `GET /purchase-requests` returns the
same rows an Owner's does, including cost fields.

Blueprint §2.1 states a Supervisor has **no access to financial reporting**. The code does
not meet a requirement already written down.

Cost fields at issue: landed cost, unit cost, supplier pricing, and the six Realisasi
components (`subtotal`, `sellerDiscount`, `platformVoucher`, `shipping`, `insurance`,
`serviceFee`).

### Why not row-level scope filtering

~~Tenancy is flat — tenant ≈ outlet. There is no cross-outlet data for a Supervisor to
leak into.~~ **Superseded by BE-10.1, 10 Aug: this premise is false.** Live query
confirms `Franchise HQ` → `Outlet 2` is a real parent-child tenant pair today — tenancy
is not uniformly flat, and `getEffectiveTenantIds()`-based aggregation already crosses
that boundary with zero scope-based redaction. Field-level DTO redaction (this task) is
still the right fix — it targets the actual exposure regardless of whether it's
row-level or field-level — but this section's original justification for deferring row-
level filtering no longer holds for any tenant with children. Re-scope this task's
urgency accordingly; it is not waiting on a hypothetical.

### Why this waits

*Which* fields on *which* DTOs is exactly what module 1's Gate 4 produces. Building it
first means guessing at the field list.

### Task (after module 1)

1. ~~Take the Gate 4 findings — every screen showing cost data to a sub-OWNER tier.~~
   Used this task's own field list instead (see "Cost fields at issue" above) — grepped
   for every DTO actually carrying those fields, found exactly two:
   `RealisasiResponse`/`RealisasiItemResponse` (`module/procurement/dto/`) and
   `PartInstanceResponse` (`module/partinstance/dto/`). No dedicated "supplier pricing"
   field exists separately from `RealisasiItem.actualUnitPrice`.
2. Redact those fields at the DTO mapping layer, keyed on `ScopeContext`. **Done** — both
   services already had exactly one private `map()` method each
   (`RealisasiService.map()`, `PartInstanceService.map()`), so one redaction point per
   service covers every public method that returns the DTO (`getAll`, `getById`,
   `create`, `approveEscalated`, `reject`, `supersede` for Realisasi;
   `scanIn`/`take`/`exhaust`/`getByPart` for PartInstance) — no per-endpoint duplication
   needed. Gate: `ScopeContext.hasAtLeast(Scope.OWNER)`, computed once per `map()` call.
3. Redact — do not omit the field. **Done** — fields are set to `null` via ternary at
   construction (e.g. `.subtotal(canSeeCost ? realisasi.getSubtotal() : null)`), not
   skipped; every other field on the response is unaffected.
4. Test each affected endpoint at SUPERVISOR, MANAGER, and OWNER scope. **Done, plus
   PRINCIPAL.** `RealisasiServiceCostRedactionTest` and
   `PartInstanceServiceCostRedactionTest` (`@SpringBootTest`, real datasource, fixtures
   built directly via repositories, `@Transactional` auto-rollback — same pattern as
   BE-03's test). 8/8 pass: SUPERVISOR and MANAGER get `null` on every cost field
   (`subtotal`, `sellerDiscount`, `platformVoucher`, `shipping`, `insurance`,
   `serviceFee`, `totalCost`, `actualUnitPrice`, `allocatedLandedCost`, `landedCost`)
   with the rest of the response intact (`status`, `purchasedQty` still present); OWNER
   and PRINCIPAL see the real values. Full suite re-run after: 17 tests, 1 failure — the
   pre-existing `PartStockServiceTest` NPE already flagged in BE-04 (unmocked
   `tenantHierarchyService`, unrelated to this change, not touched here per this file's
   own rule 3). No regressions from this change.

---

## BE-07 — PRA notification type

**Priority:** Unblocked — module 1 is complete, BE-01 and BE-05 are both done.
**Blocked by:** ~~BE-01 (hard, now resolved — see above), BE-05 (strongly
recommended).~~ Neither blocks anymore. **Task 3 below is only partially answered by
module 1**: `screens/01-procurement.md` PROC-03/PROC-04 confirm Realisasi creation has
**no scope or role restriction at all**, so there is no existing scope tier to target the
way `notifyRealisasiEscalated` targets `OWNER_OR_ABOVE`. "Everyone at the tenant" is a
defensible default given that, but it's still a product call, not something the
inventory settled outright — flag this rather than pick silently.

### Why

`00-backend-state.md` §H1 `[C]`: `NotificationType` is `NEW_PURCHASE_REQUEST`,
`PURCHASE_REQUEST_ORDERED`, `PART_END_OF_LIFE`, `LOW_STOCK`, `REALISASI_ESCALATED`.

There is no PRA event. When a PRA is created, **nobody is notified** — the person who must
create the Realisasi receives no signal. This is a flow break in the middle of module 1's
happy path.

### Why blocked

Adding a value to `NotificationType` writes to the **pre-existing** `notifications` table.
This is the exact scenario in `CLAUDE.md` rule 3, and it is how `REALISASI` /
`REALISASI_ESCALATED` silently broke every notification insert. BE-01 has now produced
the CHECK constraint list (`notifications_type_check`), so the hard block is lifted —
BE-05 (proper migration tooling) is still recommended before writing this by hand against
`ddl-auto=update`.

### Task (after BE-01 and module 1)

1. Add the enum value **with its migration in the same change**.
2. Emit from `PurchaseRequestAuthorizationService.create()`.
3. Target per module 1's inventory — which role and scope should receive it.
4. Verify by inserting a real notification row, not by compiling.

---

## BE-08 — Goods receipt list endpoint

**Priority:** Unblocked — module 1 confirmed the screen (PROC-05) and, per below, the
gap is real and unchanged.
**Blocked by:** ~~Module 1 confirming the screen exists and what it must return.~~ Done —
see PROC-05 in `screens/01-procurement.md`.

### Why

`00-backend-state.md` §A2 `[C]`: `GoodsReceiptController` exposes only `POST`,
`GET /purchase-order/{poId}`, and `GET /realisasi/{realisasiId}`. There is no list
endpoint and no `GET /goods-receipts/{id}`. Re-confirmed directly against the
controller while drafting PROC-05, 11 Aug — unchanged.

A "recent receipts" screen — almost certainly needed for module 1 or 2 — is not currently
servable.

### Task

Add `GET /goods-receipts` with filters PROC-05 identifies as useful (by date range, by
receiving user, by whether it came via PO or Realisasi — `purchaseOrderId`/`realisasiId`
being mutually exclusive on the response already gives a natural "path" filter), plus
`GET /goods-receipts/{id}`. Let the screen record define the response shape (it already
does — reuse `GoodsReceiptResponse` as-is) rather than guessing.

---

## BE-09 — Verify the reimbursement flow and PRA lifecycle states

**Status: DONE, 10 Aug 2026.**

**Priority:** Do before module 1 screen inventory (`SESSION_BRIEF_A_BACKEND_INVENTORY.md`
step 1). A reimbursement queue screen designed against an orphaned field wastes the work
and makes an aspirational column look shipped.
**Type:** Investigation, no code change.
**Effort:** ~20 minutes.

### Why

BE-01's live CHECK-constraint dump found `realisasis.payment_method` ∈
`{COMPANY_ACCOUNT, PERSONAL_REIMBURSABLE}`, `reimbursement_status` ∈ `{NOT_APPLICABLE,
PENDING, REIMBURSED}`, and `purchase_request_authorizations.status` allowing
`PARTIALLY_FULFILLED`/`FULFILLED` — none of these were in any narrative doc, and nobody
had confirmed service code actually writes (or ever transitions) them.

### What was found

**Reimbursement: half-wired, not orphaned, not closeable.**
`RealisasiService.create()` (line 170-183) sets `reimbursementStatus` at creation time —
`PENDING` when `paymentMethod == PERSONAL_REIMBURSABLE`, `NOT_APPLICABLE` otherwise. So
the field is real and a "reimbursement queue" screen filtering on `PENDING` would show
real data.

But `REIMBURSED` — the value that would close that queue — is declared in
`ReimbursementStatus.java` and **never set anywhere**. Grepped the whole codebase
(`grep -rln "Reimburs"`): exactly 3 files touch it — the entity, the enum, and
`RealisasiService` — and none of them contain a write path to `REIMBURSED`. There is no
`markReimbursed` endpoint, no service method, no scheduled job. A reimbursement gets
flagged `PENDING` at creation and stays there permanently; the workflow has no exit.

**Screen consequence:** the reimbursement queue *screen* is not an orphan (real data to
show), but any "mark as reimbursed" *action* on it is — there is no backing endpoint.
Gate 1 should flag the action, not the screen. Whether to build that endpoint is a
product decision, not a gap for the inventory session to close on its own — flag and
stop, per this task's own instruction.

**Consequence for the brief's Gate 3 question** ("may the person who made a purchase
mark their own reimbursement paid?"): currently unanswerable, because the action doesn't
exist yet to have a segregation rule about. Not a finding to invent an answer for — flag
it as blocked on the same product decision above.

**PRA lifecycle: fully wired, contrary to the ACTIVE/CANCELLED-only narrative.**
`PurchaseRequestAuthorizationService.refreshStatus(UUID)` (lines 192-235) computes
`FULFILLED` (no item has remaining ceiling), `PARTIALLY_FULFILLED` (some items fulfilled,
some not), or `ACTIVE` (nothing fulfilled yet) by comparing each line's
`approvedPurchasedQtyInStockUnits()` against `authorizedQty` — live, not stored, same
"no mutable remaining column" pattern as everything else in this module. It's called
from **four** sites in `RealisasiService`: `create()` (170), `approveEscalated()` (233),
`reject()` (258), and `supersede()` (275) — every Realisasi transition that changes
APPROVED-status aggregation triggers a PRA status recompute. This is not dead code and
not aspirational: **`PurchaseRequestAuthorizationStatus` has four real, reachable
values** (`ACTIVE`, `PARTIALLY_FULFILLED`, `FULFILLED`, `CANCELLED`), not the two
CLAUDE.md's narrative implies. `screens/00-backend-state.md` B2 and I2 should be read
alongside this — the PRA status field itself needs a four-state badge on any screen
showing it, not two.

### Done when

`screens/00-backend-state.md` gets a new item recording both findings as `[C]`, tagged
with file:line evidence. **Met** — see §C7/§C8 there.

### Do not

Build the missing `markReimbursed` endpoint here. Absence confirmed; whether it should
exist is Blitz's call, not this investigation's. Report and stop, per this task's own
instruction.

---

## BE-10 — Tenant hierarchy status and the open-in-view exposure

**Priority:** Before running Gate 4 on any module.
**Type:** Investigation, no code change (unless BE-10.2 finds a live bug).
**Effort:** ~20 minutes.
**Status: Partially resolved during the 10 Aug UI-planning sync (this repo).** Two
sub-questions; one moved from `[?]` to `[C]`, one is still open.

### Why

This task is cited by `OPERION_UI_SCREEN_INVENTORY_PLAN.md` v1.4 (§4 Gate 4, §12 Q4 and
Q6) but had no entry in this file — added here so the citation resolves to something.

### BE-10.1 — Is tenant hierarchy real or flat?

**Status: DONE, 10 Aug 2026 (live query, `[V]`).**

Gate 4 defers row-level scope filtering on the premise that tenancy is flat (tenant ≈
outlet, nothing to leak across). BE-04 found `PartStockService` depends on a
`tenantHierarchyService`, introduced in commit `3894c5e`, which put that premise in
doubt.

**Found earlier this sync, `[C]`:** `TenantHierarchyService.java` is not a stub.
`Tenant.parent` is a real self-referential `@ManyToOne` FK
(`tenant/entity/Tenant.java:31-33`), backed by `TenantRepository.findByParentId` and
`findParentIdById`. `getEffectiveTenantIds()` walks down to every descendant,
`getAncestorTenantIds()` walks up to every ancestor, `getRootTenantId()` uses the latter.
Both `PartStockService` and `BurnRateService` already call `getEffectiveTenantIds()` to
aggregate across tenants.

**Confirmed live, `[V]`:** queried `tenants` directly (JDBC, `postgresql-42.7.10.jar`
against `localhost:5432/operion` — no `psql` needed, same workaround pattern as BE-01).
**3 tenants total, 1 has a non-null `parent_id`:**

| id | code | name | parent |
|---|---|---|---|
| `a720623c-28d9-4d6b-b06b-059397718d18` | `BLITZ_BDG` | Blitz Tactical | none (root, flat) |
| `aa950bb6-cb01-41ca-b134-56bb42b730bc` | `FRANCHISE_HQ` | Franchise HQ | none (root) |
| `8417979f-b99b-4df6-b1a8-368f791e81fb` | `OUTLET_2` | Outlet 2 | `aa950bb6…` (Franchise HQ) |

**This is not theoretical.** Tenant hierarchy is live, two-level, and real today —
`Blitz Tactical` remains flat/standalone, but `Franchise HQ` → `Outlet 2` is an actual
parent-child pair. A Franchise HQ-scoped `GET /purchase-requests` (or any endpoint using
`getEffectiveTenantIds()`) aggregates Outlet 2's rows right now, cost fields included,
with zero scope-based redaction (`BE-06`'s finding). Gate 4's row-level clause is not a
"when hierarchy lands" deferral — the leak is live with real tenant data as of this
query. **This elevates BE-06's priority**: it is not waiting on a hypothetical, it is
closing an exposure that already exists between two real tenants.

### BE-10.2 — Open-in-view exposure on `BurnRateService`

**Status: DONE, resolved this sync — `[C]`, not just found latent.**

`00-backend-state.md` §F5 / `OPERION_BE_CHANGE_QUEUE.md` BE-03 found `BurnRateService`
has no `@Transactional` of its own and relies on Spring Boot's default
`open-in-view=true` to keep a session open for a lazy `part.getCategory()` load. Confirmed
`[C]`: `application.properties` has no `spring.jpa.open-in-view` override, so the default
(`true`) is what's protecting it today.

**Traced `LOW_STOCK` end to end.** `NotificationService.notifyLowStock(Part)` is called
from exactly one place: `PartStockService.adjustStock()` (`PartStockService.java:68`), a
plain synchronous method with no `@Scheduled`/`@Async` — it fires inline wherever stock
changes (goods receipt, consumable take, stock adjustment), always inside the web request
that triggered the change. `LOW_STOCK` is a simple `currentStock <= minimumStock`
threshold check in `PartStockService`; it does not call `BurnRateService` at all — the
two "low stock" concepts (`InventoryStockStatus.LOW_STOCK` vs
`NotificationType.LOW_STOCK`) are independent of `BurnRateService`'s days-of-cover
calculation.

**Answer to Q6:** nothing today calls `BurnRateService` outside a web request. The
open-in-view dependency is real but currently unreached — latent, not live. It becomes
live the moment anything calls `BurnRateService` from a scheduled job or event listener
(e.g. a future proactive reorder-suggestion job), so the fix (give `BurnRateService` its
own `@Transactional`) is still worth doing before such a caller is added, but it is not
blocking today.

### Done when

`[V]` answer obtained for the live parent-tenant data check (see BE-10.1 above — 1 of 3
tenants has a parent). Gate 4's row-level deferral is **lifted, not re-justified**: real
hierarchy exists today. **BE-10 fully met.** Still to do: record this in
`screens/00-backend-state.md` (currently only in this file), and reflect the elevated
BE-06 urgency wherever module 1's Gate 4 output gets consumed.

---

## BE-11 — Redact `PurchaseOrder` cost fields below OWNER scope

**Priority:** Same class as BE-06 — this is the gap BE-06's original grep pass missed.
**Type:** Requirement gap, same shape as BE-06.
**Status:** Open. Found `screens/01-procurement.md` PROC-07, 11 Aug 2026, while
completing module 1's screen inventory.

### Why

BE-06 redacted cost fields on `RealisasiResponse`/`RealisasiItemResponse` and
`PartInstanceResponse`, found via grepping for
`landedCost|unitCost|unitPrice|actualUnitPrice|sellerDiscount|platformVoucher
|serviceFee`. `PurchaseOrderService` was never touched — its cost fields are named
plainly `totalAmount` (header) and `price` (per item on `PurchaseOrderItemResponse`),
which didn't match that pattern. `[C]`, confirmed directly: `PurchaseOrderService.java`
has zero `ScopeContext`/`hasAtLeast` references anywhere, so nothing gates these fields
today. A Supervisor can read full PO pricing through `GET /purchase-orders` right now —
same exposure BE-06 fixed for the Realisasi/PartInstance path, just on the other genuine
transaction type (`CLAUDE.md`'s "two genuinely different transactions" table).

### Task

1. In `PurchaseOrderService`'s private `map()` method, redact `totalAmount` and each
   item's `price` to `null` below `Scope.OWNER`, same pattern as `RealisasiService.map()`
   and `PartInstanceService.map()` (BE-06).
2. Redact — do not omit the field, consistent with BE-06's own rule.
3. Test at SUPERVISOR, MANAGER, OWNER (and PRINCIPAL for completeness) — real
   `@SpringBootTest` against the live datasource, not a compile check, matching
   `RealisasiServiceCostRedactionTest`/`PartInstanceServiceCostRedactionTest`.
4. Once done, grep the rest of the codebase for other cost-shaped field names that don't
   match BE-06's original pattern (`amount`, `price`, `cost`, `value` are all candidates
   that could exist elsewhere) — this task existing at all is evidence that pattern-based
   grepping under-covers a codebase with inconsistent naming.

---

## Ordering

```
BE-01 ──┬─→ BE-07 (also needs module 1)
        │
BE-02 ──┤
BE-03 ──┤
BE-04 ──┘
BE-10 ──────→ BE-06 (done — didn't end up needing module 1, see BE-06)
BE-05 ──────→ (done — recommended before BE-07, which is still open)

module 1 inventory ──┬─→ BE-07
                     └─→ BE-08
```

BE-01 through BE-06, BE-09, BE-10 are all done, none of them ended up needing module 1.
BE-07 and BE-08 are the only tasks left, and both are genuinely blocked on module 1's
screen inventory (which role/scope for BE-07's notification; which filters for BE-08's
list endpoint).

---

## Recording rules

- Tag every finding `[V]` observed live / `[C]` read in source / `[?]` unknown. `[D]` is
  not acceptable in a completion note.
- **"No changes required" means no work happened** — it is not confirmation that a
  requirement was met (`CLAUDE.md` rule 5).
- Any test touching stock quantities or costing **must** use a `conversionFactor` other
  than 1 (rule 2). Any parameter whose neutral value is 1 or 0 must be varied (rule 6).
- Update `CLAUDE.md` when a task closes a gap it describes. Stale content there propagates
  directly into code.
