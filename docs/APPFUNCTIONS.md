# AppFunctions

How Gemini and other on-device agents talk to The Last Hit (PLAN §12, ROADMAP phase 7), how to try
it on a phone, and where Google's Early Access Programme stands.

## What it is

AppFunctions are Android's on-device version of MCP: an app describes functions, the system indexes
them, and an agent holding the `EXECUTE_APP_FUNCTIONS` permission (Gemini, for one) calls them. They
run on Android 16 (API 36) and newer. Nothing leaves the phone, and the app still has no `INTERNET`
permission.

Gemini calling other apps' functions is still limited to apps in Google's Early Access Programme
(see below). Until Google opens it up, the functions can be tried with adb, which is what
`scripts/appfunctions.sh` does.

## The five functions

Each is described by its KDoc, which the compiler turns into the descriptions the agent reads.

| Function | What it does |
|---|---|
| `logNrtUsage(productDescription, quantity?, time?, tags = [], cravingStrength?, keepDuplicate = false)` | Logs a dose through the normal logging path with source `APP_FUNCTION`. Returns the entry, a message to read out, and the day and week after it. |
| `listNrtProducts()` | The products that can be logged, quick-log ones first, with IDs, strengths and units. |
| `getNrtSummary()` | Today's mg against the daily guide, the week against its allowance, the last dose, and the demon's HP and banked hits. |
| `undoLastNrtEntry()` | Deletes the entry written last, if it was written in the last 24 hours and after the last restore from backup. |
| `getNrtAdvice(tag?)` | The best strategy for a trigger, or for the top trigger of the last 14 days, or the 4 Ds when too little is logged, with the quitlines for the user's country. |

How they behave:

- **The description** goes through the same matcher as `lasthit://log` links, so "two 4 mg gums",
  "nicorette 4", "21mg patch" and a product's ID all work.
- **An ambiguous product** saves nothing and returns `candidates`, each a product with the quantity
  it would log in that product's own unit: "two nicorette gums" is 2 of either gum, and "inhalator
  cartridge" is 8 sessions of either inhalator. The agent asks which, then calls again with that
  candidate's product `id` as the description and its `quantity`, since an ID on its own logs one.
- **The duplicate check** is the app's own (PLAN §4): the same product logged within a minute from
  somewhere else (the widget, say) saves nothing and sets `possibleDuplicate`. The agent asks, then
  calls again with `keepDuplicate` set to true. It doesn't cover the function's own logs, so an
  agent that calls `logNrtUsage` twice, retrying after a timeout say, logs two doses. The KDoc
  tells it to check `getNrtSummary` first when it isn't sure a call went through.
- **Refusals** come back as the library's exceptions, with the app's own words from
  `strings_agents.xml`. A product that matches nothing, a time that hasn't come yet, an unknown tag,
  a quantity outside 1 to 99 and a craving strength outside 1 to 5 are invalid arguments. Every call
  is refused until set-up is done.
- **The guide and the allowance** are ceilings. The summary gives each beside the total and says
  whether it's within, and never works out what's "left", which would read as an amount to take.
- **Advice** never suggests taking more: it only comes from the bundled library, which is held to
  that, and the advice for after a slip is left out. It's general information, not medical advice,
  and it comes with the free quitlines for the user's country from the same library, as PLAN §9
  asks, so an agent can pass one on when a craving is strong or the user wants more help.
- **Undo** goes by when an entry was written, so "log a gum at 3 pm yesterday" then "undo that"
  takes back the backdated one. It stops at 24 hours, so a misheard "undo" can't delete a dose from
  days ago that nothing on screen offers back. A restore from backup writes every entry it brings
  in at the moment it runs, so undo also stops at the last restore (`SettingsStore.restoredAt`):
  nothing restored, and nothing logged before it, is undone, and a dose logged after it is.
- **Days** are the app's logical days. A dose at 01:20 on Friday, before the day starts, counts
  towards Thursday, and the words sent back say so ("Logged 2 mg of Nicorette gum at 01:20, which
  counts towards Thursday."), so they agree with the summary beside them.
- **Times** an agent sends are on the phone's clock. Times sent back are on the clock where each
  dose was logged.

The app also gives the system a short description of what agents can do here, and instructions for
the agent on using the functions together (`res/xml/app_functions_metadata.xml`).

## Where the code is

| Where | What |
|---|---|
| `core/domain/.../domain/agent` | The rules, with JUnit tests: reading a log request (`AgentLogReader`), the summary, the advice pick and the undo window. |
| `app/.../agents/NrtAgent.kt` | The five functions on the app's data, with no AppFunctions types. This is the part to keep if the library changes shape. |
| `app/.../agents/AgentText.kt` | The words that go back, from `strings_agents.xml`. |
| `app/.../agents/NrtAppFunctions.kt` and `AgentResponses.kt` | The only code that knows the library: the entry point, the KDoc, and the shapes sent back. |
| `AndroidManifest.xml` | The service the compiler writes, `NrtAppFunctionService`, switched on from Android 16 by the library's own flag. |

The compiler also writes `assets/nrt_app_functions.xml`, the list the system indexes. Function IDs
are `app.thelasthit.nrt.agents.NrtAppFunctions#<name>`.

Tests: `AgentLogReaderTest`, `AgentSummaryTest`, `AgentAdviceTest` and `AgentUndoTest` in the domain,
`LastWrittenTest` in the data layer, `NrtAgentTest` for the functions on the app's data, and
`NrtAppFunctionsTest`, which calls them through the library's own test rule on Robolectric's
Android 16.

## Trying it on the phone

1. Connect the Android 16 phone with USB debugging on, then install a debug build:
   ```
   ./gradlew :app:installDebug
   ```
2. Open the app and finish set-up if it's a fresh install.
3. Check the phone has indexed the five functions:
   ```
   scripts/appfunctions.sh check
   ```
   It prints `indexed` or `missing` for each. Indexing can take a minute after an install.
4. Call them:
   ```
   scripts/appfunctions.sh products
   scripts/appfunctions.sh summary
   scripts/appfunctions.sh log "two 4 mg gums" --tags stressed,after_meal
   scripts/appfunctions.sh log "inhalator session" --at 2026-10-23T15:00 --strength 3
   scripts/appfunctions.sh advice Bored
   scripts/appfunctions.sh undo
   ```
   Or `scripts/appfunctions.sh tour`, which calls each one, then offers to undo the dose it logged.
   These log real doses on the phone, so undo the test ones, here or in History.
5. Anything else goes through `run` with JSON parameters:
   ```
   scripts/appfunctions.sh run logNrtUsage '{"productDescription": "nicorette gum"}'
   ```

`BRIEF=1` asks the phone for short YAML instead of JSON. `scripts/appfunctions.sh help` prints the
phone's own help for `cmd app_function`, which is the reference if Google changes the commands.

**Still to confirm on the phone:**
- The shape `--at` sends a time in. The script sends a `LocalDateTime` as its parts (year, month,
  dayOfMonth, hour, minute, second, nanoOfSecond), which is how the library itself sends one. If the
  phone refuses it, `list` shows the shape it expects for `time`.
- What an agent sees for a refusal. The tests check the message the app sets; how the shell prints
  it is the phone's business.

**If something's wrong:**
- `cmd: Can't find service: app_function`: the phone is older than Android 16.
- `missing` from `check`: reinstall, wait a minute, and check `assets/nrt_app_functions.xml` is in
  the APK (`unzip -l app/build/outputs/apk/debug/app-debug.apk | grep nrt_app_functions`).
- "Function not found": the ID is wrong. `list` shows the IDs the phone has.

## Early Access Programme

Google is onboarding a few apps at a time to test the whole path from an agent to an app's functions
before AppFunctions go public. Registering interest doesn't grant access: Google emails if the app
is picked, or when AppFunctions open to everyone.

- **Sign-up:** the "Early Access Program form" linked from
  [the AppFunctions overview](https://developer.android.com/ai/appfunctions), which on 24 October 2026
  pointed to `https://forms.gle/GN5ybjQFhzHRCguM7`. It needs your Google account, so it's yours to
  send.
- **What to say:** a personal app for tapering off nicotine replacement therapy, on the phone only
  with no network access, with five functions: log a dose in plain words, list products, summarise
  the day and week against the plan, undo the last entry, and give advice for a trigger.
- **Until then:** the functions are built, tested and indexed, and adb runs them. Nothing in the app
  waits on the programme.

## Updating the library

The version is pinned in `gradle/libs.versions.toml` (`appfunctions`), currently `1.0.0-beta01`,
the first beta, from 21 October 2026, which keeps the API stable from here (the last alpha was
`1.0.0-alpha13`). Since `1.0.0-alpha10` the functions sit in an abstract service marked
`@AppFunctionServiceEntryPoint`, in place of PLAN's plain class with an `AppFunctionContext`
parameter, so this follows the library (ROADMAP P7-1). What the functions do is as PLAN §12 has it.

When moving to a newer version:
1. Read the release notes on developer.android.com for changes to `@AppFunctionServiceEntryPoint`,
   `@AppFunctionDeclaration`, `@AppFunctionSerializable` and the manifest entries.
2. Run the full check. `NrtAppFunctionsTest` calls the functions through the library's test rule,
   and checks the indexed list still describes every function and parameter.
3. Run `scripts/appfunctions.sh check` and `tour` on the phone.
