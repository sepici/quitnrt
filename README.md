# The Last Hit: Quit NRT

An Android app for people who quit smoking or vaping with nicotine replacement therapy and now
want to get off the NRT itself. It logs every gum, lozenge, spray and patch, shows what triggers
them, and runs a week-by-week plan down to zero. Each week you meet earns a hit on a pixel-art
creature that stands for the habit. The last week's hit finishes it.

Everything stays on the phone. The app has no internet permission.

- **Plan:** [`docs/PLAN.md`](docs/PLAN.md)
- **Copy rules:** [`docs/STYLE.md`](docs/STYLE.md)
- **Product research:** [`docs/research/catalogue/`](docs/research/catalogue/)

## Layout

| Module | What's in it |
|---|---|
| `:app` | The phone app (Jetpack Compose) |
| `:core:domain` | Plain Kotlin rules with no Android code: doses, logical days, label limits, the product catalogue, logging rules and the backup format |
| `:core:data` | Room database, settings, repositories, and backup export and import |

## Building

You need JDK 17 or newer and the Android SDK (Android Studio installs it).

```
./gradlew :app:assembleDebug        # debug APK in app/build/outputs/apk/debug/
./gradlew :core:domain:test         # core logic tests
./gradlew checkCopy                 # app text against docs/STYLE.md
```

Without the Android SDK or Google's Maven repository, `-PjvmOnly` builds just the plain Kotlin
modules:

```
./gradlew -PjvmOnly :core:domain:test checkCopy
```

CI runs all of the above on every push and keeps the debug APK as a download on the run's page.
