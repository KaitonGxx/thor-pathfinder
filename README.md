# Thor Pathfinder

Move apps between the AYN Thor's two screens, and put shortcuts on its buttons.

Pathfinder is a free, open-source companion for the AYN Thor. Hold Back and the
apps on the top and bottom screens trade places. Double-press Select and the
Thor's mouse mode turns on or off. Every shortcut can be changed, on seven of
the Thor's buttons.

It was inspired by [Thor Wayfinder](https://github.com/Thor-Wayfinder/thor-wayfinder)
and does everything Wayfinder does. It contains none of Wayfinder's code and is
not affiliated with Wayfinder or with AYN.

## Features

**Swap screens**
- Swaps the apps on the two screens, or sends a lone app to the other screen.
- Apps keep running while they move. Pathfinder hands the app's window to the
  other screen instead of restarting the app there.
- When a lone app leaves a screen, that screen goes to its home screen rather
  than revealing whatever was underneath.
- If one of the two apps is playing a video or music, it moves first, so it
  doesn't stutter. See [Known limitations](#known-limitations).

**Button shortcuts** on Back, Home, the AYN button, Select, Start, L3 and R3:
- Back, Home and the AYN button: press, double-press and hold.
- Select, Start, L3 and R3: double-press and hold. Games still receive every
  press of these buttons, so a plain press stays with the game.
- Actions: swap screens, mouse mode on/off, back, home, recent apps,
  notifications, quick settings, screenshot, power menu, lock screen, or open
  any app.

**Mouse mode**
- Turn the Thor's mouse mode on or off from a shortcut.
- Reverse right-stick scrolling, so pushing the stick up scrolls the page up.

**Built for the Thor**
- A step-by-step setup that checks each requirement before moving on.
- Works with the Thor's controller as well as touch. Every control shows a
  clear outline when it has focus.

**Private and light**
- The accessibility service receives button presses only. It cannot read the
  screen or anything you type.
- No internet access. The app is under 2 MB and runs no background work of its
  own.

## Out of the box

| Button | Gesture | Does |
|---|---|---|
| Back | Press | Back |
| Back | Double-press | Recent apps |
| Back | Hold | Swap screens |
| Select | Double-press | Mouse mode on/off |

Everything else is left alone until you change it. Home and the AYN button keep
their normal behaviour, with no delay, until you give them a shortcut.

## Requirements

- An **AYN Thor** on firmware **1.0.0.377 or newer** (Android 13). Setup checks
  this, and offers the system update screen if the Thor needs updating.
- **Shizuku**, from [Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)
  or its [GitHub releases](https://github.com/RikkaApps/Shizuku/releases), for
  swapping screens and for anything to do with mouse mode. Shizuku grants the
  extra access without rooting the Thor, and walks you through its own setup.

## Install and set up

1. Install the APK on the Thor.
2. Open Thor Pathfinder and follow the setup:
   1. **Check your Thor**: confirms the model and firmware.
   2. **Thor Wayfinder** (only if it's installed): both apps use the Back
      button, so uninstall Wayfinder or turn off its accessibility service.
   3. **Accessibility**: turn on Thor Pathfinder's service.
   4. **Shizuku**: install and start Shizuku if needed, then allow Pathfinder.

**"Restricted setting"**: Android blocks accessibility services for apps
installed from outside the Play Store until you allow them. If you see this,
open Pathfinder's app info, tap ⋮ in the corner, choose **Allow restricted
settings**, then turn the service on again.

## Known limitations

- **One of two swapping apps can stutter.** A swap is two moves in a row, and
  for a few milliseconds the first app sits on top of the second. That is
  enough for a video or game on the second app to drop a frame. Pathfinder
  moves a playing video or song first, and otherwise the top screen's app,
  where games usually run.
- **Reverse scrolling needs a restart.** The setting lives in AYN's own mouse
  mode configuration, which the Thor reads once at start-up. Pathfinder offers
  a Restart now button after you change it. Resetting the Thor's own mouse mode
  settings can turn it off again; if so, switch it back on in Pathfinder.
- **A double-press shortcut delays single presses.** With a double-press on
  Back, Home or the AYN button, a single press waits for the double-press gap
  (300 ms by default, adjustable) before it acts.

## How it works

- **Buttons**: an accessibility service with key filtering. Back, Home and the
  AYN button are told apart by their scan codes (158, 102 and 194; the AYN
  button reaches Android as Home), which also lets presses that Android itself
  injects pass through untouched.
- **Swapping**: `am display move-stack`, run through Shizuku, moves an app's
  whole window stack to the other screen, so the app is not restarted.
- **Mouse mode**: AYN's switch is the system setting
  `global_gamepad_to_mouse_mode`, which the Thor's input engine watches.
- **Scroll direction**: in mouse mode the right stick drags a virtual finger,
  which is why pushing up scrolls the page down. AYN's input engine reverses
  that swipe when the right stick's `reverseJoystick` flag is set in
  `AYN_Thor_Settings/global_mouse_mode_config.json`. Pathfinder changes only
  that value and keeps a copy of the original next to it.

## Reporting a problem

Please include your firmware version (shown in setup) and, if you can, a log:

```bash
adb logcat -s PathfinderSwap PathfinderShell PathfinderMouse
```

Each swap logs one line describing what Pathfinder saw on both screens and
what it moved.

## Building

You need JDK 17 and the Android SDK (platform 35).

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Release
builds are currently signed with the local debug key.

## License

Thor Pathfinder is free software, released under the
[GNU General Public License v3.0](LICENSE).

It bundles the [Shizuku API](https://github.com/RikkaApps/Shizuku-API) (MIT
License) and AndroidX, Jetpack Compose and Kotlin libraries (Apache License
2.0). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md); the app shows the
same licenses under About → Open-source licenses.

AYN and Thor are trademarks of their respective owners. Pathfinder is not
affiliated with or endorsed by AYN, Thor Wayfinder or Shizuku.
