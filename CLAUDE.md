# The Last Hit: Quit NRT

An Android app for people who quit smoking or vaping with nicotine replacement therapy and now
want off the NRT itself. It logs doses, shows triggers, runs a weekly taper to zero, and turns
each week met into a hit on a pixel-art creature that stands for the habit.

## Read these first

1. `docs/STATUS.md`: where the build is, what's decided since the plan, and what's next.
2. `docs/PLAN.md`: the agreed plan (scope, rules, phases). Don't change agreed behaviour without asking.
3. `docs/STYLE.md`: the copy rules. They apply to every user-facing string.

## Working with the owner

- Ask clarifying questions when something is genuinely open, and show a plan before large coding work.
- Recommend one option rather than listing every possibility.
- The owner cares a lot about the app not looking or reading like it was made by AI. Design and
  copy go through the review agents described in `docs/STATUS.md`.

## Conventions

- **No network access.** The app has no INTERNET permission, on purpose: everything stays on the
  phone. Don't add network libraries, analytics or crash reporting.
- **App text:**
  - British English and sentence case.
  - No em or en dashes.
  - No emoji, and no marketing words.
  - `./gradlew checkCopy` enforces the mechanical parts; `docs/STYLE.md` covers the rest.
- **Colour rules:** nicotine used is the neutral `used` tone, never green or marigold. The guide is
  a marker or line, never a fill. Green only means "at or under the guide". Marigold is the game layer.
- **Doses** are the `Dose` type in whole micrograms. Never use floating-point mg for sums or checks.
- **Days** come from `DayBoundary` (04:00 default day start, entry's own zone). Never use the
  calendar date of an instant directly.
- **No DI framework.** Wiring is plain constructors in `DataGraph` (`core/data`).
- **Business rules go in `:core:domain`** (plain Kotlin, JUnit 6), with tests. Android code stays thin.

## Modules and commands

| Module | Contents |
|---|---|
| `:app` | Compose UI, theme in `ui/theme` |
| `:core:domain` | Rules, catalogue (`resources/catalogue/products.json`), sprites (`resources/creatures/`), backup format |
| `:core:data` | Room, DataStore settings, repositories, backup import/export (Robolectric tests) |

```
./gradlew checkCopy :core:domain:test :core:data:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:installDebug                      # onto a connected phone
./gradlew -PjvmOnly :core:domain:test checkCopy  # without the Android SDK (cloud sandboxes)
```

The build uses AGP 9.4 with built-in Kotlin: there's no `org.jetbrains.kotlin.android` plugin, and
`kotlin { compilerOptions { } }` replaces `kotlinOptions`. Versions live in `gradle/libs.versions.toml`.

## Branch

Work continues on `claude/elegant-bell-67rsvf`. CI (`.github/workflows/ci.yml`) runs on every push
and keeps the debug APK as a download.
