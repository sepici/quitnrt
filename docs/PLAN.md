# The Last Hit: Quit NRT, implementation plan

Status: **draft for review**. No app code gets written until this plan is approved.

The Last Hit: Quit NRT is an Android app for people who quit smoking or vaping with nicotine
replacement therapy (NRT) and are now hooked on the NRT itself. It logs NRT use, shows what
triggers it, and runs a programme that brings the daily dose down to zero. Alongside it runs a
small game: you name your addiction, pick a pixel-art creature for it, and beat it down one week
at a time. The final week's hit is the last hit.

"The Last Hit" is the family name. Apps for other habits could follow as "The Last Hit: Quit …".

---

## 0. Decisions so far

| Topic | Decision |
|---|---|
| Name | **The Last Hit: Quit NRT** in the store (22 characters, under Play's 30). **The Last Hit** under the icon. |
| App ID | **`app.thelasthit.nrt`**, from the `thelasthit.app` domain (registered). Other apps in the family follow the same pattern, e.g. `app.thelasthit.<habit>`. |
| Audience | People who already quit smoking or vaping using NRT and now want off the NRT. **No vape logging.** Nicotine pouches are allowed as a hidden extra because they're close to pure nicotine. |
| Voice and AI logging | **On-device first.** Widget, Quick Settings tile, app shortcuts and deep links now, plus **Android AppFunctions** (Android's on-device version of MCP) so Gemini can log once Google opens it to all apps. No server, and data never leaves the phone. |
| Audience | **Personal first, ready for the Play Store later.** APK built by CI and installed by hand, with disclaimers, privacy and structure kept clean for a later release. |
| Programme | **Guided plan** with a fixed weekly schedule. The last **2 weeks are always NRT-free**. |
| Week met | The **week's total** stays at or under the weekly allowance (7 × daily guide). NRT-free weeks mean zero use. |
| Demon | You name it and pick a creature. **HP = 10 × programme weeks.** Each week met = **1 hit = 10 HP**. Meeting a week after missed ones pays those back too. |
| Achievements | Give **pixel badges and gear** (weapons that change how hits look and sound). They never deal damage. **No "x days nicotine-free" achievements.** |
| v1 extras | Smart reminders and a Wear OS quick-log app. |
| Look | **Calm, readable app with a pixel-art game layer** (demon, badges, gear, small moments). |
| Language | **British English** (programme, colour, favourite). Time and date formats follow the phone. |
| Builds | You're allowing `dl.google.com` so the app can be built and screenshotted inside the dev container. |
| Test devices | Phone on Android 16 or newer, plus a **Galaxy Watch 4 on One UI 8 Watch (Wear OS 6)**. |
| Day start | 04:00, changeable in Settings. |
| Catalogue | Real products from the big brands, UK shop own-brands and the US, Canada, Australia and New Zealand, researched with sources (§3). |
| Deferred | Resisted-craving log, cost and money-saved tracking, cloud sync and remote MCP server, iOS. |
| Dose unit | Goals and charts use **labelled mg** (what the pack says). An estimated absorbed figure is optional. |
| Stack | Kotlin and Jetpack Compose, native Android. AppFunctions only exists for Kotlin/Jetpack. |

---

## 1. Scope

### In v1
1. Product catalogue of real brands and shop own-brands (gum, lozenges, patches, inhalator, sprays, microtabs), plus generic products.
2. Logging with quantity, backdated date and time, tags, craving strength, planned or craving flag, and a note.
3. History with edit, delete and undo.
4. Insights: daily intake over 7, 30, 60 and 90 days, an hour by weekday heat map, a calendar heat map, trigger stats and habit stats.
5. Guided programme with weekly allowance, daily guide, weekly results and catch-up.
6. **Name your demon**: creature choice, HP, hits, a battle screen and a final defeat.
7. **Achievements**: badges and gear.
8. Advice matched to your triggers, including a breathing timer.
9. Quick-log: home-screen widget, Quick Settings tile, app shortcuts, deep links.
10. Smart reminders (all optional).
11. AppFunctions for Gemini and other on-device agents (Android 16 and newer).
12. Wear OS app: a tile, a complication and a small logging app.
13. Export (JSON and CSV), import (JSON restore) and Android Auto Backup.
14. Onboarding with a medical disclaimer.

### Not in v1
- Resisted-craving log, cost tracking, cloud sync, remote MCP server, iOS, multiple users.
- Health Connect, which has no nicotine data type.

---

## 2. Core ideas

| Term | Meaning |
|---|---|
| **Product** | One specific NRT item: form, strength and unit. For example "Gum 2 mg (piece)", "Nicorette Inhalator 15 mg (session)" or "Patch 21 mg, 24 h". |
| **Entry** | One logged use: a product and a quantity at a point in time, with tags. It keeps a **snapshot** of the mg, so editing a product later never rewrites history. |
| **Logical day** | The day an entry counts toward. A **day start** setting (default 04:00) means a gum at 01:30 counts toward the previous evening. |
| **Background dose** | Patches. They count toward the day they were applied, but stay out of the hourly heat map, spacing stats and trigger stats, since a patch releases steadily rather than answering a craving. |
| **Programme** | A baseline, then a fixed run of weeks. Each week has a **weekly allowance** and a **daily guide** (the allowance divided by 7). The last 2 weeks have an allowance of 0. |
| **Demon** | The creature that stands for your addiction. Its HP is 10 per programme week. |
| **Hit** | Earned for each week met. One hit takes 10 HP. |

---

## 3. Product catalogue

Most people use more than one product, often from more than one brand, so the catalogue lists real products by brand. The research behind it is in `docs/research/catalogue/`, with sources: emc leaflets, FDA labels, and brand and retailer sites. It ships inside the app as a data file and is updated with app releases.

### What's in it
| Group | Products |
|---|---|
| **Nicorette** (UK) | Gum 2/4 mg, Cools lozenge 2/4 mg, Fruit lozenge 2 mg, Invisi Patch 10/15/25 mg (16 h), QuickMist mouth spray 1 mg, Inhalator 15 mg, Nasal spray 0.5 mg, Microtab 2 mg |
| **NiQuitin** (UK) | Clear Patch 7/14/21 mg (24 h), Minis 2/4 mg, Lozenges 2/4 mg |
| **Nicotinell** (UK) | 24 hour patch 7/14/21 mg, Gum 2/4 mg, Mint lozenge 1/2 mg, Rapid Relief mouth spray 1 mg |
| **Boots NicAssist** | Gum 2/4 mg, Lozenge 1/2 mg, Translucent patch 10/15/25 mg (16 h), Microtab 2 mg, Nasal spray 0.5 mg |
| **Shop own-brand** | Gum 2/4 mg and lozenges 2/4 mg from Superdrug, Tesco, Asda, Sainsbury's, Morrisons and Galpharm. Several of these come from the same manufacturer |
| **US** | Nicorette gum, lozenge and mini lozenge 2/4 mg, NicoDerm CQ and Habitrol 7/14/21 mg patches, Nicotrol NS, and store brands (CVS Health, Walgreens, Equate, Kirkland Signature, up&up) |
| **Canada, Australia, New Zealand** | Local Nicorette, Nicotinell, NicoDerm, Habitrol, Thrive, Nicabate, QuitX and pharmacy brands |
| **Legacy** | Discontinued items stay findable for people using up old stock, marked as such: NiQuitin gum and Strips, the NicAssist 24 h patch, mouth spray and inhalator, and the Nicotrol inhaler |
| **Generic** | Any form at any strength, for anything not listed |
| **Nicotine pouch** | Hidden by default. Logged as a generic pouch with the mg typed in, because strengths vary a lot between brands and the research couldn't verify them. Brand names appear only as labels |

### Picking your products
- Onboarding shows products for your phone's region first (UK by default), with a "show other countries" option for travellers and online buyers.
- Pick by brand, or search (e.g. "niquitin minis 4"). Pick as many as you use. They become your favourites.
- Each product carries its **label daily maximum**, e.g. "15 pieces" or "64 sprays, no more than 4 an hour". If a day's logs go over it, the app shows a calm note suggesting a word with a pharmacist. The programme never schedules above it.
- Flavours are listed on the product but don't create separate entries, since they don't change the dose.

Dose maths:
- `mgTotal = quantity × mgPerUnit`, saved on the entry at the time of logging.
- **Mouth spray** (QuickMist and others): 1 mg per spray. It's logged as sprays, and "puff" is accepted as another word for a spray, including by voice.
- **Inhalator:** the UK leaflet gives no puffs per cartridge. It says one 15 mg cartridge gives about eight 5-minute sessions. So by default the inhalator logs **sessions** (an eighth of a cartridge, about 1.9 mg labelled) or **whole cartridges**. Puffs are available if you set your own puffs per cartridge.
- **Patch:** the full labelled dose counts on the day it went on. An optional **removed early** setting scales it by hours worn.
- **Estimated absorbed** (optional, off by default): a rough per-form factor shown as "about x mg absorbed". The factors come from product labelling and are shown with a disclaimer. Goals never use them.

---

## 4. Logging

### Log sheet
It opens from Today, the widget, the tile, a shortcut or a deep link.
- **Product:** favourites as chips, plus "More".
- **Quantity:** a stepper.
- **Time:** **now** by default. Quick buttons for 15 min ago, 30 min ago, 1 h ago, 2 h ago and yesterday, plus a full date and time picker. Future times are refused, with a few minutes' leeway.
- **Tags:** grouped chips, pick as many as apply (see §5). Your most used tags come first.
- **Craving strength:** optional, 1 to 5.
- **Planned or craving:** a toggle (a dose taken on schedule, or one taken because of a craving).
- **Note:** optional.
- **Save.** A snackbar shows "Logged 2 mg gum" with **Undo**.

### Safeguards
- **Duplicate check:** the same product logged twice within 60 seconds from different places (say the widget and Gemini) asks "Logged twice in a minute. Keep both?"
- **Spacing note:** if the week has a minimum gap between doses set and this dose comes sooner, a gentle note appears with a link to the 4 Ds (§9). It never blocks logging.
- Editing recalculates `mgTotal` from the product's current settings only if the product or quantity changed.
- Deleting is soft, so Undo works. Deleted entries are purged after 30 days.

### Time zones
Each entry stores the UTC moment **and** its time zone. The logical day and hour are worked out in the entry's own zone, so travel and clock changes don't scramble the stats. Changing the day start recalculates logical days once.

---

## 5. Tags

Built-in tags come in groups. You can rename, hide and reorder them, or add your own, in Settings. Groups let the stats roll up and drive the advice.

| Group | Tags |
|---|---|
| **Feelings** | Stressed, Anxious, Angry, Low, Bored, Lonely, Happy, Excited, Celebrating, Relieved |
| **Routine** | After a meal, With coffee or tea, With a drink, Just woke up, Before bed, Commuting or driving, Work break, After exercise, On a call, Screen time |
| **People** | Around other nicotine users, Out socialising, On my own, After an argument |
| **Body** | Strong craving, Withdrawal, Tired, Hungry, Can't focus |
| **Habit** | On autopilot |
| **Your own** | Anything you add, placed in any group |

A scheduled dose is the **planned** toggle on the entry rather than a tag.

---

## 6. The programme

### 6.1 Baseline
- 7 days of logging as normal, with at least 5 days that have entries. The baseline is your **usual day**: how many of each product you use on a day with entries, on average, rounded to whole units. For example, "8 pieces of 4 mg gum and 3 sprays", which is 35 mg. It's shown to you to confirm or edit, because a day with no entries is usually a day you forgot to log.
- If the last 7 full days already have 5 days with entries, they can be used straight away.
- **Skip option:** say what a usual day is, product by product, and start straight away.

The plan needs the mix of products, not only the total, because it steps down through them (owner, 24 October).

### 6.2 Building the plan
- Pick a length of 8, **12** (default), 16 or 24 weeks. **The last 2 weeks are always NRT-free**, so a 12-week programme has 10 taper weeks and then 2 weeks at zero.
- **The taper follows your usual day.** It's a ladder of steps from your usual day down to one unit of your weakest product. Each step does one thing:
  - **One fewer** piece, lozenge, spray or inhalator session a day, taken from the kind of product that makes up the most mg of your day. A tie goes to the stronger unit.
  - **A lower strength,** once switching wouldn't mean more pieces a day of that kind than you started with: 4 pieces of 4 mg gum become 8 pieces of 2 mg. This is the step-down the gum makers describe: fewer of the strong pieces first, then the lower strength more often, then fewer of those.
  - **Patches** step down one strength at a time (21, 14, then 7 mg), then come off.
- The steps are spread evenly over the taper weeks. Where they don't divide evenly, the extra steps come early, when they're a smaller share of the dose. With more weeks than steps, a step lasts more than one week. Allowances never go up.
- Lower strengths come from the catalogue (the same brand and kind) or your own products. A product the plan switches you to is added to your products when the plan starts.
- Each week has a **daily mix** ("5 pieces of 4 mg gum"), a **daily guide** (the mix's mg) for pacing on the Today screen, and a **weekly allowance** (7 × the guide), which is the target. A week is judged on mg alone, so how you stay within it is up to you.
- You can edit any taper week before it starts, by moving it up or down the ladder. It can't go above the week before, and later weeks move down with it if they'd be above it.

Example 1: baseline 12 pieces of 2 mg gum (24 mg), 12 weeks.

| Week | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Daily guide (mg) | 20 | 18 | 16 | 14 | 12 | 10 | 8 | 6 | 4 | 2 | **0** | **0** |
| Weekly allowance (mg) | 140 | 126 | 112 | 98 | 84 | 70 | 56 | 42 | 28 | 14 | **0** | **0** |

Example 2: baseline 8 pieces of 4 mg gum (32 mg), 12 weeks. The ladder has 12 steps for 10 taper weeks, so weeks 1 and 2 take two steps each.

| Week | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Daily mix | 6 × 4 mg | 4 × 4 mg | 8 × 2 mg | 7 × 2 mg | 6 × 2 mg | 5 × 2 mg | 4 × 2 mg | 3 × 2 mg | 2 × 2 mg | 1 × 2 mg | none | none |
| Daily guide (mg) | 24 | 16 | 16 | 14 | 12 | 10 | 8 | 6 | 4 | 2 | **0** | **0** |
| Weekly allowance (mg) | 168 | 112 | 112 | 98 | 84 | 70 | 56 | 42 | 28 | 14 | **0** | **0** |

The step for week *w* of *T* taper weeks, on a ladder of *k* steps: with *k* ≥ *T*, every week takes *k* div *T* steps and the first *k* mod *T* weeks take one more; with *k* < *T*, week *w* is at step ⌈*k* × *w* ÷ *T*⌉. The exact rules are covered by unit tests in `:core:domain`.

### 6.3 Optional extra goals per week (off by default)
- **Most units per day.**
- **Minimum gap between doses**, e.g. 90 minutes.
- **First dose not before** a set time, moving about 15 minutes later each week. How soon you reach for nicotine after waking is a strong sign of dependence.

The strength hint that was here is now part of the plan itself (§6.2). These goals show in your stats and advice. They don't affect whether a week is met.

### 6.4 Weekly results
- A week is **met** when its total is at or under the weekly allowance. For the NRT-free weeks that means zero.
- Results come in **24 hours after the week ends**, so late logs still count. Once a result is in, editing old entries doesn't change it.
- **After a missed week**, you pick one of two options:
  - **Carry on** (the default). Next week has its planned, lower target. Meet it and you get the hits for both weeks, because you've reached that level now. The same goes for any other missed weeks since your last met one.
  - **Repeat this week.** Next week keeps the same target, which extends the programme by a week and adds **10 HP to the demon**. The app says so before you confirm. Meeting the repeat pays back the missed week too, so the hits on offer always match the demon's HP.
- **Overtime:** if the final NRT-free week is missed, the programme runs on with NRT-free weeks until one is met. That week pays back everything missed, and the demon falls.
- Tone stays supportive. Nothing resets and nothing shames. The app is clear that NRT for longer is far safer than going back to smoking.

### 6.5 After the programme
The demon is defeated and the programme closes. Logging still works. **Start a new programme** begins a rematch from your current usage, with a fresh demon or the same one back for round two. Badges and gear carry over. There are no nicotine-free day counters anywhere in the app.

---

## 7. Name your demon

### 7.1 Set-up
This is part of onboarding, so the game is there from day one.
1. **Name it.** A free text name (e.g. "Gary"), with a few suggestions if you're stuck.
2. **Pick a creature:** Dragon, Imp, Bear, Kraken, Goblin, Slime, Wraith or Serpent.
3. **Pick a colour variant:** 3 palette swaps per creature, the classic 8-bit trick.

During the baseline week the creature **lurks**: shown in silhouette with HP "??". When the plan starts, it steps out with full HP.

### 7.2 HP and hits
- **Max HP = 10 × programme weeks.** A 12-week programme gives 120 HP, shown as 12 blocks of 10.
- **Each week met earns 1 hit.** Catch-up hits arrive with the week that earns them.
- **Hits are banked until you use them.** You can:
  - **Hit**: spend 1 hit for 10 damage.
  - **5× attack**: available once you have 5 banked. It spends 5 hits for 50 damage in one combo.
- The total hits on offer equals the demon's HP blocks, so meeting every week (or catching up) means the final hit lands when the programme is complete.
- The final hit triggers the **defeat sequence**.

### 7.3 Battle screen ("The lair")
- **Header:** the creature in the middle on a simple pixel backdrop, its name, an HP bar in 10 HP blocks, banked hits, and your equipped gear.
- **Condition:** the sprite changes with HP. Fresh above two thirds, roughed up above one third, barely standing at a third and below.
- **Idle animation:** 2 to 4 frames (breathing, blinking, a flicker of flame for the dragon).
- **A hit:** wind-up, impact flash, sprite knock-back, a pixel damage number ("−10"), small particles and a haptic tick.
- **5× attack:** five quick strikes, then a heavier finisher, a screen shake and "−50".
- **Defeat:** the creature breaks apart into pixels and a victory screen sums up the run: weeks, baseline mg to zero, and the triggers you got on top of.
- **Sound:** 8-bit sound effects generated in code (square and noise waves), so there are no audio files. They respect silent mode and have their own toggle.
- **Flavour lines:** short, dry and in character, about the demon and never about you. For example, "Gary staggers." or, after a missed week, "Gary looks smug. Meet next week and take that hit back."

### 7.4 Elsewhere
- **Today screen:** a small demon sprite with its HP strip, plus a "1 hit ready" badge when you have one banked.
- **Weekly result notification:** "Week 3 met. 1 hit ready for Gary."
- **Widget and watch tile:** a mini HP strip (later polish, if it fits cleanly).

### 7.5 Pixel art pipeline
- Sprites are written as **pixel grids in code** (rows of palette letters), drawn with nearest-neighbour scaling so they stay crisp at any size. That means no image files, palette swaps for free, and easy review.
- **32 × 32 sprites**, at most 8 colours each, taken from one shared master palette of about 24 colours. The palette is built to match the app's colours.
- **Per creature:** idle frames, hit flash, 3 condition states and defeat frames.
- **Gear and badges:** 16 × 16 icons in the same palette.
- Preview sheets are rendered to PNG in the dev container for you to review before they go into the app.

---

## 8. Achievements

Achievements unlock **pixel badges**, and some also unlock **gear**. They never deal damage, so the HP maths always lands on zero at the end. Most are cumulative, so a bad week never takes anything away.

### 8.1 Badges
| Group | Achievements |
|---|---|
| **Logging** | First entry. Logged on 3, 7, 14, 30, 60 and 90 days. Logging streaks of 7, 14 and 30 days. Baseline done. "Honest ledger" (backdated a forgotten entry). Tagged 25 and 100 entries. |
| **Matching the plan** | First day within the daily guide. First week met. 3, 6 and 9 weeks met. "Back in step" (caught up after a missed week). Halfway (daily guide at half the baseline or less). Down a strength (e.g. 4 mg to 2 mg gum). Final taper week reached. |
| **Beating the plan** | A week at least 10% under its allowance. 5 days under the daily guide in one week. 3 weeks in a row under the allowance. |
| **Habits** | First dose 30 minutes later than your baseline average, across a whole week. Average gap between doses 30 minutes longer than baseline. Top trigger's share down by a third against baseline. 1, 10 and 25 breathing sessions done. |
| **The end** | Demon defeated. |

The exact thresholds will be tuned during building so they come at a steady pace across a typical 12-week programme.

### 8.2 Gear
Gear changes how your hits look and sound and is purely cosmetic. Unlocks run roughly in order of difficulty: wooden stick (start), wooden sword, iron sword, axe, war hammer, fire staff, lightning bow and legendary blade. You equip whatever you've unlocked in the lair.

### 8.3 Achievement unlock moment
A small pixel toast with the badge and its name. Gear unlocks get a short "new gear" card. Each one is also logged in a trophy shelf in the lair.

---

## 9. Advice

### Content library
The content is static and bundled, based on public health guidance (NHS, CDC). Each strategy has a title, a short body, its steps, the tags or groups it applies to, and a source note. Two are interactive:
- **Breathing timer:** box breathing (4-4-4-4) and 4-7-8, animated, 1 to 3 minutes. Counts toward the breathing achievements.
- **Delay timer:** a 5 to 10 minute countdown ("cravings peak and pass").

### What goes with what
| Trigger | Strategies |
|---|---|
| Stressed, Anxious, Angry, After an argument | Breathing timer, a 5-minute walk, muscle relaxation, riding out the urge |
| After a meal, With coffee or tea | **Move the dose to before the meal**, get up from the table, brush your teeth, take a short walk. Coffee and fizzy drinks reduce how much gum and lozenges release, so avoid them 15 minutes before and during use |
| Happy, Excited, Celebrating | Pick another treat, and tie treats to milestones |
| Bored, Screen time, On autopilot | Keep your hands busy, change rooms, make each dose a decision |
| With a drink, Out socialising | Higher risk: decide your limit before you go out |
| Just woke up | Push the first dose a little later each week. Water or breakfast first |
| Withdrawal, Strong craving | The 4 Ds. If it's severe or keeps up, talk to a pharmacist or GP about the pace |
| Any time | **The 4 Ds**: Delay, Deep breaths, Drink water, Do something else |

### Personal touches
- **Your top tags** from the last 14 days, by share of doses, put their strategies first.
- **Timing tips from your data.** For example, if "After a meal" is at least a quarter of your doses and clusters around 13:00 and 19:00: "Try having your dose at 12:30, before lunch."
- **Where advice shows up:**
  - a card on Today
  - a tip straight after logging (tagged Stressed: "Try a minute of box breathing next time")
  - the triggers section in Insights
  - the Advice tab

### Limits
It never suggests taking more, it's general information and not medical advice, and it points to the NHS and local quitlines.

---

## 10. Insights

Range buttons: **7, 30, 60, 90 days**. 7 is always on. 30 unlocks after more than 7 days of history, 60 after more than 30, and 90 after more than 60. Charts start from the first logged day, so there are no fake zeros before you began.

1. **Daily intake:** a bar chart of mg per logical day, with the **daily guide** line and a **7-day rolling average**. Bars can be stacked by product type.
2. **Weekly view:** total against allowance for each programme week, marked met, missed or caught up.
3. **Hour by weekday heat map** (when you use most):
   - laid out for a portrait phone, with 24 hour rows and 7 day columns
   - shade shows number of doses (switchable to mg)
   - patches are left out
   - tap a cell for the exact numbers
   - uses one hue, so it's colour-blind safe
4. **Calendar heat map:** one square per day, shaded by mg against the daily guide.
5. **Triggers:**
   - top tags by share of doses
   - group totals
   - tags by time of day (morning, afternoon, evening, night)
   - change against the previous period
6. **Habits:**
   - time of the first dose of the day (optionally minutes after a set wake time)
   - average gap between doses
   - longest gap
   - days within the daily guide
7. **By product:** share of total mg and units per day.

Each chart has a written summary for screen readers.

---

## 11. Quick-log

| Where | What it does |
|---|---|
| **Home-screen widget** (Glance) | Today's mg against the daily guide, plus 2 or 3 favourite buttons. A tap logs **instantly** without opening the app and briefly shows "Logged" with Undo. A setting can open a quick tag picker after logging. |
| **Quick Settings tile** | A tap logs your default product and posts a short-lived notification with **Undo**. It can be set to open the log sheet instead. |
| **App shortcuts** (long-press the icon) | Your top 3 favourites and "Log". Also declared so Google Assistant can trigger them where it still exists. Not something we rely on. |
| **Deep link** | `lasthit://log?product=nicorette-gum-2mg&qty=1&at=2026-10-23T15:00&tags=stressed,after-meal`. Lets Routines, Tasker or an NFC sticker on your gum pack log an entry. Checked, and logged through the same path as the app. |

Every entry records where it came from (app, widget, tile, shortcut, deep link, AppFunction or watch). That helps with debugging and duplicate checks.

---

## 12. AppFunctions (Gemini and on-device agents)

- **Needs Android 16 (API 36) or newer.** The Jetpack library is experimental. As of mid-2026, Gemini calling other apps' AppFunctions is in **private preview** for trusted testers through an Early Access Programme. We build it now, test it on your Android 16 phone with `adb shell cmd app_function …`, and register interest in the programme. It starts working for everyone when Google opens it up. On older Android it simply stays off.
- All of it lives in one package, so changes in the alpha library don't touch the rest of the app.

Functions (each described in KDoc so the agent knows what it does):

| Function | Purpose |
|---|---|
| `logNrtUsage(productDescription, quantity = 1, time = now, tags = [], cravingStrength?)` | Handles requests like "Log a 2 mg nicotine gum" or "Log an inhalator session at 3 pm, stressed". Returns the saved entry, the week so far against its allowance, and a possible-duplicate flag. |
| `listNrtProducts()` | Your products, so the agent can tell them apart. |
| `getNrtSummary()` | Today's mg against the daily guide, the week against its allowance, last dose time, demon HP and banked hits. |
| `undoLastNrtEntry()` | Undoes the most recent entry. |
| `getNrtAdvice(tag?)` | The best strategy for a trigger, or for your current top trigger. |

`productDescription` and the tags go through a **plain-language matcher** in `:core:domain`, shared with deep links. It handles phrases like "2mg nicotine gum", "two 4 mg gums", "nicorette 4", "21mg patch", "two sprays of QuickMist" and "inhalator session". If a phrase is ambiguous, it returns the candidates so the agent can ask you.

---

## 13. Smart reminders

All are optional and switch on or off one by one. Quiet hours are respected. On Android 13 and newer, notification permission is asked for the first time you turn a reminder on.

| Reminder | When | Default |
|---|---|---|
| Allowance nudge | A log takes the week past 80% or 100% of its allowance, or today past the daily guide | On |
| Evening check-in | Daily at 21:30 (changeable): "Today 14 mg, guide 16 mg. Anything missing?" with buttons for Log and All good | On |
| Weekly result | When a week's result comes in, with hits earned | On |
| Achievements | When a badge or gear unlocks | On |
| Forgot to log | No entries by about 2 hours after your usual first dose: "Nothing logged yet today. Forgot one?" with a button for Log | Off |
| Spacing note | Only shown in the app at the moment you log (§4). There is **never** a push saying you can have a dose now | Off |

Scheduled checks run on WorkManager, where rough timing is fine. Nudges that depend on logging fire from the logging code itself.

---

## 14. Wear OS

- A `:wear` module for Wear OS 3 and newer (API 30 and up), built with Compose for Wear OS.
- **Tile:** today's mg against the daily guide as an arc, plus 2 or 3 favourite buttons. A tap logs, with a buzz and a tick.
- **Complication:** today's mg against the guide, for watch faces.
- **Small app:** favourites, logging with a time nudge (15 min, 30 min or 1 h ago), the last 5 entries, and undo.
- **Sync** (Wearable Data Layer API):
  - The phone is the source of truth. The watch sends log messages carrying an ID and the original time, so a message sent twice is only counted once.
  - The phone sends the day's summary and your favourites back to the watch.
  - If the phone is out of reach, the watch queues logs and sends them on reconnect.
- **Needs** a paired Wear OS watch. Phone and watch apps share one app ID and signing key. Garmin, Huawei GT and Apple watches won't work.

---

## 15. Screens and navigation

The bottom bar has **Today, History, Insights, Plan and Lair**. Advice sits inside Today and Insights, with its own full list behind a button. Settings is in the top bar.

- **Onboarding:**
  - welcome and disclaimer (must be accepted)
  - when you stopped smoking or vaping. If it was under about 8 weeks ago, a gentle note says most NRT courses run 8 to 12 weeks and a pharmacist can help with timing. It doesn't block anything
  - your products, by brand or search
  - **name your demon and pick a creature**
  - day start and optional wake time
  - reminders
  - start the baseline or enter your usual amount
- **Today:**
  - daily guide ring
  - week-so-far bar
  - quick-log favourites
  - Log button
  - last dose and time since
  - today's entries
  - the demon strip with banked hits
  - an advice card
- **History:** entries grouped by logical day with daily totals. Filter by product or tag, tap to edit, swipe to delete with undo.
- **Insights:** §10.
- **Plan:**
  - this week's allowance and progress
  - the full schedule with met, missed and caught-up marks
  - extra goals
  - repeat a missed week
  - start a new programme
- **Lair:** the battle screen, gear, the trophy shelf, and renaming or restyling the demon.
- **Settings:**
  - products and tags
  - day start and wake time
  - reminders
  - quick-log behaviour
  - sound
  - estimated absorbed
  - export and import
  - About, the disclaimer and sources

---

## 16. Design, copy and the agents doing it

### 16.1 Direction
- **The calm app** handles the everyday screens: logging, history, charts, plan. These need to read easily at a glance. That means one accent colour, generous spacing and a clear hierarchy.
- **The game layer** covers the demon, badges, gear and small celebration moments. It's pixel art in a palette that matches the app.
- **Our own palette for light and dark mode.** Material You dynamic colour stays **off**, so the app and the pixel art always match.
- **Two bundled fonts** (the app has no internet access):
  - a readable body font with some character
  - a pixel font for the game layer and big numbers (Open Font Licence fonts such as Silkscreen or Pixelify Sans are candidates)

### 16.2 Avoiding the "AI look"
Visual:
- No default Material purple.
- No gradient washes or frosted glass.
- No grids of icons in circles.
- No generic stock illustrations. Pixel art does that job.
- Consistent corner radii and spacing, with real contrast between headings, numbers and body text.

Copy rules, written up in `docs/STYLE.md` and enforced by a unit test that fails the build on banned characters and phrases in `strings.xml`:
- **No em dashes or en dashes** in app text. Hyphens only where English needs them ("4-7-8 breathing"). Use full stops and commas instead.
- **Sentence case** everywhere.
- **Plain, specific words.** None of: "journey", "unlock your", "empower", "seamless", "dive in", "elevate", "game-changer", "let's".
- **No rhetorical questions.** No "not just X, it's Y". No lists of three adjectives.
- **No emoji in app text.** Pixel icons do that job.
- **One exclamation mark per screen at most,** and only in the game layer.
- **Digits for numbers, a space before units:** "2 mg", "15:00".
- **Humour belongs to the demon.** Health and logging text stays straight and kind.
- **Never shame.** For example: "Missed this one. Meet next week and you get it back."

### 16.3 Agents
Separate agents with narrow briefs run at set checkpoints rather than continuously, to keep the cost sensible.

| Agent | Job | When |
|---|---|---|
| **Visual design** | Builds the design system: palette (light and dark), type scale, spacing, shapes and components. Produces 2 or 3 style directions as HTML mockups, screenshotted for you to choose from. Then designs each screen. | Phase 0, then before each UI phase |
| **Pixel art** | Builds the master palette, the creature sprites, gear and badges as pixel grids, rendered as PNG preview sheets. Starts with one creature for you to sign off on before doing the rest. | Phase 0 (one creature), Phase 4 (the rest) |
| **Copy** | Writes all app text to the style guide in British English, including the demon's lines. | Each UI phase |
| **Fresh-eyes reviewer** | Reviews rendered screenshots of the built app without the designer's notes, for hierarchy, consistency, accessibility (contrast, 48 dp touch targets, large fonts) and anything that looks generic or AI-made. | End of each UI phase |

The chart colours follow the data visualisation guidance in this environment, so charts and heat maps sit in the same palette.

Screenshots of real screens are rendered with Roborazzi (Compose screenshots on the JVM), which needs the Android SDK from `dl.google.com`.

---

## 17. Architecture

```
:app            Phone app: Compose UI, navigation, ViewModels, widget (Glance),
                QS tile, shortcuts, deep links, AppFunctions, notifications, WorkManager,
                the lair (sprite renderer, animations, sound)
:wear           Wear OS app: tile, complication, Compose for Wear, Data Layer client
:core:data      Room database, DataStore settings, repositories, export and import
:core:domain    Plain Kotlin with no Android: models, dose maths, logical days, plan builder,
                weekly results and catch-up, demon HP and hits, achievement rules, stats,
                advice ranking, the plain-language matcher, sprite data, watch message types
```

- **Pattern:** MVVM with one-way data flow, Kotlin coroutines and Flow, plain constructor wiring through a `DataGraph` (no dependency injection framework, which keeps the build simple on AGP 9), and `java.time` (built in from API 26).
- **Libraries:**
  - Room
  - DataStore
  - WorkManager
  - Glance (widget)
  - Vico (charts)
  - custom Canvas code for the heat maps and sprites
  - kotlinx.serialization (export and watch messages)
  - `androidx.appfunctions` (alpha, pinned)
  - Play Services Wearable
  - Roborazzi (screenshots)
- **SDK levels:** `minSdk 26` (Android 8.0) on the phone and `minSdk 30` on the watch. `compileSdk` and `targetSdk` are 36.
- **Gradle:** a version catalogue, Kotlin DSL, and as little build plumbing as possible.
- **No `INTERNET` permission.** The app physically can't send data anywhere. Watch sync goes through Play Services, which doesn't need it.

### Data model (Room)

```
products(id, catalogueId?, brand?, line?, name, form, strengthMg, unitLabel, mgPerUnit, cartridgeMg?,
         unitsPerCartridge?, ratedHours?, labelMaxPerDay?, labelMaxPerHour?, market?, isBackground, isFavourite, isHidden, sortOrder, absorptionFactor?, createdAt)

entries(id UUID, productId, productNameSnapshot, mgPerUnitSnapshot, quantity, mgTotal,
        occurredAt (epoch ms UTC), zoneId, logicalDate, kind (PLANNED|CRAVING|UNSPECIFIED),
        cravingStrength?, note?, source, removedEarlyHours?, createdAt, updatedAt, deletedAt?)

tags(id, group, nameKey?, customName?, isBuiltIn, isHidden, sortOrder)
entry_tags(entryId, tagId)

programmes(id, status (BASELINE|ACTIVE|OVERTIME|COMPLETE|ABANDONED), baselineStart, baselineEnd,
           baselineDailyMg, baselineSource (MEASURED|MANUAL), lengthWeeks, nrtFreeWeeks (2),
           startDate, createdAt)
baseline_units(programmeId, productId, unitsPerDay)          -- the usual day
programme_weeks(id, programmeId, index, startDate, endDate, ladderStep, dailyGuideMg, weeklyAllowanceMg,
                isNrtFree, isExtension, maxUnits?, minGapMinutes?, firstDoseNotBefore?,
                result (PENDING|MET|MISSED|CAUGHT_UP), resultAt?)
programme_week_units(weekId, productId, unitsPerDay)         -- the week's daily mix

demons(id, programmeId, name, creature, colourVariant, maxHp, createdAt, defeatedAt?)
hit_ledger(id, programmeId, weekId?, kind (WEEK_MET|CATCH_UP|HIT|COMBO), hits (+ earned, − spent),
           createdAt)

achievements(key, unlockedAt)              -- definitions live in code
gear(key, unlockedAt)                      -- equipped gear is a setting
breathing_sessions(id, type, startedAt, durationSec)
```

The demon's current HP is `maxHp − 10 × hits spent`, and banked hits are `earned − spent`. Both come from the ledger, so they can always be recalculated and checked.

DataStore settings hold:
- day start and wake time
- reminder switches, times and quiet hours
- quick-log defaults
- sound
- equipped gear
- estimated absorbed
- onboarding state and disclaimer acceptance

Room schemas are exported and every database migration is tested from version 1, so data survives updates.

---

## 18. Privacy, safety and Play readiness

- **On the phone only.** No account, no analytics, no ads and no network.
- **Backup:**
  - Android Auto Backup (end-to-end encrypted on modern Android with a screen lock)
  - manual **export** (a full JSON backup and a CSV of entries) through the system file picker
  - **import** (a JSON restore that either merges by ID or replaces everything)
- **Medical disclaimer** in onboarding and About. It says:
  - the app isn't medical advice
  - check with a pharmacist or GP before changing how you use NRT, especially if pregnant, breastfeeding or living with a heart condition
  - NRT for longer is far safer than smoking
- **For the Play Store later:**
  - an app ID fixed now
  - a draft privacy policy ("no data collected")
  - answers for the Data safety and Health apps forms kept in `docs/`
  - Play App Signing, with the upload key in GitHub Secrets
  - no treatment claims in the store text
- **Languages:** all text lives in resource files, British English first.

---

## 19. Delivery phases

Each phase ends with a working build you can install and a short checklist to try.

| Phase | What's in it | What you get |
|---|---|---|
| **0. Foundation and direction** | Multi-module Gradle set-up, version catalogue, CI (build, unit tests, APK downloads), `:core:domain` with logical days and dose maths plus tests, the strings lint. **Design agents:** 2 or 3 style directions for you to pick from, the design system, and one creature sprite for sign-off. | An APK that opens, a chosen look, and a first demon |
| **1. Logging** | Seeded products, the log sheet (backdating, tags, craving strength, planned flag), Today, History with edit, delete and undo, Settings (products, tags, day start), onboarding and disclaimer, export and import | **An everyday logging app** |
| **2. Insights** | Daily chart with ranges and rolling average, both heat maps, triggers, habits | Stats from your first day of logging |
| **3. Programme** | Baseline, plan builder with 2 NRT-free weeks, daily guide and weekly allowance, weekly results, catch-up, repeat a week, overtime | The taper running |
| **4. Demon and achievements** | Naming and creature choice, all 8 creatures with variants, the lair, hits and 5× attacks, animations, sound, defeat sequence, achievement rules, badges, gear, trophy shelf | **The game** |
| **5. Advice** | Content library, matching, your top triggers, timing tips, breathing and delay timers, tips after logging | Advice for your own triggers |
| **6. Quick-log and reminders** | Widget, tile, shortcuts, deep links, the plain-language matcher, all reminders | One-tap logging and nudges |
| **7. AppFunctions** | The functions in §12, KDoc descriptions, an adb test script, Early Access notes | Ready for Gemini when Google opens it up |
| **8. Wear OS** | Tile, complication, small app, Data Layer sync with an offline queue | Logging from your wrist |
| **9. Polish and Play readiness** | Accessibility pass, estimated absorbed figures, privacy policy, store documents, release signing | A releasable build |

---

## 20. Testing and checks

- **`:core:domain` unit tests** (JUnit 5, run in the dev container and in CI):
  - logical-day edge cases: 01:30 with a 04:00 day start, clock changes, changing time zone mid-day, changing the day start
  - dose maths: inhalator sessions and cartridges, a patch removed early, label maximum checks
  - plan builder: unit rounding, extra units taken early, allowances never going up, 2 NRT-free weeks, extensions
  - weekly results: allowance edges, the 24-hour grace period, catch-up across several missed weeks, overtime
  - demon: HP and hit ledger, 5× attacks, the defeat condition, a 12-week run where every week is met that ends exactly at 0 HP, a run with misses and catch-up that also ends at 0
  - achievements: each rule with positive and negative cases, and a check that none of them deals damage
  - stats: ranges clipped to the first entry, heat map buckets in the entry's own zone, patches left out
  - advice ranking and timing tips
  - plain-language matcher: a table of phrases and the expected product and quantity, including ambiguous ones
  - copy lint: no em or en dashes and no banned phrases in `strings.xml`
- **`:core:data` tests** (Robolectric with an in-memory Room database): queries, migrations, and an export then import that comes back identical.
- **UI tests** (Compose): logging with a backdated time, edit, delete and undo, onboarding including naming the demon, hits and 5× attacks in the lair.
- **Screenshot tests** (Roborazzi): every main screen in light and dark mode, at normal and large font sizes. These are what the fresh-eyes reviewer looks at.
- **AppFunctions:** an adb script that lists and runs the functions on your Android 16 phone.
- **Wear:** your watch paired with the phone. Checks cover the offline queue and that a message sent twice counts once.
- **A manual checklist each phase** on your phone.

---

## 21. Open questions

None at the moment. Confirmed: the creature list, evening check-in at 21:30, day start at 04:00, a 12-week default programme including the 2 NRT-free weeks, and the `thelasthit.app` domain.
