# Calendar Widgets

Home-screen calendar widgets for Android, showing the calendars already synced
to the phone (Google Calendar included). The app only reads the calendar; it
has no internet permission and events never leave the phone.

| Widget | Default size | Shows |
|---|---|---|
| Up next | 2×2 | Today's date and the next event with a countdown |
| Agenda | 4×2 | Today's date and a scrolling list of the next week's events |
| Month | 4×3 | Month grid with event dots; arrows change month, the title returns to today |

Every widget has two styles, chosen per widget: **Android** (wallpaper colours,
light/dark mode) and **iOS-like** (white or black card, red accent).

## Install

1. On the phone, open this repository's **Releases** page and download the
   newest `calendar-widgets-…apk`.
2. Open the file. Android asks once to allow installs from the browser or file
   manager.
3. Open **Calendar Widgets** and tap **Allow calendar access**.
4. Touch and hold the home screen → **Widgets** → **Calendar Widgets**.
5. To change a widget's style or calendars: touch and hold it → **Settings**.

If widgets fall behind, set the app's battery usage to **Unrestricted**
(Settings → Apps → Calendar Widgets → Battery).

## Builds

Every push to `main` runs `.github/workflows/build.yml`, which tests, builds
and publishes an APK as a GitHub Release.

For updates to install over the previous version, builds must be signed with
the same key. Add these under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Without them the build still works but uses a temporary key, and each new
version needs the old one uninstalled first.

## Code

- `core/` — plain Kotlin calendar logic (month grid, agenda, formatting) with
  unit tests: `./gradlew :core:test`. Set `CORE_ONLY=1` to run them without the
  Android SDK.
- `app/` — the Android app: Jetpack Glance widgets, calendar provider access,
  background refresh, and the settings screens.
