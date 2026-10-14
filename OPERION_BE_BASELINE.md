# Operion Backend — Baseline Reference

**Updated:** 14 Oct 2026 for BE-08, BE-11, BE-07 (§1.4, §1.5, §4.3, §4.7, §4.9, §4.10, §5.1); 12 Oct 2026 for BE-12 (auth/tenant hardening — §0, §1.2–§1.5, §2.6, §5.1, §5.5, §5.6).
**Generated:** 22 Aug 2026, from direct source reading (evidence tier `[C]` throughout — see `CLAUDE.md` §"A summary is evidence tier [D]"). Every claim below traces to a specific file; nothing here is reconstructed from `OPERION_BE_PLAN.md` or other planning docs without independently re-reading the code.

**Purpose:** this is the thing to hand a fresh session (human or AI) that needs to know "what does Operion's backend actually do right now" — one level below `CLAUDE.md`'s summary, one level above reading the whole `src/main/java` tree yourself. `CLAUDE.md` still auto-loads every session and stays the terse rules/conventions file; this document is the detailed module-by-module reference it points to.

**Scope:** Java Spring Boot backend only (`c:\demo\src\main\java\com\example\operion`). Client apps (React Native outlet app, React/Vite web console) are out of scope.

---

## 0. Architecture at a glance

- **Modular monolith**, one Spring Boot app, one PostgreSQL database, no per-module deployment boundary. 18 modules under `module/`: `airsoft`, `analytics`, `auth`, `barcodeblock`, `burnrate`, `dashboard`, `inventory`, `maintenance`, `notification`, `part`, `parthistory`, `partinstance`, `procurement` (6 entities), `serviceevent`, `stockadjustment`, `tenant`, `unitpart`, `workorder`.
- **Auth**: stateless JWT, `JwtAuthenticationFilter` (a `OncePerRequestFilter`), no session state. Every endpoint requires a valid JWT except `POST /auth/login` (`SecurityConfig.java`, `.anyRequest().authenticated()`). `POST /auth/register` was also public until BE-12 (12 Oct 2026); it is now ADMIN-only.
- **RBAC — two independent dimensions**, both plain columns on `users`, no join tables:
  - **Scope** (linear, nullable): `SUPERVISOR &lt; MANAGER &lt; OWNER &lt; PRINCIPAL` (`Scope.java`). Compared by ordinal via `ScopeContext.hasAtLeast(min)`.
  - **Functional role** (flat, `NOT NULL`): `ADMIN, TECHNICIAN, PROCUREMENT, ACCOUNTING, OPERATOR` (`Role.java`).
  - **Critical finding, true across the entire codebase**: `Scope` is carried only in a custom `ThreadLocal` (`ScopeContext`), never placed into Spring Security's `GrantedAuthority` set. `@PreAuthorize` can therefore only ever gate on functional `Role` (as `hasRole('ADMIN')`/`hasAuthority('ROLE_ADMIN')`), never on `Scope`. Every scope-based check in the app (Owner-only approvals, cost redaction) is hand-written inside service methods, not declarative security.
  - Grep-confirmed: `@PreAuthorize` appears in exactly **four** places in the whole codebase (after BE-12) — `UserController` (`/users/{id}/scope`), `AuthController.register`, `TenantController.create`, `AirsoftUnitController.create`. Everything else that's role/scope-gated is gated by hand in service code, or not gated at all.
- **Tenant hierarchy**: `Tenant.parent` self-FK, arbitrary depth (no cap enforced). `TenantHierarchyService` provides three directions, used inconsistently by design across modules: `getEffectiveTenantIds` (self + descendants — used by most list/dashboard endpoints), `getAncestorTenantIds` (self + ancestors — used by notification fan-out and supplier root-scoping), `getRootTenantId` (top of the ancestor chain).
- **Response envelope**: `ApiResponse&lt;T&gt;{success, message, data}` is the intended convention, but it is **not universal** — `TestController` and `PartCategoryController.update()` return bare objects, and `GlobalExceptionHandler`'s error shapes (`{timestamp, error}` / `{timestamp, error, details}`) don't match `ApiResponse` at all. See §9.1.
- **JWT claims**: `userId` (subject), `role`, `tenantId`, `scope` (may be a literal absent value). 24h expiry. Signing secret falls back to a **known-committed dev value** if `JWT_SECRET` env var isn't set (`application.properties:26-30`) — production must always set it explicitly (see `CLAUDE.md` Deployment section).
- **Deployment**: live on Railway since 16 Aug 2026 — full detail in `CLAUDE.md` §"Deployment", not repeated here.

---

## 1. Auth, Tenant &amp; RBAC

### 1.1 Entities

**`User`** (`module/auth/entity/User.java`, table `users`) — `id, email (unique, NOT NULL), passwordHash, fullName, role (enum, NOT NULL), scope (enum, nullable), tenant (ManyToOne, NOT NULL)`. `role` is always present; `scope` can legitimately be absent for the entire lifetime of a user.

**`Tenant`** (`module/tenant/entity/Tenant.java`, table `tenants`) — `id, code (unique, NOT NULL), name (NOT NULL), active (NOT NULL, default true), parent (self-ManyToOne, nullable)`. No `@OneToMany children` mapped — child lookup is a repository query, not JPA-navigable downward. `active` defaults `true` and **nothing in the codebase ever sets it `false`** — no deactivate/suspend endpoint exists anywhere.

Legacy/dead tables still physically present in the schema but confirmed by full-codebase grep to have **zero** Java references: `roles`, `user_roles`, `user_profiles`. These are `pg_dump`-baseline leftovers from a superseded design, not active. `CLAUDE.md` already documents "One `Role` enum only, no `roles`/`user_roles` table pair" — this confirms it at the SQL level too.

### 1.2 REST endpoints

| Verb/Path | Auth | Notes |
|---|---|---|
| `POST /auth/register` | `hasRole('ADMIN')` **plus** tenant-tree containment (BE-12, 12 Oct 2026 — was Public) | `role` is **hardcoded to `OPERATOR` server-side**; the DTO has **no `scope` field** (a `scope` key in the body is ignored). `tenantCode` is optional and defaults to the calling admin's own tenant; it must be the admin's tenant or a descendant. **The tenant must already exist** — no auto-create-tenant path. Still returns a JWT *for the new user* (unchanged response shape). |
| `POST /auth/login` | Public | Unknown email → generic `RuntimeException` → **400**. Wrong password → `BadCredentialsException` → **401**. Inconsistent status for what's conceptually the same "invalid credentials" case. |
| `GET /auth/me` | Any JWT | Returns id/email/fullName/role/scope/tenantCode. |
| `PATCH /users/{id}/scope` | `hasRole('ADMIN')` **plus** tenant-tree containment (`TenantHierarchyService.requireWithinCallerTenantTree`, → 403) | **The only way to grant scope** since BE-12. No scope requirement on the calling admin at all — an `ADMIN` with no scope can promote anyone in their hierarchy straight to `PRINCIPAL` (deliberately left for the permission-matrix decision; a "can't grant above your own scope" rule would lock out today's null-scope admins). |
| `PATCH /users/{id}/role` | `hasRole('ADMIN')` **plus** tenant-tree containment; never your own role (BE-07, 14 Oct 2026) | The only API path to set a functional role; added so `PROCUREMENT` users can exist for BE-07's notifications and log. |
| ~~`GET /test`, `GET /admin`, `GET /api/test/me`~~ | — | **Deleted (BE-12).** Now 404. |
| `POST /tenants` | `hasRole('ADMIN')`; `parentId` must be in caller's tree; **root tenant (no `parentId`) requires `PRINCIPAL`** (BE-12) | No user holds `PRINCIPAL` today, so root tenants effectively come only from Flyway seeds (as `V2__seed_default_tenant.sql`). |
| `GET /tenants/{id}`, `GET /tenants/{id}/children` | Any JWT, but `id` must be caller's tenant or a descendant (BE-12) | Containment checked **before** lookup — out-of-tree ids answer 403 whether or not they exist. Ancestors are not readable; own `TenantResponse` already carries `parentName`. |

> **Baseline miss, recorded per `CLAUDE.md` rule 1:** the 22 Aug version of the register row above said "`scope` is optional, defaults to absent" — accurate, but it never asked what an *optional, caller-supplied* scope on a *public* endpoint allows. It allowed anyone who found the public Railway URL to register as `PRINCIPAL` (verified at test level against pre-BE-12 code: `scope=PRINCIPAL` in the body was persisted). A field description is not a capability analysis.

### 1.3 JWT → request pipeline (`JwtAuthenticationFilter`)

- Skips `/auth/login` only (`/auth/register` stopped being skipped in BE-12 — it needs the admin's JWT parsed). If no `Bearer` header, just continues the filter chain unauthenticated — it's the global `.anyRequest().authenticated()` rule, not this filter, that actually rejects unauthenticated requests.
- On a present token: sets `TenantContext.setTenantId(...)` and `ScopeContext.setScope(...)` (both plain `ThreadLocal`s, not Spring request-scoped beans) *before* building the `Authentication` object, whose only granted authority is `ROLE_&lt;role&gt;`.
- `finally { TenantContext.clear(); ScopeContext.clear(); }` — always cleared post-request.
- Invalid/expired signature → responds `401 "Invalid JWT token"` directly from the filter, bypassing `GlobalExceptionHandler` entirely (happens before `DispatcherServlet`).
- `CustomUserDetailsService` exists, implements `UserDetailsService` correctly, but is **never injected or referenced anywhere** — confirmed dead code; the filter builds `Authentication` manually from JWT claims instead.

### 1.4 Where scope is actually enforced (exhaustive list, whole-codebase grep for `ScopeContext.hasAtLeast`)

| File:line | Gate | Effect |
|---|---|---|
| `partinstance/service/PartInstanceService.java:285` | `OWNER` | Redacts `landedCost` on `PartInstance` responses |
| `stockadjustment/service/StockAdjustmentService.java:178` | `OWNER` | Gates stock-adjustment approve/reject (→ 403 since BE-12) |
| `procurement/service/PurchaseRequestService.java:202` | `OWNER` | Gates PR approve/reject (→ 403 since BE-12) |
| `procurement/service/RealisasiService.java:400` | `OWNER` | Gates escalated-Realisasi approval (→ 403 since BE-12) |
| `procurement/service/RealisasiService.java:441` | `OWNER` | Redacts cost fields on `Realisasi` responses |
| `tenant/service/TenantService.java` `create()` | `PRINCIPAL` | Gates root-tenant creation (BE-12) |
| `PurchaseRequestAuthorizationService.requireManagerScope()` | `MANAGER` | Gates PRA create and cancel (BE-13) |
| `PurchaseOrderService` / `PurchaseRequestAuthorizationService` / `ServiceEventService` mappers | `OWNER` | Redact PO cost (BE-11), PRA spend + remaining, service-event cost (BE-13) |
| `ProcurementActivityService.requireLogAccess()` | `OWNER` (or ADMIN/PROCUREMENT role) | Gates the procurement activity log (BE-07) |

Nothing in `auth` uses `ScopeContext`. Of the roles other than `ADMIN`, only `PROCUREMENT` is checked anywhere, and only in two places since BE-07: read access to `GET /procurement/activity`, and as the recipient set for `PURCHASE_AUTHORIZATION_CREATED`. `TECHNICIAN`, `ACCOUNTING` and `OPERATOR` are still **stored and returned but never checked**. Roles can now be changed via ADMIN-only `PATCH /users/{id}/role` (BE-07; tree-contained, never your own); before that only by DB edit.

### 1.5 Notable gaps (auth/tenant/RBAC)

1. ~~`TenantController` — zero authorization~~ — **closed, BE-12** (§1.2).
2. Functional roles beyond `ADMIN` are unenforced everywhere except the BE-07 procurement log (§1.4).
8. ~~`POST /purchase-requests` takes `requestedBy` unvalidated~~ — **closed 14 Oct 2026**: the requester must be a user of the caller's own tenant (the filing branch); anyone else answers 400 "Requesting user not found in your branch", same as a missing id. Filing on behalf of a same-branch colleague still works; history records the real caller.
9. ~~`POST /purchase-authorizations` (and `PATCH /{id}/cancel`) has no gate~~ — **closed 14 Oct 2026**: both need MANAGER scope or above (product owner's choice), 403 otherwise (change queue BE-13).
3. `CustomUserDetailsService` — dead code.
4. Login error-status inconsistency (400 vs 401 for two flavors of "wrong credentials").
5. ~~`GlobalExceptionHandler` has no handler for `AccessDeniedException`~~ — **closed, BE-12.** The 400 was confirmed (`[V]`, local, MockMvc against pre-fix code) before the fix; denials now answer 403 `{timestamp, error}`. Same change made the catch-all honour Spring's own `ErrorResponse` statuses — unknown URLs were 500, now 404; wrong methods could never be 405, now are.
6. No documented/implemented path to create the very first `ADMIN` user other than a direct DB edit. Since BE-12 this is also the only way to bootstrap *any* user on a fresh database (registration needs an ADMIN JWT).
7. **Unauthenticated requests answer 403, not 401** — no `AuthenticationEntryPoint` is configured, so Spring Security's default `Http403ForbiddenEntryPoint` applies. Clients can't distinguish "log in again" from "not allowed". (Invalid/expired tokens *do* get 401, from the filter — §1.3.)

---

## 2. Digital Twin — Airsoft Unit Lifecycle

Covers `airsoft`, `unitpart`, `serviceevent`, `maintenance`, `workorder`, `parthistory`.

### 2.1 `AirsoftUnit` (module `airsoft`)

Table `airsoft_units`: `serialNumber (unique, NOT NULL), name (NOT NULL), brand, model, type, operationalSeverity (enum NORMAL/WARNING/CRITICAL), status (enum ACTIVE/MAINTENANCE/BROKEN/UPGRADE/RETIRED, NOT NULL), healthScore (Integer, defaults 100), purchaseDate, notes, tenant`.

Endpoints (`/airsoft-units`): `POST` (**`hasAuthority('ROLE_ADMIN')`** — the one `@PreAuthorize` in this whole cluster) creates with `status` hardcoded `ACTIVE`; `GET` (list, any authenticated user, `healthScore`/`operationalSeverity` **not** included in the list DTO); `GET /{id}/overview` (any authenticated user, full aggregation — see §2.6).

**Confirmed gap: no path back to `ACTIVE`.** `ACTIVE` is set only at creation. Every subsequent status write (from `ServiceEventService.create` or `UnitOperationalRuleService.evaluate`) only ever pushes toward `MAINTENANCE`/`CRITICAL`. `BROKEN`, `UPGRADE`, `RETIRED` are read in `DashboardService`'s count queries but **nothing in the codebase ever assigns them** — three enum values that are structurally unreachable.

`UnitOperationalRuleService.evaluate(unit)`: `failedParts&gt;=1 OR healthScore&lt;40 → CRITICAL + status=MAINTENANCE`; `wornParts&gt;=3 OR healthScore&lt;70 → WARNING` (status untouched); else `NORMAL`. Only two call sites: `AirsoftUnitPartService.replacePart` and `.updateCondition`. **Not called from `installPart`** — a freshly-installed part never triggers a re-derivation.

### 2.2 `AirsoftUnitPart` (module `unitpart`)

Table `airsoft_unit_parts`: `tenant, airsoftUnit, part, condition (enum NEW/GOOD/WORN/FAILED/RETIRED/DONOR/REFURBISHED), status (INSTALLED/REMOVED), technicianAssessment, installedDate, removedDate, notes`. Does **not** extend `BaseEntity` — manually duplicates `createdAt`/`updatedAt`, inconsistent with sibling entities.

Endpoints (`/unit-parts`) — **no `@PreAuthorize` anywhere**, any authenticated user: `POST /install`, `POST /{id}/replace`, `PATCH /{id}/condition`, `GET /{unitId}` (currently-installed only), `GET /history/{unitId}` (full history). A dead, commented-out duplicate `POST /unit-parts/replace` route is left in the controller file.

**`installPart`** — rejects if the physical `Part` is already installed anywhere, or if the target unit already has an installed part of the same `PartType` (one active part per part-type per unit — slot-based). New unit-part row saves with `condition=null` (stays unset until a later condition update). **Stock effect**: `PartStockService.adjustStock(-1, MAINTENANCE_USAGE, UNIT_INSTALL)`. Does **not** recalculate health/severity, does **not** log a service event.

**`replacePart`** — the richest cross-module workflow in the codebase:
1. Validates same `PartType`, rejects self-replacement, rejects if the replacement part is already installed elsewhere.
2. Old part → `REMOVED`, condition set from the request; writes a `PartConditionHistory` row.
3. New part → new row, **condition hardcoded to `GOOD`** regardless of the physical part's real state.
4. **Always creates a `ServiceEvent`** (`eventType=REPAIR`), independent of whether a `workOrderId` was supplied.
5. Calls `UnitHealthService.recalculateHealth` then `UnitOperationalRuleService.evaluate`.
6. Calls `WorkOrderService.autoCompleteForPart` — auto-closes any open work order tracking the old unit-part (this bypasses `WorkOrderService.complete()`'s maintenance-schedule-advance side effect — see §2.5 gap).
7. **Stock effect**: consumes 1 unit of the *new* part only; the removed part is never returned to inventory.

**`updateCondition`** — records history, recalculates health/severity, and **if the new condition is `FAILED` and no open work order exists**, auto-creates one (`priority=CRITICAL`, "Replace Failed Part"). This is the one place a condition change auto-generates corrective work.

**Response mapping quirk**: `AirsoftUnitPartService.map()` sets both `category` and `categoryName` to the same value — redundant duplicate field.

### 2.3 `ServiceEvent` (module `serviceevent`)

Table `service_events`, extends `BaseEntity`: `tenant, airsoftUnit, technician (nullable), workOrder (nullable), eventType (MAINTENANCE/REPAIR/UPGRADE, NOT NULL), issue, actionTaken, notes, cost, serviceDate (defaults now), nextCheckDate`.

Endpoints (`/service-events`) — no `@PreAuthorize`: `POST`, `GET` (all, tenant hierarchy), `GET /work-order/{id}`.

**Confirmed business rule**: `ServiceEventService.create` **unconditionally sets `unit.status = MAINTENANCE` for all three event types**, including `UPGRADE` and even a completed `MAINTENANCE` check. Combined with §2.1's "no path back to ACTIVE," logging *any* service event permanently parks the unit at `MAINTENANCE` in the current implementation. Does not recompute health/severity, does not touch any work order even if `workOrderId` was attached.

### 2.4 `maintenance` module — two parallel, disconnected recommendation engines

**`MaintenancePolicy`** (table `maintenance_rule`, one row per tenant, `tenant_id unique`): `healthThreshold(50), maxWornParts(2), maxRefurbishedParts(3), maxRemovedParts(5, declared but never read anywhere), maintenanceIntervalDays(90), lowStockMultiplier(1.00), partEndOfLifeWarningDays(30), minimumHealthForOperation(60), autoCreateWorkOrder(true), burnRateWindowDays/MinObservationDays/MinEvents/daysOfCoverTarget (nullable, consumed by the burn-rate module, not this one)`. No `MaintenancePolicyController` exists (per `CLAUDE.md`) — a tenant without a pre-seeded row gets a hard failure on any preventive-maintenance evaluation.

**`MaintenanceSchedule`** (table `maintenance_schedules`): `tenant, airsoftUnit, title, description, intervalDays, lastMaintenanceDate, nextDueDate, autoGenerateWorkOrder(true), active(true)`.

**Engine #1 — `MaintenanceRecommendationEngineService`**, wrapped by `MaintenanceRecommendationService.generate()`. Produces plain strings from hardcoded (non-policy) thresholds: health&lt;40, any WORN/REFURBISHED/DONOR/FAILED part, age past `expectedLifespanDays`, last service &gt;90 days, &gt;25 maintenance-usage stock movements in 30 days. **Every recommendation is wrapped with `severity` hardcoded to the literal string `"INFO"`**, `category` always null. This is the engine that feeds `GET /maintenance/recommendations/{unitId}` **and** the unit-overview screen — a FAILED-part message reading "must be replaced immediately" is tagged the same generic "INFO" as everything else.

**Engine #2 — `PreventiveMaintenanceService.evaluateUnit`** — the real, policy-driven, production engine, run by a daily scheduler (below). Produces `MaintenanceRecommendation{severity: LOW/MEDIUM/HIGH/CRITICAL, title, message}` from real `MaintenancePolicy` thresholds: overdue schedule (HIGH), any FAILED part (CRITICAL), too many WORN/REFURBISHED (MEDIUM), health below threshold (HIGH) or below minimum-for-operation (CRITICAL), never serviced (MEDIUM), interval overrun (MEDIUM), low replacement stock (LOW), approaching/exceeded end-of-lifespan (MEDIUM/HIGH). Auto-creates a work order only if `priority != LOW` and `policy.autoCreateWorkOrder`, explicitly by design (code comment): a pure LOW finding is "a procurement concern, not a work-order concern." Fires `NotificationService.notifyPartEndOfLife` for EOL findings.

**These two engines share no code and disagree on severity for the same condition** — flagged as the single biggest duplication finding in this cluster.

**Scheduling**: `MaintenanceScheduleJob` (`scheduler/`, `@Scheduled(cron="0 0 2 * * *")`, daily 02:00) iterates every `AirsoftUnit` across **all tenants** and calls `PreventiveMaintenanceService.evaluateUnit` — the only automatic trigger of Engine #2. By contrast, `MaintenanceRecommendationService.processDueSchedules()` (the `MaintenanceSchedule`-interval-based auto-work-order generator) is **only reachable via the manual `POST /maintenance/process-due` endpoint** — no `@Scheduled` job calls it, so the recurring-schedule feature is effectively dormant unless something external polls that endpoint.

Dead code: `ReplacementSuggestionResponse` DTO has zero producing service and zero controller endpoint — an unfinished "suggest a replacement part" stub.

### 2.5 `WorkOrder` (module `workorder`)

Table `work_orders`, **not** `BaseEntity`-derived: `tenant, airsoftUnit, airsoftUnitPart (nullable), assignedTechnician (nullable), maintenanceSchedule (nullable), priority (LOW/MEDIUM/HIGH/CRITICAL), status (OPEN/ASSIGNED/IN_PROGRESS/WAITING_PARTS/COMPLETED/CANCELLED, defaults OPEN), title, description, targetDate, assignedAt/createdAt/updatedAt/startedAt/completedAt/completedDate`.

Endpoints (`/work-orders`) — no `@PreAuthorize` anywhere: `POST`, `GET` (paginated), `PATCH /{id}/status`, `PATCH /{id}/complete`, `GET /unit/{unitId}`, `PATCH /{id}/assign`, `PATCH /{id}/start`, `PATCH /{id}/waiting-parts`, `GET /dashboard`.

**Confirmed state-machine inconsistency — the biggest single gap in this cluster**: `assign()`, `start()`, and `waitingParts()` each validate their own preconditions rigorously (e.g. `start()` requires status ≠ `OPEN`, "assign technician before starting"). But the generic **`PATCH /{id}/status` endpoint performs zero validation** — it accepts any `WorkOrderStatus` and unconditionally overwrites, meaning a caller can jump `OPEN → COMPLETED` directly or move a `COMPLETED` order back to `OPEN`, bypassing every rule the dedicated verb endpoints enforce. It also does **not** trigger the maintenance-schedule-completion side effect (below) even when set to `COMPLETED` — only the dedicated `complete()` method does that.

`complete()` — the one path that closes the loop back to `MaintenanceSchedule`: if `maintenanceSchedule != null`, calls `MaintenanceRecommendationService.completeSchedule` (advances `lastMaintenanceDate`/`nextDueDate`). `autoCompleteForPart()` (called from `replacePart`, §2.2) bulk-completes WOs directly without going through `complete()`, so a schedule-linked WO closed this way **won't** advance its schedule — a second instance of the same inconsistency. `autoCloseOpenWorkOrders(unitId)` exists but has **zero callers anywhere** — dead public method.

### 2.6 `PartConditionHistory` (module `parthistory`)

Table `part_condition_history`, extends `BaseEntity`: `airsoftUnitPart, previousCondition, newCondition, reason, technicianAssessment`. No `tenant` FK of its own — scoping is implicit via the parent unit-part. Written only by `replacePart` (removal) and `updateCondition` (manual change) — `installPart` never writes a history row.

Endpoints (`/part-history`): `GET /unit-part/{id}`. (`GET /test` and the service's duplicated query + `System.out.println` dump were removed in BE-12.) The controller injects `PartStockService` but never calls it (unused field). The service carries a dead, unused `map(StockMovement)` method.

### 2.7 Overview aggregation — `AirsoftUnitOverviewService`

Fan-outs to `AirsoftUnitPartService.getInstalledParts`, `ServiceEventService.getRecentByUnit` (top 5), and `MaintenanceRecommendationService.generate` (Engine #1 — the weaker, "INFO"-only engine). Also independently re-derives worn/failed counts via string-matching on the DTO's condition field — a second, redundant computation of numbers already computed server-side by `UnitOperationalRuleService`.

### 2.8 Cross-module authorization summary for this cluster

Across all six modules, the **only** `@PreAuthorize` is `AirsoftUnitController.create`. Everything else — installing/replacing/removing parts, marking a part `FAILED` (which auto-generates a work order), creating/assigning/completing work orders, logging service events, evaluating maintenance, processing due schedules — is reachable by **any authenticated user regardless of role**.

---

## 3. Operational Control — Parts, Inventory &amp; Barcode Tracking

Covers `part`, `partinstance`, `inventory`, `burnrate`, `stockadjustment`, `barcodeblock`.

### 3.1 `Part`, `PartCategory`, `PartType` (module `part`)

**`Part`** (table `parts`): `tenant, name (NOT NULL), brand, category (ManyToOne, NOT NULL), legacyCategory (write-only — set on every create/update, never read anywhere; dead column), partType (nullable), expectedLifespanDays, reorderQuantity (NOT NULL, default 10 — doubles as the burn-rate bulk-buy override, see §3.3), active (default true), retired/retiredAt/retirementReason, currentStock (NOT NULL, default 0 — the single source of truth), minimumStock (NOT NULL, default 0), manualDailyUsage, manualReorderPoint, notes`.

`Part.isConsumable()`: `category != null &amp;&amp; category.getConsumable() == true`. Javadoc explicitly names this "the single call site every service should use instead of comparing `getCategory().getName()`" — see §3.7 for a violation of this rule in `PartStockService`.

Endpoints (`/parts`): `POST` (requires `categoryId`+`partTypeId`, validates type belongs to category), `GET` (active only, tenant hierarchy), `PUT /{id}` (partial update), `PATCH /{id}/retire` (blocked if `currentStock&gt;0` or the part is currently installed anywhere), `PATCH /{id}/restore`.

`PartCategory` (table `part_categories`): `name, description, consumable (Boolean, column `is_consumable`, nullable — pre-existing rows are backfilled explicitly, not defaulted)`. Endpoints (`/part-categories`): CRUD + `POST /seed` (idempotent seed of 6 canonical categories — only "Consumable" has `consumable=true`). `PUT /{id}` returns a **bare `PartCategoryResponse`**, not wrapped in `ApiResponse` — inconsistent with the rest of the API.

`PartType` (table `part_type`): `category (NOT NULL), name, description`. Endpoints (`/part-types`): CRUD + `POST /seed` (idempotent seed of ~40 default types, assumes categories already seeded).

### 3.2 `PartInstance` (module `partinstance`) — the barcode lifecycle

Table `part_instances`: `tenant, part, barcode (NOT NULL, DB-level UNIQUE **globally**, not tenant-scoped), status (IN_STOCK/TAKEN/INSTALLED/EXHAUSTED, NOT NULL default IN_STOCK), receivedAt, takenAt/takenBy, exhaustedAt, installedUnitPart (nullable), notes, landedCost (only populated from a Realisasi-sourced goods receipt — null for manual receipt and the legacy PO path)`.

Endpoints (`/part-instances`), no scope gate beyond authentication: `POST /scan-in`, `POST /take`, `POST /exhaust`, `GET /part/{partId}`. `landedCost` is redacted (nulled) below `Scope.OWNER` in the response mapper — same BE-06 pattern as Realisasi (§4).

**Two ways instances are created**: (a) `generateInstances(part, qty[, landedCost])` — auto-issues N system barcodes (`"PI-" + 12 random hex chars`), called from goods-receipt paths, "never call for a Consumable-category part" per its own javadoc; (b) `scanIn()` — **gated to Consumable-category parts only** (`!part.isConsumable() → throw`), rejects duplicate barcodes, does **not** touch `Part.currentStock`.

**`take()`** — requires `IN_STOCK`, sets `TAKEN`. **Stock effect only if `isConsumable()`**: decrements stock by 1 (`CONSUMABLE_TAKE`/`PART_INSTANCE`). Non-consumable takes have zero stock effect — deliberately, because sparepart stock is decremented exactly once, at `installPart`/`replacePart`, "regardless of whether the instance was taken first."

**`exhaust()`** — gated to Consumable-category only, requires `TAKEN`, sets `EXHAUSTED`. No further stock movement — the decrement already happened at `take()`; there is no return-to-stock/reversal path.

**`markInstalled()`** — called from `unitpart` when a barcode is supplied on install/replace. Allowed from `IN_STOCK` or `TAKEN`. **Notably does not itself enforce a "sparepart only" guard the way `exhaust()` does** — relies entirely on callers only ever resolving sparepart barcodes.

**No-double-counting invariant, precisely**: for consumables, the only decrement is in `take()` (gated `isConsumable()==true`). For spareparts, the only decrement is in `installPart`/`replacePart`, unconditional on barcode presence; `take()` explicitly skips the decrement when `isConsumable()==false`. These two sites are mutually exclusive by construction — confirmed no code path fires both for the same physical unit.

**Confirmed gap**: nothing reconciles "count of `IN_STOCK` `PartInstance` rows" against `Part.currentStock` for consumables — the two numbers are only kept consistent by scanning discipline, not by any code-enforced invariant.

### 3.3 `burnrate` module

Entirely computed-on-read, nothing stored (`BurnRateService`). `GET /burn-rate` (whole catalog, one grouped query set) and `GET /burn-rate/{partId}`.

Defaults, used whenever the tenant's `MaintenancePolicy` row/field is null: `windowDays=30, minObservationDays=21, minEvents=10, targetDays=14`.

`observationDays = max(1, min(windowDays, daysSinceFirstTaken))` — clamped both directions, `firstSeen` is all-time earliest `takenAt`, not window-limited. Stays `null` if never taken.

**Mode selection, strict priority order**:
1. **COMPUTED** — only if `isConsumable() &amp;&amp; observationDays&gt;=minObservationDays &amp;&amp; takes&gt;=minEvents`. Spareparts can never reach COMPUTED even if their take-volume clears the thresholds.
2. **MANUAL_RATE** — `manualDailyUsage != null`; permanent, not a cold-start fallback.
3. **MANUAL_LEVEL** — `manualReorderPoint != null`; no burn rate computed at all.
4. **NONE** — `burnRate` stays `null`, deliberately never `0` (`0` would render as maximum urgency).

`daysOfCover = currentStock / burnRate` (only if burnRate&gt;0). `suggestedQty`: for COMPUTED/MANUAL_RATE, `topUp = ceil(targetDays*burnRate - currentStock)`, floored at 0 — but **whenever `topUp&gt;0` and `Part.reorderQuantity` is set (which it always is, `NOT NULL` default 10), the computed top-up is discarded entirely in favor of the flat `reorderQuantity`** (explicit anti-"suggests 13 cans instead of the real ~100-can pattern" design). For MANUAL_LEVEL, `belowLevel = currentStock&lt;=manualReorderPoint`; if below, same `reorderQuantity` override applies.

### 3.4 `inventory` module — the stock ledger

`StockMovement` (table `stock_movements`, immutable audit row): `movementType (INITIAL_STOCK/PURCHASE/MAINTENANCE_USAGE/ADJUSTMENT/RETURN/CONSUMABLE_TAKE), referenceType (MANUAL/WORK_ORDER/PURCHASE/STOCK_ADJUSTMENT/UNIT_INSTALL/PART_INSTANCE), referenceId, quantity, beforeStock, afterStock, notes`.

**`PartStockService.adjustStock(...)`** is the single funnel every stock-affecting call in the codebase goes through: computes `after`, throws `"Insufficient stock"` if negative, persists, fires `notifyLowStock` **only on the down-crossing edge** (`before&gt;minimumStock &amp;&amp; after&lt;=minimumStock` — a one-shot notification, not a repeat-while-below-threshold alert), writes the audit row.

Endpoints (`/inventory`): `POST /adjust` → `manualAdjustment` (`ADJUSTMENT`/`MANUAL`) — **confirmed gap: no scope check, no approval workflow**, a direct bypass of the entire `stockadjustment` module's Owner-gated approval flow (§3.5). `POST /receive` → `receiveStock`. `GET /dashboard`, `GET /low-stock`, `GET /stock-summary`.

**Confirmed gap — inconsistent consumable check**: `PartStockService.receiveStock()` decides whether to auto-generate `PartInstance`s by string-comparing `category.getName().equals("Consumable")`, directly contradicting `Part.isConsumable()`'s own javadoc mandate, and contradicting how **both** `GoodsReceiptService` paths (§4.7) correctly use the boolean flag. A renamed or differently-named-but-flagged category would break this specific endpoint while the procurement-driven receiving paths stay correct.

Dead code: `PartStockService.map(StockMovement)` and two `StockMovementRepository` finder methods have zero call sites.

### 3.5 `stockadjustment` module — the approval-gated path

`StockAdjustment` (extends `BaseEntity`): `part, requestedBy, quantity (signed), reason, status (PENDING/APPROVED/REJECTED, default PENDING)`. `StockAdjustmentStatusHistory` tracks transitions.

Endpoints (`/stock-adjustments`): `POST` (any user, creates PENDING), `GET`, `GET /pending`, `PATCH /{id}/approve` (**Owner-scope gated**, applies the signed `quantity` verbatim, subject only to the negative-stock guard in `adjustStock`), `PATCH /{id}/reject` (same gate, no stock effect), `GET /{id}/history`.

**Confirmed gap**: no self-approval prevention — nothing stops the requester from also approving their own request if they hold `OWNER`+ scope (unlike the explicit no-exemption DL-11 rule in `GoodsReceiptService.receiveAgainstRealisasi`).

### 3.6 `barcodeblock` module (DL-06 offline code allocation)

`BarcodeCounter` (singleton row, `lastValue`) and `BarcodeAllocation` (audit record of each issued block: `deviceId, prefix, padWidth, rangeStart, rangeEnd`) — no FK between them, related only implicitly by non-overlapping ranges.

`BarcodeAllocationService.issue()`: acquires the counter row via `@Lock(PESSIMISTIC_WRITE)` (a real `SELECT ... FOR UPDATE`, not optimistic versioning), advances `lastValue` by `blockSize` (default 500, max 5000), persists both the counter update and the allocation audit row in the same transaction — this pessimistic lock is what makes range reservation atomic under concurrent issuance. Fixed `prefix="OFF-"`, `padWidth=10`.

This barcode generator is entirely separate from `PartInstance.generateBarcode()` (§3.2), which uses random hex codes with no counter/locking — collision risk there is handled purely by the DB unique constraint on `part_instances.barcode`, with no retry-on-collision logic.

### 3.7 Cross-module stock wiring summary

| Caller | Effect |
|---|---|
| `PartInstanceService.take()` (consumables only) | `-1` via `CONSUMABLE_TAKE`/`PART_INSTANCE` |
| `AirsoftUnitPartService.installPart()` | `-1` via `MAINTENANCE_USAGE`/`UNIT_INSTALL`, unconditional on barcode presence |
| `AirsoftUnitPartService.replacePart()` | `-1` on the *new* part only, via `MAINTENANCE_USAGE`/`WORK_ORDER` |
| `GoodsReceiptService` (both PO and Realisasi paths) | `+qty` then conditionally `generateInstances` — correctly uses `Part.isConsumable()` |
| `PartStockService.receiveStock()` (manual `/inventory/receive`) | `+qty` then conditionally `generateInstances` — **incorrectly** uses category-name string match |
| `StockAdjustmentService.approve()` | applies the requested signed quantity via `ADJUSTMENT`/`STOCK_ADJUSTMENT` |
| `BurnRateService` | reads `PartInstance.takenAt` history only — burn rate is driven entirely by barcode "take" events, never by `StockMovement` rows |

### 3.8 Other confirmed gaps in this cluster

- `AirsoftUnitPartService` has a field `private final PartStockService stockAdjustmentRequest;` — typed `PartStockService` but named after an unrelated, imported-but-unused DTO class. Confusing, worth a rename.
- No cross-check invariant that a `BarcodeAllocation`'s range doesn't overlap a prior one beyond the correctness of the single locked `issue()` method — a future second write path to `BarcodeCounter.lastValue` would go undetected.

---

## 4. Procurement — PR → PRA → Realisasi, and the legacy PR → PO path

Module `procurement`, six entities: `Supplier`, `PurchaseRequest`, `PurchaseOrder`, `GoodsReceipt`, `PurchaseRequestAuthorization`, `Realisasi`. No `@PreAuthorize` anywhere in this module — every gate is inside service methods via `ScopeContext`.

### 4.1 Two parallel purchasing paths, both live by design

Per explicit class-level Javadoc on `PurchaseOrder`/`GoodsReceipt` (dated 28 Jul 2026), the split is intentional:
- **Legacy/internal**: `PurchaseRequest → PurchaseOrder` (outlet→center restock only) `→ GoodsReceipt.purchaseOrder`. `PurchaseOrder.supplier` predates the split — "new code should not rely on a PurchaseOrder carrying a real external supplier."
- **Direct-purchase**: `PurchaseRequest → PurchaseRequestAuthorization → one-or-more Realisasi → GoodsReceipt.realisasi`.

`GoodsReceiptService.receive()` requires **exactly one** of `purchaseOrderId`/`realisasiId` and dispatches accordingly — both branches are fully implemented, neither is dead.

### 4.2 Entities

- **`Supplier`**: `tenant, name (NOT NULL), contact`. Managed centrally — `create()` throws unless `tenantId == rootTenantId` ("Suppliers are managed centrally — only the top-level tenant can create one").
- **`PurchaseRequest`**: `tenant, requestedBy, status (PENDING/APPROVED/REJECTED/ORDERED/AUTHORIZED/CANCELLED)`, links to `purchaseOrder` and/or `purchaseRequestAuthorization` once resolved. Item: `part, quantity` (no price).
- **`PurchaseOrder`**: `tenant, supplier (nullable, legacy), poNumber (unique, generated as `"PO-" + System.currentTimeMillis()` — not collision-safe under concurrent same-millisecond requests, would surface as an unhandled DB exception), orderDate, status (DRAFT/ORDERED/PARTIALLY_RECEIVED/RECEIVED/CANCELLED), totalAmount`. Item: `part, quantity, price` (flat price, no landed-cost concept on this path).
- **`GoodsReceipt`**: `tenant, purchaseOrder (nullable), realisasi (nullable), receivedBy, receivedDate, notes`. Item: `part, realisasiItem (nullable — populated only on the Realisasi path), quantity`.
- **`PurchaseRequestAuthorization`** ("PRA", extends `BaseEntity`): `tenant, approvedBy` — **creation is the authorization act**, per its own class doc, no separate approve step exists. `status (ACTIVE/PARTIALLY_FULFILLED/FULFILLED/CANCELLED, default ACTIVE)`. Item: `part, authorizedQty, maxValue` — **per-line ceilings only, no header-level total cap anywhere on the entity**.
- **`Realisasi`** (extends `BaseEntity`) — class doc: "a record of a purchase that already happened... never overwritten in place: a correction creates a new row with `supersedes` pointing at this one." Fields: `purchaseRequestAuthorization (required), createdBy, externalOrderRef (unique), channel, supplier (nullable), paymentRef, paymentMethod (COMPANY_ACCOUNT/PERSONAL_REIMBURSABLE), reimbursementStatus (NOT_APPLICABLE/PENDING/REIMBURSED, default NOT_APPLICABLE), reimbursedTo/At, purchasedAt`, **six separate cost components** (`subtotal, sellerDiscount, platformVoucher, shipping, insurance, serviceFee`) plus computed `totalCost`, `attachments (TEXT)`, `varianceStatus (WITHIN_CEILING/ESCALATED)`, `retroPurchaseFlag (default false)`, `status (PENDING_APPROVAL/APPROVED/FAILED/SUPERSEDED, default PENDING_APPROVAL)`, `approvedBy`, `supersedes (self-FK, nullable)`, `supersededReason`. Item: `purchaseRequestAuthorizationItem, part, purchaseUom, conversionFactor, purchasedQty (purchase UOM, not stock units), actualUnitPrice, allocatedLandedCost`.

Audit-trail inconsistency: `PurchaseRequestAuthorization`/`Realisasi`/`Notification`/all `*StatusHistory` entities extend `BaseEntity` (JPA-auditing `createdAt`/`updatedAt`); the older `Supplier`/`PurchaseRequest`/`PurchaseOrder`/`GoodsReceipt` set `createdAt` manually in `@PrePersist` and have **no `updatedAt` at all**.

### 4.3 Endpoints

| Controller | Notable endpoints |
|---|---|
| `SupplierController` (`/suppliers`) | `POST` (root-tenant only), `GET` |
| `PurchaseRequestController` (`/purchase-requests`) | `POST` (fires `notifyNewPurchaseRequest`), `GET`, `GET /{id}`, `PATCH /{id}/approve` (**OWNER+**), `PATCH /{id}/reject` (**OWNER+**), `PATCH /{id}/cancel` (no gate), `GET /{id}/history` |
| `PurchaseOrderController` (`/purchase-orders`) | `POST` (requires all linked PRs `APPROVED`, sets them `ORDERED`, notifies each requester), `GET`, `GET /{id}` (**no cost redaction — see §4.6**), `PATCH /{id}/send`, `PATCH /{id}/cancel` (reverts linked PRs to `APPROVED`), `GET /{id}/history` |
| `PurchaseRequestAuthorizationController` (`/purchase-authorizations`) | `POST` (requires linked PRs `APPROVED`, sets them `AUTHORIZED` — this *is* the authorization), `PATCH /{id}/cancel`, `GET`, `GET /{id}` (returns computed per-item purchased/remaining qty/value), `GET /{id}/history` |
| `RealisasiController` (`/realisasi`) | `POST` (**no scope restriction on creation at all**), `PATCH /{id}/approve` (**OWNER+**, escalated only), `PATCH /{id}/reject`, `POST /{id}/supersede`, `GET`, `GET /{id}` (cost-redacted below OWNER), `GET /{id}/history` |
| `GoodsReceiptController` (`/goods-receipts`) | `POST`, `GET` (list, BE-08: tenant tree, newest first, optional `from`/`to`/`receivedBy`/`path` = `PO` or `REALISASI`), `GET /{id}` (BE-08: out-of-tree answers like missing), `GET /purchase-order/{poId}`, `GET /realisasi/{realisasiId}` |

### 4.4 PRA ceiling semantics — exact mechanics

**Ceiling consumption** is computed live, never stored: `approvedPurchasedQtyInStockUnits`/`approvedPurchasedValue` sum **only `RealisasiItem`s whose parent `Realisasi.status == APPROVED`**. `FAILED`, `SUPERSEDED`, and `PENDING_APPROVAL` items never count. This single design choice ("APPROVED-only aggregation," per code comment) is what makes "a failed realisasi doesn't kill the PRA" and partial fulfillment both fall out for free with zero mutable "remaining" column. Quantity aggregation converts `purchasedQty × conversionFactor` before summing (HALF_UP rounding).

**On create**, `RealisasiService.create()` computes `alreadyQty/alreadyValue` (existing APPROVED consumption) plus the new line, sets `escalate=true` if the new total exceeds `authorizedQty` **or** `maxValue`. If escalated: `status=PENDING_APPROVAL`, `varianceStatus=ESCALATED`, history row written, `notifyRealisasiEscalated` fires. **If not escalated, the Realisasi auto-approves on creation** (`status=APPROVED` directly) — there is no explicit approval step for in-ceiling purchases. Escalated ones require `approveEscalated()` (OWNER+).

`PurchaseRequestAuthorizationService.refreshStatus()` runs after every create/approve/reject and recomputes the PRA header status (`FULFILLED`/`PARTIALLY_FULFILLED`/`ACTIVE`) purely from aggregate per-item remaining — confirmed genuinely derived/recomputed, never a mutable column.

### 4.5 Realisasi creation — other confirmed details

- `totalCost = subtotal − sellerDiscount − platformVoucher + shipping + insurance + serviceFee`.
- **Segregation of duties**: if the PRA's `approvedBy` equals the Realisasi's `createdBy`, throws **unless** the approver's *stored* scope (historical fact, not live `ScopeContext`) is `OWNER`+.
- `retroPurchaseFlag` defaults `false` if omitted; no other logic branches on it in this service (presumably consumed elsewhere for reporting — not found to gate any rule here).
- `reimbursementStatus` auto-set: `PENDING` if `paymentMethod==PERSONAL_REIMBURSABLE`, else `NOT_APPLICABLE`. `paymentMethod` itself is required input, never auto-set.
- Per-line `allocatedLandedCost` computed at creation: proportional to `lineSubtotal / headerSubtotal` (value-based allocation), zero if `headerSubtotal&lt;=0`.
- `externalOrderRef` uniqueness checked in-service before any further processing (in addition to the DB unique constraint).

### 4.6 Landed cost at goods receipt — `GoodsReceiptService.receiveAgainstRealisasi`

- Requires `Realisasi.status==APPROVED`.
- **DL-11, no exemption**: receiver must differ from the Realisasi's creator, with **no scope carve-out** — explicitly contrasted in the code comment with the PRA-approver/Realisasi-creator rule, which *does* exempt OWNER+.
- **Attachment required before receipt**: `attachments` must be non-blank or the call throws — attachments are optional at Realisasi-creation time (so urgent/retro purchases aren't blocked from being logged) but mandatory before real inventory moves.
- **Over-receipt ceiling** and **landed-cost divisor** both correctly use `purchasedQty × conversionFactor` (stock units) — `unitLandedCost = allocatedLandedCost / purchasedStockUnits` (4dp HALF_UP), computed against the line's *total purchased* quantity (not just this specific receipt), because a line can be received across multiple partial receipts.
- Consumables skip `PartInstance` generation entirely on both receiving paths — only `currentStock` moves.

The parallel `receiveAgainstPurchaseOrder` path has no landed-cost division (flat `price` only), no attachment requirement, and no receiver/creator segregation check (no "creator" concept on that path).

### 4.7 Cost redaction — BE-06 / BE-11, verified current status

| Entity | `map()` redaction | Status |
|---|---|---|
| `Realisasi` | `canSeeCost = ScopeContext.hasAtLeast(OWNER)`; nulls all six cost components, `totalCost`, and item `actualUnitPrice`/`allocatedLandedCost` below that | **Done (BE-06)** |
| `PartInstance` | Same pattern, `landedCost` field | **Done (BE-06)** |
| `PurchaseOrder` | Same pattern in `PurchaseOrderService.map()`: header `totalAmount` and item `price` | **Done (BE-11, 14 Oct 2026)** |
| `PurchaseRequestAuthorization` items | `purchasedValue` and `remainingValue` nulled below OWNER; **`maxValue` (the buyer's limit) and all quantities stay visible**. `remainingValue` goes too because limit − remaining = spend | **Done, 14 Oct 2026** (BE-13) |
| `ServiceEvent` | `cost` nulled below OWNER in `ServiceEventService.mapToResponse()` (every endpoint) | **Done, 14 Oct 2026** (BE-13) |

### 4.8 `supersede()` — correction mechanics (lives on `RealisasiService`, not the PRA service)

1. Original → `status=SUPERSEDED`, `supersededReason` set, history row written.
2. `praService.refreshStatus()` re-runs on the original's PRA — the ceiling reopens immediately (SUPERSEDED doesn't count per §4.4).
3. **Reuses the full normal `create()` path** for the correction — including re-running the escalation check and segregation-of-duties check against *current* PRA consumption.
4. The new entity gets `supersedes = original` set after creation.

Genuinely append-only: the original row's data is never mutated except `status`/`supersededReason`.

### 4.9 Notification, Dashboard, Analytics

**`notification` module** — polling only, no WebSocket/SSE/broker/scheduled job; a plain `notifications` table read via `GET`. `NotificationType`: `NEW_PURCHASE_REQUEST, PURCHASE_REQUEST_ORDERED, PART_END_OF_LIFE, LOW_STOCK, REALISASI_ESCALATED`, plus since BE-07 (14 Oct 2026, migration V3): `PURCHASE_REQUEST_APPROVED`, `PURCHASE_REQUEST_REJECTED` (with reason), `PURCHASE_REQUEST_AUTHORIZED` (all three to the PR's `requestedBy`), and `PURCHASE_AUTHORIZATION_CREATED` (to `PROCUREMENT`-role users in the PRA's own tenant only, never its creator). Nobody is notified about their own action, including an approver filing their own PR (`NEW_PURCHASE_REQUEST` skips the requester).

**Procurement activity log** (BE-07) — `GET /procurement/activity`: one newest-first feed of every status change on PRs, PRAs, Realisasi and POs, read from their existing `*StatusHistory` tables (nothing new stored). Caller's tenant tree; optional `from`/`to`/`tenantId` (subtree, must be in tree)/`actorId`/`type`/`limit` (default 100, max 500). Readable by `ADMIN` or `PROCUREMENT` role, or `OWNER`+ scope — checked in `ProcurementActivityService`, since `@PreAuthorize` can't see scope. Rows written before 14 Oct 2026 have a null actor on PR approve/reject/cancel and all PO transitions: those writes passed `changedBy = null` until BE-07.

Recipient resolution (`approversIn`): any user in the target tenant set who is **either** `Role.ADMIN` **or** scope `OWNER`/`PRINCIPAL` (an OR, not AND). New-PR and escalated-Realisasi notifications fan out to `getAncestorTenantIds` (bubbles upward toward HQ, not downward to children).

Endpoints (`/notifications`): `GET` (mine), `GET /unread-count`, `PATCH /{id}/read`, `PATCH /read-all` — all implicitly self-scoped by query construction (`recipientUserId` from JWT).

**`dashboard` module** — `GET /dashboard/unit-status-summary` (properly DB-scoped counts by status) and `GET /dashboard/overview`. **Confirmed performance gap**: `getOverview()`'s `averageHealthScore`/`criticalUnits` figures are computed by loading **every `AirsoftUnit` row across every tenant in the system** via `findAll()` and filtering in-memory — not a scoped aggregate query, unlike every other count on the same response. Data returned is still correctly tenant-filtered (no leak), just a full cross-tenant table scan on every call.

**`analytics` module** — `GET /analytics/parts/durability` (average days installed→removed per part, grouped in-memory over a tenant-scoped fetch) and `GET /analytics/consumables/performance` (average hours taken→exhausted per part). `UnitHealthService.recalculateHealth` (internal only, no REST endpoint of its own) walks removed-part history and subtracts a category-tiered penalty (critical parts: 20/FAILED, 10/WORN; important: 10/5; everything else: 5/2), floored at 0 — this score feeds directly into the dashboard's health figures above.

### 4.10 Change-queue status, verified against current source (not the doc alone)

| Item | Verified status |
|---|---|
| BE-06 — cost redaction on Realisasi/PartInstance | **Done** |
| BE-07 — PRA notification type | **Done**, 14 Oct 2026, widened to requester notifications + procurement activity log + role endpoint (`ProcurementNotificationAndLogTest`, 9 tests; migration V3) |
| BE-08 — `GET /goods-receipts` list + `GET /goods-receipts/{id}` | **Done**, 14 Oct 2026 (`GoodsReceiptListTest`, 8 tests) |
| BE-11 — `PurchaseOrder` cost redaction | **Done**, 14 Oct 2026 (`PurchaseOrderServiceCostRedactionTest`, 6 tests) |

---

## 5. Consolidated gap list

Grouped by category, pulled from every module above — this is the "if you're picking up work, start here" list.

### 5.1 Authorization gaps (highest priority — spans nearly every module)

- ~~`TenantController` — zero authorization on tenant create/read~~ — closed, BE-12 (§1.2).
- ~~`POST /auth/register` public with caller-chosen scope~~ — closed, BE-12 (§1.2). Was the most severe gap in the codebase and was missing from this list.
- Entire digital-twin cluster (§2.8) — only unit *creation* is role-gated; installing/replacing parts, marking a part FAILED, all work-order transitions, all service events, all maintenance operations are open to any authenticated user.
- `POST /inventory/adjust` bypasses the `stockadjustment` module's Owner-approval workflow entirely (§3.4/§3.5).
- `StockAdjustmentService` has no self-approval prevention (§3.5), unlike the equivalent DL-11 rule in `GoodsReceiptService`.
- Functional roles beyond `ADMIN` are enforced nowhere except `PROCUREMENT` on the BE-07 activity log (§1.4). PRA create/cancel are scope-gated at MANAGER+ since 14 Oct 2026 (§1.5 #9).
- ~~`GlobalExceptionHandler` has no `AccessDeniedException` handler~~ — closed, BE-12 (§1.5).

### 5.2 State-machine / data-integrity gaps

- `WorkOrder`'s generic `PATCH /{id}/status` bypasses every validation the dedicated verb endpoints enforce, and skips the maintenance-schedule-completion side effect (§2.5).
- `AirsoftUnit` has no path back to `ACTIVE` once it enters `MAINTENANCE`; three `UnitStatus` values (`BROKEN`/`UPGRADE`/`RETIRED`) are structurally unreachable (§2.1).
- Every `ServiceEvent`, including routine/completed maintenance, forces the unit into `MAINTENANCE` (§2.3).
- No reconciliation invariant between `PartInstance` `IN_STOCK` counts and `Part.currentStock` for consumables (§3.2).
- `PartInstanceService.markInstalled()` lacks the consumable-exclusion guard that `exhaust()` has (§3.2).
- `PurchaseOrder.poNumber` generation (`"PO-" + currentTimeMillis()`) isn't collision-safe under concurrency (§4.2).

### 5.3 Duplicated/inconsistent logic

- Two maintenance-recommendation engines with no shared code, disagreeing severities for the same conditions (§2.4) — the single largest duplication finding in the codebase.
- `PartStockService.receiveStock()` uses a category-name string match instead of `Part.isConsumable()`, inconsistent with both `GoodsReceiptService` paths which get it right (§3.4/§3.7).
- `AirsoftUnitOverviewService` redundantly re-derives worn/failed counts already computed by `UnitOperationalRuleService` (§2.7).
- `WorkOrderService.hasOpenWorkOrder` duplicates a near-identical inline query already written in `PreventiveMaintenanceService` (§2.5, §2.4).

### 5.4 Performance

- `DashboardService.getOverview()` — full cross-tenant `findAll()` scan for two of its fields instead of a scoped aggregate query (§4.9).

### 5.5 Dead code (confirmed by whole-codebase grep, zero call sites)

- `CustomUserDetailsService` (§1.3).
- DB tables `roles`, `user_roles`, `user_profiles` (§1.1).
- `Part.legacyCategory` — written, never read (§3.1).
- `PartStockService.map(StockMovement)` + two `StockMovementRepository` finder methods (§3.4).
- `WorkOrderService.autoCloseOpenWorkOrders()` (§2.5).
- `ReplacementSuggestionResponse` DTO — fully modeled, no producing service, no endpoint (§2.4).
- `MaintenanceRecommendationService.processDueSchedules()` — implemented but not wired to any scheduler, only manually reachable (§2.4).
- Commented-out duplicate routes in `AirsoftUnitPartController` and `WorkOrderService` (§2.2, §2.5).
- ~~Debug leftovers: `TestController`, `GET /part-history/test`, `System.out.println` dump in `PartConditionHistoryService`~~ — removed, BE-12.

### 5.6 API-shape inconsistencies

- `ApiResponse&lt;T&gt;` envelope is not universal — `PartCategoryController.update()` returns a bare object; `GlobalExceptionHandler` error shapes don't match the envelope at all (§0, §3.1).
- Audit-field coverage is inconsistent — newer entities (`PRA`, `Realisasi`, `Notification`, all `*StatusHistory`) extend `BaseEntity` for `createdAt`/`updatedAt`; older ones (`Supplier`, `PurchaseRequest`, `PurchaseOrder`, `GoodsReceipt`, `AirsoftUnitPart`, `WorkOrder`) hand-roll `createdAt` only, no `updatedAt` (§2.2, §4.2).
- Login returns inconsistent HTTP status for two "invalid credentials" cases (400 vs 401) (§1.2).

---

## 6. How to keep this document honest

Per `CLAUDE.md`'s own rule: **a summary is evidence tier `[D]`, not `[C]`.** Everything in this document was tier `[C]` (read directly from source) at the time of writing (22 Aug 2026). It will drift the moment new code lands. Before trusting a specific claim here for anything consequential — especially the gap list in §5 — re-open the actual file and confirm it's still true, the same way this document itself was built. If you find this document disagrees with the code, the code is right; update this file rather than the other way around.
