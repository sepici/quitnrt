# Status and handover

Last updated: 23 October 2026, after the phase 1 screens were built and reviewed in the local session.

## Where things are

| Phase | State |
|---|---|
| 0. Foundation and direction | Done. Gradle set-up, CI, copy lint, style guide, design direction chosen, first creature signed off |
| 1. Logging | **Done.** Domain rules, catalogue, data layer, backup, theme, sprites, plus **onboarding, Today, the log sheet, History, Settings and the lair**, reviewed, with the owner's calls on the reviews applied. |
| 2 to 9 | Not started |

The full check passes locally: copy rules, 93 domain tests, 28 data tests, 74 app UI, screenshot and format tests, debug APK, Android lint.

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
- **Fonts:** static weights (400, 500, 600, 700, 800 and italic) cut from the variable font by `docs/design/tools/cut_font.py`, in `app/src/main/res/font/`. Text the mockups set at 700 uses `FontWeight.Bold`, not ExtraBold. OFL licences are in `app/src/main/assets/licences/` and must be shown in About.
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
- **Palette tweak (23 October),** asked for by the owner after seeing the app:
  - The light background is closer to white: `#FAF9F5`.
  - Dark mode's greens are lighter: the hero is `#1A6A4A`, its pills `#257556`, secondary hero text `#D6ECDF`, the hero track `#358866`, and icon tiles `#224233`.
  - Every pair in the contrast table still passes. `Color.kt`, `tokens.css`, `tokens.json` and `fieldbook-v2/tools/build.py` all match.
- **Calls after the phase 1 reviews (23 October),** approved by the owner:
  - Dark mode's primary buttons and selected states are the hero pills' green, `#257556`, with `#F2F4EC` text, not cream. That's 5.0:1 for the text and 3.4:1 against the page.
  - Dose rows use the stone tile (`IconTile(dose = true)`), so nicotine used never sits on green. Other rows keep the mint tile.
  - Settings opens from a gear on Today, not sliders.
  - The creature that was "Demon" is now "Imp", so "demon" only means the thing you name.
  - The lurking demon's dark-mode rim waits for the phase 4 sprite renderer (see Loose ends).
- **Mockups for onboarding, History and Settings** are in `docs/design/fieldbook-v2/b/` (`onboarding-*`, `history*`, `settings*`). The owner signed them off with these calls:
  1. Creatures not drawn yet show as an empty dotted frame with "Only the dragon is drawn so far." No padlock.
  2. The quick log is reordered by dragging a handle, with "Move up" and "Move down" as accessibility actions.
  3. The disclaimer is accepted with one "Accept and set up" button.
  4. Onboarding buttons name the next step ("Next: your country"). The stop date can be skipped.
  5. The lurking demon is a silhouette in the stage's browns with marigold eyes, on Today's band too.
  6. Swipe to delete and "Replace everything" use the error red.
  7. History leaves out the guide ticks and the filter button until phase 3 and phase 2.
  8. Pills keep the form rule, "Mouth spray 1 mg", where the mockups say "QuickMist 1 mg".

## Next: the rest of phase 1 (the screens)

Build against the B mockups. All strings go in `strings.xml` and pass `checkCopy`.

1. **App wiring.** Done.
   - `LastHitApp` holds one `DataGraph` and runs `tags.ensureBuiltIns()` and `entries.purgeDeleted()` on start.
   - ViewModels come from `graphViewModel { graph -> ... }` in `ui/GraphViewModel.kt`.
   - `AppRoot` shows onboarding until `settings.onboardingComplete`. Until onboarding is built, a placeholder offers "Go to the app".
   - UI tests run on Robolectric with `TestApp` (an in-memory `DataGraph`). `captureLooks` in the tests renders a screen in light and dark, at normal and 1.3× font, for Roborazzi.
2. **Onboarding.** Done, from the signed-off `onboarding-*` mockups. Nothing but the disclaimer's acceptance time is stored until "Finish set-up", which writes the stop date, country, products (in pick order, onto the quick log), demon and day start together.
   - The country defaults to the phone's region. "Somewhere else" stores `ZZ` (`Markets.ELSEWHERE`), which gets only the generic products.
   - Products are picked by brand or search. "Show other countries" adds every market's products, marked with their country ("US pack").
   - The stop date can be skipped. Under 8 weeks in, a note mentions the usual course length, and nothing blocks.
   - The day start moves in half hours from midnight to 06:00, with a diagram of what counts toward Thursday.
   - The summary calls the demon "it": "Gary is lurking. It shows itself when your plan starts." The mockup said "he", but the demon could be named Mildred.
3. **Today.** Done, from B's `today.html`. Agreed with the owner before building:
   - **No meter until phase 3.** With no guide there's nothing to measure against, so the hero shows the mg and "3 doses so far".
   - **Quick-log pills:** a tap logs one unit now, with Undo in the snackbar. A long press opens the log sheet on that product.
   - **Pill labels** come from `PillLabel`: the form and pack strength ("Gum 2 mg", "Mouth spray 1 mg"), and the product's name only when two pills would read the same. The mockup's "QuickMist 1 mg" is the name version.
   - **Week columns** (A's style) are scaled to the tallest day, with no guide line, ticks or legend until there's a guide. A week with nothing logged shrinks to the baselines.
   - **Demon band:** the creature as a silhouette on the band backdrop ("Lurks until your plan starts.", "?? HP"), with the backdrop's light rim in dark mode. Tapping it opens the lair.
   - **Advice line** is left out until phase 5.
   - **Label limits:** a dose over the pack's limit shows a calm note under the hero until dismissed.
4. **Log sheet.** Done, from B's `log.html`.
   - The quick times include "2 h ago" from PLAN §4, which the mockup left out.
   - Craving strength hides when the dose is planned.
   - Tags put the most used in the last 30 days first (`TagOrder`), and each group folds after 4 behind "More". Selected tags always show.
   - "Pick a time" is a date then a time. Days after today can't be picked, and a future time is flagged at once.
   - The duplicate question is "Logged twice in a minute. Keep both?", with "Keep both" or "Discard this one".
5. **History.** Done, from the signed-off `history*` mockups, without the guide ticks or filter.
   - Entries are grouped by logical day, each with its total ("so far" for today), 30 days at a time with "Show earlier days".
   - Tap an entry to edit it in the log sheet. Its time stays, and a hidden product can still be edited.
   - Swipe left to delete, with Undo. Screen readers get Delete as an action.
   - A dose after midnight that counts toward the evening before names its clock day: "01:20 Friday".
   - Rows show the amount when it's more than one: "QuickMist, 2 sprays".
6. **Settings.** Done, from the signed-off `settings*` mockups. It opens from Today's top bar, and Today's pencil goes straight to the quick log. The tab bar hides on every Settings page.
   - **Products:** in use and hidden, with Hide and Show. Tap one to change its name or daily pack limit (an hourly limit from the pack stays). Add by hand with a type, a strength typed in mg (`Dose.parseMg`, whole micrograms) and an optional limit.
   - **Add from the catalogue** uses the same `CataloguePicker` as onboarding. Tapping a strength adds it, or hides it if it's in use.
   - **Quick log:** a preview of Today's pills, drag a handle to reorder (Move up and Move down for screen readers), take off or add.
   - **Tags:** rename, move group, hide, or add your own. A renamed built-in tag can go back to its own name.
   - **Day start:** the onboarding stepper and diagram, then "Save day start", which moves every dose.
   - **Backup:** export JSON, export CSV, and restore through the system file picker. Merge adds what's missing; replace warns first with what it deletes. Errors use the app's own words via `BackupException.Reason`.
   - **About:** the disclaimer and when it was accepted, sources as the catalogue research cites them (the emc and DailyMed), privacy, and both font licences from assets.
7. **Lair tab.** Done, lurking only, from B's `lair.html`. The battle comes in phase 4.
   - The stage backdrop with the demon as a shadow on its rock, as on Today's band. Below it are the name, "?? HP" and "Gary watches from the rocks until your plan starts."
   - The bank says "No hits yet" beside an empty slot in the HP bar's empty colour. In phase 4 the slot turns marigold once a hit is banked.
   - The stage runs up behind the status bar, so every other page makes room for the status bar itself (`page` in `AppShell`). On a tall screen the stage also takes the spare height as more sky, so the page doesn't end in a gap.
   - Left out until phase 4: the week label, the rename button, the Hit and 5× buttons, and gear. Until then the demon can only be named in onboarding.
   - Flavour lines use balanced line breaks, so a wrapped line doesn't leave one word on its own.

**Tests:** Compose UI tests for logging (including backdating), edit, delete with undo, and onboarding.

## Phase 1 reviews (23 October)

The copy agent read `strings.xml` against `docs/STYLE.md`. The fresh-eyes reviewer saw only the 70 screenshots, PLAN §16 and STYLE.md.

**Fixed:**
- **Copy:**
  - "Towards", not "toward". The log sheet's button says "Log", as STYLE.md's own example does.
  - Every back button says "Back", since some pages go back to Products or About rather than Settings.
  - The done step no longer claims everything can be changed in Settings.
  - The last dose reads "4 mg of Nicorette gum", so two 2 mg pieces don't look like the 4 mg product.
  - The restore button names what it does: "Add from backup" or "Replace everything".
  - Screen readers hear "Step 2 of 7", "Add Nicorette gum 2 mg" and "1 hour 25 minutes ago".
  - The catalogue's patches are "16-hour" and "24-hour".
- **Week:** Today's week shows days before the first dose as no data, not 0 mg, so a first week doesn't look nicotine-free (`WeekSummary.DayTotal.tracked`).
- **Bands:** the bands on Today, the demon step and the done step grow upwards as more sky when their text needs it, so text stays above the hills. This is `BandScene`, using the art's `horizon` row, which `art/tools/backdrops.py` now writes.
- **Log sheet:** the product row runs to the sheet's edges, and its tiles stay top-aligned at a large font.
- **Onboarding:** the steps bar keeps the back button's place on every step, so it doesn't jump.
- **Smaller fixes:**
  - The quick log's drag handle is a full 48 dp, with clearer dots.
  - The creature and colour tiles leave room under the sprite.
  - The own-tag legend is a tiny chip, not a checkbox-like square.
  - The Settings version lines up with the chevrons.
  - Onboarding's picks tray is one scrolling row.

**Left alone, as they match signed-off mockups or decisions:** the undrawn creatures' dotted frames, the hero's layout, the day-start diagram, About's headings, History's amounts, and the disabled button style.

**The owner's calls:** all six recommendations were approved. Five are applied (see the decisions list), and the fourth waits for phase 4.
1. **A true Bold.** The mockups set row titles, labels, pills and links at weight 700, but the app bundles only 400, 500, 600 and 800, so all of those render at 800 and the app reads heavier than the mockups. Cutting a 700 instance needs the variable font and fontTools downloaded. Recommended.
2. **Dose rows' icon tiles** use the mint tile, which the reviewer reads as green on doses. Recommended: the stone tile on dose rows only.
3. **Dark mode's primary buttons and selected chips** are cream, the brightest thing on screen at night. Recommended: the dark hero green with light text.
4. **The lurking demon in dark mode** looks like a cut-out, with a 1 dp rim finer than its pixels. Recommended: leave it to the phase 4 sprite renderer, which was going to redo the rim anyway.
5. **Today's settings icon** is sliders, which usually means filters. Recommended: a gear.
6. **"Demon"** is both what you name and one of the eight creatures. Recommended: rename that creature "Imp".

## How design and copy are reviewed

The owner asked for agents on UI/UX, colour, tone and copy, so nothing ends up looking or reading AI-made. They run at checkpoints, not constantly:

- **Fresh-eyes reviewer.** After each UI phase, screenshot every screen in light and dark mode, at normal and large font, on a real device or emulator. Give the reviewer only the screenshots plus the brief in PLAN §16 and `docs/STYLE.md`, not the designer's notes. Ask for problems with fixes, AI-made tells, contrast and touch targets.
- **Copy agent.** Before each phase is finished, review `strings.xml` against `docs/STYLE.md`.
- **Visual designer.** Mock up screens that have no mockup yet (History, Settings, onboarding), using the B tokens and components, before building them.
- **Pixel artist.** The other seven creatures (phase 4) follow the dragon's format and tools in `art/tools/`. The sprite data lives in `core/domain/src/main/resources/creatures/`.

## Setting up locally

1. Install a current Android Studio that supports AGP 9.4. It bundles a suitable JDK: if the `java` on your PATH is older than 17, point `JAVA_HOME` at Android Studio's `jbr` folder.
2. Clone `main` into a folder outside iCloud or OneDrive, since synced folders fight Gradle's build files:
   ```
   git clone https://github.com/sepici/quitnrt
   ```
3. Run the full check:
   ```
   ./gradlew checkCopy :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
   ```
4. **Phone (Android 16):** turn on Developer options and USB debugging, then `./gradlew :app:installDebug`.
5. **Galaxy Watch 4** (Wear OS 6), needed from phase 8: Developer options, then Wireless debugging, then `adb pair` and `adb connect`.

## Loose ends

- **Room schema.** `core/data/schemas/` holds the generated schemas; `1.json` is committed. Commit each new version alongside its migration.
- **Mockup fonts aren't in the repo.** The mockups only show the real fonts in the screenshots; the app bundles its own copies.
- **Sprites in dark mode.** The mockups add a 1 px light rim around sprites in dark mode. Do this in the sprite renderer (phase 4), and watch the seam where the feet meet the rock. For now the lurking silhouette gets a 1 dp rim, finer than its pixels, which the fresh-eyes reviewer read as a cut-out: draw it in whole sprite pixels instead.
- **Scratch tools.** The design tools that rebuild mockups and boards are in `docs/design/*/tools/`. `docs/design/tools/shot.mjs` reads Playwright's location from `PLAYWRIGHT_DIR`.
- **Pixel art in the app.** The band and lair backdrops are pixel grids in `core/domain/src/main/resources/backdrops/`, written by `art/tools/backdrops.py` from the mockups' shapes. `GameArt` and `drawPixels` in `ui/game` draw them and the sprites with no smoothing.
- **Icons** are the mockups' SVG symbols turned into vector drawables by `docs/design/tools/svg2vector.py`.
- **Screenshots and UI tests.** `captureLooks` captures a screen in light and dark. Large font is a second test with `@Config(fontScale = LARGE_FONT)`, so sheets and dialogs scale too. `TestApp` stops the clock at Friday 23 October, 15:05 in London. In Compose tests, wait on what the screen shows. Reading semantics syncs with Compose, which runs the main thread's pending work; a wait that only checks the database or a plain flag never does, so the save it waits for never finishes. When there's nothing on screen to wait for, call `waitForIdle()` inside the condition.
