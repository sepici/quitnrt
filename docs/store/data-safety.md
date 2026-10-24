# Data safety answers

The answers for Play Console's Data safety form (Policy, then App content, then Data safety), page
by page, with the reason for each. Checked against the app on 24 October 2026, at `versionName`
0.1.0, and against Google's help page for the form:
<https://support.google.com/googleplay/android-developer/answer/10787469>.

The form changes now and then. If a question below has moved or been reworded, the facts in
"Why" still hold, so answer from those.

## What the answers rest on

- **No internet.** The app's manifest has no `INTERNET` permission, and it removes
  `ACCESS_NETWORK_STATE`, which WorkManager would otherwise add. Without `INTERNET`, Android won't
  let the app open a network connection at all.
- **The permissions it ends up with,** from the merged manifest of the debug build (the release
  build has the same main manifest and libraries, less the debug-only test tools):
  `VIBRATE`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`,
  and the app's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which AndroidX adds so only the
  app can reach its own receivers. None of them reads data from outside the app.
- **No SDK that sends data.** The libraries are AndroidX and Compose, Room, DataStore,
  WorkManager, Glance, `androidx.appfunctions` and kotlinx.serialization. There's no analytics,
  ads, crash reporting or Google Play services library. Nothing here can send data, since the app
  has no network access.
- **No account,** and no advertising ID (the merged manifest has no `AD_ID` permission).

## Page 1: Data collection and security

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Play counts data as collected only when it's sent off the phone, and the app can't send anything. Data the app keeps and uses on the phone doesn't count. Nothing is passed to another app unless the user asks for it (see "Edge cases" below). |
| Is all of the user data collected by your app encrypted in transit? | Not asked, or not applicable | Nothing is collected. |
| Do you provide a way for users to request that their data is deleted? | Not asked, or not applicable | Nothing is collected. Users delete their data on the phone themselves: see the privacy policy. |
| Does your app allow users to create an account? (if the form asks) | **My app does not allow users to create an account** | There are no accounts of any kind. |

With **No** on the first question, the form skips the data types and the usage pages.

## Page 2: Data types

Nothing to tick. If the app ever did send what it stores, it would be **Health and fitness:
Health info** (doses, cravings and triggers) and **App activity: Other user-generated content**
(notes, tag names, the demon's name). That's why the "no network" rule matters: it keeps all of
this off the form.

## Page 3: Data usage and handling

Skipped, since no data types are ticked.

## Page 4: Preview

The store listing should then read **No data shared with third parties** and **No data
collected**. Check the preview says so before saving.

The form also needs the privacy policy's address. Host `docs/store/privacy-policy.md` at
`https://thelasthit.app/privacy` (or wherever it ends up) and give that address under App content,
Privacy policy.

## Edge cases, and why none of them is collection or sharing

| What happens | Collected or shared? | Why |
|---|---|---|
| **Backup and CSV files** the user exports from Settings | No | The app never transmits the file: the user starts the export, and Android's file picker hands the file to whatever app or storage the user picks there. The developer never receives it. Play counts data as collected when it's sent off the phone, and as shared when it goes to a third party, unless the user starts the transfer and expects it; here the user starts and directs it. This is reasoning from Play's definitions, not a quote from its help page. |
| **Android Auto Backup** and device-to-device transfer | No | Android's own backup service copies the app's private files into the user's Google account backup, under Google's terms. The developer never receives or can read it. On Android 9 and newer with a screen lock, it's encrypted with the lock screen; without one, or on Android 8, it isn't end-to-end encrypted (see "For the owner"). This is the same reasoning as a file the user exports to their own storage, above. It's a judgement, and the usual one for apps that use Auto Backup. If Play's review ever says otherwise, the answer would be Health info, collected, optional, for App functionality. |
| **AppFunctions** (Android 16 and newer): an assistant such as Gemini logs a dose or reads today's totals | Not declared | Play counts a transfer to another app on the phone as sharing, but not when the user starts it and expects the data to go there. Assistants call these functions when the user asks them to log, list or summarise, and Android only lets apps holding its `EXECUTE_APP_FUNCTIONS` permission call at all. Android doesn't check that the user asked: that's the assistant's job. Each answer carries only what the request needs. This is also a judgement, and the one to look at again if Play asks: see "For the owner" below. |
| **The widget, notifications, shortcuts and the Quick Settings tile** | No | They show the user's own data on their own phone, drawn by the launcher and system UI. Nothing goes to a third party. |
| **Log links and NFC stickers** (`lasthit://log`) | No | Data comes into the app, not out. |
| **Quitline buttons** on the advice pages | No | They hand a quitline's phone number to the phone app, or its web address to the browser, with Android's `ACTION_DIAL` and `ACTION_VIEW`. Nothing about the user goes with them. |
| **Crash reports in Play Console** | No | Android and Google Play gather these under the user's own Google settings. The app has no crash reporting of its own and sends nothing. |

## Other App content answers

The rest of the App content page, for completeness:

| Section | Answer | Why |
|---|---|---|
| Privacy policy | The hosted `privacy-policy.md` | Required for every app, and for health apps. |
| App access | All functionality is available without special access | No login, no paywall. |
| Ads | No, my app does not contain ads | |
| Advertising ID | No | No `AD_ID` permission and no ads or analytics library. |
| Content rating | See `listing.md` | |
| Target audience and content | 18 and over | It's for adults coming off NRT. 18 and over keeps it out of the Families programme, which the app isn't made for. |
| News app | No | |
| Government app | No | |
| Financial features | My app doesn't provide any financial features | |
| Health | See `health-apps.md` | |
| Foreground service permissions | Shouldn't be asked | The manifest has only the plain `FOREGROUND_SERVICE` from WorkManager, with no typed foreground service permission, and the app never starts one. If the form asks, say it doesn't use foreground services. |

## For the owner

- **AppFunctions.** Play counts on-device transfers to another app as sharing unless the user starts
  them. The functions are meant to run when the user asks an assistant to act, which is the
  user-initiated case, though Android only checks that the caller holds `EXECUTE_APP_FUNCTIONS`, not
  that the user asked. And an assistant like Gemini may send the request, and so the app's answer,
  to its own servers. Recommended: keep "No", since the user chose the assistant and asked it to
  act, and Gemini's own policy covers what it does. If Play's review disagrees, declare Health info
  as shared, optional, for App functionality.
- **Developer name and contact address.** The privacy policy ends with
  `{developer name, as shown on Google Play}` and `{contact email}`. Play's User Data policy asks
  the policy to name the developer as the Play listing does and to give a privacy contact. Fill
  both in before hosting.
- **Auto Backup without a screen lock.** Android backs up to Google even on a phone with no screen
  lock, where the backup isn't end-to-end encrypted. The privacy policy says so. To keep dose logs
  out of an unencrypted backup, each Android version needs its own change, all in one app slice:
  - **Android 12 and newer** read `data_extraction_rules.xml`: add
    `disableIfNoEncryptionCapabilities="true"` to its `<cloud-backup>`. Phone to phone transfer is
    unaffected.
  - **Android 9 to 11** read `backup_rules.xml`, which has no encryption requirement: switch it to
    `<include>` rules with `requireFlags="clientSideEncryption"`. Once it has an include, only what
    it includes is backed up, so it needs one for each of the app's domains (`database`,
    `sharedpref`, `file`), still leaving out the reminders' DataStore.
  - **Android 8** (the app's `minSdk` is 26) can't encrypt backups with the screen lock at all.
    Either accept an unencrypted backup there, which the policy already says, or leave the logs
    out of backup on Android 8. Check on an Android 8 emulator how it treats the Android 9 rules
    before relying on them.

  Recommended: make the change, so a backup that isn't encrypted never holds health data. The
  answers here hold either way, and if the app changes, update the policy's backup paragraph in
  the same slice.
