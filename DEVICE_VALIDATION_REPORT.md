# ScrollGuard — Release Hardening & Device Validation Report

## 1. Environment & Verification Scope

| Check | Status | Notes |
|---|---|---|
| Connected Android Device / Emulator (`adb devices`) | Not connected | No physical Android device or AVD emulator was attached in this workspace (`adb devices` returned empty; SDK has `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`, `34.0.0`). |
| Release Signing Credentials (`keystore.properties` / `SCROLLGUARD_KEYSTORE_*`) | Not supplied | Release APK built unsigned (`app-release-unsigned.apk`); signing config in `app/build.gradle.kts` activates automatically when credentials are supplied. |
| JVM & Robolectric Test Suite (`testDebugUnitTest`) | **23 / 23 Passed** | 0 failures, 0 errors, 0 skipped. |
| Android Lint (`lintDebug` & `lintVitalRelease`) | **0 Errors** | 79 warnings (dependency version/informational checks), `lintVitalRelease` passed. |
| Instrumentation Test Compilation (`compileDebugAndroidTestKotlin`) | **Passed** | `ScrollGuardUiTest` compiles cleanly; execution requires a connected device (`connectedDebugAndroidTest`). |
| Debug APK (`assembleDebug`) | **Built** | `app/build/outputs/apk/debug/app-debug.apk` (19,001,584 bytes) |
| Release APK (`assembleRelease` + R8 shrink/minify) | **Built** | `app/build/outputs/apk/release/app-release-unsigned.apk` (1,708,900 bytes) |

---

## 2. Release Hardening Fixes Applied

1. **`ScrollGuardAccessibilityService.kt` — Cold-Start Seeding & Battery Optimization**
   - `onServiceConnected()` now runs idempotent `blockingRuleRepository.seedBuiltInRulesIfMissing()` before `ruleRepository.refreshNow()`, preventing a cold-start race if Android binds the AccessibilityService before `MainActivity` launches.
   - `extractSnapshotIfNeeded()` now skips `AccessibilityNodeInfo` tree traversal entirely when an unconditional `FULL_APP` rule applies or when no `SELECTIVE_CONTENT` rule/schedule is active for the foreground package, reducing CPU and battery overhead.
2. **`BlockingOverlayManager.kt` — Self-Healing Overlay Window Lifecycle**
   - `isShowing` and `showOrUpdate()` now check `overlayView?.isAttachedToWindow == true`, cleaning up stale detached views before attaching a new overlay so permission revocation or OS window detachment cannot permanently wedge future overlays.
3. **`ContentDetectionEngine.kt` — Facebook False-Positive Tightening**
   - Narrowed `evaluateFacebookReels()` selected-tab matching from broad `desc.contains("reels")` to exact/tab-scoped cues (`id.contains("reels_tab")`, `desc == "reels"`, `desc.startsWith("reels,")`, `desc.contains("reels tab")`, `text == "reels"`), preventing feed buttons such as `"Watch Reels"` from triggering false positives.
4. **`app/proguard-rules.pro` — R8 Release Minification Coverage**
   - Added explicit keep rules for `com.scrollguard.app.domain.usecase.**` (`@Serializable` backup/restore DTOs) and `com.scrollguard.app.scheduling.**` (`HiltWorker` & broadcast receivers) under `minifyReleaseWithR8`.

---

## 3. Regression & Unit Test Results (`testDebugUnitTest`)

| Test Suite | Passed | Failed | Skipped | Coverage |
|---|---|---|---|---|
| `BlockingDecisionEngineTest` | 14 | 0 | 0 | Instagram Home/DMs/Stories allowed vs Reels blocked; YouTube Watch/Home-with-Shorts-shelf allowed vs Shorts blocked; Facebook feed allowed vs Reels blocked; full-app precedence; daily limits; schedules; ScrollGuard self-exemption; text-only anti-false-positive guard |
| `WellbeingUseCasesTest` | 1 | 0 | 0 | `BackupRestoreUseCase` JSON export/import round-trip with Room DB |
| `PlatformBehaviorRobolectricTest` | 3 | 0 | 0 | `ScheduleAlarmScheduler` reboot alarm setup; `UsageStatsMonitor` non-fabrication when `PACKAGE_USAGE_STATS` denied; `BlockingOverlayManager` permission revocation & repeated-call guard |
| `ScheduleEvaluatorTest` | 2 | 0 | 0 | Same-day schedules and overnight Mon–Fri `22:00 – 07:00` schedules |
| `PinManagerTest` | 3 | 0 | 0 | PBKDF2 unique salts, constant-time verify, 3-attempt lockout delay, recovery key reset, Strict Mode lock |
| **Total** | **23** | **0** | **0** | **All passed** |

---

## 4. Physical Device Validation Checklist (Pending Hardware Execution)

Run the following checklist once an Android device (API 26–35) is connected via USB debugging (`adb devices`):

1. **Install & Run Instrumentation Tests**:
   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   .\gradlew.bat connectedDebugAndroidTest
   ```
2. **Permission Setup & Revocation**:
   - Grant Accessibility Service, Usage Access, Display over other apps, and Notifications; verify green status badges on Permission Setup.
   - Revoke Overlay or Accessibility permission in Android System Settings while Instagram Reels is open; confirm no crash and graceful status update on Dashboard.
3. **Selective Content Detection Accuracy (using Diagnostics Mode)**:
   - Open **Instagram**: verify Home feed, Stories viewer, Direct Messages, and Profile stay unblocked; open Reels tab and a Reel from DMs and confirm overlay appears. If Instagram updates resource IDs, capture matched signals in **Diagnostics** (`Package`, `Event`, `Signals`, `Confidence`).
   - Open **YouTube**: verify Home feed (including Shorts shelf), Search, Subscriptions, and standard video playback stay unblocked; open Shorts tab / Shorts video and confirm overlay appears.
   - Open **Facebook**: verify Feed, Groups, and Marketplace stay unblocked; open Reels and verify detection behavior (see limitations below).
4. **Full-App Blocking, Schedules & Reboot**:
   - Toggle full-app blocking and a daily limit on a test app; confirm priority over selective rules.
   - Configure a near-term schedule and an overnight schedule; reboot device (`adb reboot`) and verify schedules remain persisted and alarms reschedule via `BootReceiver`.
5. **Strict Mode, PIN & Usage Reports**:
   - Create a PIN, save the 8-character recovery key, enable Strict Mode, verify settings lock, test 3 wrong PINs for lockout delay, and test emergency recovery.
   - Compare Reports screen foreground times against Android **Digital Wellbeing** / `dumpsys usagestats`.
6. **Battery & Performance**:
   - Monitor battery usage over 24 hours with AccessibilityService active; confirm no excessive wakelocks or UI jank.

---

## 5. Known Compatibility Limitations

- **Unverified against live Play Store app binaries in this session**: Selective adapters for Instagram, YouTube, and Facebook have been verified against realistic node trees in JVM unit tests, **not yet on live physical devices** against the latest Play Store releases of those apps.
- **Third-party app UI & obfuscation changes**: Instagram, YouTube, and especially Facebook (Litho/React Native views) may rename resource IDs or omit accessibility labels across app updates. ScrollGuard fails safely rather than blocking indiscriminately and exposes **Custom Rules** + **Developer Diagnostics** for inspection/tuning.
- **OEM background restrictions**: Xiaomi MIUI/HyperOS, Samsung One UI, Oppo/ColorOS, and Vivo FuntouchOS may kill AccessibilityServices aggressively unless ScrollGuard is excluded from battery optimization.
- **Android OS boundaries**: Overlays and Strict Mode are self-control mechanisms and cannot block Android System Settings, permission revocation, or app uninstallation.
