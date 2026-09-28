# Measuring button latency

How long a button press takes to reach an app, measured in software on the
Thor itself: no camera, no rig. Android's tracer (`atrace`) records when the
kernel event wakes InputReader and when the app starts `deliverInputEvent`, so
the difference is the time Android (and Pathfinder, when it filters keys)
took to hand the press over. Presses are written into the controller's kernel
device with `sendevent`, which is the same path a thumb takes.

Needs adb over USB. Pick a button the app ignores; L3 (scan code 317) does
nothing in most games. Find the controller's node with `adb shell getevent -pl`
(name "Odin Controller"; `/dev/input/event9` on the test Thor).

```bash
adb push tools/latency/inject.sh /data/local/tmp/lat-inject.sh
adb shell 'atrace --async_start -b 65536 -c sched input view binder_driver ss >/dev/null && sh /data/local/tmp/lat-inject.sh /dev/input/event9 317 60 && atrace --async_stop -o /data/local/tmp/lat.txt >/dev/null'
adb pull /data/local/tmp/lat.txt run1.raw
APP=$(adb shell pidof <game package>) python3 tools/latency/analyze.py run1.raw:$(adb shell pidof com.thorpathfinder.app)
```

Alternate the conditions being compared (for example Pathfinder's service off
and on, or the Disabled profile and another) over several rounds, so drift in
clocks and temperature lands on both. A dump is about 300 MB for 60 presses in
a game; the first few events can be lost from the ring buffer.

Leave `uiautomator dump` out of runs: it connects a UiAutomation, which makes
Android suspend every accessibility service, Pathfinder included, while it runs.

## Results (firmware 1.0.0.377, Silksong, 2026-09-28)

Kernel to game, 180 presses and 180 releases per condition:

| | median | p90 | p99 | worst |
|---|---|---|---|---|
| Pathfinder's service off | 2.93 ms | 4.94 ms | 8.35 ms | 10.7 ms |
| Service on (any profile with shortcuts) | 4.88 ms | 7.40 ms | 12.9 ms | 25.4 ms |
| Service on, Disabled profile | 3.01 ms | 5.11 ms | 7.80 ms | 12.1 ms |

Presses and releases are delayed alike. Of the extra time, Pathfinder's own
`onKeyEvent` is about 0.3 to 0.4 ms (median); the rest is Android routing the
key through system_server's main thread, into Pathfinder and back, and
re-injecting it. Sticks are barely affected (+0.16 ms median): they pass
through system_server's filter only, not through Pathfinder.
