# Status and handover

Last updated: 24 October 2026, late. Phases 2 to 7 and 9 are built, reviewed and on `main`, and the app is ready for testing on a phone. Wear OS (phase 8) comes after release.

## Where things are

| Phase | State |
|---|---|
| 0. Foundation and direction | Done. Gradle set-up, CI, copy lint, style guide, design direction chosen, first creature signed off |
| 1. Logging | **Done.** Domain rules, catalogue, data layer, backup, theme, sprites, plus **onboarding, Today, the log sheet, History, Settings and the lair**, reviewed, with the owner's calls on the reviews applied. |
| 2. Insights | **Done.** The stats, every Insights section and the History filter, reviewed, with the findings fixed. |
| 3. Programme | **Built and reviewed.** The mix-based taper (P3-6), the Plan tab, Today, History and Insights with a plan, extra goals and their stats, and judged totals. |
| 4. Demon and achievements | **Built and reviewed.** The game rules, unlocks with toasts and the trophy shelf, gear, and the lair's battle with animations, haptics and sound. The seven new creatures stay off until your look (P4-1). |
| 5. Advice | **Built and reviewed.** The content, the rules, the list, strategy pages, both timers, and advice on Today, after logging and in Insights. |
| 6. Quick-log | **Built and reviewed.** Links, shortcuts, the widget, the tile, notifications, reminders, and a reminders step in onboarding. |
| 7. AppFunctions | **Built** on `androidx.appfunctions` 1.0.0-beta01, waiting for a run on your phone. |
| 9. Polish | **Built.** The release build, estimated absorbed figures, the copy, design and accessibility reviews of every screen, the store documents, and an end-to-end journey test. |
| 8. Wear OS | After release. |

The full check passes locally on `main`. Its tests run the domain, data and app, including `JourneyTest` (onboarding to a rematch through the real screens) and `ProgrammeConsistencyTest` (seeded random runs that check HP, hits, results and allowances after every step).

## Waiting for the owner

Nothing blocks the build. It's in four parts: what to try, what to decide, what's needed before release, and the calls taken while running, for reference.

### 1. Try it on your phone

Install with `./gradlew :app:installDebug`, or the APK from CI's latest run. A fresh install starts at onboarding. Things only a phone can show:

- the widget, the tile and the notifications, including the delay timer's buzz with the screen locked
- the lair's sounds and haptics, with the phone on silent too
- a link sent while the app is open (from Tasker, or `adb shell am start -a android.intent.action.VIEW -d "lasthit://log?product=..."`), which should reach the open app without a second copy
- AppFunctions: `scripts/appfunctions.sh`, and the Early Access sign-up in `docs/APPFUNCTIONS.md`
- `docs/RELEASE.md`'s phone checklist, with a release build. Its API 37 emulator pass is still to do, since this machine has no API 37 image.

### 2. Decisions, most important first

Each has a recommendation in brackets. None blocks testing.

- **The art (P4-1).** `art/previews/line-up.png` and the creature sheets, plus `icons-gear.png`, `icons-badges.png` and `icons-3x.png`. (Sign off the seven and flip `SEVEN_SIGNED_OFF` in `Creature.kt`: onboarding, the rematch and the lair then offer all eight, and the "only the dragon" placeholders go.)
- **The HP bar is green** in the signed-off `lair.html` and `today.html`, which the final reviewer read against "green only means at or under". (A pixel-red ember for HP left, keeping the tan for HP lost.)
- **Privacy wording.** Android's own backup to your Google account can copy the app's data, so three lines overclaim: onboarding's "Everything you log stays on this phone", About's "stays on this phone unless you export it" and backup's "nothing leaves the phone unless you send it". (Mention Android's backup and point to the hosted policy. Also make Android's backup encrypted-only: `disableIfNoEncryptionCapabilities` on Android 12 and newer, and client-side encryption rules for 9 to 11.)
- **"Start a 7-day baseline"** does the same as "Use your last 7 days" when the last 7 days already qualify, so someone whose last week wasn't typical can't measure a fresh one. (Make it always start today.)
- **"Honest ledger"** unlocks the first time the log sheet's "1 h ago" is used. (Make it more than an hour, so "2 h ago" or "This time yesterday" earn it.)
- **Patch absorption** is 80% of the labelled mg, from the Nicotinell leaflet's 77% bioavailability. (Keep 80%; 100% if you'd rather read the labelled mg as what's absorbed.)
- **An agent's two log calls for the same product within a minute** log two doses, since PLAN §4's duplicate check covers only logs from elsewhere. (Flag them too, for agent calls only.)
- **Copy in signed-off mockups** that the copy agent would change (recommendation after the arrow):
  - build screen "while you're still using more" → "while the daily guide is still high"
  - Today's "this fortnight" → "over the last 14 days"
  - tag names mid-sentence → in quotes: "You tagged that dose “Stressed”."
  - confirm screen "if it isn't a normal day for you" → "if it doesn't match your usual day"
  - goals intro "Each one starts off." → "Each is off until you switch it on."
  - "Next, you pick" → "Next, pick"
  - the morning routine and gum-and-drinks summaries in the advice list
  - Overtime's "If a craving gets too much, NRT is far safer..." was changed already, because it tied NRT to a craving in an NRT-free week; it now uses the missed-week sheet's sentence.
- **Smaller design calls** from the final review:
  - plan dates in full ("From 24 October")
  - the build table's "Week and daily mix" → "Week and mix", since it wraps at large font
  - empty HP blocks and the lightest heat step under 3:1 → a 1 dp edge
  - the widget's big figure in the app's font, as a bitmap
  - keep "?? HP", the hero's green when over the guide in dark mode, and the spoken "mg left" line
- **Phase 2 review calls** that would change signed-off designs:
  - heat-map shading → whole-mg steps from the lowest day, and dose steps of 1 to 5+
  - the History filter's product row → wrap
  - Body and Habit in the filter sheet → a row each
  - By group's five tiles, most at 0% → only groups with doses
  - single-choice chips' check mark → drop it there
  - shares under about 20 doses → counts ("3 of 16 doses")
  - History rows "13:40, After a meal" → "13:40 · After a meal"
  - the green on selected states and primary buttons → keep
- **Two limits at 2× font:** the smallest widget (250 × 110) cuts its note to one line, and plan schedule rows break "September" mid-word.
- **PLAN wording:** §8.1 says a day counts once its late-log window is over, but "Logged on N days" counts a day as soon as it has a dose, as "First entry" must. (Reword PLAN.)

### 3. Before release

- Review the advice content (P5-1): `core/domain/src/main/resources/advice/strategies.json`, with each strategy's sources.
- Make the upload key and add the four `LASTHIT_UPLOAD_*` secrets (`docs/RELEASE.md`).
- The store (`docs/store/`):
  - fill in the developer name and contact email in the privacy policy and listing
  - host the policy at thelasthit.app
  - make the 512 × 512 store icon
  - create the Play Console app
- If you want certainty on the medical-device question, ask the MHRA's borderline products enquiry about the plan and the timing tips (`docs/store/health-apps.md`).

### 4. Calls taken while running, for reference

Each was taken so building could go on. Say if any should change.

- **The taper:** a switch to a lower strength gets a week of its own (PLAN §6.2, example 3). A pack with nothing lower from its brand or your products falls back to the catalogue's generic line, keeping to the strictest limit of the packs on sale, so no plan drops from 4 mg or 21 mg straight to nothing. Patch strength steps don't get a switch week; the rule is about pieces. The Plan mockups' numbers from week 3 on predate the switch rule, and the app shows the domain's.
- **The Plan tab:**
  - A baseline short of 5 days with doses runs on a day at a time.
  - The missed-week sheet has "Carry on" chosen, and closing it counts as Carry on.
  - Editing a week lists every step it can take.
  - "Most units a day" is a cap per product.
  - Past weeks keep their mix line.
  - Gary lurks on Today until the plan's first day.
  - An NRT-free week's meter runs to the usual day.
- **Insights with a plan:**
  - Overtime weeks are named "Overtime", "Overtime 2" and so on.
  - "Days within the guide" is a Habits tile.
  - The calendar shades in quarters of the guide.
  - The guide shows only with all products chosen.
  - "Your goals" follows Habits.
- **Badges and gear (P4-4, tuned by simulation):**
  - Streaks are 10, 21 and 40 days, the last weeks-met tier is 10, and "Logged on 30 days" is 28.
  - The war hammer comes at half the programme's hits, and the lightning bow when the final taper week is met.
  - A week under its allowance needs every day logged.
- **Advice:**
  - The tip after logging is a card in the advice line's slot, at most one a day per tag.
  - Advice uses the last 14 days everywhere.
  - A breathing session counts when it runs to the end.
  - The delay timer's notice is a reminder on its own channel, outside quiet hours.
- **Reminders:**
  - Quiet hours (22:00 to 07:00) drop nudges, the check-in and "Forgot to log", while results and badges wait.
  - Results come in while the app stays open, once a minute.
  - Tapping a result opens the Plan tab, and a badge opens the lair's shelf.
  - Reminder settings aren't backed up.
  - The onboarding step asks for notifications, with Skip only where there's a question to skip.
- **Absorbed figures:** off by default. When on, they show on Today, History and the log sheet, never in Insights or any target.
- **No mockups,** so built from B's language and reviewed: the trophy shelf, toasts, the "new gear" card, "Your goals", the Plan tab's goal lines, the spacing note and the onboarding reminders step (`onboarding-6b-reminders`).

The roadmap's decisions were all approved on 23 October, with Wear OS moved to after release. On 24 October the owner gave the phase 3 and phase 5 mockups a green light, asked for the build to run on to a build ready for testing, and changed the taper to follow the mix of products (PLAN §6.2, ROADMAP P3-6).

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

## Phase 1: the screens

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

## Phase 2: Insights

From PLAN §10 and §19, agreed with the owner on 23 October.

**In phase 2:**
- The 7, 30, 60 and 90 day ranges.
- Daily intake with a 7-day rolling average.
- The hour-by-weekday and calendar heat maps.
- Triggers, habits and by product.
- The History filter by product or tag.

**Waits for phase 3, because it needs a plan:** the guide line, the weekly view, calendar shading against the guide, and "days within the guide". Advice cards wait for phase 5.

**The owner's calls:**
1. Charts are drawn with Compose Canvas, not Vico, like the heat maps and Today's week.
2. A product-type filter scopes every chart, in place of bars stacked by type. Nicotine has to stay neutral, so stacked types couldn't be told apart by colour.
3. "Minutes after waking" waits for the wake-time setting, with reminders in phase 6. Habits show the first dose's clock time.

**Rules,** each in `core/domain/.../insights` with tests:
- **Ranges:** 7 days is always on; 30 needs more than 7 days of history, 60 more than 30, and 90 more than 60. A window never starts before the first logged day (`InsightRange.window`).
- **Rolling average:** up to 7 days, never before the first day. Today is left out until it's over, and is left out of the average a day too (`DailyIntake`).
- **Hour heat map:** rows run an hour each from the day start, so a 04:30 day start gives 04:30 to 05:30 rows, and columns are the logical day. A dose just before the day start sits at the bottom of the day it counts towards. Hours are read off the dose's own clock, and patches are left out (`HourHeatMap`).
- **Calendar:** days in the range before the first dose have no data, not zero (`CalendarHeat`).
- **Heat shading:** an empty step plus 5 shades of one ramp (`HeatScale`).
- **Times of day:** morning from 05:00, afternoon from 12:00, evening from 17:00, night from 22:00, on the dose's own clock (`TimeOfDay`).
- **Triggers:** shares are of doses, so they don't add up to 100%. A dose counts once for its group, and patches are left out (`Triggers`).
- **Habits:** gaps count within a day, so sleep is never one, and patches are left out of every habit. The first dose is averaged in minutes from the day start, so it works across midnight (`Habits`).
- **Comparisons:** a change against the previous period only shows when that period is fully logged (`InsightWindow.hasFullPrevious`).
- **By product:** shares of mg, with patches included, and units a day across the whole period (`ByProduct`).

**Slices:**
1. Domain stats and tests. Done.
2. Mockups for Insights and the History filter. Done and signed off: `docs/design/fieldbook-v2/b/insights*` and `history-filter*`. The owner's calls on them:
   - The 7-day line starts once there are 7 days to average, rather than averaging fewer days at the start.
   - Changes against the previous period wait until it's fully logged, and the screen says when they'll start.
   - Patches stay out of the first dose, as out of every habit: a patch goes on at the same time each day, so it would hide whether the first craving dose comes later.
   - Above 30 days, the chart shows one column a week, that week's average a day, with no 7-day line.
   - The calendar shades from the range's lightest day to its heaviest, with the legend in mg. Phase 3 shades against the guide instead.
   - The History filter takes one product or one tag, confirmed with "Show 48 doses". Days with no match are hidden, and day lines read "1 of 5 doses".
   - Each chart has a table button that turns it into a plain two-column list of its numbers.
   - The heat-map ramp: light `#A6A398 #8A877D #6F6C62 #555248 #3C3A30`, dark `#4E4A40 #6D6A5F #8F8B7F #B2AEA2 #D5D2C5`, with `sunk` for none. It passes the chart guidance's ordinal checks in both modes.
3. The Insights screen and daily chart. Done.
   - The range buttons sit in a sunk track. Locked ones are dashed, with a line saying when the next opens. The product-type chips run in one scrolling row, most mg first.
   - The chart is `MgColumns` in `ui/insights`, drawn in Canvas. It has stone columns with rounded tops, the 7-day line on a halo, and today as an outline. Tapping a column rings it and shows a tip beside it. The y axis widens to fit its labels at large font.
   - Above 30 days the chart shows a column a week, with this week outlined.
   - The table button swaps the chart for `ChartTable`, every day's number, newest first.
   - With nothing logged, the screen says so and offers the log sheet.
   - `seedHistory()` in the app tests logs two months of plausible use for Insights screenshots.
4. Both heat maps. Done.
   - **When you use** (`HourMap` in `ui/insights/HeatCharts.kt`): shaded by doses or mg, with a tip on tap, a table of every hour with a dose, and a legend of the steps in use.
   - **Calendar** (`CalendarMap`): days before the first dose are dashed, and today is ringed. The legend marks where each step starts, so its labels can be decimals.
   - A tip goes below its cell only when it fits, otherwise above, and both maps draw over what follows them.
   - The heat ramp is in `Color.kt` (`heat`, with `heatFill` and `heatInk`).
5. Triggers, habits and by product. Done, in `ui/insights/PatternSections.kt`.
   - **Triggers:** the top 5 tags with their change in points (arrows, never green or red), group shares, the top tag in each part of the day, and the untagged line. Under 10 doses these read as counts.
   - **Habits:** first dose, average gap and longest gap tiles.
   - **By product:** each product's share of the finished days' mg and units a day, or today's doses so far when a type has none on finished days.
   - With a product type chosen, each section names it, as the gum mockup does ("Gum triggers"). Only gum's wording was signed off; the other types follow its pattern.
   - A window of nothing but patches shows one note in place of the hour map, triggers and habits. Comparisons in the 7-day range say "the week before".
6. The History filter. Done.
   - A filter button in History's top bar opens a sheet with one product or one tag. Only products and tags with doses are listed, most used first, and tags fold after 4 per group behind "More".
   - Under a filter, a chip names it and clears it, with the count beside it. Days with no match are hidden, day totals count only the matches, and day lines read "1 of 5 doses". A page is 30 days with a match.
   - `HistoryFilter.matches` (`domain/log`) is the rule, and `EntryRepositoryTest` checks the SQL in `observeMatching` against it.
7. Copy and fresh-eyes reviews. Done on 24 October.
   - The copy agent's wording throughout: "From 1 November, you'll see changes against the week before.", "First dose and gaps, against the 30 days before.", "Used on 6 of 29 days", "mg from gum", "Each day runs from 04:00".
   - Tips sit under a calendar row, with a pointer and a tight shadow. A picked day has a ring inside its square, so only today has the outer ring. The calendar runs edge to edge in 48 dp rows, and the 1st puts its month on a line of its own.
   - The hour map dashes weekdays the window hasn't reached (`HourHeatMap.daysOf`), and a long press then a slide moves the pick.
   - Days with no doses are a tick on the baseline. Averages keep one decimal (`Dose.averageText`).
   - At large font the range buttons go two by two, the product chips wrap, and habit tiles stack.
   - Every sheet has the mockups' 56% scrim, plus a hairline edge in dark mode (`sheetScrim`, `Modifier.sheetEdge`).

## Phases 3 to 6: built so far (24 October)

- **The mix-based taper** (P3-6, `domain/programme`):
  - `UsualDay` holds whole units per product, from logs or typed. Each product is its average a day on days with doses, rounded half up, and the product with the most mg always keeps a unit.
  - `Ladder.build` takes one unit a step from the kind with the most mg, switches to lower strengths, and takes patches down a strength at a time. It keeps to pack limits as it walks down: a usual day over a limit comes down to it at step 1, and a switch waits until the lower strength's pieces fit their pack.
  - Lower strengths come from the user's products, then the lines in use wherever they're sold, then other lines of the same brand and form. Discontinued packs are never offered.
  - When both rules fit the last strong pieces, the switch wins: 3 × 4 mg gum goes 1 × 4 mg, then 2 × 2 mg, then 1 × 2 mg.
  - Weeks follow PLAN §6.2's formula, each with its mix, a guide equal to the mix's mg and an allowance of 7 × the guide. Edits move a week along the ladder. The main product (P3-5) is gone.
- **The data for phases 3 to 5:** Room version 2, by one migration from version 1, tested; `2.json` is committed.
  - Tables: `programmes`, `baseline_units`, `ladder_units`, `programme_weeks` (kind, step, result, the choice after a miss, and the extra-goal columns), `programme_week_units`, `demons`, `hit_ledger`, `achievements`, `gear` and `breathing_sessions`.
  - Repositories in `DataGraph`: `programmes` (baseline, usual day, build, start, edit, the missed-week choice, settle, end, abandon, rematch), `game` (ledger, hit and 5×, restyle, defeat, unlocks, equip) and `breathing`.
  - Results are worked out when the app looks (P3-3): `programmes.observeCurrent()` settles when collection starts, and every entry write settles first. The day start changes through `entries.changeDayStart`, which settles under the old one first.
  - When a plan starts, the demon moves into a `demons` row with 10 HP a week, and a repeat adds 10. Settings hold the next demon, so a rematch brings the same one back unless it's restyled first. A programme ends only once its demon is defeated.
  - Lower strengths the plan switches to join the products list, but not the quick log.
  - Backups are format version 2, and still read version 1. A merge never adds a second running plan, and `StoredCounts` lists everything a replace deletes.
- **Advice rules** (`domain/advice`): `TopTriggers` over the 14 full days before today; `AdviceMatcher` in PLAN §9's table order, then narrowest first, with no repeats across the top triggers; Today's card turning daily through the top trigger's first 3; the tip after logging leading with an interactive strategy that names the tag; `after_a_slip` only when a slip is passed in; `TimingTips` moving an after-meal dose half an hour earlier (never before the day's first dose) and the first dose 15 minutes later each week.
- **Links and shortcuts** (phase 6 slice 2): `lasthit://log?...` from cold or into the open app (singleTop), and from an NFC sticker. A link logs with source `DEEP_LINK`, or `SHORTCUT` for the app's own shortcuts, with the duplicate question and Undo over whichever page is open (`ui/link/LinkHost`). An ambiguous product opens the sheet on its candidates. Every `LogLinkError` has its own message. Shortcuts: "Log a dose", plus the first 3 quick-log products.
- **The release build** (phase 9 slice 3): R8 and resource shrinking, `LASTHIT_UPLOAD_*` signing with a debug-key fallback (CI fails if only some secrets are set), a CI release job on `main`, and `targetSdk` 37. `docs/RELEASE.md` has the steps.

- **Game rules** (`domain/game`), on the game's own inputs (`GameRecord`, `GameProgramme`, `GameWeek`, `BreathingSession`), so the programme can be mapped onto them:
  - `HitLedger`: hits for weeks met and catch-up, a hit costs 10 HP, and the 5× attack costs 5 banked hits for 50. HP is max − 10 × spent, and 0 is the defeat. Only weeks earn hits, so no achievement deals damage.
  - `AchievementRules`: a rule for every badge. `Gear.unlockedBy` and `Loadout`: only unlocked gear can be equipped.
  - `NewUnlocks.since` (P4-2): what's new against the stored keys, as one summary card when more than 3 arrive at once.
  - PLAN's two 12-week runs end at exactly 0 HP.
  - For the mapping: set `endedOn` from an abandoned programme and keep every planned week; leave restored entries (`createdAt` set at restore) out of `GameRecord.loggedAt`, or a restore would unlock "Honest ledger".

- **Programme rules** (`domain/programme`): the baseline, the plan builder, the clock, results with the 24-hour grace period, carry on, repeat, overtime, hits and HP. The data layer calls `Programme.settle` for due results before any entry write, since the domain can't tell when an entry was written: that ordering is what keeps a result fixed once it's in.
- **Creatures** (`resources/creatures/`): the imp, bear, kraken, goblin, slime, wraith and serpent, each with idle1, idle2, hurt, rough, barely and defeat1 to defeat4, in the dragon's four variants. The dragon gained defeat frames; its signed-off frames are unchanged.
  - The defeat (`SpriteFrame.DEFEAT`, `SpriteSheet.defeatPixels`) is a drawn knockout, then three breakup frames that crack, burst and scatter into pixels.
  - `SpriteSheetTest` checks that every hurt and rough frame, and the step to barely, changes enough pixels to see at a glance.
  - The sources are in `art/tools/creatures/`, built by `build_creatures.py`; `preview.py` renders `art/previews/` without Pillow.
  - `Creature.isDrawn` stays false for the seven until the owner has looked (P4-1).
  - Sprites run to the edges of their 32 × 32 grid, so the dark-mode rim needs a canvas padded by 1 px a side.
- **Gear and badges** (`resources/icons/`, `domain/game`): 8 gear icons and 33 badges at 16 × 16, a frame per badge group, drawn by `art/tools/icons.py`. `Gear` and `Achievement` hold the keys.
- **Master palette:** one file, `resources/palette.json`, as ramps (dark to light) shared by creatures, gear and badges, loaded by `MasterPalette`. It has 44 colours, above PLAN's "about 24", because each creature needs its own ramps.
- **Advice content** (`domain/advice`, `resources/advice/strategies.json`): 35 strategies from NHS, CDC and HSE pages, each with sources and the date read. The breathing and delay timers are marked as interactive. A test holds the content to PLAN §9's table, and strategies can be limited to product forms. It also has quitlines for each market.
- **Matcher** (`domain/matcher`): `ProductMatcher`, `TagMatcher` and `LogLinkParser`. A number before a unit or a plural is how many ("nicorette 2 gums"), and a leading number before a singular is the strength ("21 patch"). Times in links are local, and a future time is refused.

## Phases 3 to 7: the screens (wave C, 24 October)

Built in parallel worktrees, each reviewed through a code lens and a design lens against the mockups, then merged and checked for lost work.

- **Plan tab** (`ui/plan`): every state from the plan mockups, from start and baseline to the rematch, plus the extra goals page. `Programme.switches()` marks the preview's switch weeks. Results that fell due while the app was away arrive when it comes back (`SettleOnStart` in `AppShell`).
- **With a plan:** Today's meter against the daily guide, programme weeks in the week columns, and the demon band with HP from the ledger (`DemonHpBand`). History ticks days at or under their guide. Insights adds the stepped guide line, "Week by week" against each allowance, "days within the guide" and the calendar against the guide. The rules are in `programme/GuideDays.kt`, `programme/WeekReport.kt` and `insights/PlanGuides.kt`; test seeds are `seedPlan()` and `seedMockupPlan()` in `PlanTestData.kt`.
- **Game:** `db/GameRecordMappers.kt` maps what's stored onto `GameRecord`. `graph.unlocks` checks after every write and on opening, and `UnlockHost` shows a pixel toast per badge, a "new gear" card, or one summary card for more than 3 (P4-2). The lair has the battle (`BattleMotion`: a hit, the 5× with its shake, the defeat frames and the victory), the gear row and sheet, the trophy shelf, and renaming and restyling. Sound is `ChipSynth` and `ChipPlayer`, quiet on silent or vibrate, with a switch under Settings, The lair. With animations off, the lair keeps still. The dark-mode rim is drawn in whole sprite pixels everywhere (`PixelRim`), and broken-up frames get none (`BreakingFrames`).
- **One demon picker** (`ui/game/DemonPicker.kt`) serves onboarding, the rematch and the lair's restyle, and honours `Creature.isDrawn`. Flip `SEVEN_SIGNED_OFF` in `Creature.kt` after the art review (P4-1).
- **Sheets:** every modal sheet goes through `AppSheet` (`ui/components/Sheet.kt`), which draws the dark hairline inside the sheet so it moves with it. Don't put `Modifier.sheetEdge` on a `ModalBottomSheet`'s own modifier.
- **Advice:** the list, strategy pages, breathing (box or 4-7-8, in whole rounds by the clock) and the delay timer. Other screens open them through `ui/advice/AdviceNavigation.kt`.
- **Reminders** (`reminders/`): WorkManager with a channel each and quiet hours; the nudge from each log, the 21:30 check-in, the weekly result (it opens the Plan tab), achievements (they open the lair's shelf), and "Forgot to log". The widget is Glance; the tile logs or opens the sheet. New `EntrySource.NOTIFICATION`. After changing the widget, re-record `WidgetScreenshotTest` and copy `widget-preview-*.png` into `res/drawable(-night)-nodpi`.
- **AppFunctions** (`agents/`, on `androidx.appfunctions` 1.0.0-beta01): the five functions through the matcher and the normal logging path. An ambiguous product returns candidates with the quantity each would log. `undoLastNrtEntry` stops at the last restore from backup. `docs/APPFUNCTIONS.md` and `scripts/appfunctions.sh` cover the phone run.
- **Strings** for each feature are in their own `strings_*.xml`, which `checkCopy` reads too.

## Wave D (24 October)

- **Goals in stats** (`programme/GoalProgress.kt`, `GoalWeeks`, `GapNote`): "Your goals" in Insights after Habits, a line per goal on the Plan tab's week, and the log sheet's spacing note when a dose comes sooner than the gap goal. It never blocks.
- **Judged totals:** each result keeps the total it was judged on (`ProgrammeWeek.resultTotal`). Room version 4 adds `programme_weeks.resultTotalMicrograms`, and backups are format version 3, still reading 1 and 2. Week by week, the schedule and the missed-week sheet show the judged total.
- **Advice in place:** Today's advice line, the tip after logging (`TipCard`, one a day per tag, kept in `ReminderStore`), after-a-slip advice from the running programme (`domain/advice/Slips`), and Insights' Triggers. The delay timer's end is a notice booked with AlarmManager on its own channel, and the app keeps a partial wake lock while it runs, since the booking can be a few minutes late on Android 12 and newer.
- **Absorbed figures** (`domain/absorbed`, P9-1): each form's share with its range and sources, in whole micrograms, never used for a guide, allowance, result, achievement or Insights (a test reads the sources to check). Off by default, under Settings, Doses, "Absorbed amounts". When on, "about 1 mg absorbed" shows in the secondary colour on Today, History and the log sheet. Backups carry the setting.
- **Onboarding** has 8 steps, with reminders between the day start and done. Notifications are asked once: there, or at the first reminder switched on in Settings.

## Wave E: the final reviews (24 October)

- **Copy:** the copy agent read every strings file and the advice text. Fixed: overtime no longer ties NRT to a craving, withdrawal no longer "can't hurt you", numbers are digits, "Badges and gear" replaces "Achievements", and after a slip the links say "What helps" (`advice_slip_how`). Wording in signed-off mockups waits for the owner (above).
- **Design and accessibility** (phase 9 slice 1):
  - Screen readers get labels that match the screen, sheet and step titles, headings, named fields, and live regions for notes, errors, steppers, timers and the lair. Unlock cards are read first, and figures are said as "used of the allowance".
  - Dialog buttons and date pickers are in the link colour. `ColorContrastTest` holds the token pairs to their ratios.
  - The 5× flashes only on its finisher, and breathing keeps still with animations off.
  - Captures now include 2× font (`-x2`, `HUGE_FONT`) for the busiest screens. The unit-test heap is 2 GB, so the tallest captures don't run out of memory.
  - New strings are in `strings_a11y.xml`.
- **Store** (`docs/store/`, phase 9 slice 4): the privacy policy, Data safety and Health apps answers, the listing, and eight framed screenshots with a feature graphic (P9-2). `StoreScreenshotTest` renders the sources from one seed of generic products, and `docs/store/tools/store_art.py` (standard library only) frames them; re-run both after a look change.
- **End to end:** `JourneyTest` runs MainActivity from onboarding to a rematch over an 8-week plan, checking the Plan tab, Today, Insights, the lair's HP and every badge as the app finds it. `ProgrammeConsistencyTest` plays 16 seeded random runs against the repositories, with writes landing just before results fall due.
  - It found that a result falling due with the app open waited for the next dose. `SettleOnStart` now settles each minute in the foreground, on the same tick the screens move to a new day.
  - Each test's app stops its background work when the test ends, so reminder tests can't see an earlier test's notices.
  - Testing tip: a minute passing with the app open means idling the main looper (the screens' minute ticks) and advancing Compose's `mainClock` (the shell's settling).

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

- **Room schema.** `core/data/schemas/` holds the generated schemas, versions 1 to 4. Commit each new version alongside its migration, and test it from every earlier version. Backups are format version 3, and read every earlier version.
- **Mockup fonts aren't in the repo.** The mockups only show the real fonts in the screenshots; the app bundles its own copies.
- **Sprites in dark mode.** The light rim is drawn in whole sprite pixels on a canvas padded by 1 px a side (`PixelRim`), and frames that break apart get none (`BreakingFrames`).
- **Scratch tools.** The design tools that rebuild mockups and boards are in `docs/design/*/tools/`. `docs/design/tools/shot.mjs` reads Playwright's location from `PLAYWRIGHT_DIR`.
- **Pixel art in the app.** The band and lair backdrops are pixel grids in `core/domain/src/main/resources/backdrops/`, written by `art/tools/backdrops.py` from the mockups' shapes. `GameArt` and `drawPixels` in `ui/game` draw them and the sprites with no smoothing.
- **Icons** are the mockups' SVG symbols turned into vector drawables by `docs/design/tools/svg2vector.py`.
- **Screenshots and UI tests.** `captureLooks` captures a screen in light and dark. Large font is a second test with `@Config(fontScale = LARGE_FONT)`, so sheets and dialogs scale too. `TestApp` stops the clock at Friday 23 October, 15:05 in London. In Compose tests, wait on what the screen shows. Reading semantics syncs with Compose, which runs the main thread's pending work; a wait that only checks the database or a plain flag never does, so the save it waits for never finishes. When there's nothing on screen to wait for, call `waitForIdle()` inside the condition. Never block the main thread on a repository call (`runBlocking` on main can hang Room): run it on another thread and wait on the screen. Each feature's test seeds live in its own file (`PlanTestData.kt`, `GoalSeeds.kt` and so on).
