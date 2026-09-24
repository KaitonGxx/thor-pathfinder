# Thor Pathfinder — project notes

Open-source (GPL-3.0) Android app for the AYN Thor that replaces Thor Wayfinder:
swaps apps between the two screens and puts shortcuts on the Thor's buttons.
Kotlin + Jetpack Compose, package `com.thorpathfinder.app`, minSdk 33
(the Thor ships Android 13). README.md is the user-facing description.

Machine-specific notes (toolchain paths, the test Thor's serial, git
identity) live in `CLAUDE.local.md`, which is gitignored.

## Status (2026-09-24)

- v0.8.3 (versionCode 18), released 2026-09-24. Also in it, described in
  their own sections below: the profile map keeps the buttons and its
  Dismiss shows an A, What's New on the update card, and the neutral update
  button when Check on open is off. `fix-accessibility.sh` is no longer a
  release asset. The label beside the profile
  oval reads "Active Profile:", and screens showing profiles follow switches
  made elsewhere (a Profile switcher shortcut, or its Ask question) instead of
  keeping what they read when built. In the shortcut list, the actions that
  ask more before saving (Open an app, Home, Close app(s), Profile switcher)
  carry the same arrow as a row that opens a page, and come last, together,
  after a small gap (`ChoiceDialog` groups by `leadsOn`; in a grid the group
  starts its own row). The shortcut list is a 3-column grid read across by
  default (`ChoiceLayout.WIDE`): both screens are landscape and only about
  468 dp tall (top 1920x1080, bottom 1240x1080, both 369 dpi), so 17 choices
  in one column always scrolled. Two icons on its title row switch between
  list and wide (the grid icon is drawn by hand, since Material's core icons
  have none); the pick is kept in the `ui` prefs (`shortcutListColumns`). A
  dialog that can be a grid drops Android's default dialog width and caps
  itself (760 dp wide, 560 dp as a list), so switching never changes how
  Android sizes the window.
  **AYN's auto launch list** (issue #1, see "Facts learned on the Thor"):
  `AutoLaunchList` reads `boot_auto_launch_list` directly (no Shizuku
  needed to read it); `SystemState.autoLaunchBlocked` puts it in the
  attention card and the setup step, and the Diagnostics report prints the
  list. `ServiceSwitch.turnOn` (the setup button, "Fix it" in this case)
  takes Pathfinder off the list through Shizuku, keeping the other entries
  and deleting the setting when nobody is left, before its off-and-on.
  `watchdog.sh` and `tools/fix-accessibility.sh` do the same, and the
  watchdog then switches the service off and on because Android never
  retries on its own (reproduced on the test Thor: listed, a toggle leaves
  it enabled and unbound with the stale `ServiceRecord`; unlisting alone
  changes nothing; unlisting plus an off-and-on binds at once, no restart).
  The watchdog leaves the list alone while `com.odin.settings` is in front.
  Watchdog resume moved from `PathfinderService` to `PathfinderApp` (new
  Application class), because a listed Pathfinder's service never starts,
  while Shizuku still starts the process through `ShizukuProvider`.
- v0.8.2 (versionCode 17), released 2026-09-24: the watchdog starts again by
  itself once Shizuku is running, at boot or when started by hand later, if it
  was left switched on. `Watchdog.alive()` ran `pgrep` inside `sh -c`, whose
  own command line carries the pattern, so it always answered yes: the page
  never showed "On, but not running", the report always said running, and a
  resume would never have fired. `pgrep` and `pkill` now run directly (through
  `sh -c`, `pkill` also killed its own shell). The mouse mode shortcut no
  longer shows its own "on/off" message, since the Thor shows one.
- v0.8.1 (versionCode 16), released 2026-09-23: everything about the
  accessibility service going quiet. `SystemState.serviceOn` now means Android
  has actually *started* the service (from
  `AccessibilityManager.getEnabledAccessibilityServiceList`, which AMS builds
  from its bound services) rather than "the switch is on"; `serviceListed` is
  the switch, and `serviceStuck` is the two disagreeing, which used to show as
  everything being fine while no shortcut worked. `ServiceLog` records every
  time the switch changes and the service starts or stops, and pulls the
  system log at the next start when a stop went unexplained (ActivityManager
  names a force-stop's reason, `installPackageLI` for an update or `from pid
  N` for another app; nothing anywhere names who wrote the setting). A
  `Diagnostics` page gathers the lot for a bug report. Setup offers a
  Shizuku-backed off-and-on, and `tools/fix-accessibility.sh` does the same
  from the Thor's own "Run script as Root" for when Shizuku isn't up (attached
  to the 0.8.0 to 0.8.2 releases; from 0.8.3 on it is in the repo only, not
  a release asset). An
  opt-in `Watchdog` runs `assets/watchdog.sh` through Shizuku with `setsid`,
  so it belongs to Shizuku and survives Pathfinder being killed; it checks
  every 5 seconds by default (12 ms a check, measured; 22 ms since 0.8.3
  reads AYN's auto launch list too), only ever adds
  Pathfinder's own component, and leaves the switch alone while Android's
  settings are open so a deliberate switch-off stands.
- v0.8.0 (versionCode 15), released 2026-09-23: **profiles**, several named
  sets of shortcuts with one in use at a time, chosen from the oval under the
  title, from a Profile switcher shortcut (cycle, enable one by name, or ask)
  or from Manage profiles, where they are renamed, added, removed and where
  any of them can be made the main one. Switching draws the Thor with the new
  profile's shortcuts beside it until dismissed. Also: "Home" reaches a screen
  by starting its home screen rather than pressing the Home key, which AYN's
  "Enable Home and Back focus lock" re-aimed at the focused screen; a button
  whose plain press Pathfinder could not give back is no longer intercepted,
  so the AYN button keeps its own menu with Shizuku down; Close app(s) gains
  "Close background apps"; and setup and the README call the Thor Lite tested.
- v0.7.0 (versionCode 14), released 2026-09-22: the "Close all apps" action
  is now "Close app(s)", closing all apps, the focused one, the top screen's,
  the bottom screen's, or apps picked by name (counting only those that were
  open); all share the keep-running list. The settings page is "Close app(s)"
  too.
- v0.6.1 (versionCode 13), released 2026-09-21: button cards close instantly,
  so a D-pad press right after closing one no longer throws focus to the top.
- v0.6.0 (versionCode 12), released 2026-09-21: Back and Home replay the
  real key when left on Normal (the RetroArch fix), the Thor Lite passes the
  device check, "Open an app" takes one app (top, bottom or ask) or two (one
  per screen), "Home" names its screens (top, bottom or both), the button
  cards fold away (closed by default, with a summary), About moved to the
  bottom, the title's "Pathfinder" is in the theme's accent, and Pulse joined
  the recommended keep-running apps.
- v0.5.1 (versionCode 11), released 2026-09-20: the update check runs on
  every return to the settings, so coming back from Home refreshes the button
  instead of showing what the last check found.
- v0.5.0 (versionCode 10), released 2026-09-20: updates look after themselves
  (check on open, the button's wording and mark, the yellow notice card, and
  an opt-in silent install), the cog's Settings menu, the keep-running list
  for Close all apps, and a screen to open an app on.
- v0.4.0 (versionCode 9), released 2026-09-18: adds the Screen record action
  (labelled "(Testing)"), the AYN button's own menu when a gesture is left on
  Normal, Check For Updates, and controller scrolling that reaches the true
  top and bottom of a page. 0.3.0 added Close all apps; 0.2.0 was the first
  public release.
  Release APK is about 1.7 MB (R8 on). Bump `versionCode` for every APK
  handed over.
- What 0.5.0 changed: the cog next to the update button opens a full-screen
  Settings menu (`ui/MoreSettings.kt`) with a page each for Close all apps
  (the keep-running list, OdinTools and ClusterTune offered first), Mouse mode
  and Timing, plus Run setup again, which asks on a page of its own before it
  starts (the cautious button holds focus). Mouse mode, Timing and Setup moved
  off the main screen, which now holds the header, what needs attention, the
  update notice, About and the seven button cards. Also: "Open an app" asks
  which screen to open it on, and updates check themselves (see below). Each line in the attention card can carry a
  reason: the accessibility service says Android switches it off on every app
  update, and Shizuku's line depends on its status (not installed, stopped by
  a restart, or waiting to allow Pathfinder).
- **Tested on an AYN Thor (firmware 1.0.0.377):** the setup wizard, hold
  Back to swap (two apps; a lone app in both directions), double Back for
  recents, double Select for mouse mode, the focus fix, the YouTube/Discord
  sequence (a lone app's vacated screen goes home, with no flash), Close all
  apps (Recents left empty, apps stopped, screens home, service alive), the
  overlay messages, Screen record from a tile on either Quick Settings page,
  the AYN button with a double-press shortcut (press opens AYN's menu, long
  press its panel), Check For Updates and its release-page link, and
  controller scrolling to both ends of the settings and the shortcut picker.
- **Not yet verified on hardware:** "Open an app", shortcuts on Home in daily
  use, the covered-app fix-up (`moveTaskToFront`), the
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

## Layout

```
app/src/main/kotlin/com/thorpathfinder/app/
  Buttons.kt            PhysicalButton (scan codes), Gesture, ButtonAction
  GestureEngine.kt      press / double-press / hold state machine (pure, tested)
  Shortcuts.kt          SharedPreferences store + defaults; implements GestureConfig
  PathfinderService.kt  accessibility service: key events -> engine -> actions
  ScreenSwap.kt         parse `am stack list`, plan, script, covered-app fix-up
  RecentTasks.kt        Close all apps: parse `dumpsys activity recents`, `am stack remove`
  KeyReplay.kt          replay a real key with `sendevent` (AYN button; Back and Home too)
  Launcher.kt           open apps and home screens on a chosen screen, Shizuku or not
  ScreenRecord.kt       Screen record: expand QS, `uiautomator dump`, find the tile, tap
  MouseMode.kt          mouse mode toggle, reverse scrolling (AYN config edit)
  UpdateCheck.kt        GitHub's latest release: version compare, page and APK links
  Updates.kt            update preferences, and the silent install through Shizuku
  Shell.kt              Shizuku process runner (newProcess via reflection)
  Overlay.kt            toast-like message as an accessibility overlay window
  Device.kt             Thor + firmware gate (min 1.0.0.377)
  SystemState.kt        what setup checks (device, service, Shizuku, Wayfinder)
  ui/Setup.kt           step-by-step wizard with gated Next
  ui/SettingsScreen.kt  settings; ObservedShortcuts keeps focus on edits
  ui/Components.kt      theme, focusOutline, SwitchRow, ValueRow, ChoiceDialog,
                        PageScaffold (a page with a back arrow), NavRow, ListHeading
  ui/EdgeScroll.kt      ScrollingColumn: controller focus reaches the true ends
  ui/AppPicker.kt       launcher apps for "Open an app" (loadApps is shared)
  ui/MoreSettings.kt    the cog's menu: Mouse mode, Timing, Update settings, Setup
  ui/ScreenChoiceActivity.kt  an "Ask" shortcut's top-or-bottom question
  ui/Updates.kt         update state for the screen: button wording, notice card
  ui/KeepRunning.kt     the cog's Close all apps page: what not to force-stop
  ui/Licenses.kt        About → Open-source licenses (texts in assets/licenses)
  ui/Preview.kt         debug builds only: fake states for screenshots
app/src/test/           JVM tests; resources are real captures from the Thor
```

## Facts learned on the Thor (firmware 1.0.0.377)

- **AYN's auto-launch list blocks the accessibility bind** (read from this
  firmware's `services.jar` and `OdinSettings.apk`; found through issue #1).
  `ActiveServices.bindServiceLocked` and `startServiceLocked` call
  `com.android.server.StartupManager.isBlockServiceLaunch(pkg, …)` right after
  `retrieveServiceLocked` and return 0 when it says yes, with no log line.
  `StartupManager` is built fresh on every call and reads
  `Settings.System boot_auto_launch_list` (comma-separated packages); it
  blocks a listed package unless one of its processes is at importance 100
  (foreground) or 125 (foreground service). `BroadcastQueue` does the same
  for broadcasts through `isBlockBroadcastLaunch`, and always blocks
  BOOT_COMPLETED for listed packages. So a listed Pathfinder is enabled but
  never bound: at boot, and at a toggle in Android's Settings, its process
  is not in front. Symptoms: `ServiceRecord` with `app=null`, no Bindings,
  `lastActivity` equal to `createTime`; `Binding` and `Crashed` empty. The
  list is written only by OdinSettings' "APP Auto Launch Manage" page
  (on screen: Settings → Thor settings → Advanced Settings; the labels are
  OdinSettings' `app_name_for_thor` and `title_advanced`)
  (`AppAutoLaunchManagerActivity`, opened from
  `preference.advanced.AppAutoLaunchManagerPreference`): switching an app
  on adds it, off removes it. Its own tip says selecting an app turns off
  its startup services. GameAssistant and OdinLauncher only carry the key
  in a shared constants class and never read it. Unset (`null`) on the test
  Thor.
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
- **Cocoon** (a third-party launcher) closes its home activities (MainActivity on
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
- **Close all apps.** Recents' Clear all = `removeAllVisibleRecentTasks`;
  the shell (REMOVE_TASKS) gets the same per task with `am stack remove <id>`,
  which also works for tasks only in Recents (no longer on a screen) and ends
  the process. `dumpsys activity recents` lists `* Recent #n: Task{.. #id
  type=standard|home|recents ..}` blocks with `mActivityComponent=pkg/.Cls`
  (abbreviated), `intent={.. flg=0x.. ..}` (0x00800000 = exclude from
  Recents; Cocoon's secondary-home tasks carry it) and `inRecents=`. The
  Thor's own Clear all (its recents app, "Killing ...: stop <pkg> due to from
  pid <recents>" in logcat) removes the tasks, force-stops each package, and
  sends both screens home; it removes Pathfinder's task too, but the service
  process survives. Close all matches that: it removes Pathfinder's own task
  (Recents ends up empty) but never force-stops its own package, since the
  accessibility service runs in it. Removal alone leaves processes cached, so
  every other closed app gets `am force-stop`. Apps on the user's keep-running
  list (`Shortcuts.keepRunning`, a StringSet of package names, chosen in
  `ui/KeepRunning.kt`) are left out of the force-stop only: their task is
  removed like any other, so Recents still ends up empty, but their background
  work survives. SharedPreferences hands out its own Set instance, so the
  getter copies it. The action keeps its enum name CLOSE_ALL, because stored
  mappings use it, though it's labelled "Close app(s)"; `.close` (ALL or
  FOCUSED, absent means ALL) picks the mode. **Close focused app** reads the
  one `ResumedActivity:` line of `dumpsys activity activities` (per-display
  `topResumedActivity` lines are ignored): on the Thor it follows the screen
  last touched, and `mTopFocusedDisplayId` in `dumpsys window` agrees. Its task
  goes with `am stack remove`, then `am force-stop` unless the app is on the
  keep-running list or is Pathfinder; home screens and System UI are left.
  **Close top / bottom app** use `ScreenSwap.visibleApp` on `am stack list`
  for that display (the swap's own rule: top visible task, homes excluded)
  and remove its root task. **Close specific apps** stores a StringSet
  (`.closeApps`); it removes every closable Recents task of those packages,
  then force-stops each one even with no task (keep-running and Pathfinder
  excepted). `RecentTasks.targets` never lets a home package or System UI
  through, and only names matching the package pattern reach the script. OdinTools (`de.langerhans.odintools`), ClusterTune
  (`com.aure.clustertune`) and Pulse (`com.kei.pulse`) head that page under
  "Highly recommended", when installed: they work from the background, which a
  force-stop ends. They are
  named in the manifest's `queries`, or Android would hide them from
  `getApplicationInfo`.
- **AYN button.** AYN's PhoneWindowManager.interceptKeyBeforeDispatching
  (when config bool 17891754 is true) consumes scan code 194 and sends the
  broadcast `action.tcc.button.key.event` with `key_action_down` and
  `key_isLongPress`: down (true, false), long press on key repeat after
  ~400 ms (true, true), up (false, false). Receivers: com.odin.dualscreen.
  assistant (press: its menu, window on display 4), System UI and system
  (long press: `primaryScreenTopLayout` on the top screen). It never acts as
  Home. The broadcast is protected (shell: "Permission Denial: not allowed
  to send broadcast"), so a Normal press is replayed as the real key:
  `sendevent` on the `gpio-keys` device (path from /proc/bus/input/devices,
  readable by shell; event1 on the test Thor) via Shizuku, 50 ms press or
  900 ms for a long press, and `GestureEngine.letThrough` passes that one
  press untouched. A long press with no hold shortcut fires HOLD/NORMAL.
- **Screen record.** System UI's recorder panel (window "Screen Recorder",
  TYPE_STATUS_BAR_SUB_PANEL, owner com.android.systemui; on this ROM it also
  picks top/bottom screen) opens only from the `screenrecord` QS tile.
  Dead ends: `RecordingService` (actions `com.android.systemui.screenrecord.
  START/STOP/SHARE/UPDATE_STATE`) is not exported, so the shell may not
  start it; `ScreenRecordDialog` is a SystemUIDialog, not an activity;
  `cmd statusbar click-tile` only reaches TileService tiles; no broadcast
  opens it; AYN's ST_OPERATION_RECORD_SCREEN uses gameassistant's own
  VideoRecordService (not exported). The shell `screenrecord` tool (v1.3)
  records display 0 only, no audio, no menu. Working route: `cmd statusbar
  expand-settings`, `uiautomator dump` (about 2 s; needs a file, /dev/tty
  has no tty under Shizuku), tiles are `tile_label` nodes inside
  `qs_pager` (8 per page in landscape), page from `footer_page_indicator`
  ("Page 1 of 2"), swipe the pager to change page, `input -d 0 tap`. QS
  keeps the last page shown. Label from System UI's string
  `quick_settings_screen_record_label` via getResourcesForApplication.
- **Updates** are the app's only network use and the reason for the INTERNET
  permission. `UpdateCheck.kt` GETs
  `api.github.com/repos/KaitonGxx/thor-pathfinder/releases/latest` with
  `User-Agent: Thor-Pathfinder` (Android's default names the device model),
  and reads `tag_name` (compared with versionName number by number),
  `html_url` and the first `assets[].browser_download_url` that starts with
  the repo's `/releases/download/` and ends in `.apk`. Unsigned API calls are
  limited to 60 an hour per IP; a 403 or 429 gets its own message.
  `ui/Updates.kt` holds the screen's state: a check runs on every ON_RESUME
  of the settings (`LifecycleEventEffect`, not `LaunchedEffect(Unit)`, or
  returning from Home would never re-check), unless one ran in the last 15
  minutes. The button reads "Update Available", "Up to date" or "Check For
  Updates", and the last answer is kept in the `updates` preferences so the
  button says something before the new check lands. With Check on open off
  (since 0.8.3), a remembered "Up to date" is not repeated, since nothing
  keeps it fresh: the button reads "Check For Updates" with `NeutralDot` (a
  white dot with a dash; outlined in the light theme). A remembered release
  newer than the installed version still shows, since that stays true.
  A fresh check from the button shows its real answer. A new version raises a yellow card
  (`WarningContainer`) with Update now, What's New (since 0.8.3: the release
  page, which holds that version's notes; without Update now the card shows
  Open release page instead, which is the same page), Dismiss (until the app
  is reopened) and Don't show again (kept per version in `hiddenVersion`).
  The Check For Updates dialog's button reads What's New when there is an
  update and Open release page otherwise.
- **Installing an update** (`Updates.kt`, off by default) goes through
  Shizuku: `pm install-create -r -S <size>`, then `pm install-write -S <size>
  <session> base -` with the APK on stdin (`Shell.pipe`, so no file has to be
  readable by the shell user), then `pm install-commit`. Nothing is confirmed
  by the user, and the commit usually never returns because Android stops the
  process as it swaps the app over. Before that, the download must come from
  the repo's own `/releases/download/`, and the APK must carry Pathfinder's
  package name, the same signing certificate as the installed copy
  (`GET_SIGNING_CERTIFICATES`, SHA-256 compared) and a higher versionCode.
  Without Shizuku there is no silent route, so the card offers the release
  page instead.
- **Messages.** `Toast` from the service never shows: Android 13 suppresses
  background toasts from an app without the notification permission
  ("Suppressing toast from package ... by user request" in logcat). The
  service draws its messages as a TYPE_ACCESSIBILITY_OVERLAY window on the
  default display instead (`Overlay.kt`), which needs no permission.
- **Home exclusions** come from HOME and SECONDARY_HOME activities; whole
  packages only when priority >= 0. Android Settings registers FallbackHome at
  -1000, and excluding its package would make Settings unswappable. Launchers
  on the test Thor: launcher3, odinlauncher, cocoonshell and
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
  build time when the name doesn't parse. Any other "AYN Thor ..." model (the
  Thor Lite reports "AYN Thor Lite") passes without the gate, since its
  firmware has its own numbering. A Thor Lite owner reported 0.6.1 tested and
  working, so `Device.tested` counts it with the Thor and setup shows no
  caveat for it; any other variant still gets "please report it". The update screen is the non-public
  intent `android.settings.SYSTEM_UPDATE_SETTINGS` (com.odin.fota).
- **Back and Home replay.** Android's global Back and Home arrive with no
  input device and scan code 0; RetroArch binds to the device and scan code,
  so it ignored Pathfinder's Back. With Shizuku, a Normal press is replayed on
  "Odin Controller" (Back 158, Home 102; event9 on the test Thor, while
  gpio-keys moved between event1 and event3 across boots, hence lookup by
  name, cached, and dropped when a press fails). Verified with `getevent`:
  a kernel-level press is followed about 330 ms later (the double-press gap)
  by exactly one replayed press. Without Shizuku, or if the replay fails,
  the global action is used.
- **Home screens on a chosen display, no Shizuku** (`Launcher.homeIntent`).
  Tested on the Thor: CATEGORY_HOME with `setLaunchDisplayId(4)` is accepted
  but ignored (the main home belongs to display 0). CATEGORY_SECONDARY_HOME
  lands on display 4 but opens Android's chooser, since Cocoon, Launcher3 and
  others all claim it. Starting one component works: the candidate from the
  default home's package (`resolveActivity` on CATEGORY_HOME gives Cocoon),
  else Launcher3's, else the first. This is now the way "Home" reaches every
  screen; the key press below is only a last resort for a screen with no home
  activity to start.
- **AYN's two focus settings, and why Home stopped using the Home key.** The
  Thor has "Focus Mode" (DualScreenAssistant) with Auto-lock / Top screen /
  Bottom screen, stored in `Settings.System.screen_focus_lock` as 0 / 1 / 2,
  and a separate switch in OdinSettings called "Enable Home and Back focus
  lock", stored as `enable_system_key_focus_lock`. The Focus Mode menu never
  writes the second one. With both on, an injected `input -d 4 keyevent
  KEYCODE_HOME` is re-aimed at the locked screen: the **top** screen goes
  home and the bottom one is untouched, so "Home (bottom)" and "Home (both)"
  both send the wrong screen home. Measured on firmware 1.0.0.377 with the
  lock off (all three modes, before and after a reboot, Shizuku up and down:
  every combination correct) and then with it on (top correct, bottom and
  both wrong). A launch display is addressed directly and routes no key, so
  it obeys the target whatever the setting says. Reported by a user as
  "only home (top) is working" in top focus; the trigger is the OdinSettings
  switch, not Focus Mode, which only chooses the screen the lock points at.
- **The button map** (`ButtonMap` and `ThorMapView`) is Pathfinder's own
  drawing, not AYN artwork: a lid, a control deck, the staggered sticks, a
  D-pad, four face buttons and the five small ones. Button positions are
  fractions of the picture in `ButtonMap.SPOTS`, taken from the device itself
  rather than from AYN's key test screen, which lays the buttons out in a row
  of its own making. Each mapped button gets a box on the nearer side, boxes
  are stacked by the button's height down the device, and a line leaves each
  box level with its own button so two on a side don't cross; the AYN button
  sits dead centre, so its box goes on the left where its line doesn't have to
  cross Back's. The overlay is a `TYPE_ACCESSIBILITY_OVERLAY` like the toast,
  so it needs no permission and draws over games, but unlike the toast it
  takes touches (it has a Dismiss button) and stays up until dismissed. A tap
  anywhere closes it, and so does any key press after a 900 ms grace period,
  which stops the release of the hold that opened it from closing it at once
  and means a busy touchscreen can never strand the user behind it.
- **A real scroll wheel for mouse mode is shelved** (local branch
  `shelved/mouse-wheel`). A true wheel works on the Thor: Android's own
  `/system/bin/uinput` tool, run as the shell user, makes a virtual mouse
  whose `REL_WHEEL` scrolls lists as `ACTION_SCROLL`, and the driver runs from
  the APK through `app_process` (R8 needs a -keep rule for it). What stops it
  is the stick data. "Odin Controller" (`event9`) is a virtual device fed by
  `com.odin.mapping`; in AYN's mouse mode that service stops sending the sticks
  to it and turns the right stick into synthetic touches written into the
  touchscreen (`event6`), so nothing readable carries the stick. Outside their
  mouse mode the sticks reach apps through that device, which an app cannot
  stop. There is no EVIOCGRAB in play (a second reader gets injected events
  either way). AYN reads its mouse mode config only at start-up and its
  service runs as system, so the shell cannot make it reload; setting the
  right stick's `sensitivity` to 0 turned the swipe into stationary taps, not
  nothing. AYN's behaviour list has no wheel and no right click (their English
  "mouse wheel" description is wrong; their UI says the right stick simulates
  a finger turning pages). Untried: removing `RIGHT_JOYSTICK` from their config
  to see if the stick is then passed through, or grabbing the controller in
  native code, which would also take Back, Home and the AYN button.
- **The watchdog comes back after a restart.** Its marker file in
  /data/local/tmp survives a reboot but the script doesn't, since Shizuku
  starts afresh. `PathfinderApp` (the Application) registers a sticky Shizuku
  binder-received listener, so whenever Shizuku becomes available (already up
  at boot, or started by hand later) `Watchdog.resume` starts the script again
  if the marker says it was switched on and no copy is running. Switched off,
  the marker is gone and nothing restarts. It lived in `PathfinderService` in
  0.8.2, which never runs when AYN's auto launch list blocks it; Shizuku
  starts the process through its provider either way.
- **Profile screens watch the profiles file** (`rememberProfileUi`). A
  `ProfileUi` used to read the profiles once, when its screen was built, so a
  switch made by a shortcut or the Ask question while the app sat behind
  another one (or on the other screen) left the oval and the shortcut cards on
  the old profile. It now registers a SharedPreferences listener on `profiles`
  for as long as the screen is composed; the service and the app share one
  process, so the listener hears the service's writes. The listener lives in
  the `ProfileUi` because Android holds it weakly.
- **An arrow means more to pick.** `ChoiceDialog`'s `leadsOn` puts
  `NavRow`'s arrow on an option that asks more before saving; in the shortcut
  list that is decided by `nextStep`, the same function that picks the next
  dialog, so the arrow and the flow cannot disagree.
- **Mouse mode shows only the Thor's message.** AYN posts its own toast when
  mouse mode changes, so Pathfinder's shortcut says nothing on success and
  only reports "needs Shizuku".
- **Profiles are one SharedPreferences file each** (`Profiles`). The main
  profile keeps the original `shortcuts` file, so an update needs no
  migration; the others are `shortcuts.<id>`. A separate `profiles` file holds
  the order, the names, which is active, and a `next` counter so that deleting
  the newest profile and making another cannot hand the new one the deleted
  one's file. `Shortcuts` resolves its file on every read, so a switch takes
  effect on the next key event with nothing to reload. Which profile is the
  *main* one is the user's choice (`main` in the profiles file, `ORIGINAL`
  until they move it), and it decides three things: where an "Enable" shortcut
  returns to on a second press, what a deleted active profile falls back to,
  and which profile cannot be deleted. That is deliberately separate from
  `ORIGINAL`, which is only about which file the shortcuts sit in, so
  `ORIGINAL` itself is deletable once another profile is the main one; hence
  `parseIds` never forces it back into the list. `setupDone` lives in the
  profiles file, not in a profile, so deleting one cannot make setup run
  again; it is carried over once from where it used to be kept. Mouse
  mode is not in a profile at all: it is AYN's own system setting and JSON
  config (see `MouseMode`), shared device-wide, so there is nothing to copy or
  switch. Creating a profile offers to carry the keep-running list over.
- **Nothing is swallowed that cannot be given back** (`GestureEngine`'s
  `canRestore`). Intercepting a system button means swallowing its down, and
  a plain press can then only be returned by replaying the real key (Shizuku)
  or by a stand-in global action (Back and Home have one; the AYN button does
  not). So a button with a shortcut on some other gesture is left alone while
  it could not be given back, and the AYN menu keeps working with Shizuku
  down; a gesture mapped on that button simply waits for Shizuku. A press
  mapped to a shortcut of its own never needs giving back, so it is still
  intercepted.
- **The profile map keeps the buttons** (`PathfinderService.keptForMap`,
  since 0.8.3; the one deliberate exception to the rule above). While the map
  is up, a fresh real press (repeat count 0, non-zero scan code, not volume
  or power) closes it and is consumed, and so are that key's repeats and
  release (`closedMap`), so neither a shortcut nor the app underneath sees
  it. A key already down when the map appeared (the hold that switched)
  passes as usual, which is why the old 900 ms grace period is gone. The map
  window stays `FLAG_NOT_FOCUSABLE`: taking focus would make the game lose
  window focus (many pause) and move the top-focused display. Sticks are
  motion events and cannot be held back on Android 13. The Odin Controller
  declares the D-pad both as `BTN_DPAD_*` keys and as `ABS_HAT0X/Y`; only
  the key form can be kept.
- **Accessibility events carry the display.** `AccessibilityEvent.getDisplayId()`
  (from AccessibilityRecord) is filled in on this firmware (0 top, 4 bottom)
  for window-state events, with no window-content capability; window ids come
  back as -1. Worth knowing for any Shizuku-free swap.
- **Compose focus.** Since Compose 1.7, clickables take focus only in keyboard
  input mode, so focus requests are retried when
  `LocalInputModeManager.inputMode` changes. Never rebuild UI to refresh
  values (`key(revision)`): it destroys the focused row and focus jumps away.
  Likewise, `AnimatedVisibility` keeps exiting content focusable until its
  animation ends: a D-pad press during a fold-away landed on a row that then
  vanished, and focus fell back to the top of the page. `CollapsibleCard`
  therefore closes instantly (`ExitTransition.None`) and only animates open.
  Verified on the Thor: Down or Up with no pause after closing lands on the
  next or previous card every time.
- **Controller scrolling.** Compose brings a focused item into view with the
  least scrolling, so focusing the first or last control left the page's
  padding out of sight. `ScrollingColumn` (ui/EdgeScroll.kt) provides a
  `BringIntoViewSpec` (experimental in foundation 1.7) that scrolls to the
  very top or bottom when the item is within 64 dp of that end and still fits
  there, and gives its content the default spec back so dialogs opened inside
  are unaffected. The settings screen and the setup pages use it.
- **List dialogs.** The list gets `weight(1f, fill = false)` inside the
  dialog's Column. Without it, a list taller than the screen (the shortcut
  picker in landscape) squeezed the Cancel button to zero height; the squeezed
  button could still take focus, and Up from the first option jumped to it.

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

`.gitattributes` pins `*.sh` and `gradlew` to LF. The build machine has
`core.autocrlf=true`, so without it a fresh checkout turns `watchdog.sh`
into CRLF, the APK carries it as it is, and the Thor's `sh` reads each
carriage return as part of the command (`x=1\r` does not set `x` to `1`).

## Possible next steps

- A GitHub Actions build (it would need the key as repository secrets).
- Per-app exclusions (for example, leave Select alone in emulators).
- An atomic swap (WindowContainerTransaction) to remove the brief cover.
- Button combinations; opening an app on a chosen screen; translations.
- Faster Screen record. The recorder panel still opens only from its QS tile
  (rechecked in this firmware's SystemUI: `ScreenRecordDialog` is built only
  in `ScreenRecordTile.handleClick`). Skip the `uiautomator dump` reads when
  `sysui_qs_tiles` is unchanged since the tile was last found, by keeping
  that string with the page and box and tapping straight away (about a
  second instead of 2 s per read), falling back to the search otherwise.
  The alternative is Pathfinder's own MediaProjection recorder, which opens
  Android's consent prompt directly but captures the top screen only.
