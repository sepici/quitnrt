# Store listing

The Play listing for The Last Hit: Quit NRT (Grow, then Store presence, then Main store listing),
written to `docs/STYLE.md`: British English, sentence case, no dashes, no emoji, no marketing words
and no treatment claims. The text blocks are ready to paste as they are.

## App name

```
The Last Hit: Quit NRT
```

22 characters, under Play's 30. Under the icon on the phone it's "The Last Hit".

## Short description

```
Log your NRT, see what sets off cravings and taper to zero a week at a time.
```

76 characters, under Play's 80. It's the welcome line from onboarding, so the store and the app
open with the same words.

## Full description

3,123 characters, under Play's 4,000. Play shows line breaks as they are, and the bullets are
plain text.

```
The Last Hit is for people who stopped smoking or vaping with nicotine replacement therapy (NRT) and now want to stop the NRT too. Log the gum, lozenges, patches, sprays or inhalator you use, see what sets off your cravings, and follow a plan that steps your own usual day down to none, a week at a time.

Logging
• Tap a product on Today to log one, or open the log sheet for the amount, the time and what was going on: stressed, after a meal, with coffee, on autopilot.
• Add one you forgot at the time you had it.
• Products come from a catalogue of real packs sold in the UK, the US, Canada, Australia and New Zealand, each with the strength and daily limit from its leaflet. If a day goes past a pack's limit, the app says so and suggests a word with a pharmacist.
• A day runs from 04:00 by default, so a piece at 01:30 counts towards the evening before.

The plan
• Log a normal week, or say what a usual day is, then choose 8, 12, 16 or 24 weeks.
• Each week steps down from your own mix: one piece or spray fewer, or a lower strength. The last 2 weeks are NRT-free.
• Each week has an allowance, with a daily guide for pacing. Today shows where you are against it.
• Miss a week and nothing resets. Meet the next one and the missed week is paid back, or repeat the week to take it slower.

The demon
• Name the habit. It becomes a pixel-art creature in its lair, with 10 HP for every week of the plan.
• Every week you meet earns a hit. Meet them all and the last hit lands as the plan ends.
• Badges and gear come along the way. Gear changes how your hits look and sound. Neither does damage.

Insights
• Each day and each week against the plan, over 7, 30, 60 or 90 days.
• The hours you use most, a calendar of your days, and your top triggers and how they change.
• Your first dose of the day, the gaps between doses and the days within the guide.
• Every chart can be read as a table.

Advice
• Ways to get through a craving without a dose, from NHS, CDC and other public health guidance, with your own top triggers first.
• Timing tips from your logs, such as having an after-meal dose before the meal instead.
• A breathing timer, with box breathing or 4-7-8, and a delay timer for waiting a craving out.

Quick logging and reminders
• A home-screen widget, a Quick Settings tile, shortcuts on the app's icon, and log links for Tasker, Routines or an NFC sticker.
• Reminders, all optional: a nudge as the week nears its allowance, an evening check-in, weekly results, and a nudge if nothing's logged by your usual time. Quiet hours keep them off at night.

Private
No account, no ads and no tracking. The app has no internet access, so what you log stays on your phone. Export a backup, or your doses as a CSV file, whenever you like.

Before you start
The Last Hit helps you keep track and plan. It isn't medical advice. It is not a medical device and does not diagnose, treat, cure or prevent any medical condition. Talk to a pharmacist or GP before you change how you use NRT, especially if you're pregnant, breastfeeding or living with a heart condition. Using NRT for longer is far safer than smoking.
```

Notes on the wording:

- **No treatment claims.** It says what the app does (logs, shows, plans), never that it gets
  anyone off nicotine or works.
- **The medical device sentence** is there because Play's Health Content policy asks health apps
  that aren't medical devices to say so in the description, in about those words.
- **No brand names.** The catalogue has the big brands, but naming them in the listing would read
  as keyword stuffing and could draw trademark complaints. "Real packs" says it.
- **One creature.** Only the dragon can be picked until the other seven are signed off (P4-1), so
  the listing says "a pixel-art creature", not "eight creatures". Change it once they're in.
- **Assistants left out.** The AppFunctions only work once Google opens them to every app, so the
  listing doesn't mention Gemini yet.

## Category and contact

| Field | Answer | Why |
|---|---|---|
| App or game | App | A tracker with a game layer, not a game. |
| Category | **Health & Fitness** | Where habit and quit-smoking trackers sit. Medical is for clinical and care apps. |
| Tags | The closest the console offers to quitting smoking and habit tracking | Play picks tags from its own list; choose there. |
| Email | `{contact email}` | Required. The same address as the privacy policy. |
| Website | `https://thelasthit.app` | Optional. |
| Privacy policy | `https://thelasthit.app/privacy` | Where `privacy-policy.md` is hosted. |

## Content rating

The rating comes from the IARC questionnaire in Play Console (Policy, then App content, then
Content rating). Its questions change with the category picked, so here are the facts to answer
from, and the answers they lead to.

- **Category:** all other app types. It's a health tracker; the game is a layer on top.
- **Violence:** **Yes, fantasy only.** In the lair, the user's hits knock back a pixel-art
  creature, with a flash and a "−10". When its HP reaches 0 it breaks apart into pixels. There's
  no blood, no people and nothing realistic.
- **Tobacco, alcohol and drugs:** **Yes, references only.** The app is about nicotine replacement
  medicine and stopping smoking or vaping, and tags include "With a drink" and "Around other
  nicotine users". Nothing shows anyone smoking, vaping or drinking, and nothing encourages it: the
  whole app is about using less.
- **Sexual content, bad language, crude humour, gambling:** none.
- **User interaction:** none. There's no chat, sharing between users or user content anyone else
  sees.
- **Location:** not shared, and not read.
- **Digital purchases:** none.
- **Web access:** none. The app can't go online.

The tobacco references may lift the rating to around 12 or Teen. That sits fine with the 18 and
over target audience (`data-safety.md`).

## Screenshots

Eight phone screenshots in `docs/store/screenshots/`, 1080 × 1920 (9:16) 24-bit PNGs, in the
order to upload them. Play takes 2 to 8, and at least 4 at 1080 px or more makes the app eligible
for its larger layouts.

| File | Screen | Caption |
|---|---|---|
| `01-today.png` | Today with a plan: the meter against the daily guide | Today against your plan's daily guide |
| `02-plan.png` | Building a plan: the step-down chart and each week's mix | A taper built from your own usual day |
| `03-lair.png` | The lair's battle: Gary at 100 of 120 HP, with a hit banked | Every week you meet is a hit on the demon you named |
| `04-schedule.png` | The Plan tab's schedule: met, missed and caught-up weeks | A missed week comes back when you meet the next |
| `05-insights.png` | Insights: each day and week by week | Each day and each week against the plan |
| `06-hours.png` | Insights: when you use, by hour and day | The hours of the day you use it most |
| `07-advice.png` | Advice: moving the after-meal dose, timed from the logs | Advice for your own triggers, timed from your logs |
| `08-log.png` | The log sheet | Log a dose now, or one you forgot earlier |

Each one is a real Roborazzi render of the app in light mode, from the test data, in a plain phone
frame with a caption above. The phone's status bar (15:05, the time the tests stop the clock at,
and a battery) is drawn by the script, since the renders have none. A tall page is cut to a
phone's height from a chosen row, as if scrolled there, and the phone runs off the bottom edge.

**The feature graphic** is `feature-graphic.png`, 1024 × 500, a 24-bit PNG: Today's demon band,
drawn from the app's own pixel art (`backdrops/band.json` and the dragon), with the app's name and
an HP bar in the sky. The name keeps well clear of the edges.

**To make them again,** after the app's look changes:

```
./gradlew :app:recordRoborazziDebug
python -I docs/store/tools/store_art.py
```

`store_art.py` lists each screenshot's render, starting row and caption at the top. It needs only
Python's standard library: `ttf.py` draws the captions in the app's own Schibsted Grotesk.

Still needed for the listing, and not in this folder: the 512 × 512 app icon.
