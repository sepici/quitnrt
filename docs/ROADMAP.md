# Roadmap: phases 2 to 9

The slice plans for the rest of the build, from `docs/PLAN.md` §19, written on 23 October 2026.
PLAN says what each phase delivers; this says how it's cut up and what each slice needs.

- **A slice** ends with the full check passing and a push to `main`.
- **Owner** marks a decision that needs a yes before the slice that uses it. Each has a recommendation.
- **Needs you** marks a step only the owner can do: a device in hand, an account, a sign-off.

**Approved on 23 October:** every recommendation below, G1 and G2 included. One change: **Wear OS (phase 8) is built after release**, so the order is phases 2 to 7, then 9, then 8.

**24 October:** the owner gave the batched mockups (phase 3 and phase 5) a green light, and asked for the build to run on through phase 9 to a build ready for testing. They also changed the taper: it now follows the mix of products in a usual day, not whole units of one main product (PLAN §6.1 and §6.2). So **P3-5 is replaced by P3-6** below, and the Plan mockups are updated for the mix before they're built.

**How the rest is built:** in waves of parallel slices, each in its own worktree, reviewed and fixed, then merged one at a time with the full check. One data slice adds every table phases 3 to 5 need in a single migration from version 1, so the screens of phases 3, 4 and 5 can then be built side by side.

## Running unattended

The owner plans to let the work run for long stretches. These rules keep that safe.

1. **Approved decisions only.** Every Owner decision below is settled before the slice that needs it. Anything else that would change agreed behaviour goes in STATUS under "Waiting for the owner", and work carries on elsewhere.
2. **Design gate.** New screens are built from signed-off mockups. Mockups are made in batches ahead of the work (see G1), so building doesn't stop for a sign-off.
3. **Blocked slices.** When a slice needs the owner, it's skipped, and the next unblocked slice goes ahead. STATUS lists what's waiting and why.
4. **Checks.** Every slice runs the full check before its push. After each push, CI is checked once it finishes, and a failure is fixed before the next slice starts.
5. **Reviews.** The copy agent and the fresh-eyes reviewer run at the end of each phase. Findings that would undo a signed-off design wait for the owner; the rest are fixed.
6. **Handover.** STATUS is updated with each slice, so any session can pick up where the last one stopped.

## Phase 2: Insights (the rest)

Slices 1 to 3 are done: the domain stats, the signed-off mockups, and the screen with the daily chart.

4. **Both heat maps.** Hour by weekday, shaded by doses or mg, with a tap for a cell's numbers. The calendar, shaded from the lightest day with doses to the heaviest. Both have table views.
5. **Triggers, habits and by product.** Top tags with their change, group totals, parts of the day and untagged doses. The three habit tiles. Product shares and units a day.
6. **The History filter.** One product or one tag, from a sheet with "Show 48 doses". The list then shows "1 of 5 doses" day lines.
7. **Reviews,** then fixes and STATUS.

Also: CI failed once in the UI tests on `92be404`, then passed twice on the same code. If it happens again, find the flaky test and make it wait on the screen.

## Phase 3: Programme

The taper itself: a baseline, a plan built from it, and weekly results. PLAN §6.

1. **Domain rules.** The plan builder:
   - 8, 12, 16 or 24 weeks, the last 2 always NRT-free
   - whole units of the main product, with extra units taken off early and allowances never going up
   - the daily guide is the allowance ÷ 7

   Weekly results: met at or under the allowance, with a 24-hour grace period, then fixed. Also carry on, repeat a week (+1 week, +10 HP) and overtime. With every test PLAN §20 lists.
2. **Data.** The `programmes` and `programme_weeks` tables, with a migration from version 1, tested. Backup format version 2, which still reads version 1 files. The demon moves from settings into a `demons` row with its HP when a plan starts.
3. **Plan tab, before a programme.** Start a baseline or type a usual amount, see the baseline as it builds, then confirm or edit it. Then build the plan: the length, the main product, a preview of every week, and edits to weeks before they start.
4. **Plan tab, during a programme.** This week's allowance and progress, the schedule with met, missed and caught-up marks, and the choice after a missed week (carry on, or repeat with the HP cost shown). Overtime, the end of the programme, and "Start a new programme".
5. **Today with a plan.** The meter against the daily guide, from B's `today.html`. The week columns gain the dashed guide line, ticks and key. The demon band steps out in colour with its HP bar. History gets its guide ticks, held back from phase 1.
6. **Insights with a plan.** The guide line on the daily chart, the weekly view (each programme week against its allowance), calendar shading against the guide, and "days within the guide".
7. **Extra goals (PLAN §6.3), off by default.**
   - Most units a day.
   - A minimum gap, which brings the spacing note on the log sheet.
   - First dose not before a time that moves 15 minutes later each week.
   - The strength hint.

   They show in stats and never change a result.
8. **Reviews.**

**Owner decisions:**
- **P3-1 Baseline from what's logged.** If the last 7 full days have at least 5 days with doses, offer them as the baseline at once, rather than making an existing user wait another week. Recommended.
- **P3-2 Week boundaries.** Programme weeks run from the start day you pick (tomorrow by default), not from Mondays. Once a plan runs, Today's week columns follow programme weeks. Recommended.
- **P3-3 When results arrive.** Work them out whenever the app looks, since they only depend on the time and the logs. The weekly-result notification arrives with reminders in phase 6. Recommended.
- **P3-4 Hits before the battle.** Earned hits go into the ledger from phase 3, so none are lost. Spending them waits for phase 4. Recommended.
- **P3-5 Main product.** Replaced by P3-6 on 24 October.
- **P3-6 The taper follows your mix** (the owner's rule, 24 October). The baseline is a usual day of whole units per product. The plan is a ladder from it: one fewer unit of the kind that makes up the most mg, a switch to the lower strength once that wouldn't mean more pieces than you started with, patches a strength at a time. A switch gets a week of its own, as the owner described it (4 pieces of 4 mg, then 8 of 2 mg). PLAN §6.2 has the rule and three worked examples.

**Needs you:** sign off the Plan tab mockups (see G1). Today's meter is already in the signed-off `today.html`.

## Phase 4: Demon and achievements

The game. PLAN §7 and §8.

1. **The other seven creatures** (Imp, Bear, Kraken, Goblin, Slime, Wraith and Serpent), by the pixel artist agent in the dragon's format. Each gets 5 frames, defeat frames and 3 colour variants, and the dragon gets defeat frames too. Preview sheets go in `art/` for review.
2. **Gear and badges.** 8 gear icons and about 35 badges, at 16 × 16 in the master palette.
3. **Domain rules.**
   - The hit ledger: earn, spend, and the 5× attack.
   - HP from the ledger, and the defeat condition.
   - Each achievement rule, with positive and negative cases, and a check that none deals damage.
   - Gear unlocks.
   - PLAN's two 12-week runs: one that ends at exactly 0 HP, and one with misses and catch-up that also ends at 0.
4. **Data.** The ledger, achievements, gear and breathing sessions tables, with a migration from version 2 and backup version 3.
5. **The lair's battle,** from the signed-off `lair.html`:
   - the HP bar, banked hits, the Hit and 5× buttons, and the gear row
   - condition sprites by HP, and the idle animation
   - renaming and restyling the demon
   - the dark-mode rim, drawn in whole sprite pixels
6. **Animations, haptics and sound.**
   - A hit: wind-up, flash, knock-back and "−10".
   - The 5× combo and its screen shake.
   - The defeat sequence and the victory summary.
   - 8-bit sound made in code, which respects silent mode and has its own switch.
7. **Achievements on screen.** The unlock toast, the "new gear" card and the trophy shelf.
8. **The rest of the game layer.** All eight creatures selectable in onboarding, the "1 hit ready" badge on Today and the Lair tab, and the HP strip on Today's band.
9. **Reviews.**

**Owner decisions:**
- **P4-1 Creature review.** The pixel artist draws all seven, and you review the sheets together once, not one by one. The art can start in the background during phase 3. Recommended.
- **P4-2 Achievements already earned.** When phase 4 arrives, they unlock in one summary card, not a flood of toasts. Recommended.
- **P4-3 Sound.** On by default, quiet in silent mode, with its own switch, as PLAN says. Recommended.
- **P4-4 Thresholds.** As PLAN lists them, tuned by simulating a typical 12-week run in tests so unlocks come at a steady pace. Recommended.

**Needs you:** review the creature, gear and badge sheets, and listen to the sounds and feel the haptics on your phone.

## Phase 5: Advice

Advice matched to your own triggers. PLAN §9.

1. **The content.** About 30 strategies from NHS and CDC guidance, each with steps, the tags it suits and its source. It's written by the copy agent and checked against the sources. It never suggests taking more.
2. **Domain rules.** Matching triggers to strategies, ranking by your top tags of the last 14 days, and timing tips from your data, such as "Try having your dose at 12:30, before lunch."
3. **The breathing timer** (box breathing and 4-7-8, animated, 1 to 3 minutes, with sessions recorded for the achievements) **and the delay timer** (5 to 10 minutes).
4. **Where advice shows:**
   - Today's card, the advice line held back since phase 1
   - a tip straight after logging
   - the triggers section in Insights
   - the full list behind a button
5. **Reviews.**

**Owner decisions:**
- **P5-1 Sources.** I read the NHS and CDC pages and cite each strategy. You review the text before release, not before building. Recommended.
- **P5-2 The full list.** It sits behind a button from Today's card and Insights, as PLAN §15 has it, not in a sixth tab as §9 mentions. Recommended.

**Needs you:** sign off the advice mockups (see G1), and review the content before release.

## Phase 6: Quick-log and reminders

One-tap logging and nudges. PLAN §11 and §13.

1. **The plain-language matcher.** Phrases like "two 4 mg gums" or "21mg patch" become a product and a quantity, and an ambiguous phrase returns its candidates. It's tested against a table of phrases, and the deep-link parser shares it.
2. **Deep links and shortcuts.** `lasthit://log?...`, plus app shortcuts for your top 3 favourites and "Log". Every way in uses the same logging path, records its source and has the duplicate check.
3. **The home-screen widget** (Glance). Today's mg against the guide and 2 or 3 favourites. A tap logs at once, shows "Logged" with Undo, and can open a quick tag picker.
4. **The Quick Settings tile.** It logs your default product with an Undo notification, or opens the log sheet if you'd rather.
5. **Notifications and WorkManager.**
   - The permission is asked on Android 13 and newer, the first time a reminder is switched on.
   - Channels and quiet hours.
   - The reminders: the allowance nudge, the evening check-in at 21:30 with Log and All good, the weekly result, achievements, and "Forgot to log" (off by default).
   - A Reminders page in Settings.
6. **Reviews.**

**Owner decisions:**
- **P6-1 Deep links.** The custom scheme only, with no https links, since those would need a file hosted at thelasthit.app. Recommended.
- **P6-2 The widget's Undo.** It stays for about 5 seconds after a tap. Recommended.

**Needs you:** try the widget, the tile and the notifications on your phone.

## Phase 7: AppFunctions

Ready for Gemini when Google opens it up. PLAN §12.

1. **Check the library.** Look at the current `androidx.appfunctions` alpha, pin it, and keep all of it in one package.
2. **The five functions** (`logNrtUsage`, `listNrtProducts`, `getNrtSummary`, `undoLastNrtEntry` and `getNrtAdvice`), each described in KDoc. They go through the matcher and the normal logging path.
3. **Tests, an adb script** for `adb shell cmd app_function …`, and Early Access notes in `docs/`.

**Owner decision:**
- **P7-1 API changes.** If the alpha API no longer matches PLAN's shape, follow the library and keep what the five functions do. Recommended.

**Needs you:** connect your Android 16 phone for the adb run, and register for the Early Access Programme.

## Phase 8: Wear OS (after release)

Logging from your wrist. PLAN §14. The owner moved this phase after phase 9.

1. **The watch module.** A `:wear` module (minSdk 30) with the same app ID and signing. The watch message types go in the domain: an ID and the original time, so a message sent twice counts once. With tests.
2. **The phone side.** A Data Layer listener that applies each watch log once and sends back the day's summary and the favourites.
3. **The watch app.** Favourites, logging with a time nudge, the last 5 entries and undo, with a queue for when the phone is out of reach.
4. **The tile** (an arc of mg against the guide, plus favourites) **and the complication.**
5. **Reviews.**

**Owner decision:**
- **P8-1 Testing without the watch.** Download a Wear OS emulator image (about 1 to 2 GB) so the watch app can be checked unattended. Your Galaxy Watch 4 then confirms it at the end. Recommended.

**Needs you:** pair the Galaxy Watch 4 for the final check.

## Phase 9: Polish and Play readiness

A releasable build. PLAN §18.

1. **Accessibility.** A screen-reader pass on every screen, with semantics checks in the tests, plus touch targets, contrast and large fonts.
2. **Estimated absorbed figures.** An off-by-default setting, with each form's factor from published sources. It shows beside the labelled mg and never sets a target.
3. **The release build.**
   - R8 and resource shrinking, which clears lint's NotShrinkingResources.
   - Tidying lint's ObsoleteSdkInt.
   - Release signing with the upload key from GitHub Secrets.
   - A signed release bundle from CI.
4. **Store documents** in `docs/`:
   - the privacy policy ("no data collected")
   - the Data safety and Health apps answers
   - the store listing, by the copy agent, with no treatment claims
   - the screenshots
5. **Final reviews** of the whole app.

**Owner decisions:**
- **P9-1 Absorbed figures.** Build them, off by default. Recommended. The alternative is to drop them, since PLAN calls them optional.
- **P9-2 Store screenshots.** From the Roborazzi renders in a device frame. Recommended.

**Needs you:**
- create the Play Console account and app
- make the upload key and add it to GitHub Secrets
- host the privacy policy at thelasthit.app
- submit the app

## For working unattended

- **G1 Batch the mockups.** Before a long unattended run, the designer agent mocks up every new screen for the next phases:
  - phase 3: the Plan tab's states, the missed-week choice and the end of a programme
  - phase 5: the advice card, the tip after logging, the full list and the two timers

  You sign them off in one go, and building then runs without stopping. The lair battle is already signed off, and phase 6's widget and tile can follow the app's look without mockups. Recommended.
- **G2 Waiting list.** STATUS keeps a "Waiting for the owner" list: sign-offs, device checks and review findings that touch signed-off designs. Recommended.
