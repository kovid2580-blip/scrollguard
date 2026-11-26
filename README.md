# ScrollGuard

ScrollGuard is an offline-first Android digital wellbeing and anti-doomscrolling application built with Kotlin, Jetpack Compose, Material 3, Room, DataStore, Hilt, WorkManager, and Android AccessibilityService.

Its primary goal is **selective content blocking**: allowing users to keep using useful parts of social apps (Instagram Feed, Stories, Direct Messages, Profiles; YouTube normal videos, Search, Subscriptions; Facebook Feed, Groups, Marketplace) while intercepting short-form addictive feeds (Instagram Reels, YouTube Shorts, Facebook Reels), alongside full-app blocking, daily usage limits, schedules, and Strict Mode with PIN protection.

---

## Architecture & Tech Stack

- **UI**: Jetpack Compose + Material 3 (Light & Dark themes, Deep Indigo / Soft Blue / Emerald / Amber palette)
- **Architecture**: MVVM + Clean Architecture (`data`, `domain`, `blocking`, `monitoring`, `scheduling`, `security`, `presentation`)
- **Database**: Room (`BlockingRuleEntity`, `FocusScheduleEntity`, `UsageRecordEntity`, `BlockedAttemptEntity`, `DailyReportEntity`)
- **Preferences**: Jetpack DataStore (theme mode, onboarding/consent, strict mode, focus session timers, diagnostics toggle)
- **Dependency Injection**: Hilt (`@HiltAndroidApp`, `@HiltWorker`)
- **Blocking Engine**:
  - `ScrollGuardAccessibilityService`
  - `AppDetectionEngine`
  - `ContentDetectionEngine` (dedicated Instagram, YouTube, Facebook, and Custom Rule adapters)
  - `DetectionConfidenceEvaluator` (multi-signal weighted scoring + exclusion signals to prevent false positives)
  - `BlockingDecisionEngine` (centralized precedence engine: self/system exempt -> paused -> temp unlock -> full-app/daily-limit/schedule -> selective content)
  - `BlockingOverlayManager` (`WindowManager` `TYPE_APPLICATION_OVERLAY` Compose interruption UI)
- **Usage Tracking**: `UsageStatsManager` (`queryEvents` + daily fallback; never fabricates metrics)
- **Scheduling**: `ScheduleEvaluator`, `AlarmManager` (`setAndAllowWhileIdle`), `BootReceiver`, `ScheduleAlarmReceiver`, `UsageSyncWorker`
- **Security**: `PinCrypto` (PBKDF2-HMAC-SHA256 with 120,000 iterations, 16-byte random salt, constant-time `MessageDigest.isEqual`, `CharArray` zeroing, Android Keystore AES-256-GCM wrapping), `PinRepository`, `PinManager` (failed-attempt delays + recovery key)

---

## Required Permissions & Privacy

ScrollGuard works **100% offline** with no backend or user account.

1. **Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`)** — Used strictly as a digital wellbeing content guard (not an accessibility aid) to inspect structural view IDs, selection states, and short UI labels transiently in memory. Unrelated screen text is never collected, persisted, or uploaded.
2. **Usage Access (`PACKAGE_USAGE_STATS`)** — Reads local foreground app durations for usage reports and daily app limits.
3. **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`)** — Displays the interruption overlay when a blocked short-form feed or restricted app opens.
4. **Notifications (`POST_NOTIFICATIONS`, Android 13+)** — Optional alerts for focus sessions and schedule transitions.
5. **Boot Completed (`RECEIVE_BOOT_COMPLETED`)** — Restores scheduled blocking boundaries after device reboot.

Android 11+ package visibility uses scoped `<queries>` for launcher activities (`ACTION_MAIN` / `CATEGORY_LAUNCHER`) and supported selective target packages without requesting `QUERY_ALL_PACKAGES`.

---

## Project Setup

1. Open `ScrollGuard` in **Android Studio** (Ladybug or newer) or build via command line.
2. Ensure **JDK 17** and **Android SDK Platform 35** (`compileSdk = 35`, `minSdk = 26`) are installed.
3. Set `sdk.dir` in `local.properties` (already configured if built locally).

---

## Building the APK

### Debug APK
```bash
# Linux / macOS
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK & Signing Configuration
Never commit signing keystores or passwords to version control (`keystore.properties`, `*.jks`, `*.keystore` are in `.gitignore`).

To build a **signed release APK**:
1. Generate or locate your upload/release keystore (`keytool -genkey -v -keystore release-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias scrollguard`).
2. Copy `keystore.properties.example` to `keystore.properties` and fill in `storeFile`, `storePassword`, `keyAlias`, and `keyPassword` (or set `SCROLLGUARD_KEYSTORE_PATH`, `SCROLLGUARD_KEYSTORE_PASSWORD`, `SCROLLGUARD_KEY_ALIAS`, `SCROLLGUARD_KEY_PASSWORD` environment variables).
3. Run:
```bash
# Linux / macOS
./gradlew assembleRelease

# Windows
gradlew.bat assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

---

## Running Tests

### Unit & Robolectric Tests
```bash
gradlew.bat testDebugUnitTest
```
Covers all 17 verification scenarios:
- Instagram Home / DMs / Stories allowed vs Reels blocked
- YouTube standard videos / Search allowed vs Shorts blocked
- Facebook Reels blocked vs Feed allowed
- Full-app precedence, daily limits, unselected apps, ScrollGuard self-exemption
- PBKDF2 PIN hashing, lockout delays, recovery key, Strict Mode lock
- Same-day & overnight schedules (`22:00 – 07:00`), reboot alarm scheduling
- UsageStats permission-denied non-fabrication, overlay permission revocation & duplicate-window guards

### Instrumentation Tests (requires connected Android device/emulator)
```bash
gradlew.bat connectedDebugAndroidTest
```

---

## Known Compatibility Limitations

- **Third-party app UI updates**: Instagram, YouTube, and Facebook periodically rename resource IDs or use custom-rendered views (such as Facebook Litho surfaces). ScrollGuard uses multi-signal confidence thresholds and safe-fail diagnostics rather than brittle single-string checks, and lets users adjust or create custom rules.
- **OEM background & battery policies**: Some Android manufacturers (MIUI, OnePlus, Samsung, ColorOS) aggressively suspend background accessibility services unless ScrollGuard is excluded from battery optimization.
- **OS security boundaries**: In accordance with Android security and Google Play policy, ScrollGuard's overlay and Strict Mode act as intentional self-control interruptions and cannot prevent uninstalling the app or revoking permissions in Android System Settings.
