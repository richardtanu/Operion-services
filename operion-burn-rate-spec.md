# Operion — Burn Rate & `suggested_qty` — Implementation Spec

**Date:** 28 Jul 2026
**Implements:** `OPERION_BE_PLAN.md` §6.1
**Status:** Design spec. Nothing here has been run against PostgreSQL — same standard as the LLD, not confirmed until it executes cleanly on a live instance.

---

## 1. What this is, and what it is not

Two different measurements are easy to confuse. They use different data and answer different questions.

| | Burn rate | Efficiency analysis |
| --- | --- | --- |
| **Question** | How fast does stock leave the shelf? | How long does one unit last in use? |
| **Unit** | units per day | duration per unit |
| **Source** | `takenAt` only | `takenAt` **and** `exhaustedAt` |
| **Needs completed cycles?** | No | Yes |
| **Feeds** | days of cover, `suggested_qty`, reorder | cost-per-shot, usage patterns |

**Only burn rate is in scope here.**

### 1.1 Correction to decision #6

`OPERION_BE_PLAN.md` records the cold-start threshold as *"10 completed take→exhaust cycles."* That is the wrong measurement for this purpose, for two reasons:

1. **It undercounts.** Every tag currently open — taken but not yet scanned empty — is invisible to a completed-cycle count, even though its stock is already gone. Items that are consumed slowly are penalised worst, exactly the items where a reorder mistake hurts most.
2. **It lags.** A can of gas that takes a week to finish contributes nothing to the rate for a week after it left the shelf.

**Stock depletes at take time.** Confirm this against `PartStockService` before implementing — if stock is decremented at take, this section holds; if it is decremented at exhaust, the whole model needs revisiting.

The threshold also has to change. Ten events in one busy Saturday says nothing about a daily rate. What makes a rate trustworthy is **elapsed observation time**, with event count as a secondary floor.

---

## 2. Computation

Scope: **per part, per tenant (outlet).** Never aggregate across outlets — volume differs.

```
takes          = count of PartInstance rows for (part, tenant)
                 where takenAt >= now - windowDays

firstSeen      = earliest takenAt for (part, tenant), all time

observationDays = max(1, min(windowDays, days between firstSeen and now))

burnRate       = takes / observationDays        [stock units per day]

daysOfCover    = currentStock / burnRate        [only when burnRate > 0]

suggestedQty   = max(0, ceil(targetDays * burnRate - currentStock))
```

### 2.1 Why `observationDays` is clamped both ways

**Upper clamp (`windowDays`)** is obvious — don't count beyond the window.

**Lower clamp (days since first seen)** is the one that gets missed, and it is the difference between a usable number and a dangerous one. An item introduced 6 days ago with 12 takes has a real rate of 2/day. Dividing by a 90-day window gives 0.13/day — a **15× understatement**, which produces a confident "you have 46 days of cover" on an item that will run out on Thursday.

Any item added mid-life to the catalog hits this, so it is not an edge case.

### 2.2 Divide by calendar days, not active days

Days with zero takes must be included in the denominator. Counting only days with activity inflates the rate.

This holds even when the outlet is closed on some days. Reorder lead time runs in calendar days, so a calendar-day rate is what matches the decision being made.

---

## 3. Four modes, not two

The plan frames the manual reorder point as a cold-start fallback. It is more than that: **spareparts never get a computed rate at all.** A spring is installed on failure, not consumed daily. For a whole class of items the manual path is permanent, not temporary.

That needs four distinct modes, and the read path must say which one applies.

| Mode | When | Days of cover |
| --- | --- | --- |
| `COMPUTED` | Thresholds met (§4) | `stock / burnRate` |
| `MANUAL_RATE` | `manualDailyUsage` set, thresholds not met | `stock / manualDailyUsage` |
| `MANUAL_LEVEL` | `manualReorderPoint` set, no rate available | **Not applicable** — compare stock against the level directly |
| `NONE` | No data, no manual value | **Not applicable** — return null |

`MANUAL_LEVEL` is the sparepart path: "keep at least 3 on hand," which is a level, not a rate.

### 3.1 `NONE` must be explicit

An item with no history and no manual value must return **null** days of cover and mode `NONE`.

It must not return `0`, and it must not compute `stock / 0`. Zero days of cover renders as a maximum-urgency emergency in the UI; on a brand-new catalog item, most of the catalog would light up red on day one and staff would learn to ignore the signal. Null renders as "not enough data yet," which is true.

---

## 4. Thresholds

Switch to `COMPUTED` only when **both** hold:

| Threshold | Default | Rationale |
| --- | --- | --- |
| `minObservationDays` | 21 | Three full weeks — three weekend cycles. A shooting range has heavy weekday/weekend swing; two weeks is two samples of the dominant pattern. |
| `minEvents` | 10 | Guards against a slow-moving item that has been in the catalog 60 days with 2 takes. |

Both are required. Either alone admits a bad estimate: many events over a short span, or a long span with almost no events.

### 4.1 Window

> **Revised 28 Jul from 26 months of real data** (`Data_Ops.xlsx`, May 2024 – Jun 2026). The earlier 90-day recommendation was wrong. Backtest, mean absolute error predicting the next month from a trailing window:
>
> | Window | Gas error | BB error |
> | --- | --- | --- |
> | 1 month | **27.7%** | **21.0%** |
> | 3 months | 35.7% | 26.0% |
> | 6 months | 43.6% | 24.1% |
> | 12 months | 64.4% | 28.5% |
>
> Shorter wins decisively, and the reason is trend, not noise: gas consumption fell **77%** over the period (94.8/month → 21.7/month) alongside a 57% revenue decline. Any long window averages in a business that no longer exists. My original argument — long windows smooth the weekly cycle — was answering the wrong question.

`windowDays` default **30**.

Do not go below 30: at ~0.7 gas takes/day, a 14-day window holds ~10 events, and `minEvents` would rarely clear. Thirty days is roughly the shortest window that satisfies both thresholds at current volume.

**Noise floor.** Month-over-month variation is **26% for gas, 19% for BB** even with a perfect model. Days-of-cover should therefore be presented in bands or whole days, never to one decimal — `4.2 days` implies precision the data cannot support. The prototypes currently show one decimal; that should change.

### 4.2 Configuration

All four values go in `maintenance_rule`, which already holds per-tenant thresholds. **Do not build a second settings mechanism.**

```
burn_rate_window_days           default 30
burn_rate_min_observation_days  default 21
burn_rate_min_events            default 10
days_of_cover_target            default 14
```

**Check first:** `maintenance_rule` already has a `low_stock_multiplier`. Determine whether `days_of_cover_target` duplicates or replaces it. Two overlapping low-stock mechanisms is worse than either alone.

---

## 5. Schema

### On `Part`

| Field | Type | Notes |
| --- | --- | --- |
| `manualDailyUsage` | `BigDecimal`, nullable | Stock units per day. Consumables lacking history. |
| `manualReorderPoint` | `Integer`, nullable | Minimum stock level. Spareparts. |

Both nullable, both settable via existing part CRUD. They are not mutually exclusive in the schema, but `manualDailyUsage` takes precedence if both are set.

### Nothing else

`PartInstance` already has `takenAt` and `exhaustedAt` (confirmed). No new tables. No stored burn-rate column — compute on read, consistent with the PRA ceiling decision, and for the same reason: a stored derived value drifts silently from its inputs.

---

## 6. Read path contract

Every consumer needs the mode, not just the number.

```json
{
  "partId": "...",
  "currentStock": 6,
  "burnRate": 4.2,
  "burnRateSource": "COMPUTED",
  "observationDays": 90,
  "eventCount": 378,
  "daysOfCover": 1.4,
  "targetDays": 14,
  "suggestedQty": 53
}
```

`burnRateSource`, `observationDays`, and `eventCount` are not diagnostics — they are the difference between two claims the UI currently renders identically. "5 days of cover" from a manual guess and from 378 observed takes are not the same statement, and an owner approving a purchase deserves to see which one they are looking at.

For `MANUAL_LEVEL`: `burnRate` and `daysOfCover` are null, `manualReorderPoint` and `currentStock` are returned instead.

### 6.1 Performance

Compute for a whole catalog in **one grouped query** — `GROUP BY part_id` over `PartInstance` filtered by tenant and window. The PR creation screen requests every item at once; a per-item query is an N+1 that will be felt immediately.

If it becomes slow, the next step is a nightly materialised snapshot, not a cache. Do not build that pre-emptively.

---

## 7. Two known gaps — flagged, not built

### 7.1 Lead time is not modelled

`daysOfCover` answers "how long until I run out," not "should I order now." Marketplace delivery runs 2–7 days. An item with 5 days of cover and a 7-day lead time is already late, and nothing in this spec says so.

The 14-day target implicitly absorbs lead time. That works while lead times are uniform and breaks when they aren't. Proper fix later: `leadTimeDays` per part or per supplier, and a reorder point of `leadTime × burnRate + safety`.

### 7.2 `suggestedQty` is in stock units, purchases are in packs

Suggesting 53 cans when gas arrives in 6-can packets is not directly actionable — the PIC has to round anyway, and will round differently each time.

Blocking issue: **`conversionFactor` lives on `RealisasiItem`, not on `Part`.** There is no canonical "cans per packet" at part level to round against. Fixing this means a `defaultPackSize` on `Part`, which is a real decision about whether pack size is a property of the item or of a particular purchase. Defer, but be aware the PR creation screen will show unrounded numbers until it is resolved.

---

### 7.3 Reorder point and order quantity are two numbers; this spec has one

`suggestedQty` tops stock up to `targetDays`. That silently assumes you buy little and often. The actual buying pattern is bulk — roughly 100 gas cans and 200 BB packs every 3–4 months.

At ~0.95 cans/day, 100 cans is **105 days of cover**. Against a 14-day target the model behaves like this:

- Right after a bulk buy: `suggestedQty` = 0 for about three months. Correct, but the screen has nothing to say for a quarter.
- When stock finally drops below ~13 cans: suggests topping up to **13 cans**.

Nobody buys 13 cans. The real decision is another 100.

Classical inventory splits these: **reorder point** (when to order — driven by lead time and burn rate) and **order quantity** (how much — driven by bulk pricing, trip cost, storage, cash). This spec collapses both into `targetDays`, which only works when the two happen to coincide.

Minimum viable fix: a per-part `preferredOrderQty`. When set, `suggestedQty` returns that instead of the top-up figure, and days-of-cover drives *timing* only. Small change, and it makes the suggested basket match how purchasing actually happens.

Related: with a quarterly cadence, the reorder decision fires ~4 times a year per item. The suggested-basket screen is therefore mostly a **timing alert**, not a weekly composing tool — worth keeping in mind when the PR creation screen is rebuilt.

### 7.4 Every item is cold-start at launch — seed the manual rates

Existing history is **monthly aggregates**, not per-take timestamps. There is no `PartInstance` history to compute from, so on day one every consumable returns `NONE` and every days-of-cover gauge is blank. At ~0.7 gas takes/day it then takes about a month to clear `minObservationDays` and `minEvents`.

That is a month of the flagship screens showing nothing. Avoid it by seeding `manualDailyUsage` from the spreadsheet:

| Item | Seed `manualDailyUsage` | Basis |
| --- | --- | --- |
| Green Gas | **0.71** /day | 21.7 cans/month, last 6 months |
| BB 0.25 g | **0.92** /day | 27.8 bags/month, last 6 months |

Recompute these before launch — the trend is still moving.

### 7.5 The real driver is business volume, not consumption history

Consumption correlates with revenue at **0.95** and with visitor count at **0.96** (18 months where visitor data exists). Trailing-average burn rate is a proxy for a variable the business already tracks directly.

The implication is not to build revenue forecasting now. It is that a trailing average will always lag a turn in volume — it cannot see a quiet month coming, only report one that happened. If booking or visitor data becomes available to the system later, it is a strictly better input than take history, and the read path's `burnRateSource` field is already the place to express that.

---

## 8. Test cases

Per `CLAUDE.md` rule 2, any test touching stock quantities uses a `conversionFactor` other than 1.

| # | Scenario | Expected |
| --- | --- | --- |
| 1 | 378 takes over 90 days, stock 6 | `burnRate` 4.2, `daysOfCover` 1.4, `COMPUTED` |
| 2 | Item first seen 6 days ago, 12 takes, stock 4 | `observationDays` **6** not 90; `burnRate` 2.0; `daysOfCover` 2.0. **The clamp test — a wrong implementation returns 0.13 and 30 days.** |
| 3 | 30 takes over 12 days | `MANUAL_RATE` or `NONE` — fails `minObservationDays` despite passing `minEvents` |
| 4 | 4 takes over 60 days | `MANUAL_RATE` or `NONE` — fails `minEvents` despite passing `minObservationDays` |
| 5 | No takes, no manual values | `daysOfCover` **null**, mode `NONE`. Not zero, no divide-by-zero. |
| 6 | Sparepart, `manualReorderPoint` 3, stock 2 | `MANUAL_LEVEL`, below-level flag set, `daysOfCover` null |
| 7 | Stock 40, target 14, burn 1.0 | `suggestedQty` **0**, not negative |
| 8 | Item with takes in two tenants | Rates computed independently; neither sees the other's takes |
| 9 | Goods receipt with `conversionFactor=6` → 6 instances → 6 takes | Burn rate counts **6**, not 1 |
| 10 | Takes present but zero exhausts | Burn rate unaffected — confirms takes drive the rate, not completed cycles |

Cases 2, 5, and 10 are the ones that catch a plausible-but-wrong implementation. Case 10 in particular fails loudly if someone implements decision #6 as originally written.

---

## 9. Build order

1. Confirm stock decrements at **take** time, not exhaust (§1.1)
2. Check whether `days_of_cover_target` conflicts with the existing `low_stock_multiplier` (§4.2)
3. Add the two nullable `Part` fields + CRUD
4. Add the four `maintenance_rule` thresholds — **watch for the enum/constraint trap in `CLAUDE.md` rule 3 if any new enum is introduced for `burnRateSource`**
5. Implement computation as a single grouped query
6. Wire into the part read path with mode + observation metadata
7. Run §8 — case 2 first, since it is the one that produces confident wrong answers rather than obvious failures
