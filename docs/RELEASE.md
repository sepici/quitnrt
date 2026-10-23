# Release builds

How the release build is made and signed, how to try one on your phone, and what to check
before it goes to Play. PLAN §18 has the rules behind it.

## What the release build does

- **Shrinking.** R8 removes unused code and renames what's left, and resource shrinking drops
  unused resources. `app/proguard-rules.pro` explains what's loaded by reflection and why it
  needs no extra rules.
- **The domain's files.** `:core:domain` reads the catalogue, the sprites, the backdrops and the
  advice from JSON files by name. `./gradlew :app:checkReleaseResources` fails if any of them is
  missing from the release APK or bundle. CI runs it on every release build.
- **Signing.** The release build is signed with the upload key when all four of these are set,
  as Gradle properties or as environment variables:

  | Name | What it holds |
  |---|---|
  | `LASTHIT_UPLOAD_STORE_FILE` | The keystore's path |
  | `LASTHIT_UPLOAD_STORE_PASSWORD` | The keystore's password |
  | `LASTHIT_UPLOAD_KEY_ALIAS` | The key's alias, `upload` below |
  | `LASTHIT_UPLOAD_KEY_PASSWORD` | The key's password |

  With any of them missing, it's signed with the debug key instead, so a shrunk build can still
  be installed for testing. Gradle prints a warning when some are set but not all.

## Make the upload key

Play App Signing keeps the key that signs the app on phones. You keep only the upload key, which
proves an upload came from you. If it's ever lost, Play support can reset it.

1. Make the keystore with the `keytool` that comes with Android Studio. In PowerShell:

   ```
   New-Item -ItemType Directory -Force "$HOME\keys"
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
     -keystore "$HOME\keys\the-last-hit-upload.jks" -storetype PKCS12 `
     -alias upload -keyalg RSA -keysize 4096 -validity 10000 `
     -dname "CN=Your name"
   ```

   It asks for a password. A PKCS12 keystore uses the same password for the key, so the store
   password and the key password are the same.
2. Keep the `.jks` file and its password somewhere safe outside the repository, such as a
   password manager. Never commit either.
3. To sign release builds on your own computer as well (optional), add the four names to
   `C:\Users\<you>\.gradle\gradle.properties`, not the repository's `gradle.properties`. Use
   forward slashes in the path:

   ```
   LASTHIT_UPLOAD_STORE_FILE=C:/Users/<you>/keys/the-last-hit-upload.jks
   LASTHIT_UPLOAD_STORE_PASSWORD=<password>
   LASTHIT_UPLOAD_KEY_ALIAS=upload
   LASTHIT_UPLOAD_KEY_PASSWORD=<password>
   ```

## Add the GitHub secrets

CI signs the release APK and bundle with the upload key once these repository secrets exist. On
GitHub, open the repository's **Settings**, then **Secrets and variables**, **Actions**, and
**New repository secret** for each:

| Secret | Value |
|---|---|
| `LASTHIT_UPLOAD_KEYSTORE_BASE64` | The keystore file as base64 (below) |
| `LASTHIT_UPLOAD_STORE_PASSWORD` | The keystore's password |
| `LASTHIT_UPLOAD_KEY_ALIAS` | `upload` |
| `LASTHIT_UPLOAD_KEY_PASSWORD` | The same password again |

To copy the keystore as base64, in PowerShell:

```
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\keys\the-last-hit-upload.jks")) | Set-Clipboard
```

Then paste it as the secret's value.

The `release` job in `.github/workflows/ci.yml` runs on each push to `main` after the checks
pass, or from **Run workflow** on the Actions tab. It writes the keystore to the runner's temporary
folder, builds, and deletes it at the end. Without the secrets it still builds, signed with that
run's own debug key, which Play won't accept. The run's summary says which key was used. The
artefact `the-last-hit-release-<commit>` holds the APK, the bundle and R8's `mapping.txt`.

## Build and install a release-like build

With your phone connected and USB debugging on:

```
./gradlew :app:assembleRelease :app:bundleRelease :app:checkReleaseResources
./gradlew :app:installRelease
```

The APK is in `app/build/outputs/apk/release/` and the bundle in
`app/build/outputs/bundle/release/`.

- **Signed with the debug key** (no upload key set on your computer), it installs over the debug
  build from the same computer and keeps your data.
- **Signed with any other key** (the upload key, or a CI build without the secrets), Android
  won't install it over a build signed with a different key. Export a backup in Settings first,
  uninstall, install the new build, then restore.

To see which key signed an APK, in PowerShell:

```
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.1.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

`CN=Android Debug` means the debug key.

The bundle is only for Play. To try exactly what Play serves, upload it to an internal testing
track in the Play Console.

## What to check on the phone

R8 and resource shrinking can break things that only show when the app runs, so go through each
screen once on a fresh install of the release build:

1. **Onboarding.** The country list and the product picker fill in (the catalogue). The dragon
   and the band behind it draw (the sprites and backdrops). Text is in Schibsted Grotesk.
2. **Today.** Log from a quick-log pill, then Undo. Log from the sheet with a tag and a time in
   the past. The week columns show the doses.
3. **After a restart.** Swipe the app away, open it again, and the doses are still there (Room).
4. **History.** Edit a dose, swipe one to delete and Undo it, and filter by a product.
5. **Insights.** Change the range and product type, and open the table view.
6. **Lair.** The stage, the demon's shadow and "?? HP" in the pixel font.
7. **Settings.** Export a backup and a CSV, then restore the backup with "Add to what's here".
   About shows both font licences.
8. **Advice,** once it's built: the cards and the full list fill in (the advice file).
9. **Dark mode and large font.** A quick look at Today and Settings in each.
10. **Permissions.** In the phone's Settings, the app's permissions page lists none. The app has
    no internet access.

If it crashes, save the crash log and read it back with the mapping file from the same build. In
PowerShell:

```
adb logcat -b crash -d | Out-File -Encoding utf8 crash.txt
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\retrace.bat" app\build\outputs\mapping\release\mapping.txt crash.txt
```

For a CI build, use the `mapping.txt` in its artefact. Play reads the mapping from the bundle
itself, so its crash reports are readable already.

## Before each Play upload

- Raise `versionCode` in `app/build.gradle.kts` (Play refuses a code it has seen), and set
  `versionName` to match.
- On the first upload, let Google manage the app signing key (Play App Signing), and upload a
  bundle signed with the upload key.
