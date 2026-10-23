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
cost redaction, same shape as BE-06) is a new gap module 1 surfaced. **BE-12
(auth/tenant hardening) done 12 Oct 2026**, merged as PR #1. **BE-08, BE-11 and BE-07 done 14 Oct 2026** (BE-07 widened: requester
notifications, procurement activity log, role endpoint, migration V3). No change-queue
items open. **BE-13 done 14 Oct
2026** (requester branch check, PRA spend + service-event cost redaction). Next: the
permission matrix. **BE-14 open, 16 Oct 2026** (PR creation gate + HQ filing a PR
for a store). **BE-15 open, 16 Oct 2026** (PR cancel: who, reason, notification).
**BE-16 done, 22 Oct 2026** (store spending summary + optional monthly budget; HQ can read).
**BE-17 open, 19 Oct 2026** (Owner sets limits; who logs, rejects, corrects purchases; HQ buying for a store).
**BE-18 open, 19 Oct 2026** (paying back the Store Manager). **BE-19 proposed** (expense claims for non-stock needs).
**BE-20 done, 22 Oct 2026** (orders to HQ: draft/send/cancel; who receives; self-receive with photo).
**BE-21 done, 22 Oct 2026** (HQ catalog + mandatory items: buy from HQ vs buy elsewhere; HQ super admin created locked by V11, 22 Oct).
**BE-22 done, 22 Oct 2026** (automatic status changes name what triggered them).
**BE-19 confirmed, 20 Oct 2026** (expense claims, paid by store or me). **BE-23 open** (file uploads on Cloudflare R2). **BE-24 done, 22 Oct 2026** (store approval alerts stay in the store; HQ not told).
**BE-19 ready to build, 23 Oct 2026** (screen record PROC-10 written). **Module 2 inventory complete, 23 Oct 2026** (`screens/02-inventory-stock.md`): **BE-25 open** (who changes stock and item data), **BE-26 open** (store on every inventory row, item history, consumables in "used today"), **BE-27 open** (one definition of "low"; tell the Store Manager).

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

### Decisions (product owner, 13–14 Oct 2026)

The first proposal ("everyone in the PRA's tenant except its creator") was rejected: a
PRA's creator *is* its approver, so it aimed at the wrong person. Decided instead:

- The **PR requester** hears about every decision on their PR: approved, rejected (with
  reason), authorized.
- **PROCUREMENT** users get a notification when a PRA is created — they do the buying.
- HQ needs a **log** of who requested and who approved what, readable by **ADMIN,
  PROCUREMENT, or OWNER scope and above**.

Claude's default, open to revision: the PRA notification goes to PROCUREMENT users in the
PRA's **own** tenant only (the outlet's buyers, not HQ's), never its creator.

### What was done — DONE, 14 Oct 2026

1. **Who-did-it was being lost.** `PurchaseRequestService` approve/reject/cancel and every
   `PurchaseOrderService` history write passed `changedBy = null` (`[V]` local DB: 6 of 6
   APPROVED rows had no actor). All now record `currentUser()`. PR creation records the
   real caller too, which can differ from `requestedBy` (see finding below).
2. **PRA creation now writes the PR's APPROVED → AUTHORIZED history row.** It never did —
   and couldn't have: `purchase_request_status_history`'s CHECK constraints didn't allow
   `AUTHORIZED` (rule 3, third time).
3. **Migration `V3__procurement_notifications_and_authorized_history.sql`** recreates four
   constraints: `notifications_type_check` (+4 types), `notifications_reference_type_check`
   (+`PURCHASE_REQUEST_AUTHORIZATION`), and both `purchase_request_status_history` status
   checks (+`AUTHORIZED`). Applied cleanly to the local DB (`[V]`, "now at version v3",
   existing rows re-validated). **Not yet applied to prod** — Flyway will run it on the
   next deploy.
4. **Notifications** (`NotificationService`): `PURCHASE_REQUEST_APPROVED` /
   `_REJECTED` / `_AUTHORIZED` to the requester; `PURCHASE_AUTHORIZATION_CREATED` to the
   PRA tenant's PROCUREMENT users. Nobody is notified of their own action — this also
   changed the existing `NEW_PURCHASE_REQUEST`, which used to tell an approver about a PR
   they filed themselves.
5. **`GET /procurement/activity`** (`ProcurementActivityService`): a merged, newest-first
   feed over the four `*StatusHistory` tables, nothing new stored. Tree-scoped; filters
   `from`/`to`/`tenantId`/`actorId`/`type`/`limit` (default 100, max 500). Access is
   checked in the service because it's role **or** scope.
6. **`PATCH /users/{id}/role`** — ADMIN-only, tree-contained, never your own role. The
   local DB had zero PROCUREMENT users and no API way to make one.
7. `ProcurementNotificationAndLogTest` (9), end to end over HTTP on an HQ → Outlet tree +
   unrelated root, with the approving Owner also holding PROCUREMENT so the
   creator-exclusion has someone to exclude. Smoke-tested against real dev data: the log
   returned all 13 existing PR history rows (old ones with null actor, as expected).
8. Postman PROD/DEV: `user-update-role`, new folder `PROCUREMENT ACTIVITY`.

### Found while doing this

- `POST /purchase-requests` took `requestedBy` from the body with no check. **Fixed in
  BE-13.**
- `POST /purchase-authorizations` has no role or scope gate. **Open — see BE-13.**

---

## BE-13 — Requester branch check, PRA spend + service-event cost redaction

**Status: DONE, 14 Oct 2026** (branch `fix/pr-requester-branch-and-cost-fields`, stacked on
BE-07). Product owner's instructions, same day: follow the business flow so a PR only
accepts a requester from the filing branch; redact the two money fields BE-11 found.

### What was done

1. **Requester must belong to the filing branch.** `PurchaseRequestService.create()` only
   accepts a `requestedBy` whose tenant is the caller's own tenant — not the parent HQ,
   not a child outlet, not another root. Anything else: 400 "Requesting user not found in
   your branch", identical to a nonexistent id (no probing). Filing on behalf of a
   same-branch colleague still works; history records the real caller. `requestedBy`
   stays required, so the request shape is unchanged.
2. **PRA items below OWNER:** `purchasedValue` and `remainingValue` → null; `maxValue`
   (the limit) and all quantities stay. `remainingValue` has to go too:
   `maxValue − remainingValue = purchasedValue`. The buyer doesn't need it to stay within
   the limit — escalation already catches an over-ceiling buy server-side.
3. **`ServiceEvent.cost` below OWNER → null**, in `mapToResponse()`, which every
   service-event endpoint uses.
4. Tests: 2 requester cases in `ProcurementNotificationAndLogTest` (same-branch on
   someone's behalf accepted; unrelated root, parent HQ and child outlet all refused with
   no notification sent); `PraAndServiceEventCostRedactionTest` (3) with 2 paket × factor
   6 at Rp 300,000 against a 12-unit / Rp 900,000 line (rules 2 and 6). **Against the
   unfixed code exactly the 3 targeted assertions fail.** `[V]` local.

### PRA creation gate — DONE, 14 Oct 2026 (MANAGER and above, product owner's choice)

`POST /purchase-authorizations` and `PATCH /purchase-authorizations/{id}/cancel` were open to
any authenticated user, though creating a PRA *is* the authorization act. **Recommended:
MANAGER scope or above**, because:

- the SoD rule "PRA approver ≠ Realisasi creator, Owner/Principal exempt, **Manager does
  not qualify**" only makes sense if Managers can approve PRAs, and
- `OPERION_BE_PLAN.md` row 34 verified a "manager-approved PRA" live (28 Jul).

The PR approve step stays OWNER-only, so the chain reads: Owner approves the *need*,
Manager or above sets the *spending ceiling*, and an over-ceiling buy escalates back to
Owner. Alternative: OWNER+ only, if the Manager cases were test data rather than intent.
**Decided: MANAGER and above** (product owner, 14 Oct 2026). **Superseded 19 Oct 2026 by
BE-17: OWNER and above**: the store Owner sets limits, the Store Manager buys. `requireManagerScope()` runs
first in `create()` and `cancel()` → 403 below MANAGER. Two HTTP tests (no scope with the
PROCUREMENT role and SUPERVISOR refused, MANAGER allowed, for create and for cancel); both
fail without the gate. `[V]` local.

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

### What was done — DONE, 14 Oct 2026

- `GET /goods-receipts`: caller's tenant tree (own + descendants, same as every other
  procurement list), newest `receivedDate` first. Optional `from`/`to` (inclusive
  `yyyy-MM-dd`), `receivedBy` (user id), `path` (`PO` | `REALISASI`; anything else → 400).
  Built with a JPA `Specification` so absent filters add no SQL. Plain list, no paging —
  matches the other procurement `getAll()` endpoints.
- `GET /goods-receipts/{id}`: looked up *within* the tree, so an out-of-tree id answers
  exactly like a missing one (no probing). `GoodsReceiptResponse` reused unchanged — it has
  no cost fields, so no BE-06-style redaction is needed.
- `GoodsReceiptListTest` (8): HQ → Outlet tree + unrelated root, receipts on both paths;
  covers tree scope both directions, each filter, inclusive date ends (a 23:30 receipt on
  the `to` date is included), bad `path`, and by-id in/out of tree. `[V]` local.
- Postman PROD/DEV: `goods-receipt-list` (filters present but disabled) and
  `goods-receipt-get-by-id`.

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
**Status:** DONE, 14 Oct 2026 (see end of entry). Found `screens/01-procurement.md`
PROC-07, 11 Aug 2026, while completing module 1's screen inventory.

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

### What was done — DONE, 14 Oct 2026

1–2. `PurchaseOrderService.map()` now nulls `totalAmount` and item `price` below OWNER
   (`canSeeCost`, same as BE-06). Covers every PO response: create, get, list, send,
   cancel.
3. `PurchaseOrderServiceCostRedactionTest` (6): no scope, SUPERVISOR, MANAGER redacted;
   OWNER, PRINCIPAL visible; list path redacted too. Item quantity is 6, not 1, so
   `price` and `totalAmount` are different numbers (rule 6). **Run against the unfixed
   service: 4 of 6 fail** (exactly the redaction cases). `[V]` local.
4. Follow-up grep over every `*Response*` DTO for
   `amount|price|cost|value|fee|total|subtotal|discount|voucher|shipping|insurance`.
   Two more unredacted money fields — **left unchanged, each is a product decision**:
   - `PurchaseRequestAuthorizationItemResponse.maxValue` / `purchasedValue` /
     `remainingValue`. `purchasedValue` is summed from the very Realisasi spend BE-06
     hides, so it partially undoes BE-06 at the line level. But the buyer (often below
     OWNER) needs the ceiling to buy within it. Options: hide only `purchasedValue`, or
     accept that the buyer sees their own budget.
   - `ServiceEventResponse.cost` (maintenance service cost). Is maintenance spend as
     sensitive as purchase cost?

   **Both resolved in BE-13 (14 Oct 2026):** spend and remaining hidden, limit kept;
   service-event cost hidden.

---

## BE-12 — Close self-assigned scope, lock down tenants, 403 for denials

**Priority:** Urgent — exploitable on the public prod URL against a full copy of real data.
**Type:** Security fix.
**Status: DONE, 12 Oct 2026** (branch `fix/be-12-auth-hardening`; not yet deployed when
written — check `git log main` before assuming it's live).

### Why

`POST /auth/register` was `permitAll` and `RegisterRequest` had a caller-chosen `scope`.
Anyone who found `operion-services-production.up.railway.app` could register into
`BLITZ_BDG` as `PRINCIPAL` — unredacted landed costs (bypassing BE-06), approve escalated
Realisasi, PRs and stock adjustments. Even scope-less, an anonymous OPERATOR could read all
tenant operational data and do every unguarded maintenance write (baseline §5.1).
`OPERION_BE_BASELINE.md` §1.2 described the field ("`scope` is optional") but not the
capability — it was absent from its own gap list. `TenantController` had no authorization
at all.

How every OWNER test account in the dev DB (`pra_owner`, `conv_owner`, `case9_owner`, …)
was created: by this exact self-assignment.

### What was done

1. `RegisterRequest.scope` removed; `/auth/register` → `hasRole('ADMIN')`, out of
   `permitAll` and out of the JWT filter's skip list. `tenantCode` defaults to the admin's
   own tenant (was hardcoded `"BLITZ_BDG"`), must be in the admin's tree.
2. `TenantHierarchyService.requireWithinCallerTenantTree(UUID)` — one shared
   self-or-descendant rule, throws `AccessDeniedException`. Used by register, scope grant,
   and all three tenant endpoints.
3. `POST /tenants` → ADMIN; parent must be in tree; **root tenant needs `PRINCIPAL`**
   (nobody holds it → roots come via Flyway seed). `GET /tenants/{id}[/children]` →
   tree-scoped, checked before lookup.
4. `GlobalExceptionHandler`: `AccessDeniedException` → 403 (was 400, `[V]`). The four
   manual gates (3× `requireOwnerScope`, `UserService` tree check) now throw it.
   Segregation-of-duties checks deliberately still 400.
5. Catch-all handler honours Spring `ErrorResponse` statuses: unknown URL 500 → 404, wrong
   method 500 → 405. Found while verifying step 6.
6. Deleted `TestController`, `GET /part-history/test`, `PartConditionHistoryService`'s
   `System.out` dump (+ its duplicate query). Postman PROD/DEV synced: debug requests
   gone, register sends admin token, new `user-update-scope` request.

### Verification

- `AuthHardeningTest` (18) + `GlobalExceptionHandlerStatusTest` (2), MockMvc through the
  real filter/method-security stack, real JWTs, real `HQ → Outlet` + unrelated-root tree.
  **Run against pre-BE-12 `main`: 13/18 fail** with the old behaviour (anon register 200,
  `scope=PRINCIPAL` persisted, 400 instead of 403, tenants open). `[V]`
- App booted locally (Flyway validate clean), every changed path exercised over HTTP. `[V]`
- Full suite: 36/37. The 1 failure, `PartStockServiceTest`, pre-dates BE-12 — its Mockito
  setup never mocks `TenantHierarchyService`, which `PartStockService` has used since an
  earlier commit. Not touched here. `[C]`
- **Not** verified against prod — needs the deploy.

### Client-visible changes (tell the web console / RN teams)

- `/auth/register` now needs an ADMIN bearer token; `scope` in its body is ignored.
- Scope gates and `@PreAuthorize` denials: **400 → 403**.
- Unknown paths: 500 → 404. Wrong method: 500 → 405.

### New test-setup workflow

Log in as an ADMIN → `register` → `user-update-scope`. Self-registering an OWNER no longer
works by design.

### Deliberately not done

- ADMIN with null scope can still grant `PRINCIPAL` via `PATCH /users/{id}/scope` —
  belongs in the role × scope × action permission matrix.
- Unauthenticated → 403 (Spring default entry point), not 401.
- No role-change endpoint; register still always yields OPERATOR.

---

## BE-14 — PR creation gate, and HQ filing a PR for a store

**Status: DONE, 21 Oct 2026** (see end of entry). Product owner's decisions, 16 Oct 2026
(module 1 deferred-decision #1, "should PR creation be restricted?"). Mobile side waits on
this; see "Client follow-up".

### Why

`POST /purchase-requests` is open to any authenticated user (`API_CONTRACT.md` §1 in the
mobile repo: "Auth beyond JWT: none"). How the business actually runs:

- **Each store's app is installed on one store phone, used only by the Store Manager.**
  One person, one account, so the requester on the record is the real person.
- Technicians don't file PRs. Two technicians visit on separate, non-overlapping
  schedules and sync what's needed with the Store Manager daily; the SM files it.
- **ADMIN and PROCUREMENT staff at HQ** use the app on their own phones and must be able to
  file a PR **for a specific store**.
- **ACCOUNTING does not create PRs** (view only): the person who asks for a purchase
  shouldn't also handle the money for it.
- Stores are independent: each owns its stock and its requests. **No stock moves between
  stores**, so there is no transfer flow to design for.

Today HQ can't file for a store at all: BE-13 rule 1 refuses a `requestedBy` outside the
caller's own tenant, and the PR always lands on the caller's tenant.

### Task

1. **Create gate.** `POST /purchase-requests` is allowed for **MANAGER scope or above**
   (MANAGER, OWNER, PRINCIPAL), **or role ADMIN or PROCUREMENT**. Anyone else → 403, the
   same shape as BE-13's PRA gate. TECHNICIAN, OPERATOR and ACCOUNTING are refused unless
   their scope is MANAGER+. Reads (`GET`) are unchanged.
2. **Target store.** Add optional `tenantId: UUID` to `CreatePurchaseRequestRequest`.
   - Omitted → the caller's own tenant (today's behaviour; what a Store Manager sends).
   - Present and different from the caller's tenant → only ADMIN, PROCUREMENT or OWNER+
     may do this (else 403), and the tenant must be inside the caller's tree (own tenant
     or a descendant). Outside the tree → 400 with the same message as a nonexistent id,
     no probing (BE-12/BE-13 convention).
   - The PR is saved on the **target** tenant.
3. **Requester when filing for a store — decided 16 Oct 2026.** The requester is **the HQ
   caller themself**; the PR's tenant says which store it's for. Screens read it as
   "Requested by Rina Wijaya for Outlet Bandung" (`requestedByName` + `tenantName` from
   item 5). BE-13 rule 1 changes from "requester must be in the caller's tenant" to
   "requester must be the caller, or a user in the PR's (target) tenant". Anything else
   stays 400 "Requesting user not found in your branch". History records the real caller,
   as now. **No notes field is needed for this** — the store is a real field, not free text.
4. **Notification routing — verify, don't change.** `NotificationService.notifyNewPurchaseRequest`
   already notifies approvers in `getAncestorTenantIds(pr.getTenant())` `[C]`, so saving
   the PR on the target tenant routes it to that store's approvers. Add a test proving an
   HQ-filed PR for Outlet A notifies Outlet A's chain and nobody at Outlet B.
5. **Response.** Add `tenantId` and `tenantName` to `PurchaseRequestResponse`, so HQ lists
   and the approval console can show which store a PR is for.
6. **Store list for the picker.** The mobile form needs the caller's own tenant plus its
   descendants (id + name). `GET /tenants/{id}/children` exists `[C]`; check whether it is
   tree-contained, callable by ADMIN/PROCUREMENT, and enough for today's depth (BE-10.1:
   HQ → outlets). If not, add a small `GET /tenants/tree` returning exactly that set.

### Tests (rules 2 and 6 where quantities are involved)

- Gate: OPERATOR, TECHNICIAN, ACCOUNTING (no scope) → 403; MANAGER, ADMIN, PROCUREMENT → 201.
- Target store: ADMIN at HQ files for Outlet A → saved on Outlet A; MANAGER at Outlet A
  passing HQ's id → 403; ADMIN passing a tenant from another root → 400, same body as a
  random UUID.
- Requester: HQ caller as requester for Outlet A accepted; Outlet A user accepted when the
  target is Outlet A; Outlet B user refused.
- Notification routing as in item 4.
- Every new test fails against the unfixed code.

### Client follow-up (mobile repo, after merge)

- PROC-01 "Create request" becomes a gated button, badge `MANAGER+·ADMIN·PROC`, disabled
  for everyone else (client mirror of rule 1 in `auth/permissions.ts`).
- Store field: a locked derived field ("Store: Outlet Bandung") for a Store Manager; a
  required picker with **no default** for ADMIN/PROCUREMENT at HQ.
- PROC-02 rows and the activity log show the store name from item 5; a PR filed from HQ
  reads "Requested by {name} for {store}".
- `API_CONTRACT.md` regenerated from source.

### Not in this task

- **Cross-store stock dashboard** ("this part is low at Bandung and Jakarta"). Wanted by the
  product owner as the reason HQ files PRs; best driven by burn rate ("runs out in ~3 days")
  rather than zero stock, with a "Create PR for this store" shortcut. Needs a stock-by-store
  read across the caller's tree — **[?] not known to exist**; belongs with modules 2–3.
- Optional per-line note on PRs ("needed by Andi, visit 14 Oct") — parked, not decided.

### What was done — DONE, 21 Oct 2026 (shipped together with BE-17)

1. **Create gate** in `PurchaseRequestService.create()`: MANAGER+ scope, or role ADMIN or
   PROCUREMENT; else 403 "Only Manager-level users or higher, Admin or Procurement can
   create purchase requests". Reads unchanged.
2. **Target store**: optional `tenantId` on `CreatePurchaseRequestRequest`, resolved by the
   new shared `TenantHierarchyService.resolveTargetTenant(requested, mayActForAnother)`:
   omitted/own → own; another store without ADMIN/PROCUREMENT/OWNER+ → 403; outside the
   tree or nonexistent → 400 "Store not found" (identical). The PR, and its part lookups,
   are on the target store.
3. **Requester**: the caller, or a user of the target store (BE-13 rule widened); else 400
   "Requesting user not found in your branch". History still records the real caller.
4. **Notification routing**: unchanged code, now tested — an HQ-filed PR for Outlet A
   notifies A's chain and nobody at Outlet B `[V]`.
5. **Response**: `tenantId`, `tenantName` on `PurchaseRequestResponse`.
6. **Store picker**: `GET /tenants/{id}/children` returns direct children only and not the
   caller's own tenant, so a new **`GET /tenants/tree`** returns the caller's tenant first,
   then every descendant (never ancestors or other roots). Any signed-in user.

Tests: `PurchaseGatesTest` (6 for BE-14, with HQ → Outlet A + Outlet B + unrelated root).
The BE-07 test's requester fixture became a real Store Manager (OPERATOR + MANAGER), since
a scope-less OPERATOR can no longer file. Against the unfixed code every new BE-14 test
fails. Postman: `tenant-tree`.

---

## BE-15 — PR cancel: who can cancel, a required reason, and telling the requester

**Status: DONE, 21 Oct 2026** (see end of entry). Product owner's decision, 16 Oct 2026
(module 1 deferred-decision #2, "cancel/approve scope asymmetry"). Independent of BE-14,
but its "requester" rule reads best after BE-14 lands.

### Why

`PurchaseRequestService.cancel()` `[C]` has no scope or role check, takes no reason
(history row written with `reason = null`) and sends no notification. Anyone signed in,
including OPERATOR and TECHNICIAN, can cancel a PENDING or APPROVED PR, and the requester
is never told.

Approve/reject stay OWNER+: approving spends money. Cancel *stops* spending, so the bar
stays lower — but a needed request must not disappear silently. In practice the Store
Manager is the gate for requests and sometimes files one wrong (wrong part, wrong
quantity, duplicate); there is no PR edit, so cancel-and-refile is how they fix it.

### Task

1. **Who can cancel, by status.** Status rules unchanged (only PENDING or APPROVED).
   | PR status | Allowed | Everyone else |
   |---|---|---|
   | PENDING | the PR's requester (`requestedBy` = caller), or OWNER+ | 403 |
   | APPROVED | OWNER+ only — undoing an Owner's decision takes an Owner | 403 |
   Check order: tenant containment (`findOwned`) first, then this rule, then status.
2. **Reason required.** `PATCH /purchase-requests/{id}/cancel` takes
   `{ reason: string }` `@NotBlank`, the same shape as reject's `RejectRequest`; written to
   the status-history row, so it shows in the procurement activity log (BE-07).
3. **Tell the requester.** New `NotificationType.PURCHASE_REQUEST_CANCELLED`, sent to
   `requestedBy` when someone *other than the requester* cancels (same skip rule as BE-07's
   "an approver filing their own PR isn't told"). Message includes the reason.
   `notifications.type` has a CHECK constraint (BE-01) — needs a Flyway migration, like V3.

### Tests

- PENDING: requester → 200; OWNER → 200; another MANAGER in the same store → 403;
  OPERATOR, TECHNICIAN, ACCOUNTING → 403.
- APPROVED: requester (MANAGER) → 403; OWNER → 200.
- Missing or blank reason → 400; reason stored on the history row.
- OWNER cancels the SM's PR → SM gets `PURCHASE_REQUEST_CANCELLED`; SM cancels own PR →
  no notification.
- Every new test fails against the unfixed code.

### Client follow-up (mobile repo, after merge)

- PROC-02 "Cancel" becomes a gated button: badge `REQUESTER·OWNER+` while PENDING,
  `OWNER+` once APPROVED; disabled for anyone else.
- The reason field there becomes "Reason (required to reject or cancel)".
- Notifications: label "Request cancelled".
- `API_CONTRACT.md` regenerated from source.

### Known edge, accepted

- An SM who spots a mistake **after** the Owner approved can't cancel it themselves; they
  ask the Owner. Accepted: once approved, it's the Owner's decision to undo.
- A PR filed from HQ for a store (BE-14) has the HQ person as requester, so that store's SM
  can't withdraw it while pending — the HQ requester or an Owner can.

### What was done — DONE, 21 Oct 2026

1. **Who**: PENDING → the PR's requester or OWNER+; APPROVED → OWNER+ only; else 403.
   Status rule unchanged (only PENDING/APPROVED, else 400).
2. **Reason required**: `PATCH /purchase-requests/{id}/cancel` takes `{reason}`
   (`RejectRequest`, `@NotBlank`); written to the history row with the caller as actor.
3. **`PURCHASE_REQUEST_CANCELLED`** to the requester when someone else cancels (BE-07's
   self-skip). **Migration `V5__notification_pr_cancelled.sql`** recreates
   `notifications_type_check` (rule 3); applied cleanly to the local DB `[V]`.

**The "known edge" above was not true of the code `[C]`** (recorded per CLAUDE.md rule 1):
`cancel()`, `getById()` and `getHistory()` all used `findOwned` — **the caller's own
tenant only** — so an HQ person could neither cancel nor even open a PR they had filed for
a store (it lives on the store since BE-14). Fixed here:
- `getById`, `getHistory` and `cancel` look the PR up across the caller's tree (own +
  descendants), like `getAll()` already did. Out-of-tree ids still answer "not found".
- On cancel, **the requester** may withdraw their own PENDING PR anywhere in their tree;
  **OWNER+ applies only to PRs of the Owner's own store** — HQ's Principal can't cancel a
  store's own requests (stores are independent).
- **Approve and reject stay own-store** (`findOwned`): they're the store Owner's decisions.

Tests: `PrCancelTest` (5) — every case above plus the HQ-requester/HQ-Owner case. Against
the old cancel logic (new enum kept so the test compiles) all 4 original tests fail.
Full suite 96/96. Postman `purchase-request-cancel` now sends a reason.

---

## BE-16 — Store spending summary and an optional monthly budget

**Status: DONE, 22 Oct 2026** (see end of entry). Product owner's decisions, 16 Oct 2026 (module 1 deferred-decision #3,
"PRA header-level cap").

### Why

Decision #3 is closed **against** a header-level cap on PRAs: ceilings stay per line. The
real need is a budget, and it sits at store level:

- This is a franchise and **every store is independent**: its own stock, its own budget.
  The store's Owner (and anyone above) watches what the store spends.
- **HQ (PRINCIPAL) must see every store's spending** for business intelligence: what each
  store spends most on, against its budget. HQ uses it to **advise** the store Owner,
  advice they're free to ignore. HQ does not approve, block or set anything for a store.
- **Warn, never block.** A budget number must never stop a store from buying what it
  needs; over-ceiling purchases already escalate (Realisasi `varianceStatus`).

Every number here is money, so it's **OWNER+ only**, consistent with BE-06/BE-11/BE-13.

### Definitions — decided 16 Oct 2026, use these words exactly

These two words mean the same thing in the API, the screens and every document. If code or
copy needs a third bucket, add it here first.

**Spent** — money the store is now obliged to pay. Counted in the month of the purchase.

| Source | Counts when | Month taken from | Amount |
|---|---|---|---|
| Realisasi | status `APPROVED` — auto-approved inside the ceiling, or escalated and then approved by an Owner | `purchasedAt` | `totalCost` |
| Purchase order | status `ORDERED`, `PARTIALLY_RECEIVED` or `RECEIVED` (sent to the centre) | `orderDate` | `totalAmount` |

Spent is split by **who it was bought from**, not by record type (amended 19 Oct 2026):
**from the franchise centre** = every PO, plus any Realisasi whose supplier is the centre
(an urgent buy from HQ paid on the spot, often with the SM's own money — BE-18);
**outside** = every other Realisasi.

**Committed** — money that's been allowed or is waiting on a decision, but isn't spent
yet. **A snapshot as of now, not a monthly figure**: it has no purchase date yet.

| Source | Counts when | Amount |
|---|---|---|
| PRA line | PRA is `ACTIVE` or `PARTIALLY_FULFILLED` | the line's remaining value |
| Escalated Realisasi | status `PENDING_APPROVAL` (over its ceiling, waiting for an Owner) | its cost on that line |

**No double counting.** A pending escalated Realisasi will use up the same PRA line's
remaining value once approved. So per PRA line, committed =
**the larger of** (the line's remaining value, the total of its pending Realisasi on that
line) — never the sum. Example: line remaining Rp 380,000, escalated purchase Rp 600,000
waiting → committed Rp 600,000. After the Owner approves: spent +600,000, committed 0
(remaining is clamped at 0, not negative). If the Owner rejects: committed goes back to
Rp 380,000.

**Counted in neither:** purchase requests (they carry no money); POs in `DRAFT` or
`CANCELLED`; Realisasi `FAILED` or `SUPERSEDED` (a correction's replacement record counts
instead); PRAs `CANCELLED` or `FULFILLED`.

**Budget check (warning only):** spent this month + committed now + other expenses this
month (BE-19, both the "HQ" and "Branch specific" categories) > monthly budget.

### Task

1. **Monthly budget (optional).** Nullable `monthlyPurchaseBudget` (`BigDecimal`) per
   store tenant, Flyway migration. Setting it (**confirmed by the product owner**): the
   store's own OWNER only — the caller's
   tenant must *be* that store; HQ PRINCIPAL can read but not set it (the store owns its
   budget). Clearing it (null) is allowed. Endpoint suggestion:
   `PATCH /tenants/{id}/purchase-budget { amount: number | null }`.
2. **Spending summary.** `GET /procurement/spending?month=yyyy-MM[&tenantId=]`. OWNER+
   only, else 403. Tree-scoped: a store Owner gets their store; an HQ PRINCIPAL gets one
   row per store in their tree (or one store with `tenantId`). Per store, using the
   Definitions above exactly:
   - `spentFromCentre` — spent in the month from the franchise centre (POs + Realisasi
     with the centre as supplier)
   - `spentOutside` — spent in the month anywhere else (marketplace etc.)
   - `committed` — committed now (snapshot, not filtered by `month`)
   - `budget` — item 1's value or null
   - `topParts` — top 5 parts by spent in the month (`partId`, `partName`, `amount`)
   Keep the from-centre/outside split: for a franchisor, "bought from us" vs "bought
   outside" is itself a business signal. To tell a centre Realisasi apart, add a boolean
   `franchiseCentre` to `Supplier` (Flyway), set on the root tenant's own supplier record.
   **Built in BE-21** (V8, `suppliers.franchise_centre`, one per tenant; HQ_BANDUNG's is
   "HQ BANDUNG"). Every PO now has it as its supplier.
3. **Money arithmetic.** `BigDecimal` throughout; month boundaries in the store's
   timezone **[?]** (check how `purchasedAt` is stored before choosing).

### Tests (rules 2 and 6)

- Realisasi with `conversionFactor` ≠ 1, mixed statuses: APPROVED counted as spent;
  PENDING_APPROVAL counted as committed; FAILED and SUPERSEDED in neither. PO statuses
  DRAFT and CANCELLED not counted; a PRA partly consumed contributes only its remaining
  value.
- No double counting: the Rp 380,000 / Rp 600,000 example from Definitions, before
  approval, after approval and after rejection.
- A purchase on the last day of the month vs the first day of the next.
- Scope: MANAGER → 403; store OWNER sees only their store; HQ PRINCIPAL sees all stores in
  their tree and nothing from another root.
- Budget: store OWNER sets it; HQ PRINCIPAL setting it → 403; null clears it.
- Every new test fails against the unfixed code.

### Client follow-up (mobile repo, after merge)

- PROC-02 header for OWNER+: "This month · {store}: Spent Rp … · Committed Rp …", plus
  "of Rp … budget" when set, turning warning-toned (not blocking) when an approval would
  push spent + committed over budget.
- Budget field for the store Owner (settings).
- **HQ spending dashboard** — a new screen; needs a screen-inventory record first (same
  process as module 1). Pairs with the cross-store stock dashboard parked under BE-14.

### Not in this task

- An in-app channel for HQ to send suggestions to a store Owner — parked; they talk
  outside the app for now.
- Hard budget enforcement — rejected: it fights "every store is independent".

### What was done — DONE, 22 Oct 2026

1. **Budget:** `tenants.monthly_purchase_budget` (V9, nullable, CHECK ≥ 0) and
   `PATCH /tenants/{id}/purchase-budget { amount | null }`. Only OWNER+ signed in at that
   very store (HQ PRINCIPAL, a Manager → 403; negative → 400; outside the tree → 403 as
   every `/tenants` path). `TenantResponse.monthlyPurchaseBudget`, null below OWNER.
2. **Summary:** `GET /procurement/spending?month=yyyy-MM[&tenantId=]` (`SpendingService`,
   computed on read). OWNER+ only, else 403. Rows: the caller's tree (own store first,
   then tree-walk order), or the one `tenantId` names (outside the tree → 400 "Store not
   found", via `resolveTargetTenant`). Per row: `spentFromCentre`, `spentOutside`, `spent`,
   `committed`, `budget`, `overBudget` (null without a budget), `topParts` (5). Exactly the
   Definitions above, with three choices made where they were silent:
   - **Committed basis:** remaining value and pending purchases are both on the ceiling's
     basis (unit price × purchased qty), so the max compares like with like. Spent uses
     landed `totalCost`, as defined.
   - **Pending purchases on a non-live PRA** (FULFILLED/CANCELLED) still count as
     committed (the pending Realisasi is its own source in the table); only the
     remaining-value half needs a live PRA.
   - **topParts amounts:** each purchase line's `allocatedLandedCost`, each order line's
     price × qty, so they add up to spent.
   - `overBudget` = spent + committed > budget. **BE-19's "other expenses" join this sum
     when BE-19 ships.**
3. **Month boundaries `[?]` → decided by default, revisable:** `purchasedAt` is a zone-less
   `timestamp` (from the receipt, or `LocalDateTime.now()` when omitted) and `orderDate` is
   a server `LocalDate.now()`. Months are the calendar months of those stored values. So the
   server's zone decides server-stamped dates: **set `TZ=Asia/Jakarta` on Railway** (and
   locally the machine is already +07:00). No code change for zones.
4. `Supplier.franchiseCentre` came with BE-21.

Tests: `SpendingTest` (8): spent split with FAILED/SUPERSEDED/DRAFT/CANCELLED excluded
and landed cost (shipping) counted; last second of September vs midnight 1 October;
the Rp 380,000 / Rp 600,000 example before, after rejection, and after approval (remaining
clamps at 0); a 3-paket × factor 6 = 18-can purchase escalating a 12-can line though its
value fits (rule 2: with factor 1 it would be spent, not committed); only live PRAs' remaining;
scope (MANAGER 403, Owner one row, HQ its tree in order, other root → 400); budget gates
and `overBudget`. Against the unfixed code all 8 fail. Full suite 131/131. Postman:
SPENDING folder, `tenant-purchase-budget`.

---

## BE-17 — Who sets limits, logs, rejects and corrects purchases; HQ buying for a store

**Status: DONE, 21 Oct 2026** (with BE-14; see "What was done" at the end of this entry).
Product owner's decisions, 19 Oct 2026 (module 1 deferred-decision #4, "PRA/Realisasi
absence of scope gating", the part BE-13 didn't cover).

### Why

How a store actually runs:

- The **store Owner sets the spending limits** (creates the PRA). The **Store Manager
  mostly does the buying** and logs it (Realisasi); HQ PROCUREMENT staff sometimes buy for
  a store too.
- This keeps the DL-08 separation rule (`requireSegregationFromPraApprover` `[C]`: whoever
  approved a PRA can't log its Realisasi unless OWNER+) working as intended: Owner sets the
  limit, someone else spends against it, over-limit purchases come back to the Owner.
  Under BE-13's MANAGER+ gate an SM could set limits and then be refused when buying
  against them — the business flow above never needs that.

Gaps in `RealisasiService` today `[C]`:

| Action | Today |
|---|---|
| `create()` (log a purchase) | any authenticated user; only the DL-08 rule applies |
| `approveEscalated()` | OWNER+ ✓ |
| `reject()` | **no check** — and it accepts APPROVED as well as PENDING_APPROVAL, so anyone can mark an approved purchase FAILED, which reopens the PRA's remaining ceiling |
| `supersede()` | **no check**, and **no status check** on the original (a FAILED or already SUPERSEDED record can be superseded again) |
| HQ buying for a store | impossible: `create()` looks the PRA up with the caller's own tenant |

### Task

1. **PRA create and cancel → OWNER+** (was MANAGER+, BE-13). Changes BE-13's decision on
   the product owner's instruction; keep the 403 shape. HQ doesn't set a store's limits
   (stores are independent): a PRA is only created on the caller's own tenant, as now.
2. **Log a purchase** (`POST /realisasi`): MANAGER+ scope or the PROCUREMENT role, else
   403. TECHNICIAN, OPERATOR, ACCOUNTING and ADMIN without MANAGER+ are refused. DL-08
   unchanged.
3. **HQ buying for a store.** Optional `tenantId` on `CreateRealisasiRequest`, same rules
   as BE-14 item 2: omitted → caller's tenant; a different tenant only for the PROCUREMENT
   role or OWNER+, and only inside the caller's tree (outside → 400, same body as a
   missing id). The PRA is looked up in, and the Realisasi saved on, the target tenant.
   Ship together with BE-14 if convenient — same picker, same tenant check.
4. **Reject → OWNER+**, both from PENDING_APPROVAL and from APPROVED. Deciding on an
   over-limit purchase is the Owner's call either way, and failing an approved one
   reopens spending room.
5. **Supersede (correct) → the original's creator, or OWNER+**, else 403 — same pattern as
   BE-15's PR cancel. Add a status guard: only PENDING_APPROVAL or APPROVED originals can
   be superseded (400 otherwise). The correction still goes through `create()`, so items 2
   and 3 and DL-08 apply to it.

### Tests (rules 2 and 6)

- PRA create/cancel: MANAGER → 403 (was 201), OWNER → 201.
- Realisasi create: OPERATOR, TECHNICIAN, ACCOUNTING → 403; MANAGER, PROCUREMENT → 201;
  the Owner who created the PRA can still log it (exempt); a MANAGER-created PRA
  (pre-existing data) logged by that same MANAGER → still refused by DL-08.
- HQ PROCUREMENT logs for Outlet A against Outlet A's PRA → saved on Outlet A; a MANAGER
  passing another tenant → 403; a tenant outside the tree → 400.
- Reject: MANAGER → 403 on PENDING_APPROVAL and on APPROVED; OWNER → 200; the PRA's
  remaining reopens only after an OWNER reject (conversion factor ≠ 1).
- Supersede: creator → 200; another MANAGER → 403; OWNER → 200; superseding a SUPERSEDED
  or FAILED record → 400.
- Every new test fails against the unfixed code.

### Client follow-up (mobile repo, after merge)

- PROC-03 create button badge `MANAGER+` → `OWNER+`; disabled-state copy "needs Owner or
  above".
- PROC-04: Reject becomes gated `OWNER+`; "Supersede" becomes gated `CREATOR·OWNER+`;
  create gated `MANAGER+·PROC`; store picker for HQ PROCUREMENT (as BE-14).
- `auth/permissions.ts` mirrors; `API_CONTRACT.md` regenerated from source.

### What was done — DONE, 21 Oct 2026 (shipped together with BE-14)

1. **PRA create/cancel → OWNER+** (`requireOwnerScopeForLimits`), 403 below. PRAs stay on
   the caller's own store.
2. **Log a purchase** → MANAGER+ scope or the PROCUREMENT role, else 403. ADMIN without
   MANAGER+ is refused. DL-08 (`requireSegregationFromPraApprover`) unchanged.
3. **HQ buying for a store**: optional `tenantId` on `CreateRealisasiRequest` through the
   same `resolveTargetTenant` as BE-14, here allowing PROCUREMENT or OWNER+. PRA lookup,
   part lookups and the saved Realisasi all use the target store.
4. **Reject → OWNER+** from PENDING_APPROVAL and APPROVED alike.
5. **Supersede → creator or OWNER+**; the original must be PENDING_APPROVAL or APPROVED
   (400 otherwise). The correction is forced onto the original's store and still goes
   through `create()`, so items 2–3 and DL-08 apply to it.

Tests: `PurchaseGatesTest` (5 for BE-17): every case listed above, factor 6 throughout —
the PRA's remaining quantity stays 6 after a refused MANAGER reject and returns to 12 only
after the OWNER's. The two BE-13 PRA tests were rewritten from MANAGER+ to OWNER+.
Against the unfixed code every new test fails except the DL-08 guard, which is meant to
pass on both (it pins unchanged behaviour). Full suite 91/91.

---

## BE-18 — Paying back the Store Manager (reimbursement completion)

**Status: DONE, 22 Oct 2026** (see end of entry). Product owner's decisions, 19 Oct 2026
(module 1 deferred-decisions #5 "reimbursement completion + its SoD question" and #10
"`reimbursedTo`/`reimbursedAt` missing from the response"; supersedes BE-09's "don't build
`markReimbursed`" hold).

### Why

- When the Store Manager buys for the branch with their own money, **the store Owner pays
  them back**. HQ isn't involved: it's the branch's matter.
- **When** the Owner pays back is up to the branch: one purchase at a time, or several at
  once, on no fixed schedule. HQ's job is that **the Owner and the SM see the same, clear
  record** of what's owed and what's been paid, not to impose a process.
- Today `reimbursementStatus` goes `PENDING` and never moves: no endpoint marks it
  `REIMBURSED` `[C]`. `reimbursedTo` and `reimbursedAt` exist on `Realisasi` `[C]` but are
  not on `RealisasiResponse`.
- **The SM can't see what they're owed.** BE-06 redacts Realisasi cost below OWNER, so a
  Manager sees PROC-08 as names with no amounts — including the money owed to themself,
  money they typed in.

### Task

1. **Mark paid back.** `PATCH /realisasi/reimbursements/mark-paid`
   `{ ids: UUID[], paidAt: yyyy-MM-dd, method: CASH | TRANSFER, reference?: string }`.
   One id or several — one endpoint, so the Owner can settle one purchase or a whole pile.
   All-or-nothing: if any id isn't `PENDING` or isn't in the caller's store → 400 and none
   change. Sets `REIMBURSED`, `reimbursedAt`, and `reimbursedTo` = the Realisasi's
   `createdBy`. Writes a history row per record.
2. **Who.** The store's own OWNER (caller's tenant = the Realisasi's tenant), else 403. HQ
   PRINCIPAL can read, not mark — the store's money is the store's business. The person
   owed can never mark their own (settles the SoD question in #5).
3. **Undo.** `PATCH /realisasi/{id}/reimbursement/undo { reason }` `@NotBlank`, same OWNER
   rule; back to `PENDING`, payment fields cleared, reason in history.
4. **Tell the SM.** New `NotificationType.REIMBURSEMENT_PAID` to `reimbursedTo`: "Rp … paid
   back for {ref}" (one notification per mark-paid call, listing the records). Flyway
   migration for the `notifications.type` CHECK constraint.
5. **Response.** Add `reimbursedToName`, `reimbursedAt`, `reimbursementMethod`,
   `reimbursementReference` to `RealisasiResponse` (closes #10).
6. **Server-side filter.** `GET /realisasi?paymentMethod=PERSONAL_REIMBURSABLE&reimbursementStatus=PENDING`
   (and `REIMBURSED`), so PROC-08 stops filtering on the device.
7. **The person owed sees the amount.** Exception to BE-06: `totalCost` is returned
   unredacted to the caller when the caller is the Realisasi's `createdBy` **and** its
   payment method is `PERSONAL_REIMBURSABLE`. Nothing else about BE-06 changes.
8. **A rejected purchase is still owed.** Marking a Realisasi `FAILED` doesn't touch
   `reimbursementStatus`: the SM's money really was spent. The Owner settles it the same
   way. (Product default, recorded here so nobody "fixes" it.)

### Tests (rules 2 and 6)

- Mark-paid: store OWNER, 3 ids → all REIMBURSED, `reimbursedTo` = creator; one id already
  REIMBURSED → 400, none changed; MANAGER (incl. the creator) → 403; HQ PRINCIPAL → 403.
- Undo: OWNER with reason → PENDING; blank reason → 400.
- Notification sent once per call to the SM.
- Visibility: the SM creator sees `totalCost` on their own personal-reimbursable Realisasi,
  still null on a colleague's and on their own COMPANY_ACCOUNT ones.
- A FAILED Realisasi with a pending reimbursement can still be marked paid.
- Every new test fails against the unfixed code.

### Client follow-up (mobile repo, after merge)

- PROC-08: tabs "Owed" / "Paid back"; Owner gets select-several + "Mark paid back"
  (date, cash/transfer, reference); undo with reason. The SM sees their own amounts.
- The old "no mark-reimbursed endpoint" info card and callouts go.
- Notifications label "Paid back".

### What was done — DONE, 22 Oct 2026

1. **`PATCH /realisasi/reimbursements/mark-paid {ids, paidAt, method, reference?}`** —
   all-or-nothing; sets `REIMBURSED`, `reimbursedAt` (start of `paidAt`), `reimbursedTo` =
   the creator, method (`CASH`/`TRANSFER`, new enum `ReimbursementMethod`), reference. One
   history row per record: the purchase's own status doesn't change, so the row repeats it
   (`APPROVED → APPROVED`, or `FAILED → FAILED`) with reason "Paid back by transfer (BCA …)"
   — that's how it shows, with the actor, in the activity log.
2. **Who**: the store's own OWNER+. An id **outside** the caller's tree, or missing → 400
   "…nothing was changed" (no probing); a store's purchase seen **from HQ** → 403 "Only the
   store's own Owner…"; money owed to the caller → 403 (SoD #5). MANAGERs → 403.
3. **`PATCH /realisasi/{id}/reimbursement/undo {reason}`** — same rule, `REIMBURSED` →
   `PENDING`, payment fields cleared, reason on the history row.
4. **`REIMBURSEMENT_PAID`**: one notification per person owed per call, "Rp 1.051.150 paid
   back by … for REF1, REF2" (Indonesian number format). **Migration
   `V6__reimbursement_payment.sql`**: two columns (`reimbursement_method`,
   `reimbursement_reference`, no CHECK — enum validates) and `notifications_type_check`
   recreated (rule 3). Applied cleanly to the local DB `[V]`.
5. **Response**: `reimbursedToName`, `reimbursedAt`, `reimbursementMethod`,
   `reimbursementReference` (closes #10).
6. **`GET /realisasi?paymentMethod=…&reimbursementStatus=…`** — both optional, filtered on
   the server.
7. **The person owed sees `totalCost`** of their own PERSONAL_REIMBURSABLE purchase; every
   other cost field stays redacted for them, as do colleagues' and company-paid ones.
8. FAILED purchases with a pending reimbursement can still be paid back (tested).

The shared OWNER gate's message became "…approve, reject or settle a purchase" (it now
guards approve-escalated, reject and mark-paid). Tests: `ReimbursementTest` (7), all fail
against the old service/controller. Full suite 103/103. Postman: new `REALISASI` folder
(list with filters, mark-paid, undo).

---

## BE-19 — Expense claims: branch spending that isn't stock

**Status: CONFIRMED by the product owner, 20 Oct 2026. Screen record written 23 Oct 2026:
PROC-10 in `screens/01-procurement.md` (addendum) — ready to build.** Depends on BE-23 for receipt photos.

### Why

Branches spend on things that aren't parts and must not go into stock: stationery,
cleaning supplies, small items bought online. The procurement chain (PR → PRA → Realisasi
→ goods receipt) doesn't fit — logging them would mean creating "parts" like paper towels
and tracking their stock.

### Decided

- **A separate, lightweight expense record**: what, amount, date, receipt photo (BE-23).
  No request, no limits, no stock.
- **"Paid by: store / me."** The store Owner records expenses paid with the store's money;
  the SM records ones they paid personally. Both show here, so "other expenses" is *all*
  branch spending outside parts, not only reimbursements.
- **Paid personally → paid back** with BE-18's rules exactly: the store OWNER marks paid
  (one or several), undo with reason, the claimant notified and always sees their own
  amount.
- **Spending summary (BE-16)** gets a third line, **other expenses**, beside the parts
  spent figures. HQ reads it like the rest.
- **Two categories, two tabs: "HQ" and "Branch specific"** (`category` enum on the
  record). "HQ" = spending the branch does for HQ-related needs (e.g. something HQ asked
  every branch to buy); "Branch specific" = the branch's own running costs. **The branch
  pays for both** — this is a label for visibility, not a charge to HQ:
  - an SM who paid personally is **always paid back by their store Owner**, whatever the
    category (the SM belongs to the branch and is paid by its Owner);
  - **both categories count against the branch's monthly budget** (BE-16);
  - nothing in the app claims money back from HQ.
  HQ's spending view can show how much of each branch's spending is HQ-related.

### Rules

- Create: the store's MANAGER+ (SM, Owner). Paid-by "store" only by OWNER+.
- Edit: no edit; correct by void-and-recreate with a reason, like supersede.
- Money visible OWNER+, plus the claimant for their own (same exception as BE-18).

---

## BE-23 — File uploads: photos and receipts on Cloudflare R2

**Status: DONE (code), 21 Oct 2026 — the R2 bucket itself is the product owner's step
(task item 1); until its env vars are set, `/uploads` answers 503.** Product owner's
decision, 20 Oct 2026. Prerequisite for BE-20's goods photos and BE-19's receipt photos;
later for Realisasi receipts and module 3 labels.

### Why

`[C]` There is no file upload in the backend: `Realisasi.attachments` is a plain string and
the app asks for a URL ("not a file picker"). Photos must not go in Postgres (size, backups,
query weight) or on the Railway container disk (lost on redeploy unless a volume is
attached, and a volume ties files to one instance). Object storage is the standard answer.

**Chosen: Cloudflare R2** — S3-compatible (the AWS SDK works unchanged), no charge for
downloads, a free tier that covers a small franchise for a long time. Supabase Storage was
considered: it adds a whole platform (its own database, auth) where only storage would be
used, and its free projects pause when idle — not acceptable for production. Check current
R2 pricing and limits when setting up.

### Task

1. **R2 bucket + credentials** as Railway env vars (`R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`,
   `R2_SECRET_ACCESS_KEY`, `R2_BUCKET`). Bucket **private** — no public URLs.
2. **Upload by presigned URL**: `POST /uploads { purpose, contentType, sizeBytes }` →
   `{ uploadId, putUrl, expiresAt }` (PUT, ~10 min). The phone uploads straight to R2; the
   API server never carries the bytes. Allowed: `image/jpeg`, `image/png`, `image/webp`,
   `application/pdf`; max ~5 MB. Object key `{tenantId}/{purpose}/{uploadId}`.
3. **`Upload` record**: id, tenant, uploader, purpose, key, content type, size, created,
   `attachedTo` (type + id, null until used). An upload can be attached once, only by its
   uploader, only within its tenant. Unattached uploads older than 24 h are deleted by a
   scheduled job (R2 lifecycle rule or a Spring `@Scheduled` sweep).
4. **Viewing**: `GET /uploads/{id}` → a short-lived presigned GET URL, after the same tenant
   and visibility checks as the record it's attached to.
5. **Phone side (client follow-up)**: take or pick a photo, resize to ~1600 px long edge and
   JPEG ~0.7 before upload (~200–400 KB), upload, then send `uploadId`s with the record.
   Uploads go through the outbox so a bad signal never loses a photo.

### Tests

- Wrong content type or oversize → 400. Another tenant's upload attached → 400 (same body
  as a missing id). Attaching twice → 400. View URL refused across tenants.
- Presigning is unit-tested against a fake S3 endpoint; no real R2 calls in CI.

### What was done — 21 Oct 2026

- **Module `upload`.** `R2Storage` is the only class touching storage: AWS SDK 2.55.0 `s3`
  (Netty and Apache HTTP clients excluded; the JDK `url-connection-client` instead),
  endpoint `https://{R2_ACCOUNT_ID}.r2.cloudflarestorage.com`, region `auto`, path-style.
  `R2_ENDPOINT` optionally overrides it. Missing env vars → the app boots, `/uploads`
  answers 503 "File uploads are not configured on this server".
- **`POST /uploads {purpose, contentType, sizeBytes}`** → `{uploadId, putUrl, method:"PUT",
  headers:{Content-Type}, expiresAt}`, 10-minute URL. JPEG / PNG / WebP / PDF, ≤ 5 MB
  (exactly 5 MB allowed). **Content-Type and Content-Length are signed into the URL**, so
  R2 itself rejects a bigger or different file than declared — the declared size is
  enforced, not just checked. Key `{tenantId}/{purpose}/{uploadId}`.
  `purpose`: `GOODS_RECEIPT_PHOTO`, `EXPENSE_RECEIPT`, `PURCHASE_RECEIPT`.
- **`GET /uploads/{id}`** → 5-minute presigned GET. Unattached: uploader only. Attached:
  the caller's tenant tree (own + descendants), matching how the records that hold uploads
  are scoped. When BE-20/BE-19 attach to records with stricter visibility, revisit.
- **`UploadService.attach(uploadId, purpose, attachedType, attachedId)`** for BE-20 and
  BE-19 to call inside their own transaction. Refused as "Upload not found" (same as a
  missing id): another tenant's, another user's, wrong purpose, or **a file never actually
  PUT** (HEAD on the object). Second attach → "Upload is already attached"; the attach is a
  conditional `UPDATE … WHERE attached_type IS NULL`, so two racing attaches can't both win.
- **Sweep**: hourly `@Scheduled` deletes unattached uploads older than 24 h — object first,
  then row, so a storage error leaves the row for the next run instead of an orphan file.
- **Migration `V4__uploads.sql`** — `uploads` table, no CHECK on `purpose` (Java enum
  validates; new purposes need no migration). Applied cleanly to the local DB (`[V]`).
- `GlobalExceptionHandler` now honours `ResponseStatusException`'s status (it was a
  `RuntimeException`, so it would have become 400).
- Tests: `R2StorageTest` (4, real SDK presigner vs `http://localhost:9999`) and `UploadTest`
  (9, HTTP + DB with storage mocked). Mutation check: dropping the signed size makes the
  presign test fail. Full suite 80/80. Postman: folder `UPLOADS`.

**Not verified:** a real PUT/GET against R2 — needs the bucket. Once the env vars are set,
upload one file end to end before relying on it (`[?]` until then).

---

## BE-20 — Orders to HQ (who drafts, sends, cancels) and who receives goods

**Status: DONE, 22 Oct 2026** (see end of entry). Product owner's decisions, 19 Oct 2026 (module 1 deferred-decisions #9
"no scope gating on the PO path" and #6 "PO path has no creator≠receiver check"; also
reshapes the Realisasi receipt rule).

### Why

`PurchaseOrderService` `[C]`: `create()`, `sendToSupplier()` and `cancel()` have no scope
or role check. Linking approved PRs is optional, so an order of any size can be sent to the
centre without an Owner. `cancel()` works on ORDERED and PARTIALLY_RECEIVED orders too.
`PurchaseOrder` has **no `createdBy`**, which is why no creator≠receiver rule was possible.

`GoodsReceiptService.receive()` `[C]`: no check on who may receive, and `receivedBy` is
looked up with `userRepository.findById` from the request body — **any user id, from any
tenant**, the same class of bug BE-13 fixed for PR requesters.

How the business runs:

- **Urgent buys** — the SM buys and logs a Realisasi, within the Owner's limits (BE-17).
- **Planned restocks from the centre** — the SM drafts a PO, **the Owner sends it**:
  sending commits the store's money ("spent", BE-16).
- **The receiver is whoever opens the package** when it arrives. At a store that is
  usually the SM — who also bought it. Each received item is photographed.

### Task

1. **PO draft:** `create()` → MANAGER+ scope or the PROCUREMENT role, else 403. Add
   `createdBy` to `PurchaseOrder` (Flyway; existing rows null) and `createdByName` to the
   response.
2. **PO send:** `sendToSupplier()` → OWNER+, else 403.
3. **PO cancel:** DRAFT → the creator or OWNER+; ORDERED or PARTIALLY_RECEIVED → OWNER+
   only, with `{ reason }` `@NotBlank` written to history. Else 403.
4. **Receiver is the caller.** `receivedBy` must equal the authenticated user (or drop the
   field and take it from the token — preferred; keep accepting it for one release and
   400 when it differs). Receiving requires being in the receipt's store; tree checks as
   BE-12.
5. **PO path: no creator≠receiver rule** — deliberate. The Owner sends, the SM receives,
   and the centre knows what it shipped. Record that decision in `CLAUDE.md`.
6. **Realisasi path: self-receiving allowed with a photo.** Replace the hard
   "receiver ≠ Realisasi creator" refusal with:
   - receiver ≠ creator → as today;
   - receiver = creator → allowed **only with at least one goods photo**; the receipt is
     saved with `selfReceived = true` (Flyway column, on the response, filter
     `GET /goods-receipts?selfReceived=true`).
   - New `NotificationType.GOODS_SELF_RECEIVED` to the store's OWNER+ chain ("SM
     received their own purchase {ref} — photo attached"), so prevention becomes
     visibility. Flyway migration for the CHECK constraint.
   The Realisasi `attachments` (receipt) requirement is unchanged and separate: the
   receipt proves the purchase, the photo proves the goods arrived.
7. **Goods photos.** `goodsPhotoUploadIds: UUID[]` on the receipt, attached through
   **BE-23** (Cloudflare R2, decided 20 Oct 2026). Build BE-23 first; there is no file
   upload anywhere in the backend today `[C]`.

### Tests

- PO: OPERATOR/TECHNICIAN/ACCOUNTING create → 403; MANAGER creates, MANAGER send → 403,
  OWNER send → 200; DRAFT cancel by creator → 200, by another MANAGER → 403; ORDERED
  cancel by MANAGER → 403, by OWNER without reason → 400, with reason → 200.
- Receipt: `receivedBy` of a user in another tenant → 400; differs from the caller → 400.
- Realisasi path: creator receives without photo → 400; with photo → 201,
  `selfReceived = true`, Owner notified; a different receiver → `selfReceived = false`, no
  notification. Stock quantities with `conversionFactor` ≠ 1 (rule 2).
- Every new test fails against the unfixed code.

### Client follow-up (mobile repo, after merge)

- PROC-07: draft gated `MANAGER+·PROC`; **Send to supplier** becomes gated `OWNER+`;
  Cancel gated `CREATOR·OWNER+` (draft) / `OWNER+` (sent) with a reason field.
- PROC-05 form: "Received by" stays derived (the caller); realisasi path shows
  "You bought this — add a photo of the goods to receive it" and a camera/photo field
  instead of the hard block; the old "receiver must differ, no exemption" callout changes.
- PROC-05 list: a "Self-received" chip, filter.

### Not in this task

- **QR / barcode labels per item**, printed on an outlet sticker printer when goods are
  received — the product owner's idea. Belongs with module 3 (scan loop): non-consumable
  parts already get a `PartInstance` row per unit on receipt `[C]`, which is the natural
  thing a label points at; consumables (BBs) are tracked in aggregate, so they'd be
  labelled per box or lot, not per piece. Needs its own screen-inventory pass.

### What was done — DONE, 22 Oct 2026

1. **PO draft** → MANAGER+ or PROCUREMENT, else 403. `purchase_orders.created_by`
   (migration V7, existing rows null); `createdById`/`createdByName` on the response.
2. **PO send** → OWNER+, else 403 (checked before the lookup).
3. **PO cancel**: DRAFT → creator or OWNER+; ORDERED / PARTIALLY_RECEIVED → OWNER+ only,
   reason required (400 when blank), written to the history row with the actor. Body
   `{ reason }` optional for a DRAFT (`CancelPurchaseOrderRequest`).
4. **Receiver is the caller**, taken from the token. `receivedBy` is no longer required;
   when sent it must equal the caller (400). The PO / Realisasi is looked up in the
   caller's own tenant, so the receiver is always in the receipt's store.
5. **PO path: no creator≠receiver rule**, recorded in `CLAUDE.md`.
6. **Realisasi path**: the DL-11 refusal is gone. Buyer = receiver needs at least one goods
   photo (400 "You bought this: add a photo of the goods to receive it"); the receipt is
   saved `selfReceived = true` (`goods_receipts.self_received`, V7), on the response, and
   filterable (`GET /goods-receipts?selfReceived=true`). `GOODS_SELF_RECEIVED` goes to
   Owner+ in the store and above, never the receiver; V7 recreates
   `notifications_type_check` (rule 3).
7. **Goods photos**: `goodsPhotoUploadIds` on create and response, attached through
   `UploadService.attach` in the same transaction (someone else's photo → 400 "Upload not
   found", the whole receipt rolls back). New `UploadService.attachedIds`.

Found while doing this: **PO numbers were `"PO-" + millis`** on a unique column, so two
drafts in the same millisecond failed. Now `PO-<millis>-<random hex>`.

Tests: `OrdersAndReceivingTest` (11), all through HTTP and the real DB, storage mocked.
Realisasi lines use 2 pakets × factor 6 = 12 cans (rule 2; stock 5 → 17, 13 cans refused).
Against the unfixed services 9 of 11 fail; the two that pass are the pre-existing
over-receipt guard (kept as a rule-2 regression guard) and the PO-number check, which
can't force two requests into one millisecond and is only a sanity check. Full suite
114/114. Postman: PO cancel body, receipt `goodsPhotoUploadIds`, a Realisasi receipt
example, the `selfReceived` filter.

---

## BE-21 — HQ catalog and mandatory items: two ways a store buys

**Status: DONE, 22 Oct 2026**, HQ super admin included (V11, see side effect 3; was waiting on the person's name
and email; see the end of entry). Item 1 report 20 Oct, decisions 21 Oct. Product owner's decisions,
19 Oct 2026 (module 1 deferred-decision #7, "non-root outlet silently sees the root's
supplier list", and the supply model behind it).

### The supply model ("model C")

- **HQ sells what it sells.** Some items are **mandatory**: a store must buy them from
  HQ. Today: BB, gas, paper targets, plate targets, gel balls. The list will change after
  internal review, so it's data, not code.
- **Everything else** (parts, service/maintenance units, operational needs) the store may
  buy from HQ **or anywhere else**. No restriction for now.
- **The store decides when and how much** to restock, mandatory or not. HQ's role in
  restocking is business intelligence and recommendations only.
- So a store buys in exactly two ways:
  1. **Buy from HQ** — the order to HQ (PurchaseOrder, PROC-07). The store sees only
     what HQ offers: mandatory items plus anything else HQ sells.
  2. **Buy elsewhere** — the purchase record (Realisasi, PROC-04), **non-mandatory items
     only**. An urgent buy of a mandatory item is still a Realisasi, but only with HQ (the
     `franchiseCentre` supplier, BE-16) as the seller.
- **Mandatory ≠ consumable.** Both parts and consumables flow through all of this
  (`Part`, consumable via `PartCategory.consumable`, BE-04). Plate targets may not be
  consumable; a part may become mandatory later. Keep the two flags independent.
- Restock needs (low stock, burn rate) cover **all** items, mandatory or not — this is an
  ERP; operations break when a non-mandatory part runs out too.

### Why this needs an investigation first

`[C]` Parts are **per store**: `Part.tenant` is required, `currentStock` lives on the
`Part` row, `PartService.getAll()` reads `getEffectiveTenantIds()` (self + descendants, so a
store never sees HQ's parts), and `PurchaseRequestService.create()` looks a part up with
`findByIdAndTenantId(partId, callerTenant)`. So each store has its own copy of "6mm BBs".
An HQ catalog has to be HQ-owned while each store keeps its own stock.

### Task

1. **Investigate and propose** (report before building): how a store's part relates to an
   HQ catalog item. Options to weigh:
   - **Link (recommended):** HQ owns catalog items (root-tenant `Part` rows or a new
     `CatalogItem`); each store part gets a nullable `catalogItemId`. Stock stays on the
     store's row; the flags below are read from the catalog item. A store part for a
     catalog item is created automatically when the store first orders it.
   - **Copy:** HQ pushes catalog parts into each store with a reference back. Simpler
     reads, but flags and names drift.
   Also report how many existing store parts would map to which HQ items (live data,
   `[V]`).
2. **Two flags, set by HQ only** (ADMIN or PRINCIPAL at the root; 403 otherwise):
   `soldByHq` and `mandatoryFromHq` (mandatory implies sold). Flyway.
3. **HQ catalog read** for stores: `GET /catalog` (or a filter on parts) → the items HQ
   sells, with `mandatoryFromHq`. Any authenticated user in the tree.
4. **Buy from HQ:** `PurchaseOrder` items must be HQ catalog items with `soldByHq`, else
   400. A PO's supplier is the franchise centre (set automatically; the legacy optional
   `supplierId` stops mattering on this path).
5. **Buy elsewhere:** `Realisasi` items (and the PRA lines they consume) that are
   `mandatoryFromHq` are refused (400 "This item must be bought from HQ") **unless** the
   Realisasi's supplier is the `franchiseCentre` supplier.
6. **Where it was bought, outside HQ:** add free-text `sellerName` to `Realisasi`
   (e.g. "Shopee · Toko Airsoft Jaya"). The HQ supplier list stays HQ-managed and isn't
   needed for outside buys. HQ's spending data (BE-16) can then show which outside
   sellers stores use most, for the internal review.
7. **#7 closed by design:** supplier creation stays root-only (as today); stores see HQ's
   list on purpose. Client labels it "Managed by HQ".

### Item 1 report — investigation, 20 Oct 2026 (nothing built)

**Code `[C]`.** `Part` is a per-store row (`tenant_id NOT NULL`) carrying its own stock,
category and type; nothing on it points to anything HQ-owned. `PartCategory` and
`PartType` are *also* per-store copies, seeded by name into each new tenant
(`MasterDataInitializer`) — so "copy" is already how master data works here, and the open
duplicate-`part_categories` housekeeping item (`OPERION_BE_PLAN.md` §5.3) is that approach
drifting. **Ten tables** hold a foreign key to `parts.id`: `airsoft_unit_parts`,
`goods_receipt_items`, `part_instances`, `purchase_order_items`,
`purchase_request_authorization_items`, `purchase_request_items`, `realisasi_items`,
`service_event_parts`, `stock_adjustments`, `stock_movements` (`[V]`, `pg_constraint`).

**Data `[V]`, local DB (a copy of dev, same as prod's 17 Aug load).**

| Tenant | Parent | Parts | Notes |
|---|---|---|---|
| `BLITZ_BDG` | — (root) | 31 | The real store. Its own root: **no HQ above it** |
| `FRANCHISE_HQ` | — (root) | 0 | BE-10 test fixture |
| `OUTLET_2` | `FRANCHISE_HQ` | 1 | BE-10 test fixture ("Outlet2 Test Gear") |

- **0 store parts map to an HQ item:** no HQ tenant holds any part, and no part name occurs
  in more than one tenant.
- **None of the five mandatory items exists as a real part.** The only gas rows are test
  data ("Green Gas Test Can", "Case9 Gas ConvFactor"); no BB, targets or gel balls.
- No supplier represents HQ / the franchise centre (6 suppliers, all under `BLITZ_BDG`).

So the catalog starts empty: the choice below is about structure, not about migrating
existing data.

**Recommendation: Link, with the HQ catalog as the root tenant's own `Part` rows.**
Add a nullable self-reference `parts.catalog_part_id` (store part → HQ part) and the
flags `sold_by_hq`, `mandatory_from_hq` on `parts`, meaningful only on root rows.

1. **All ten referencing tables stay untouched.** Stock, part instances, burn rate,
   procurement lines and unit parts keep pointing at the store's own row.
2. **HQ needs its own stock anyway.** HQ sells and ships to stores, so it must track what
   it holds. A root `Part` row is both the catalog entry and HQ's stock record. A separate
   `CatalogItem` table would still need an HQ `Part` row beside it — two things to keep in
   step instead of one.
3. **Flags live in one place.** With Copy, `mandatory_from_hq` would be repeated on every
   store's row and drift, exactly as `part_categories` already has.
4. **The store row is created on first order**, copying name, brand and type, and
   mapping the category to the store's same-named category.

Costs: `PartService.getAll()` at HQ returns its descendants' parts too, so the catalog read
(item 3) must filter `tenant_id = root`. A name change on the HQ row doesn't rename store
rows unless the client shows the catalog name (the response can carry both).

**Blocking decision for the product owner — the real tenant tree.** Model C assumes the
root tenant is HQ. In the data, the real store is the root and there is no HQ. Before
BE-21 (or BE-16's "from HQ" split) can do anything useful, a real HQ tenant must exist
above `BLITZ_BDG`: one Flyway migration inserting the HQ tenant and setting
`BLITZ_BDG.parent_id` to it (root tenants are seed-only since BE-12). Needed: HQ's name and
code, and confirmation that `FRANCHISE_HQ` / `OUTLET_2` are test data to drop at the
clean-database go-live.

**Status after item 1:** ~~waiting on the product owner for (a) and (b)~~ — decided below.

### Decisions — product owner, 21 Oct 2026

**(a) Link, via the root tenant's own `Part` rows** — as recommended in the item 1 report.
No separate `CatalogItem` table.

**(b) The HQ tenant: "HQ BANDUNG".** Create it as a new root and make `BLITZ_BDG` its child.
- Name **`HQ BANDUNG`**, code **`HQ_BANDUNG`** (codes follow the `BLITZ_BDG` pattern:
  uppercase, underscores — confirm the code with the product owner if a shorter one is
  wanted; the name is theirs verbatim).
- One Flyway migration: insert the tenant, set `BLITZ_BDG.parent_id` to it. New roots are
  seed-only since BE-12, so a migration is the right path.
- `FRANCHISE_HQ` and `OUTLET_2` are test data (confirmed: the app isn't live; go-live is
  on a clean database). Leave them alone in this migration.

**Side effects to handle in the same change — check each in the code before relying on
this list `[?]`:**
1. **Suppliers move to HQ.** `SupplierService`, `PurchaseOrderService.create()` and
   `RealisasiService.create()` look suppliers up under `getRootTenantId()` `[C]`. Once
   `BLITZ_BDG` has a parent, its root becomes `HQ_BANDUNG` and the 6 suppliers owned by
   `BLITZ_BDG` stop being found. The migration must move them to `HQ_BANDUNG` — which is
   also decision #7: suppliers are HQ's.
2. **HQ's master data.** A new tenant normally gets its `part_categories` / `part_types`
   from `MasterDataInitializer`; make sure `HQ_BANDUNG` gets them, or catalog parts
   (item 2) can't be created.
3. **HQ users.** Nobody is at HQ yet and no user holds PRINCIPAL. **Decided 21 Oct: HQ's
   first account is a "super admin" — role ADMIN with PRINCIPAL scope at `HQ_BANDUNG`**,
   the top of the tree, so it can administer every branch and see every store's data.
   Create it in the migration (or move the existing ADMIN to HQ — report which ADMIN
   accounts exist at `BLITZ_BDG` today and ask the product owner for the person's name and
   email; never put a password in the migration or this file). HQ Procurement and other HQ
   accounts are then registered by that admin. Note for the product owner: one account with
   ADMIN + PRINCIPAL can do everything, including Owner-only approvals in any store and the
   DL-08 Owner/Principal exemption — keep it for administration, not daily buying.
4. **Everything that walks the tree** (`getEffectiveTenantIds`, `getAncestorTenantIds`):
   HQ now sees `BLITZ_BDG`'s data, and notifications for `BLITZ_BDG` may now also reach
   approvers at HQ. Run the existing suites; report any behaviour that changed.
5. **App theme** (mobile repo): decided 21 Oct — HQ uses Tactical Green too ("the main
   theme"). Done in the app: Tactical Green is now the default for every tenant
   (`theme/ThemeContext.tsx`); the design file stays in the light theme for now. No
   backend work.

**Next:** items 2–7 as written above, after the migration.

### Tests

- Flags: store MANAGER/OWNER setting either flag → 403; HQ ADMIN → 200.
- PO with a non-catalog item → 400; with catalog items → 201.
- Realisasi for a mandatory item from an outside seller → 400; same item with the
  franchise-centre supplier → 201; a non-mandatory item from an outside seller → 201.
- A PRA line for a mandatory item can only be consumed by a franchise-centre Realisasi.
- Stock quantities with `conversionFactor` ≠ 1 (rule 2).
- Every new test fails against the unfixed code.

### Client follow-up (mobile repo, after merge)

- PROC-07 becomes **"Order from HQ"**: item picker shows the HQ catalog only, mandatory
  items marked.
- PROC-04 **"Buy elsewhere"**: item picker hides mandatory items (unless the seller is
  HQ, for urgent buys); a "Seller" free-text field replaces the supplier picker for outside
  buys.
- PROC-06 shows "Managed by HQ".
- The menu and copy use the product owner's words: "Buy from HQ" / "Buy elsewhere".

### What was done — DONE, 22 Oct 2026 (except the super admin)

**Migration V8** (`V8__hq_bandung_and_catalog.sql`), confirmed against the local
PostgreSQL 18 (copy of dev) `[V]`: `HQ_BANDUNG` inserted as a root, `BLITZ_BDG.parent_id`
set to it, BLITZ_BDG's 6 suppliers moved to HQ, `suppliers.franchise_centre` (partial
unique index: one per tenant) plus HQ's own "HQ BANDUNG" franchise-centre supplier,
`parts.catalog_part_id` (FK, unique per store), `sold_by_hq`, `mandatory_from_hq` (CHECK:
mandatory implies sold), `realisasis.seller_name`. After boot, `MasterDataSyncRunner` gave
HQ_BANDUNG its 6 categories and 45 part types `[V]` (side effect 2: no code needed).

Side effects, checked `[C]`/`[V]`:
1. Suppliers: moved; `findByIdAndTenantId(…, root)` finds them again.
2. Master data: as above.
3. **HQ super admin: not created.** ADMIN accounts on the local DB today `[V]`:
   `admin@test.com` ("Admin", ADMIN, no scope) at BLITZ_BDG, and `owner_test@test.com`
   (ADMIN + OWNER) at FRANCHISE_HQ (test data). Waiting on the product owner for the
   person's name and email. Two ways once known: register them at HQ_BANDUNG (no API path
   for a root yet: `register` needs an ADMIN in the same tree, and nobody is at HQ), or move
   an existing ADMIN with one UPDATE (`tenant_id` → HQ_BANDUNG, `scope` → PRINCIPAL). Either
   way, no password in a migration.
   **Done 22 Oct 2026:** the product owner gave `tanugraha.richard@gmail.com`. **V11**
   inserts "Richard Tanugraha" (name read from the email; change via DB if wrong), ADMIN +
   PRINCIPAL at HQ_BANDUNG, **locked** (`password_hash = '!'`, matches nothing). There is no
   password-change endpoint, so `InitialPasswordBootstrap` (startup runner) sets the first
   password from `BOOTSTRAP_ADMIN_EMAIL` + `BOOTSTRAP_ADMIN_PASSWORD` (≥ 12 characters), and
   only on a locked account: it never overwrites a real password. Set them once, start,
   log in, remove them. `HqSuperAdminTest` (4): the V11 row, locked login refused,
   bootstrap then login works and a second run changes nothing, a real password and a
   short one are never applied, and the super admin's `GET /tenants/tree` is HQ_BANDUNG
   then BLITZ_BDG. V11 confirmed against the local PostgreSQL. Full suite 139/139.
4. **Tree walks:** HQ now sees BLITZ_BDG's data through `getEffectiveTenantIds`. And
   `approversIn(getAncestorTenantIds(…))` (NotificationService) already includes ADMIN and
   OWNER+ users of every ancestor, so once HQ has accounts they will get BLITZ_BDG's
   NEW_PURCHASE_REQUEST, REALISASI_ESCALATED and GOODS_SELF_RECEIVED notifications —
   while approve/reject look records up in the caller's own store only (`findOwned`), so HQ
   can't act on them. Nobody is at HQ today, so nothing changes yet. **Open question for the
   product owner:** should HQ get store approval notifications at all (BI only) or not?
   No code changed for this. **Decided 22 Oct 2026: no** → BE-24.

Items 2–7:
2. `PATCH /parts/{id}/hq-flags` (`soldByHq`, `mandatoryFromHq`, nulls unchanged). ADMIN role
   or PRINCIPAL scope signed in at the root, else 403; HQ rows only (a store part → 400
   "Part not found"); mandatory without sold → 400. Plus `PATCH /parts/{id}/catalog-link`
   (`catalogPartId`, null unlinks), same gate, so a store's existing "6mm BBs" can be
   linked instead of duplicated on its first order (not in the task list, needed for the
   31 BLITZ_BDG parts; one link per HQ item per store).
3. `GET /catalog`: the root's live `soldByHq` parts, name order, with `mandatoryFromHq`,
   `consumable`, and the caller's own `storePartId`/`storeCurrentStock`. `PartResponse`
   gains `catalogPartId`, `soldByHq`, `mandatoryFromHq` (read from HQ's row).
4. PO create: every line through `HqCatalogService.storePartForHqOrder` (HQ item id or the
   store's linked part; first order creates the store part — name/brand/lifespan/reorder qty
   copied, category and type mapped by name or created, stock 0). Not sold → 400 "Only items
   HQ sells can be ordered from HQ: …". Supplier = the root's franchise centre, set
   automatically; `supplierId` ignored; none set up → 400.
5. Realisasi create (and so supersede): a mandatory item without the franchise-centre
   supplier → 400 "This item must be bought from HQ: …", checked before anything is saved.
6. `sellerName` on request, entity and response.
7. Unchanged by design; `SupplierResponse.franchiseCentre` added so the client can label HQ.

Tests: `HqCatalogTest` (9): flags gate (store MANAGER/OWNER/ADMIN and an HQ MANAGER → 403;
HQ ADMIN and HQ PRINCIPAL → 200), mandatory-needs-sold, catalog read and isolation between
roots, first order creates one linked store part and later orders reuse it, non-catalog → 400,
no franchise centre → 400, mandatory item from Tokopedia or a free-text seller → 400 with
nothing consumed, same item with HQ as seller → APPROVED with the PRA line at 0 remaining
(2 pakets × factor 6 = 12 of 12, rule 2), non-mandatory from an outside seller with
`sellerName` → 6 of 12 remaining, linking gates. `OrdersAndReceivingTest`'s fixture now
gives the store's gas an HQ item and HQ a franchise-centre supplier. Against the unfixed
services/controllers all 9 new tests fail (the franchise-centre one after pinning its
message: unfixed, it also answered 400, for "Part not found"). Full suite 123/123.
Postman: CATALOG folder, `parts-hq-flags`, `parts-catalog-link`, `realisasi-create` with
`sellerName`, PO create bodies without `supplierId`.

---

## BE-22 — Automatic status changes name what triggered them

**Status: DONE, 22 Oct 2026** (see end of entry). Product owner's decision, 20 Oct 2026 (module 1 deferred-decision #12,
"the activity log's System actor covers two cases").

### Why

The procurement activity log (BE-07) shows a lock and "System" whenever a history row has
no actor. Two writers produce those rows `[C]`:

- `PurchaseRequestAuthorizationService.refreshStatus()` → `writeHistory(pra, previous,
  next, null, "Recomputed from Realisasi fulfillment")`, called after a Realisasi is
  created, approved, rejected or superseded.
- `GoodsReceiptService` (PO path) saves a `PurchaseOrderStatusHistory` with no
  `changedBy` and reason `"Goods receipt " + <uuid>` when an order becomes
  PARTIALLY_RECEIVED or RECEIVED.

Both run inside a real person's request, so every automatic change has a human cause —
and for an accountability log that cause is the useful part. The other source of
actor-less rows (written before actors were recorded) **only exists in test data**: the
product owner confirmed the app isn't live yet, so go-live starts from a clean database
and that case disappears.

### Task

1. **Record the trigger.** On every status-history table that can receive an automatic
   row (PRA, PO; check PR and Realisasi for any others), add `automatic boolean not null
   default false`, `triggered_by` (user FK, nullable) and `trigger_reference`
   (short human text). Flyway migration.
   - PRA refresh: `automatic = true`, `triggered_by` = the user whose Realisasi action
     caused it, reference e.g. "REA-2026-0114 approved".
   - PO auto-status: `automatic = true`, `triggered_by` = the receiver, reference = the PO
     number plus "goods received" — not a raw receipt UUID.
   - `changedBy` stays null on automatic rows: the person didn't make *this* change.
2. **Activity response.** `GET /procurement/activity` adds `actorKind`
   (`USER` | `AUTOMATIC` | `NOT_RECORDED`), `triggeredByName` and `triggerReference`.
   `NOT_RECORDED` = no actor and not automatic (old test rows); kept so the app never has
   to guess from an empty name.
3. **History endpoints.** The same three fields on `StatusHistoryResponse`.

### Tests

- Approving a Realisasi that fulfils its PRA → the PRA row is `AUTOMATIC`, triggered by the
  approving Owner, reference names the Realisasi.
- A goods receipt that completes a PO → `AUTOMATIC`, triggered by the receiver, reference
  names the PO number.
- Manual changes stay `USER`. A legacy row with no actor → `NOT_RECORDED`.
- Every new test fails against the unfixed code.

### Client follow-up (mobile repo, after merge)

- PROC-09 line 3: "Automatic · after Rina Wijaya — REA-2026-0114 approved" (lock icon,
  italic, the derived treatment); "Not recorded" greyed for `NOT_RECORDED`. The expanded
  card's "Changed by" row uses the same words.

### What was done — DONE, 22 Oct 2026

Writers checked `[C]`: only the two named above leave the actor empty. PR history (incl.
the ORDERED/APPROVED rows a PO writes and the AUTHORIZED rows a PRA writes) and Realisasi
history always record the person who acted, so only the PRA and PO history tables get the
columns.

1. **V10:** `automatic boolean not null default false`, `triggered_by` (FK users),
   `trigger_reference` on `purchase_request_authorization_status_history` and
   `purchase_order_status_history`. Existing actor-less rows stay `automatic = false`.
   - PRA refresh: `refreshStatus(praId, triggeredBy, triggerReference)`; callers pass
     the buyer and "{externalOrderRef} logged" (create), the Owner and "… approved" /
     "… rejected", the corrector and "… corrected" (supersede). The Realisasi has no
     "REA-…" number; its external order ref is what people know it by.
   - PO auto-status on goods receipt: the receiver and "{poNumber} goods received" (the
     raw receipt UUID reason is gone).
   - `changedBy` stays null on automatic rows.
2. **Activity:** `actorKind` (`USER` | `AUTOMATIC` | `NOT_RECORDED`, new enum `ActorKind`),
   `triggeredByName`, `triggerReference` on every entry; PR and Realisasi entries are USER
   or NOT_RECORDED. The `actorId` filter still matches `changedBy` only (automatic rows
   aren't the person's own change).
3. **History endpoints:** the same three fields on `StatusHistoryResponse` (PR, PRA,
   Realisasi, PO).

Tests: `AutomaticTriggerTest` (4): an Owner approving an escalated 2-paket × 6 purchase
that fills a 12-can limit → the PRA's FULFILLED row is AUTOMATIC, no changedBy, triggered
by the Owner, "… approved", in history and the activity log, while the Realisasi's own
approval stays USER; an in-limit purchase fulfilling on logging → triggered by the buyer,
"… logged"; a receipt completing a PO → AUTOMATIC by the receiver, "{poNumber} goods
received", while the Owner's ORDERED row stays USER; a legacy actor-less row →
NOT_RECORDED. Against the unfixed services all 4 fail. Full suite 135/135. Postman: no new
requests (response fields only).

---

## BE-24 — Store approval alerts stay in the store

**Status: DONE, 22 Oct 2026.** Product owner's decision, 22 Oct 2026, answering BE-21 side
effect 4: HQ should **not** get a store's approval alerts.

### Why

`NotificationService` sent NEW_PURCHASE_REQUEST, REALISASI_ESCALATED and (BE-20)
GOODS_SELF_RECEIVED to `approversIn(getAncestorTenantIds(store))`: ADMIN and OWNER+ users
of the store **and every tenant above it** `[C]`. Approve/reject only find records in the
caller's own store (`findOwned`), so once HQ BANDUNG had accounts (the super admin, V11),
HQ would be told about approvals it can't make. HQ follows stores through the activity
log, the spending summary (BE-16) and `GET /goods-receipts?selfReceived=true` instead.

### What was done

The three notifications now go to the store's own tenant only (`storeOnly(...)`), still
ADMIN + OWNER+ there, still never the person who acted. `TenantHierarchyService` is no longer
a dependency of `NotificationService`. Unchanged: the requester/procurement notifications
(BE-07/15/18) were already store- or person-scoped.

Tests: `StoreAlertsStayInStoreTest` (2): a Store Manager's new request, an over-limit
purchase and a self-received receipt reach the store's Owner, and neither an HQ ADMIN nor an
HQ ADMIN + PRINCIPAL. Against the unfixed code both fail. Full suite 141/141.

---

## BE-25 — Who changes stock and item data

**Status: OPEN.** From the module 2 screen inventory (`screens/02-inventory-stock.md`,
INV-04, INV-06, INV-07, Gate 3) and the product owner's decision of 23 Oct 2026 on the two
stock shortcuts.

### Why

`[C]` Nothing in the inventory or part modules checks who is acting:

- `POST /inventory/adjust` sets any item's stock immediately, no approval, no check
  (`PartStockService.java:127-147`) — the stock adjustment workflow can simply be skipped.
- `POST /inventory/receive` adds stock as `PURCHASE` with no purchase behind it (`:149-178`),
  and still decides "consumable" by category **name** (`:170`) — the check BE-04 replaced
  everywhere else.
- Stock adjustments: anyone may request; approve/reject is OWNER+ but **an Owner can
  approve their own**, and `findInHierarchy` (`StockAdjustmentService.java:184-192`) lets
  HQ approve a store's adjustment.
- Items (`POST/PUT /parts`, retire, restore), categories and types (CRUD and seed) have
  no checks; `minimumStock` (which raises "low") and a category's `consumable` flag
  (which changes how stock is counted) can be changed by anyone.

### Task

1. **The two shortcuts → ADMIN only** (decided 23 Oct): `/inventory/adjust` and
   `/inventory/receive` need the ADMIN role, a required `reason` (400 if blank), and write
   the actor into the movement (see BE-26 item 2). Anyone else → 403. In `receiveStock`,
   replace the name check with `part.isConsumable()`.
2. **Stock adjustments:** request — the store's MANAGER+ (else 403); approve/reject — the
   **store's own** OWNER+ (`findOwned`, not `findInHierarchy`). **Requester ≠ approver
   `[?]`:** recommended — allowed when they're the same person, but the adjustment is saved
   `selfApproved = true` and shown as such (same choice as BE-20's self-received). Confirm
   with the product owner before building.
3. **Notify the store's Owner+** when an adjustment is requested: new
   `NotificationType.STOCK_ADJUSTMENT_REQUESTED`, store-only like BE-24, never the
   requester. Flyway for the CHECK constraint.
4. **Items:** create/edit → the store's MANAGER+, or ADMIN; retire/restore → OWNER+, or
   ADMIN. Else 403.
5. **Categories and types** (CRUD and seed) → ADMIN only.

### Tests (rules 2 and 6)

Every gate above both ways; an Owner's own adjustment → `selfApproved` (or 403, per the
confirmed rule); HQ Principal approving a store's adjustment → not found; `receive` of a
renamed consumable category creates no part instances; stock quantities with a
conversion factor ≠ 1 where receipts are involved. Every new test fails against the
unfixed code.

### Client follow-up (mobile repo)

Inventory screens INV-04/06/07 gated accordingly; no screen calls the two shortcuts
except an ADMIN-only data-repair action.

---

## BE-26 — Inventory reads: store on every row, item history, consumables in "used today"

**Status: OPEN.** From the module 2 screen inventory's gap list (INV-03, INV-05, INV-08,
INV-09).

### Why

`[C]` The stock, low-stock, burn-rate and adjustment lists already walk the caller's tree,
so HQ receives every store's rows — but **no row says which store it belongs to**
(`LowStockResponse`, `BurnRateResponse`, `StockAdjustmentResponse`, `PartResponse`), so
HQ can't tell stores apart and can't lay out "Outlet Bandung's items" to link to its
catalog. Per-item stock history is stored (`StockMovementRepository
.findByPartIdOrderByCreatedAtDesc`) but no endpoint returns it, and movements record no
actor. The dashboard's "used today" counts `MAINTENANCE_USAGE` only
(`PartStockService.java:226-232`), not `CONSUMABLE_TAKE`. Burn rate reads the
`MaintenancePolicy` of the **caller's** tenant (`BurnRateService.java:80`).

### Task

1. **Store on every row:** `tenantId`, `tenantName` on `LowStockResponse`,
   `BurnRateResponse`, `StockAdjustmentResponse`, `PartResponse`; plus `catalogPartId` on
   the stock and burn-rate rows. Optional `?tenantId=` filter on `GET /parts`,
   `/inventory/stock-summary`, `/inventory/low-stock`, `/burn-rate` (inside the caller's
   tree, else 400 "Store not found"). This also closes the two module 1 workarounds in the
   mobile `API_CONTRACT.md` (HQ picking parts and PRAs for a store — add `tenantId` to
   `PurchaseRequestAuthorizationResponse` too).
2. **Item history:** `GET /parts/{id}/movements` (newest first, `limit`), from
   `findByPartIdOrderByCreatedAtDesc`, tree-scoped like the other reads. Add `actorId` /
   `actorName` to `StockMovement` (Flyway, null on old rows) and fill it everywhere stock
   moves.
3. **"Used today"** counts `MAINTENANCE_USAGE` and `CONSUMABLE_TAKE`.
4. **Burn-rate policy per item:** use the part's own store's `MaintenancePolicy`.
5. Optional: `daysOfCover`, `suggestedQty`, `burnRateSource` on `stock-summary` rows, so
   INV-01 is one call.

### Tests

HQ caller sees two stores' rows told apart by `tenantId`; `?tenantId=` outside the tree →
400; a movement written by an approval carries the approver; "used today" counts a
consumable take (conversion factor ≠ 1, rule 2); burn rate for a store item uses the
store's policy, not HQ's (rule 6: a non-default policy value).

---

## BE-27 — One definition of "low", and tell the Store Manager

**Status: OPEN.** Product owner's decision, 23 Oct 2026 (module 2, INV-02).

### Why

`[C]` "Low" exists twice: `minimumStock` (inventory lists, dashboard, `LOW_STOCK`
notification) and burn rate (`daysOfCover` vs `targetDays`, `belowLevel`). They can
disagree. `LOW_STOCK` goes to ADMIN or OWNER+ in the store only
(`NotificationService.notifyLowStock`), never the Store Manager, who files the request.

### Task

1. **One rule** (decided): an item is low when, with usage data (`COMPUTED` /
   `MANUAL_RATE`), `daysOfCover < targetDays`; otherwise when `currentStock ≤
   minimumStock`. Out of stock is `currentStock ≤ 0` either way. Return the trigger on
   each row: `lowBecause: RUNS_OUT_SOON | BELOW_MINIMUM | null`.
2. Use it in `low-stock`, `stock-summary` status, the dashboard counts, and `LOW_STOCK`.
3. **`LOW_STOCK` recipients:** the store's MANAGER+ and ADMIN, in the store only (as
   BE-24). Fire when an item **becomes** low by the rule (not on every movement while
   low).

### Tests

An item with usage data and plenty above minimum but 2 days of cover → low,
`RUNS_OUT_SOON`; an item with no usage data below minimum → low, `BELOW_MINIMUM`; the
notification reaches the store's Manager and not HQ; fires once on the crossing, not
again on the next take. Conversion factor ≠ 1 on the takes that move days of cover.

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
