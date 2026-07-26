# Operion — Backend Development Plan & TODO

**Status as of:** 15 Jul 2026
**Repo:** `richardtanu/Operion-services` (private) — Java Spring Boot + PostgreSQL, modular monolith
**SSOT docs:** BRD (7/10) · FRS (8/10) · HLD (7/10) · LLD (8/10) · Decision Log — all completed 11 Jul 2026, LLD DDL validated against a live PostgreSQL instance with zero errors.

---

## 1. Context

Operion is a specialized ERP for shooting range businesses (adaptable to mechanical/technical service businesses — repair shops, rentals). Core modules:

- Inventory control — stock management with approval workflows
- Operational control — barcode-driven consumable tracking (gas/BB/spareparts)
- Efficiency analysis — usage patterns and consumable performance
- Item lifetime analysis — spareparts and airsoft unit lifecycle tracking
- Procurement — Requisition → Purchase Order → Invoice workflow
- Digital twin — per-airsoft-unit representation with health/maintenance state

The prototype already has a working DB foundation (25 tables) that's largely aligned with the blueprint. Formal SDLC docs closed most open architecture questions; what's left is implementation.

---

## 2. Resolved Architecture Decisions (do not re-litigate these)

### RBAC model

Dual-dimension:

- **Scope** (linear hierarchy): `Principal > Owner > Manager > Supervisor`
- **Functional role** (existing enum, unchanged): `ADMIN / TECHNICIAN / PROCUREMENT / ACCOUNTING / OPERATOR`

Implementation: add a **nullable scope column** to the `users` table. Not yet implemented.

### Offline-first sync

**Option B — Local Agent per outlet**: a mini-backend runs at each outlet with its own local DB, local auth/RBAC, and a REST API reachable over the outlet LAN. Syncs to the central cloud backend when online.

Why: outlets have multiple simultaneous device logins (supervisor, cashier, etc. at once), so a single-device embedded SQLite approach isn't enough — you need a real local server, not just a local file DB.

Remaining detail-level items (not architecture, just implementation choices — see HLD §3.2):

- Local Agent platform choice
- Update distribution mechanism
- RBAC cache refresh strategy

### Sync mode per data path (from blueprint)

| Path                            | Mode          | Why                                                                        |
| ------------------------------- | ------------- | -------------------------------------------------------------------------- |
| Procurement (PR → PO → Invoice) | Realtime      | Needs single source of truth at the center so central stock stays accurate |
| Inventory & daily operations    | Eventual sync | Outlet must keep working offline                                           |
| Efficiency & lifetime analysis  | Eventual sync | Computed locally, aggregated later                                         |
| Digital twin update             | Eventual sync | No cross-outlet unit transfer scenario exists                              |

Key principle: **outlet = source of truth for its own physical stock; center = aggregate/reporting.** If an outlet is offline, new requisitions/POs simply can't be created — that's by design, not a bug to fix.

---

## 3. Confirmed Implementation Gaps (from LLD, with proposed DDL/API contracts already drafted)

Ordered roughly by recommended build sequence:

1. **Procurement API** — PR → PO → Invoice has zero API implementation despite the DB tables already existing. **Recommended starting point** — safest module, no open dependencies on other unresolved gaps.
2. **Consumable barcode item-instance entity** — currently only aggregate stock is tracked for gas/BB; no per-instance (per-barcode) entity with take/exhaust timestamps. This blocks efficiency analysis, since it depends on the take→scan cycle.
3. **Tenant hierarchy** — `tenants` table is flat; no franchise/principal hierarchy above outlet yet.
4. **WorkOrder ↔ ServiceEvent FK** — the two entities exist but aren't linked.
5. **Stock adjustment approval workflow** — confirmed absent in code (verified directly, not assumed). Needed for the "employee forgot to scan" reconciliation case, with Owner approval and audit trail.
6. **WorkOrder auto-creation from maintenance recommendations** — `evaluateUnit` currently returns `List<String>` only; the `autoCreateWorkOrder` flag exists in the schema but is unused.
7. **Notification engine** — does not exist yet. Blueprint spec: lightweight in-app notification queue (not push/websocket) — inserts a row targeted at role/user on events (new PR, sparepart nearing end-of-life, low stock); shown on app open or during sync poll. Procurement notifications ride the existing realtime path; operational notifications surface during eventual sync.
8. **Part restore controller endpoint** — service method already exists, just needs a controller endpoint wired up. Easy fix.

### Already implemented (don't rebuild — verify at service layer, not just controller)

- `PreventiveMaintenanceService` + `MaintenancePolicy` entity (table `maintenance_rule`) — per-tenant configurable thresholds (health, max worn/refurbished parts, maintenance interval, low stock multiplier, part end-of-life warning). More sophisticated than initially assumed.
- Part retire has real business rules (blocks retire if stock > 0 or still installed).
- `part_condition_history` — tracks condition-state transitions (new → installed → used/damaged → retired) as history, not just a final status — this is the lifetime-analysis foundation.
- `airsoft_units`, `airsoft_unit_parts`, `service_events` — digital twin basics already structured.
- Procurement chain skeleton — `purchase_requests → purchase_orders → goods_receipts` already exist as separate entities.
- `stock_movements` — captures before/after stock plus a reference to the source document.

---

## 4. Backend TODO List (suggested order)

- [ ] **Procurement API** — implement PR → PO → Invoice endpoints on top of existing tables (status tracking + audit trail per transition; each transition is a natural notification hook)
- [ ] **RBAC scope column** — add nullable `scope` to `users`, wire dual-dimension (scope × functional role) enforcement into auth/authorization layer
- [ ] **Consumable barcode item-instance entity** — model per-barcode instance with take/exhaust timestamps; wire into efficiency analysis
- [ ] **Stock adjustment approval workflow** — new table(s) + Owner-approval flow + audit trail
- [ ] **WorkOrder auto-creation** — extend `evaluateUnit` to actually create WorkOrders when `autoCreateWorkOrder` is set, instead of just returning strings
- [ ] **WorkOrder ↔ ServiceEvent FK** — add the missing linkage
- [ ] **Part restore controller endpoint** — expose existing service method
- [ ] **Tenant hierarchy** — model franchise/principal relation above outlet
- [ ] **Notification engine** — in-app notification queue table + insertion on key events + polling-based read path
- [ ] **Local Agent** — resolve platform choice, update distribution, RBAC cache refresh strategy (HLD §3.2), then implement
- [ ] Incrementally raise SDLC document confidence scores as each gap above is closed and verified against real code

---

## 5. Working Principles (carry into every BE session)

- Explain trade-offs and the "why" behind decisions, not just the directive — this is a learning-while-building process.
- SSOT docs (BRD/FRS/HLD/LLD) are the reference; don't re-decide things already resolved above without new information.
- **Verify at the service layer, not just controllers** — past reviews that only checked controllers missed real service-layer implementations (e.g. `PreventiveMaintenanceService`).
- "No changes required" noted in a past session likely just means no work happened that session — don't treat it as confirmation the requirement is met. Check the actual code.
- DDL validation against a real running schema (not just design review on paper) is the bar for confidence, same standard used for the LLD.
- `CLAUDE.md` at the repo root is the context bridge between claude.ai sessions and Claude Code sessions — keep it in sync as gaps close.

---

## 6. Two Transactions, Not One (procurement design note)

Don't collapse these into a single PO entity:

- **(a)** PO from outlet → center
- **(b)** PO/order from center → supplier

The current prototype schema has `purchase_orders.supplier_id` directly on the table, which conflates these — this needs to be split when implementing the Procurement API (item #1 above).
