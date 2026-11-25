# Focus Timer — Android App Download & Install Guide

The Android build of Focus Timer gives you the same timer, session history, analytics and
Google sync as the web app, packaged as a native app that works offline and keeps alarms
running in the background.

> The APK is distributed straight from GitHub (not the Play Store). Android will therefore
> ask you to allow installing apps from your browser the first time.

---

## 1. Download

**Direct APK:**
<https://github.com/SahilSk-dev/Focus_Timer/releases/download/android-latest/focus-timer-debug.apk>

**Release page (version notes + all assets):**
<https://github.com/SahilSk-dev/Focus_Timer/releases/tag/android-latest>

You can also download from inside the web app:
**History & Data → Android app → Download APK**.

| Item | Value |
| --- | --- |
| Package name | `com.sahil.focusstudy` |
| Version | 1.0 (debug build) |
| Approx. size | ~27 MB |
| Minimum Android | Android 7.0 (API 24) |
| Target SDK | Android 16 (API 36) |
| Signing | Debug key (direct install only) |

The APK is rebuilt automatically by GitHub Actions on every change under
`Focus_Timer_Android/`. The `android-latest` release tag always points at the newest
successful build.

---

## 2. Install (4 steps)

1. **Download the APK** on your phone using the link above.
   If Chrome says *"This type of file can harm your device"*, tap **Download anyway**.
2. **Open the file.** Pull down the notification shade and tap the finished download, or open
   **Files / Downloads** and tap `focus-timer-debug.apk`.
3. **Allow installs from this source.** When Android asks, tap **Settings**, enable
   **Allow from this source** (or *Install unknown apps*) for your browser or file manager,
   then go back.
4. **Tap Install → Open.** Sign in with the same Google account you use on the web app to
   sync your sessions.

### Where the "unknown apps" toggle lives

The exact path varies slightly by manufacturer:

- **Stock Android / Pixel:** Settings → Apps → Special app access → Install unknown apps → *your browser* → Allow
- **Samsung:** Settings → Apps → ⋮ → Special access → Install unknown apps → *your browser* → Allow
- **Xiaomi / Redmi (MIUI/HyperOS):** Settings → Privacy protection → Special permissions → Install unknown apps → *your browser* → Allow
- **OnePlus / Oppo / Realme:** Settings → Apps → Special app access → Install unknown apps → *your browser* → Allow

---

## 3. Updating the app

1. Download the newest APK from the same link.
2. Install it **over** the existing app — do not uninstall first.
3. Your local history, subjects, work types and preferences are preserved.
4. Sessions stored in Firestore sync back automatically after you sign in.

If Android refuses to update because of a **signature mismatch**, the APK was built with a
different key. Uninstall the old copy, install the new APK, then sign in again to restore
cloud data.

---

## 4. Troubleshooting

| Symptom | Fix |
| --- | --- |
| "App not installed" | Uninstall any older Focus Timer build first, then install again. |
| "Blocked by Play Protect" | Tap **More details → Install anyway**. This appears because the APK is not distributed through the Play Store. |
| Install button greyed out | Enable *Install unknown apps* for the app you opened the APK with (see paths above). |
| Download stops midway | Use Wi-Fi, or open the release page and download the asset directly. |
| App opens but data is empty | Sign in with the same Google account used on the web app; check your connection once. |
| Alarms do not fire | Disable battery optimisation for Focus Timer: Settings → Apps → Focus Timer → Battery → Unrestricted. |
| "Package appears to be invalid" | The download was truncated. Delete the file and download it again. |

---

## 5. Uninstalling

Long-press the Focus Timer icon → **App info → Uninstall**.

Cloud sessions stay in your Google/Firestore account. Local-only sessions are removed with
the app, so export a JSON backup from the web app (**History & Data → Download JSON**) before
uninstalling if you need them.

---

## 6. For maintainers

- Workflow: `.github/workflows/android-apk.yml`
- Build trigger: any push to `main` touching `Focus_Timer_Android/**` or the workflow file,
  plus manual `workflow_dispatch`.
- Toolchain: JDK 21, Gradle 9.3.1, Android SDK 36, AGP 9.1.1, Kotlin 2.2.10.
- Output asset: `focus-timer-debug.apk`, published to the `android-latest` GitHub Release.

To build locally:

```bash
cd Focus_Timer_Android
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Local builds need `google-services.json` (or `GOOGLE_SERVICES_JSON` for the secrets plugin)
and the Android SDK installed. Release builds additionally need the signing keystore and
`KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_PASSWORD` environment variables.
