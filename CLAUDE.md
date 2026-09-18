# Thor Pathfinder — project notes

Open-source (GPL-3.0) Android app for the AYN Thor that replaces Thor Wayfinder:
swaps apps between the two screens and puts shortcuts on the Thor's buttons.
Kotlin + Jetpack Compose, package `com.thorpathfinder.app`, minSdk 33
(the Thor ships Android 13). README.md is the user-facing description.

Machine-specific notes (toolchain paths, the test Thor's serial, git
identity) live in `CLAUDE.local.md`, which is gitignored.

## Status (2026-09-18)

- v0.2.0 (versionCode 5), the first public release. 33 unit tests pass.
  Release APK is about 1.7 MB (R8 on). Bump `versionCode` for every APK
  handed over.
- **Tested on an AYN Thor (firmware 1.0.0.377):** the setup wizard, hold
  Back to swap (two apps; a lone app in both directions), double Back for
  recents, double Select for mouse mode, the focus fix, and the
  YouTube/Discord sequence (a lone app's vacated screen goes home, with no
  flash).
- **Not yet verified on hardware:** "Open an app", shortcuts on Home and the
  AYN button in daily use, the covered-app fix-up (`moveTaskToFront`), the
  media-first swap order with a real video (logic unit-tested), and the
  "Restricted setting" flow for sideloaded installs (adb installs skip it).
- **Signing.** Releases are signed with the project key (certificate SHA-256
  `2706e85ba69b3f77e37227b4c0a0f99311fc73434d7264216f7bf32cbe439897`,
  CN=Thor Pathfinder). Gradle reads it from `keystore.properties` in the
  project root (gitignored) or from `PATHFINDER_KEYSTORE*` environment
  variables; debug builds use it too, so either installs over the other.
  Without it, both fall back to the local debug key. Never commit, print or
  share the key or its password.
- **Releasing.** Bump `versionCode`/`versionName`, run the tests, build
  `assembleRelease`, copy the APK to `release/Thor-Pathfinder-X.Y.Z.apk`
  (gitignored), check it with `apksigner verify --print-certs`, commit and
  push, then `gh release create vX.Y.Z <apk>` with notes that list the APK's
  SHA-256 and the certificate fingerprint.
- **Clean-room.** Written from scratch. Never copy Thor Wayfinder's code, text
  or branding: it is CC BY-NC-ND, and its README forbids copying, modifying or
  derivatives without written permission. Facts about AYN's firmware are fine.
- **Licenses.** Anything bundled into the APK needs its license in
  `app/src/main/assets/licenses` (shown under About → Open-source licenses)
  and in THIRD_PARTY_NOTICES.md. Shizuku's app terms forbid using the name
  "Shizuku" for an app, its package ID or icon, or declaring its
  `moe.shizuku.manager.permission.*` permissions (using them, as the API
  library does, is how clients work).
- Git: `main`, published at https://github.com/KaitonGxx/thor-pathfinder.
  Commit and push only when asked.

## Layout

```
app/src/main/kotlin/com/thorpathfinder/app/
  Buttons.kt            PhysicalButton (scan codes), Gesture, ButtonAction
  GestureEngine.kt      press / double-press / hold state machine (pure, tested)
  Shortcuts.kt          SharedPreferences store + defaults; implements GestureConfig
  PathfinderService.kt  accessibility service: key events -> engine -> actions
  ScreenSwap.kt         parse `am stack list`, plan, script, covered-app fix-up
  MouseMode.kt          mouse mode toggle, reverse scrolling (AYN config edit)
  Shell.kt              Shizuku process runner (newProcess via reflection)
  Device.kt             Thor + firmware gate (min 1.0.0.377)
  SystemState.kt        what setup checks (device, service, Shizuku, Wayfinder)
  ui/Setup.kt           step-by-step wizard with gated Next
  ui/SettingsScreen.kt  settings; ObservedShortcuts keeps focus on edits
  ui/Components.kt      theme, focusOutline, SwitchRow, ValueRow, ChoiceDialog
  ui/AppPicker.kt       launcher apps for "Open an app"
  ui/Licenses.kt        About → Open-source licenses (texts in assets/licenses)
  ui/Preview.kt         debug builds only: fake states for screenshots
app/src/test/           JVM tests; resources are real captures from the Thor
```

## Facts learned on the Thor (firmware 1.0.0.377)

- **Buttons.** Controller is "Odin Controller" (`/dev/input/event9`):
  KEY_BACK 158 -> BACK, KEY_HOME 102 -> HOME, plus Select, Start, L3 and R3.
  The AYN button is KEY_F24 (194) on `gpio-keys`, mapped to HOME WAKE. Keys
  Android injects (global actions, `input`, on-screen gestures) have scan code
  0, so system buttons are matched on scan code too. No back paddles; C, Z,
  MODE and APP_SWITCH are in the key layout but not on the device. In AYN's
  mouse mode, B sends BACK through a virtual keyboard.
- **Displays.** 0 = top (1920x1080 landscape), 4 = bottom (1240x1080). The
  other screen is the lowest non-default display ID.
- **`am stack list`**: `RootTask id=N ... displayId=D`, a configuration line,
  then `  taskId=T: pkg/cls ... visible=... topActivity=ComponentInfo{...}`,
  per display topmost first. `am display move-stack <root> <display>` moves a
  root task on top without relaunching it (~15 ms; `am` is a script around
  `cmd activity`). There is no shell "bring task to front": use
  `ActivityManager.moveTaskToFront` (REORDER_TASKS, a normal permission).
- **Cocoon** (the user's launcher) closes its home activities (MainActivity on
  0, ExternalDisplayActivity on 4) while an app covers them, and relaunches
  ExternalDisplayActivity itself when display 4 changes, which can land on top
  of an app that just arrived. Hence the 300 ms `covered()` fix-up.
- **A lone app's move** presses Home on the screen it left, in the same shell
  command (`input -d D keyevent KEYCODE_HOME`). Without it Android reveals the
  next thing down: Launcher3's Recents, or an app the user had sent home.
  Never press Home before the move: it covers the moving app.
- **Swap stutter.** A swap is two moves; the second app is covered for ~15 ms,
  and a video drops its surface. A media-playing app (`dumpsys media_session`,
  state 3 or 6) moves first; otherwise the top app. An atomic swap would need
  a WindowContainerTransaction.
- **Home exclusions** come from HOME and SECONDARY_HOME activities; whole
  packages only when priority >= 0. Android Settings registers FallbackHome at
  -1000, and excluding its package would make Settings unswappable. Launchers
  on the user's Thor: launcher3, odinlauncher, cocoonshell and
  `xyz.blacksheep.mjolnir`.
- **Mouse mode.** Switch: system setting `global_gamepad_to_mouse_mode`
  (com.odin.mapping logs "Config changed: Empty->Mouse"). Config:
  `/sdcard/AYN_Thor_Settings/global_mouse_mode_config.json` (folder from
  `ro.product.vendor.model`, spaces to underscores). com.odin.settings is a
  persistent app that loads it once at start-up, and `am force-stop` does not
  restart it, so a change needs a reboot (`svc power reboot`; shell holds
  REBOOT). The right stick is RIGHT_JOYSTICK type 3003
  (ST_MOUSE_MOVE_TOUCHSCREEN, a virtual finger), handled in native
  `librsinput.so`: it copies `reverseJoystick` (+0x30) into the stick state and
  multiplies both swipe axes by -1. `reverseJoystick1` is ignored for this type.
- **Firmware gate.** `Build.DISPLAY` is `Thor_V1.0.0.377_20260206_165408_user`;
  `Device.check` parses the version (newer accepted) and falls back to the
  build time when the name doesn't parse. The update screen is the non-public
  intent `android.settings.SYSTEM_UPDATE_SETTINGS` (com.odin.fota).
- **Compose focus.** Since Compose 1.7, clickables take focus only in keyboard
  input mode, so focus requests are retried when
  `LocalInputModeManager.inputMode` changes. Never rebuild UI to refresh
  values (`key(revision)`): it destroys the focused row and focus jumps away.

## Testing on the device

- A real Back hold, through the same path as a thumb (shell is in group input):
  `adb shell "sendevent /dev/input/event9 1 158 1; sendevent /dev/input/event9 0 0 0; sleep 1.2; sendevent /dev/input/event9 1 158 0; sendevent /dev/input/event9 0 0 0"`
  (find the node with `getevent -pl`, name "Odin Controller").
- Shizuku's wireless debugging makes the Thor show up twice in
  `adb devices`; pick the USB serial with `adb -s`. In Git Bash with
  `MSYS_NO_PATHCONV=1`, give adb.exe Windows paths (`C:/...`) for local files.
  Quote `$` in component names on the device side (YouTube is
  `.app.honeycomb.Shell$HomeActivity`).
- Logs: `adb logcat -s PathfinderSwap PathfinderShell PathfinderMouse`.
- Screens a set-up Thor never shows (debug builds only; `ui/Preview.kt`):
  install `app-debug.apk` (same key, installs over the release build), then
  `adb shell am start -n com.thorpathfinder.app/.ui.MainActivity -f 0x10008000 --es step SHIZUKU --es shizuku NOT_INSTALLED`.
  Other extras: `--ez service false`, `--ez wayfinder true`,
  `--es firmware 1.0.0.300`. Reinstall the release APK afterwards.

## Building

JDK 17 and the Android SDK (platform 35); `local.properties` (gitignored)
points at the SDK. Gradle 8.13, AGP 8.7.2, Kotlin 2.1.0, Compose BOM
2024.10.01, Shizuku 13.1.5.

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease --console=plain
```

Escape sequences written through Python or heredocs can land as real line
breaks or control bytes. Write Kotlin escapes with the Edit/Write tools and
check sources for bytes below 0x20 (other than tab/newline) and 0x7F.

## Possible next steps

- A GitHub Actions build (it would need the key as repository secrets).
- Per-app exclusions (for example, leave Select alone in emulators).
- An atomic swap (WindowContainerTransaction) to remove the brief cover.
- Button combinations; opening an app on a chosen screen; translations.
