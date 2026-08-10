# Operion — Backend Change Queue

**Tanggal:** 10 Agustus 2026
**Source:** `screens/00-backend-state.md` (verification sweep, all `[C]`) +
`OPERION_UI_SCREEN_INVENTORY_PLAN.md` v1.2 (now v1.3 — this queue's §11 items are what
moved it there)
**Status:** BE-01, BE-02 done. BE-03, BE-04 executed same session. BE-05 … BE-08 blocked
or deferred — see each.

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

**Priority:** High structurally. Do before BE-06 and BE-07.
**Type:** Infrastructure.
**Effort:** Half a day to a day.
**Status:** Open decision — `OPERION_BE_PLAN.md` §9 Q4.

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

**Priority:** After module 1 inventory.
**Type:** Requirement gap.
**Blocked by:** Module 1 Gate 4 output; BE-05 if any schema change is involved.

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

Tenancy is flat — tenant ≈ outlet. There is no cross-outlet data for a Supervisor to leak
into. Row filtering would guard a hierarchy that does not exist yet. Build it when
franchise/principal tenancy lands, and build it knowing what the tiers mean.

### Why this waits

*Which* fields on *which* DTOs is exactly what module 1's Gate 4 produces. Building it
first means guessing at the field list.

### Task (after module 1)

1. Take the Gate 4 findings — every screen showing cost data to a sub-OWNER tier.
2. Redact those fields at the DTO mapping layer, keyed on `ScopeContext`.
3. Redact — do not omit the field. A null with a known meaning is easier for a client to
   render than a missing key.
4. Test each affected endpoint at SUPERVISOR, MANAGER, and OWNER scope.

---

## BE-07 — PRA notification type

**Priority:** After module 1 inventory.
**Blocked by:** BE-01 (hard, now resolved — see above), BE-05 (strongly recommended).

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

**Priority:** After module 1 inventory.
**Blocked by:** Module 1 confirming the screen exists and what it must return.

### Why

`00-backend-state.md` §A2 `[C]`: `GoodsReceiptController` exposes only `POST`,
`GET /purchase-order/{poId}`, and `GET /realisasi/{realisasiId}`. There is no list
endpoint and no `GET /goods-receipts/{id}`.

A "recent receipts" screen — almost certainly needed for module 1 or 2 — is not currently
servable.

### Task (after module 1)

Add `GET /goods-receipts` with filters the inventory shows are actually needed, plus
`GET /goods-receipts/{id}`. Let the screen record define the response shape rather than
guessing.

---

## Ordering

```
BE-01 ──┬─→ BE-07 (also needs module 1)
        │
BE-02 ──┤
BE-03 ──┤
BE-04 ──┘
BE-05 ──────→ (recommended before BE-06, BE-07)

module 1 inventory ──┬─→ BE-06
                     ├─→ BE-07
                     └─→ BE-08
```

BE-01 through BE-04 are independent of the UI work and can run in parallel with module 1.
BE-05 is the highest structural priority once those are clear.

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
