# QuitNRT — Implementation Plan

Status: **draft for review** — no code is written until this plan is approved.

QuitNRT is an Android app for logging nicotine replacement therapy (NRT) use, understanding
what triggers it, and following a guided programme that tapers the daily nicotine dose to zero.

---

## 0. Decisions so far

| Topic | Decision |
|---|---|
| Voice / AI logging | **On-device first.** Quick-log surfaces (widget, Quick Settings tile, app shortcuts, deep links) now, plus **Android AppFunctions** (Android's on-device MCP equivalent) so Gemini can log once Google opens it up. No server; data never leaves the phone. A cloud MCP server is out of scope for v1. |
| Audience | **Personal first, Play-ready later.** Sideloaded APK built by CI, but disclaimers, privacy and structure kept clean for a later Play Store release. |
| Programme | **Guided plan.** Baseline week → choose a length → app proposes weekly step-downs of the daily mg budget; every step is editable and can be repeated. |
| v1 extras | **Smart reminders** and **Wear OS quick-log**. |
| Deferred | Resisted-craving log, cost / money-saved tracking, cloud sync + remote MCP server, iOS. |
| Dose unit | Goals and charts use **labelled mg** (what's on the pack). An "estimated absorbed" figure is an optional, clearly-labelled extra. |
| Stack | Kotlin + Jetpack Compose (Material 3), native Android. Required for AppFunctions, which is a Kotlin/Jetpack API. |

---

## 1. Scope

### In v1
1. Product catalogue (pre-seeded, editable) covering gum, lozenges, patches, inhaler, sprays, microtabs, other.
2. Logging with quantity, retroactive date/time, tags, craving intensity, planned-vs-craving flag, note.
3. History with edit / delete / undo.
4. Insights: daily intake charts (7/30/60/90 days), hour × weekday heat map, calendar heat map, trigger stats, habit stats.
5. Guided reduction programme with daily budget, weekly review, repeat-a-step.
6. Advice engine mapped to triggers, including an in-app breathing timer.
7. Quick-log: home-screen widget, Quick Settings tile, app shortcuts, deep links.
8. Smart reminders (all optional, individually toggleable).
9. AppFunctions for Gemini / on-device agents (Android 16+).
10. Wear OS app: tile, complication, minimal logging UI.
11. Export (JSON + CSV) and import (JSON restore); Android Auto Backup.
12. Onboarding with a medical disclaimer.

### Not in v1
- Resisted-craving log, cost tracking, cloud sync, remote MCP server, iOS, multi-user.
- Health Connect integration (it has no nicotine data type).

---

## 2. Core concepts

| Concept | Meaning |
|---|---|
| **Product** | A specific NRT item: form + strength + unit, e.g. "Gum 2 mg (piece)", "Inhaler 15 mg cartridge (puff)", "Patch 21 mg / 24 h". |
| **Entry** | One logging event: product × quantity at a moment in time, with tags. Stores a **snapshot** of the mg so editing a product later never rewrites history. |
| **Logical day** | The day an entry counts toward. A configurable **day start** (default 04:00) means a gum at 01:30 counts toward the previous evening. |
| **Background dose** | Patches. Count toward the daily total for the day applied, but are **excluded** from the hourly heat map, spacing stats and trigger stats (a patch is steady release, not a craving response). |
| **Programme** | Baseline period plus a sequence of weekly **steps**, each with a daily mg budget and optional secondary goals. |

---

## 3. Product catalogue

Pre-seeded products. Onboarding asks which ones you use: those become visible **favourites**, the rest are hidden but can be re-enabled.

| Form | Strengths seeded | Logged unit |
|---|---|---|
| Gum | 2 mg, 4 mg | piece |
| Lozenge / mini lozenge | 1.5 mg, 2 mg, 4 mg | piece |
| Patch (24 h) | 7, 14, 21 mg | patch (background) |
| Patch (16 h) | 10, 15, 25 mg | patch (background) |
| Inhaler | 10 mg and 15 mg cartridges | puff (or whole cartridge) |
| Mouth spray | 1 mg / spray | spray |
| Nasal spray | 0.5 mg / spray | spray |
| Sublingual tablet (microtab) | 2 mg | tablet |
| Nicotine pouch | custom mg (hidden by default; not licensed NRT) | pouch |
| Other | custom mg | custom |

Dose maths:
- `mgTotal = quantity × mgPerUnit`, snapshotted on the entry.
- **Inhaler:** `mgPerUnit = cartridgeMg / puffsPerCartridge`. The puffs-per-cartridge default is approximate and editable per product. You can log by puff or by whole cartridge.
- **Patch:** full labelled dose on the day applied. An optional **"removed early"** setting prorates it (`hoursWorn / ratedHours`).
- **Estimated absorbed (optional, off by default):** a per-form factor shown as "≈ x mg absorbed (rough estimate)". Factors are researched from product labelling during implementation and shown with a disclaimer. Goals never use it.

---

## 4. Logging

### Log sheet (bottom sheet, opened from Today, widget, tile, shortcut or deep link)
- **Product:** favourites as chips, plus "More…".
- **Quantity:** stepper (puffs default to a larger step, e.g. ±5).
- **Time:** defaults to **now**. Quick chips: `−15 m`, `−30 m`, `−1 h`, `−2 h`, `Yesterday`, plus a full date + time picker. Future times are rejected (a few minutes of tolerance).
- **Tags:** grouped chips, multi-select (see §5). The most-used tags come first.
- **Craving intensity:** optional 1–5.
- **Planned vs craving:** toggle (a dose taken on schedule vs in response to a craving).
- **Note:** optional free text.
- **Save** → snackbar "Logged 2 mg gum · **Undo**".

### Safeguards
- **Duplicate warning:** the same product logged twice within 60 s from different sources (e.g. widget + voice) → "Logged twice in a minute — keep both?".
- **Spacing note:** if the current step has a minimum-spacing goal and the dose comes too soon, show a gentle, non-blocking note with a "try a 4D first" link (§7).
- Edits recompute `mgTotal` from the product's current definition only when product or quantity changes.
- Deletes are soft (`deletedAt`) for undo, and are purged after 30 days.

### Time zones
Each entry stores the UTC instant **and** its zone ID. Logical day and hour-of-day are computed in the entry's own zone, so travel and DST don't scramble stats. Changing the day-start setting triggers a one-off recomputation of logical days.

---

## 5. Tags

Built-in tags in groups (rename, hide, reorder, or add custom tags in Settings). Groups let stats roll up and drive advice.

| Group | Tags |
|---|---|
| **Emotional** | Stressed, Anxious, Angry / frustrated, Sad / low, Bored, Lonely, Happy / joyful, Excited, Celebrating, Relieved |
| **Routine** | After meal, With coffee / tea, With alcohol, Just woke up, Before bed, Commuting / driving, Work break, After exercise, On a call, Screen time (TV / gaming / scrolling) |
| **Social** | Around other nicotine users, Socialising / party, Alone, After an argument |
| **Physical** | Strong craving, Withdrawal (irritable / restless), Tired, Hungry, Can't focus |
| **Habit** | Autopilot (didn't think about it) |
| **Custom** | User-defined, assigned to any group |

"Scheduled dose" is the **planned** toggle on the entry, not a tag.

---

## 6. Guided reduction programme

### 6.1 Baseline
- 7 days of normal logging (at least 5 days with entries). The baseline is the **mean daily mg over days with entries**, shown for the user to confirm or edit (days without entries are likely missed logs).
- **Skip option:** enter your typical daily usage manually to start immediately.

### 6.2 Plan generation
- User picks a length: 8 / **12 (default)** / 16 / 24 weeks, or a target NRT-free date.
- Budgets step down linearly from baseline toward the smallest unit of the main product, are rounded to whole units, and never increase. After the final step comes the **NRT-free date**.
- Every step's budget can be edited, and the plan can be regenerated from current usage at any time.

Example: baseline 24 mg/day of 2 mg gum, 12 weeks:

| Week | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | then |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Budget (mg) | 22 | 20 | 18 | 16 | 14 | 12 | 12 | 10 | 8 | 6 | 4 | 2 | **0** |

The exact formula is unit-tested in `:core:domain`.

### 6.3 Optional secondary goals per step (off by default)
- **Max units per day.**
- **Minimum spacing between doses** (e.g. 90 min).
- **First dose not before** a given time, moving later by ~15 min per week. How soon you use nicotine after waking is a strong marker of dependence.
- **Strength step-down hint:** e.g. suggest switching 4 mg → 2 mg gum when the budget makes it practical.

### 6.4 Weekly review (end of each step)
- Shows days within budget, average vs budget, top triggers, and time-of-first-dose trend.
- If at least 5 of 7 days were within budget → **advance** (default). Otherwise → **repeat this step** (default), which shifts later steps by a week, or advance anyway.
- Tone is supportive with no resets or shaming. The app states plainly that taking longer with NRT is far safer than relapsing to smoking.

### 6.5 After the programme
**Maintenance mode** with an NRT-free day counter. Logging still works for slips, and you can **restart a plan** from current usage.

---

## 7. Advice engine

### Content library (static, bundled, sourced from public health guidance such as NHS and CDC)
Each strategy has a title, short body, steps, the tags or groups it applies to, and a source note. Interactive ones:
- **Breathing timer:** box breathing (4-4-4-4) and 4-7-8, animated, 1–3 minutes.
- **Urge-surf / delay timer:** a 5–10 minute countdown ("cravings peak and pass").

### Mapping (examples)
| Trigger | Strategies |
|---|---|
| Stressed, Anxious, Angry, After an argument | Breathing timer, 5-min walk, progressive muscle relaxation, urge surfing |
| After meal, With coffee / tea | **Move the dose before the meal**, get up from the table, brush teeth, short walk. Note: coffee and acidic drinks reduce gum/lozenge absorption, so avoid them 15 min before and during use |
| Happy, Excited, Celebrating | Pick a substitute reward; tie rewards to milestones |
| Bored, Screen time, Autopilot | Keep hands busy, change environment, make the dose a conscious decision |
| With alcohol, Socialising | High-risk situation: plan ahead, set a limit before going out |
| Just woke up | Push the first dose later each week; have water/breakfast first |
| Withdrawal, Strong craving | 4 Ds; if severe or persistent, talk to a pharmacist or GP about pace |
| Always available | The **4 Ds**: Delay, Deep breaths, Drink water, Do something else |

### Personalisation
- Rank your top tags over the last 14 days by share of doses and show the mapped strategies first.
- **Data-driven timing tips:** e.g. if "After meal" accounts for at least 25% of doses and clusters around 13:00 and 19:00 → "Try taking your dose at 12:30, before lunch."
- **Where it appears:**
  - an advice card on Today
  - a contextual tip right after logging (e.g. tagged Stressed → "Try 1 minute of box breathing next time")
  - the Triggers section in Insights
  - the Advice tab

### Guardrails
Never suggests increasing a dose. General information, not medical advice. Signposts NHS / local quitlines.

---

## 8. Insights (statistics & charts)

Range selector: **7 / 30 / 60 / 90 days**. 7 is always available; 30 unlocks after more than 7 days of history, 60 after more than 30, 90 after more than 60. Charts start at the first logged day, with no fake zeros before it.

1. **Daily intake:** bar chart of mg per logical day, with the **step budget line** and a **7-day rolling average**. Toggle for stacking by product form.
2. **Hour × weekday heat map:** answers "when do I use most". Laid out vertically for portrait phones: 24 hour rows × 7 weekday columns. Colour is the number of doses (toggle: mg). Patches are excluded. Tap a cell for exact values. Single-hue, colour-blind-safe scale.
3. **Calendar heat map:** one square per day, coloured by daily mg vs budget.
4. **Triggers:**
   - top tags (share of doses)
   - group roll-up
   - tags × time of day (morning / afternoon / evening / night)
   - change vs the previous period
5. **Habits:**
   - time of first dose (optionally minutes after a set wake time)
   - average spacing between doses
   - longest gap
   - days-within-budget streak
6. **Per product:** share of total mg, units per day.

Every chart has a text summary for screen readers.

---

## 9. Quick-log surfaces

| Surface | Behaviour |
|---|---|
| **Home-screen widget** (Glance) | Today's mg vs budget plus 2–3 favourite buttons. A tap logs **instantly** (no app open) and briefly shows "Logged · Undo". An optional setting opens a quick tag picker after logging. |
| **Quick Settings tile** | A tap logs the default product (configurable) and posts a short-lived notification with **Undo**. Alternative mode: open the log sheet. |
| **App shortcuts** (long-press icon) | Top 3 favourites plus "Log…". Also declared as App Actions capabilities so Google Assistant can trigger them where it still exists. Not relied on. |
| **Deep link** | `quitnrt://log?product=gum-2mg&qty=1&at=2026-10-23T15:00&tags=stressed,after-meal`. Lets Routines, Tasker or an NFC sticker on your gum pack log an entry. Validated; logs through the same path as the UI. |

All surfaces record a `source` on the entry (APP, WIDGET, TILE, SHORTCUT, DEEPLINK, APPFUNCTION, WEAR) for debugging and duplicate detection.

---

## 10. AppFunctions (Gemini / on-device agents)

- **Requires Android 16 (API 36)+.** Experimental Jetpack library. As of mid-2026, Gemini calling third-party AppFunctions is in **private preview** (trusted testers / Early Access Program). We build and verify it now with `adb shell cmd app_function …`, register interest in the EAP, and it starts working for users when Google opens it up. On older Android it's simply inactive.
- All functions are isolated in one package so API changes in the alpha library don't touch the rest of the app.

Functions (described via KDoc so the agent understands them):

| Function | Purpose |
|---|---|
| `logNrtUsage(productDescription, quantity = 1, time? = now, tags? = [], cravingIntensity?)` | "Log a 2 mg nicotine gum", "log 10 inhaler puffs at 3 pm, stressed". Returns the saved entry, today's total vs budget, and a `possibleDuplicate` flag. |
| `listNrtProducts()` | The user's products, so the agent can disambiguate. |
| `getTodayNrtSummary()` | Total mg, budget, remaining, last dose time, step info. |
| `undoLastNrtEntry()` | Undo the most recent entry. |
| `getNrtAdvice(tag?)` | Top strategy for a trigger, or for your current top trigger. |

`productDescription` and tags are resolved by a **natural-language matcher** in `:core:domain`, shared with deep links. Examples: "2mg nicotine gum", "two 4 mg gums", "nicorette 4", "21mg patch", "10 puffs inhaler", "mouth spray". Ambiguous input returns an error listing the candidates so the agent can ask.

---

## 11. Smart reminders

All optional, each toggleable, with quiet hours respected. Android 13+ asks for notification permission at the moment a reminder is first enabled.

| Reminder | Trigger | Default |
|---|---|---|
| Budget nudge | A log brings today to 80% / 100% of the budget | On |
| End-of-day check-in | Daily at 21:30 (configurable): "Today 14 / 16 mg. Anything missing?" [Log] [All good] | On |
| Weekly review | End of each programme step | On |
| Milestones | First day within budget; 3 / 7 / 14-day streaks; step completed; first NRT-free day; programme complete | On |
| Forgot-to-log | No entries by ~2 h after your usual first-dose time (learned): "NRT-free so far, or forgot to log?" [Log missed dose] [NRT-free so far] | Off |
| Spacing note | Shown in-app at log time only (§4). **Never** a push saying "you may dose now". | Off |

Implementation: WorkManager for scheduled checks (inexact timing is fine); event-driven nudges fire from the logging use case.

---

## 12. Wear OS

- `:wear` module, Wear OS 3+ (API 30+), Compose for Wear OS.
- **Tile:** today's mg vs budget arc plus 2–3 favourite buttons. A tap logs, with a haptic and confirmation.
- **Complication:** today's mg / budget for watch faces.
- **Mini app:** favourite list, log with time adjust (−15 m / −30 m / −1 h), last 5 entries, undo.
- **Sync** (Wearable Data Layer API):
  - The phone is the source of truth. The watch sends `log` messages carrying a UUID and the original timestamp, so they're idempotent.
  - The phone pushes the today summary and favourites back to the watch.
  - The watch queues logs offline and flushes them on reconnect.
- **Requirements:** a Wear OS watch (e.g. Pixel Watch, Galaxy Watch 4+), paired; phone and watch apps share an applicationId and signing key. Garmin, Huawei GT and Apple watches are not supported.

---

## 13. Screens & navigation

Bottom navigation: **Today · History · Insights · Plan · Advice**. Settings is reached from the top bar.

- **Onboarding:**
  - welcome and disclaimer (must acknowledge)
  - pick your products
  - day-start time
  - optional wake time
  - reminders opt-in
  - start baseline or enter usage manually
- **Today:**
  - budget ring (mg used / budget, units)
  - quick-log favourite chips
  - "+ Log" button
  - last dose and time since
  - today's entries
  - advice card
  - baseline / step banner
- **History:** entries grouped by logical day with daily totals; filter by product or tag; tap to edit; swipe to delete with undo.
- **Insights:** §8.
- **Plan:** current step, progress through the programme, full schedule (editable), secondary goals, weekly review history, repeat / regenerate.
- **Advice:** personalised top strategies, full library by trigger group, breathing and urge-surf timers.
- **Settings:**
  - products (add / edit / hide / favourite / reorder)
  - tags
  - day start
  - wake time
  - reminders
  - quick-log behaviour
  - estimated-absorbed toggle
  - export / import
  - about, disclaimer and sources

Design: Material 3 with dynamic colour (Material You) and a calm fallback palette. Dark mode. 48 dp touch targets. Supports large fonts.

---

## 14. Architecture

```
:app            Phone app — Compose UI, navigation, ViewModels, widget (Glance),
                QS tile, shortcuts, deep links, AppFunctions, notifications, WorkManager
:wear           Wear OS app — tile, complication, Compose for Wear, Data Layer client
:core:data      Room database, DataStore settings, repositories, export/import (Android library)
:core:domain    Pure Kotlin/JVM — models, dose maths, logical-day rules, programme generator,
                stats aggregation, advice ranking, natural-language product matcher,
                wear sync message DTOs. No Android dependencies → fast unit tests.
```

- **Pattern:** MVVM with unidirectional data flow; Kotlin Coroutines + Flow; Hilt for DI; `java.time` (native from API 26).
- **Libraries:**
  - Room
  - DataStore
  - WorkManager
  - Glance (widget)
  - Vico (charts)
  - custom Canvas composables for the heat maps
  - kotlinx.serialization (export, wear messages)
  - `androidx.appfunctions` (alpha, pinned)
  - Play Services Wearable
- **SDK levels:** `minSdk 26` (Android 8.0) for phone, `minSdk 30` for wear, `compile/targetSdk 36`.
- **Gradle:** version catalog (`libs.versions.toml`), Kotlin DSL, convention plugins kept minimal.
- **No `INTERNET` permission** in v1. That's a hard privacy guarantee the app can't send data anywhere. Wear sync goes through Play Services, which doesn't need it.

### Data model (Room)

```
products(id, name, form, strengthMg, unitLabel, mgPerUnit, cartridgeMg?, puffsPerCartridge?,
         ratedHours?, isBackground, isFavourite, isHidden, sortOrder, absorptionFactor?, createdAt)

entries(id UUID, productId, productNameSnapshot, mgPerUnitSnapshot, quantity, mgTotal,
        occurredAt (epoch ms UTC), zoneId, logicalDate, kind (PLANNED|CRAVING|UNSPECIFIED),
        cravingIntensity?, note?, source, removedEarlyHours?, createdAt, updatedAt, deletedAt?)

tags(id, group, nameKey?, customName?, isBuiltIn, isHidden, sortOrder)
entry_tags(entryId, tagId)

programmes(id, status (BASELINE|ACTIVE|MAINTENANCE|ABANDONED), baselineStart, baselineEnd,
           baselineDailyMg, baselineSource (MEASURED|MANUAL), lengthWeeks, mainProductId,
           nrtFreeDate, createdAt)
programme_steps(id, programmeId, index, startDate, endDate, dailyBudgetMg, maxUnits?,
                minSpacingMinutes?, firstDoseNotBefore?, repeatOf?, reviewOutcome?)
```

DataStore preferences hold: day start, wake time, reminder toggles and times, quiet hours, quick-log defaults, the estimated-absorbed toggle, onboarding state and disclaimer acceptance.

Room schemas are exported and every migration is tested from v1 onward, so data survives updates.

---

## 15. Privacy, safety, Play-readiness

- **Local-only.** No account, no analytics, no ads, no network.
- **Backup:**
  - Android Auto Backup (end-to-end encrypted on modern Android with a screen lock)
  - manual **export** (full JSON backup + entries CSV) via the system file picker
  - **import** (JSON restore: merge by UUID, or replace)
- **Medical disclaimer** in onboarding and About. It says:
  - the app is not medical advice
  - talk to a pharmacist or GP before changing your NRT regime, especially if pregnant, breastfeeding, or with heart conditions
  - using NRT longer is far safer than smoking
- **Play-ready later:**
  - permanent applicationId chosen now
  - privacy policy draft ("no data collected")
  - Data safety and Health-apps declaration answers documented in `docs/`
  - Play App Signing with the upload key in GitHub Secrets
  - no therapeutic claims in copy
- **Localisation:** all strings in resources (English first). Time and date formats follow the device (24 h "15.00" vs 12 h).

---

## 16. Delivery phases

Each phase ends with a working, installable build and a short demo checklist.

| Phase | Content | Usable result |
|---|---|---|
| **0. Foundation** | Multi-module Gradle skeleton, version catalog, CI (build + unit tests + APK artefacts), theme, nav shell, `:core:domain` with logical-day + dose maths + tests | CI produces an APK that opens |
| **1. Logging core** | Seeded products, log sheet (retroactive time, tags, intensity, planned flag), Today screen, History edit/delete/undo, Settings (products, tags, day start), onboarding + disclaimer, export/import | **Daily-driver logging app** |
| **2. Insights** | Daily chart with ranges, rolling average, hour × weekday + calendar heat maps, triggers, habits | Stats from day one of logging |
| **3. Programme** | Baseline, plan generator, budget ring + chart target line, secondary goals, weekly review, repeat/regenerate, maintenance mode | Guided taper running |
| **4. Advice** | Content library, mapping, personal ranking, timing tips, breathing + urge-surf timers, contextual post-log tips | Trigger-specific coaching |
| **5. Quick-log & reminders** | Widget, QS tile, app shortcuts, deep links, NL product matcher, all smart reminders | One-tap logging, nudges |
| **6. AppFunctions** | Functions in §10, KDoc descriptions, adb verification script, EAP notes | Ready for Gemini when it opens up |
| **7. Wear OS** | Tile, complication, mini app, Data Layer sync with offline queue | Log from the wrist |
| **8. Polish & Play-readiness** | Accessibility pass, estimated-absorbed toggle, privacy policy, store docs, release signing workflow | Releasable build |

---

## 17. Testing & verification

- **`:core:domain` unit tests** (JUnit 5, run locally and in CI):
  - logical-day edge cases: 01:30 with a 04:00 day start, DST transitions, zone changes mid-day, changing the day start
  - dose maths: inhaler puffs, patch removed early
  - programme generator: rounding, monotonic budgets, repeat-step shifting, regeneration
  - stats: ranges clipped to the first entry, heat-map bucketing in the entry's zone, patches excluded
  - advice ranking and timing-tip detection
  - NL matcher: a table of phrases → expected product/quantity, including ambiguous cases
- **`:core:data` tests** (Robolectric + in-memory Room): DAO queries, migrations, export → import round-trip.
- **UI tests** (Compose): log flow incl. retroactive time, edit/delete/undo, onboarding.
- **AppFunctions:** `adb shell cmd app_function list-app-functions` / execute script on an Android 16 emulator or device.
- **Wear:** paired phone + watch emulators (or a real device): offline queue, idempotent delivery.
- **Manual checklist per phase** on a real phone.

### Build environment note
This cloud container's network policy blocks `dl.google.com`, which is where the Android SDK downloads from. Gradle, Maven Central and Google Maven are reachable. So:
- `:core:domain` (pure Kotlin) can be built and tested in the container.
- Full APK builds run in **GitHub Actions** (runners ship with the Android SDK). Or allow `dl.google.com` in the environment's network settings to build locally too.

---

## 18. Open questions (defaults in brackets)

1. **Application ID**: permanent once on the Play Store. [`com.quitnrt.app`, or a domain you own, e.g. `org.<yourdomain>.quitnrt`]
2. **App name** on the home screen. [QuitNRT]
3. **Languages** besides English for v1? [English only, ready for more]
4. **Your products**: which ones do you use today, so the defaults and examples fit? [Gum 2 mg / 4 mg]
5. **Devices**: is your phone on Android 16+ (needed to try AppFunctions)? Which watch do you have (must be Wear OS)?
6. **Builds**: rely on GitHub Actions for APKs, or allow `dl.google.com` in this environment so I can build here too? [GitHub Actions]
7. **Day start** [04:00] and **end-of-day check-in time** [21:30].
8. **Programme default length** [12 weeks] and **step length** [1 week].
