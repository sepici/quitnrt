# Health apps declaration

The answers for Play Console's Health apps declaration (Policy, then App content, then Health
apps), with the reason for each. Every app has to fill it in. Checked on 24 October 2026 against
Google's help page for the form, <https://support.google.com/googleplay/android-developer/answer/14738291>,
and its Health Content and Services policy.

## What the app is

The Last Hit is a self-tracking and habit tool. It's for adults who stopped smoking or vaping
with nicotine replacement therapy (NRT) and now want to stop using NRT too.

- It logs the NRT someone uses, shows their patterns and triggers, and builds a plan that steps
  their own usual day down to none, a week at a time. A small game sits on top: the habit is a
  pixel-art demon, and each week met is a hit on it.
- Its advice is general information from public health guidance (NHS, CDC, HSE), cited on each
  page, about getting through a craving without a dose. It never suggests taking more.
- It also gives timing tips from the user's own logs (`TimingTips` in the code), each marked as
  the app's own suggestion. Where a dose usually follows a meal, it suggests having that dose half
  an hour earlier, before the meal instead of after ("Try having your dose at 12:30 and 18:30").
  Where the first dose usually comes on waking, it suggests having it 15 minutes later. A tip only
  moves a dose the user already has: it never adds one, never changes an amount, and never brings
  the first dose of the day earlier.
- It makes no treatment claims, doesn't diagnose anything, and isn't a medical device.
- It points people to professionals: a pharmacist or GP before changing how they use NRT, local
  stop smoking services, and a quitline for each country it covers.
- Nothing leaves the phone (see `data-safety.md`).

## Page 1: health features

Tick what the app does, honestly. Ticking a category doesn't make an app a medical device; that's
the separate Medical Device Apps box.

| Feature | Tick | Why |
|---|---|---|
| **Health and fitness** | | |
| Activity and fitness | No | No exercise or body tracking. |
| Nutrition and weight management | No | |
| Period tracking | No | |
| Sleep management | No | |
| Stress management, relaxation, mental acuity | **Yes** | The breathing timer (box breathing and 4-7-8) for riding out a craving. |
| **Medical** | | |
| Diseases and conditions management | No | It doesn't manage a disease or condition. Coming off nicotine sits under the two below. |
| Clinical decision support | No | It isn't for health professionals. |
| Disease prevention and public health | No | |
| Emergency and first aid | No | |
| Healthcare services and management | No | No appointments, records or care services. |
| Mental and behavioural health | **Yes** | It supports someone coming off nicotine, which Play groups with addiction recovery. |
| Medical reference and education | No | The advice is self-help for cravings, not medical reference or treatment guidelines. |
| Medication and treatment management | **Yes** | NRT is a medicine. The app logs it and plans its reduction week by week. Its reminders are about logging and the plan, never about taking a dose. Its timing tips move a dose the user already has, never add one. |
| Physical therapy and rehabilitation | No | |
| Reproductive and sexual health | No | |
| **Medical Device Apps** | **No** | Not regulated as a medical device anywhere it's offered. See "For the owner" below. |
| **Health subjects research** | No | No studies or trials. |

Health Connect: the app doesn't use it (there's no nicotine data type), so there are no Health
Connect permissions to declare.

## Page 2: details

Play may ask more about each ticked feature on a second page. Answer from these facts:

- **Intended purpose,** if there's a box for it: "A self-tracking and habit tool for adults coming
  off nicotine replacement therapy. It logs the NRT you use, shows your patterns and triggers,
  and plans a week-by-week step-down from your own usual day. It gives general information on
  getting through cravings, and timing tips from your own logs that move a dose you already have
  and never add one. It isn't medical advice and isn't a medical device."
- **Regulated as a medical device:** no, not in any market.
- **Diagnosis or treatment:** none. The plan starts from the user's own usual day, takes one
  piece or spray off a step or moves to a lower strength, and never goes above a pack's labelled
  daily limit. Weeks can be edited before they start, and a missed week can be repeated. It
  doesn't prescribe a dose for a condition. Timing tips read the user's own logs and suggest
  moving one usual dose to a nearby time: before a meal rather than after, or the first dose of
  the day 15 minutes later. They never add a dose, change an amount or bring the first dose
  earlier, and each one says it's the app's own suggestion.
- **Who it's for:** adults, 18 and over (the Target audience answer in `data-safety.md`).
- **Disclaimers in the app:**
  - onboarding, which must be accepted before set-up
  - Settings, About
  - each advice page, the absorbed figures page and the agents' advice
  - the store description (Play asks health apps that aren't medical devices to say so there)
- **Professionals:** a pharmacist or GP before changing how NRT is used, especially in pregnancy,
  breastfeeding or with a heart condition. NHS stop smoking services in the UK, and a quitline for
  each country the catalogue covers.
- **Sources:** the catalogue's strengths and daily limits come from each pack's leaflet or label
  (the emc in the UK, DailyMed in the US, and the makers' and shops' own sites). The advice cites
  NHS, CDC and HSE pages, with the date each was read.
- **Health data:** stays on the phone. No collection or sharing (see `data-safety.md`), and the
  privacy policy covers it.

## For the owner

- **The plan, the timing tips and "dose calculators".** The UK's MHRA guidance on software as a
  medical device names software that works out a medicine's dose for a person as likely to be a
  device. The Last Hit's plan steps down from the user's own usual day by whole units and never
  goes above the pack's limit, which is closer to the step-down the gum makers print on their
  packs than to working out a dose for a condition, and the app says plainly that it isn't
  medical advice. The timing tips sit closer to the line: they read a person's own logs and
  suggest when to have a dose ("Try having your dose at 12:30 and 18:30"). They only move a dose
  the person already has, to before a meal or a little later in the morning, and never add one or
  change an amount. Recommended: declare "not a medical device", keep the store text free of
  treatment claims and keep the timing tips out of the listing and screenshots (`listing.md`
  does), and if you want certainty before release, put both the plan and the timing tips to the
  MHRA's borderline products enquiry.
- **Three boxes ticked.** Medication and treatment management, Mental and behavioural health, and
  Stress management. Ticking fewer could look like hiding health features. Ticking these brings a
  few more questions on the second page. What the policy asks of health apps that aren't medical
  devices, a disclaimer in the description, a reminder to see a professional and a privacy policy,
  is all in place.
