# Operion-services

Java Spring Boot + PostgreSQL backend for Operion, a specialized ERP for shooting range
businesses. Start with `CLAUDE.md` for context — architecture, active rules, and module
inventory. `OPERION_BE_PLAN.md` has the full detail; `OPERION_BE_CHANGE_QUEUE.md` tracks
open backend tasks; `OPERION_UI_SCREEN_INVENTORY_PLAN.md` and `screens/` track the UI
planning track.

## Maintenance API

- `GET /maintenance` — fetch all maintenance schedules
- `GET /maintenance/unit/{unitId}` — fetch schedules for one unit
- `GET /maintenance/recommendations/{unitId}` — generate maintenance recommendations
- `POST /maintenance/create` — create a maintenance schedule
