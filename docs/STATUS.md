# Status and handover

Last updated: 23 October 2026, when work moved from a cloud session to a local one.

## Where things are

| Phase | State |
|---|---|
| 0. Foundation and direction | Done. Gradle set-up, CI, copy lint, style guide, design direction chosen, first creature signed off |
| 1. Logging | **About half done.** Domain rules, product catalogue, data layer, backup, theme, sprites and settings are built and tested. **The screens aren't started.** |
| 2 to 9 | Not started |

CI is green on the latest commit: copy rules, 59 domain tests, Robolectric data tests, debug APK, Android lint.

## Decisions made since `docs/PLAN.md` was written

The plan has most of these already; this list is the quick reference.

- **Look: Fieldbook v2, variant B, with variant A's weekly bars.**
  - B's layout and all-sans type, with Schibsted Grotesk for the UI and Silkscreen for game numbers only.
  - For the week, use **A's vertical columns** under a dashed guide line, with M to S labels and a tick for days at or under the guide. Not B's seven-slot strip.
  - References:
    - B mockups: `docs/design/fieldbook-v2/b/` (`today.html`, `log.html`, `lair.html`, screenshots, `tokens.json`)
    - A's week chart: `docs/design/fieldbook-v2/a/today.html`
    - Comparison boards: `docs/design/fieldbook-v2/board-*.png`
- **Theme is in code:** `app/src/main/kotlin/app/thelasthit/nrt/ui/theme/`. It holds the B tokens in light and dark, Material roles, the type scale and shapes. Material You dynamic colour is off on purpose.
- **Fonts:** static instances cut from the variable font, in `app/src/main/res/font/`. OFL licences are in `app/src/main/assets/licences/` and must be shown in About.
- **Colour rules:** see `CLAUDE.md`. The independent reviewer's key finding was that nicotine use must never look like progress.
- **Catch-up and repeat rule:** see PLAN §6.4.
  - **Carry on:** meeting a week pays back every missed week since the last met one.
  - **Repeat this week:** adds a week and 10 HP, and meeting the repeat pays back too.
- **Mouth spray:** "puff" is accepted as another word for a spray. The **inhalator** logs sessions (1/8 of a cartridge) or cartridges. Puffs only if the user sets their own count.
- **Creature frames by HP:** fresh above two thirds, roughed up above one third, barely standing at a third or below (`SpriteFrame.forHealth`).
- **Dependency injection:** none. `DataGraph` wires everything. Hilt was dropped to avoid AGP 9 plugin friction.
- **Demon details** (name, creature, palette) sit in settings until the programme starts (phase 3), when the demon moves into the database with its HP.
- **Only the dragon is drawn.** Onboarding should list all eight creatures, with only the dragon selectable for now.
- **Palette variants:** `forest` (the app's palette, default), `ember`, `moss`, `frost`.

## Next: the rest of phase 1 (the screens)

Build against the B mockups. All strings go in `strings.xml` and pass `checkCopy`.

1. **App wiring.**
   - An `Application` class that creates one `DataGraph`.
   - On start: `tags.ensureBuiltIns()` and `entries.purgeDeleted()`.
   - ViewModels via `viewModelFactory { initializer { } }`, which needs `androidx.lifecycle:lifecycle-viewmodel-compose`.
   - `AppRoot` shows onboarding until `settings.onboardingComplete`.
2. **Onboarding:**
   1. welcome and disclaimer (must be accepted; store the time)
   2. when you stopped smoking or vaping. If under about 8 weeks ago, a gentle note that most courses run 8 to 12 weeks. Never blocking.
   3. country, defaulting to the phone's region
   4. your products: by brand or search, using `Catalogue.forMarket` and `search`. Picks go on the quick log in order.
   5. **name your demon**: name, creature (dragon only for now), and colour from the sprite's palette variants
   6. day start (04:00 default)
   7. done
3. **Today**, from B's `today.html`:
   - **Hero:** the big mg number, a meter (the guide marker comes in phase 3), and quick-log pills with an edit button.
   - **Last dose row,** leading with the time since ("1 h 25 min ago").
   - **Advice line:** a placeholder until phase 5, or hide it.
   - **Week columns** as described above.
   - **Demon band:** lurking silhouette and "??" HP until the plan starts.
   - **Entries list.**
   - **Pill labels:** quick-log pills need short labels ("Gum 2 mg"). Derive them from form and strength, and add the brand only when two pills would otherwise read the same.
4. **Log sheet**, from B's `log.html`:
   - product, quantity stepper, quick times plus a date and time picker, planned or craving, craving strength 1 to 5, grouped tags, and an optional note
   - Save pinned to the bottom
   - Results handled: the duplicate question, label-limit notes, Undo snackbar, and future-time refusal
5. **History:**
   - entries grouped by logical day with totals
   - tap to edit (the log sheet in edit mode)
   - swipe to delete, with Undo
6. **Settings:**
   - products: add from the catalogue or by hand, hide
   - quick log: add, remove, reorder
   - tags: rename, hide, add
   - day start: then `entries.recalculateLogicalDays`
   - backup: JSON export and import (merge or replace) and CSV, through the system file picker
   - About: disclaimer, sources and font licences
7. **Lair tab:** for now, Gary large on his backdrop with the "lurking" state. The battle comes in phase 4.

**Tests:** Compose UI tests for logging (including backdating), edit, delete with undo, and onboarding.

## How design and copy are reviewed

The owner asked for agents on UI/UX, colour, tone and copy, so nothing ends up looking or reading AI-made. They run at checkpoints, not constantly:

- **Fresh-eyes reviewer.** After each UI phase, screenshot every screen in light and dark mode, at normal and large font, on a real device or emulator. Give the reviewer only the screenshots plus the brief in PLAN §16 and `docs/STYLE.md`, not the designer's notes. Ask for problems with fixes, AI-made tells, contrast and touch targets.
- **Copy agent.** Before each phase is finished, review `strings.xml` against `docs/STYLE.md`.
- **Visual designer.** Mock up screens that have no mockup yet (History, Settings, onboarding), using the B tokens and components, before building them.
- **Pixel artist.** The other seven creatures (phase 4) follow the dragon's format and tools in `art/tools/`. The sprite data lives in `core/domain/src/main/resources/creatures/`.

## Setting up locally

1. Install a current Android Studio that supports AGP 9.4 (it bundles a suitable JDK).
2. Get the branch:
   ```
   git clone https://github.com/sepici/quitnrt
   git checkout claude/elegant-bell-67rsvf
   ```
3. Run the full check:
   ```
   ./gradlew checkCopy :core:domain:test :core:data:testDebugUnitTest :app:assembleDebug :app:lintDebug
   ```
4. **Phone (Android 16):** turn on Developer options and USB debugging, then `./gradlew :app:installDebug`.
5. **Galaxy Watch 4** (Wear OS 6), needed from phase 8: Developer options, then Wireless debugging, then `adb pair` and `adb connect`.

## Loose ends

- **Room schema.** It's generated into `core/data/schemas/` on build. Commit the generated `1.json` so later migrations can be checked against it.
- **Mockup fonts aren't in the repo.** The mockups only show the real fonts in the screenshots; the app bundles its own copies.
- **Sprites in dark mode.** The mockups add a 1 px light rim around sprites in dark mode. Do this in the sprite renderer (phase 4), and watch the seam where the feet meet the rock.
- **Scratch tools.** The design tools that rebuild mockups and boards are in `docs/design/*/tools/`. `docs/design/tools/shot.mjs` expects Playwright at `/opt/node-tools`; point it at a local Playwright install.
