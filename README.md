# Go.dot remote

An Android app that holds a [Go.dot](https://github.com/pob31/go.dot) show in the operator's
hands: the running cues, the cue list with its levels, an on-screen surface and the video
mapping, with GO, Doh! and PANIC always on screen. It is a second surface, not a second screen
(Go.dot PRD §3.17).

A foreground service keeps one WebSocket open to each Go.dot machine it is linked to and keeps a
mirror of that machine's whole state, fed by one snapshot and then by one OSC bundle per engine
tick. Swiping the app away and back is a redraw, never a reconnect, so switching between it and
the WFS-DIY remote on one tablet takes a fraction of a second.

**What it says to Go.dot** is written down in Go.dot's repository:
[`docs/godot-remote-protocol-0.1.md`](https://github.com/pob31/go.dot/blob/main/docs/godot-remote-protocol-0.1.md)
— connecting, the snapshot, the stream, structure frames, HELLO and FIND, writes as text,
refusals, staleness and two machines. Where this app and that page disagree, one of them is
wrong and gets fixed.

**What it may change**: levels, sends, EQ, DCA trims, pre and post waits, speed, the on/off
switches, flags and notes. Never the shape of the cue list — no cue is created, deleted, moved,
renumbered or renamed from the tablet.

## Status

Early. The stages are in Go.dot's namespace draft §68 and devplan Phase 7 (A.0–A.11). Built so far:
the skeleton (A.0), the OSC codec (A.1) and the link (A.2) — the service, one WebSocket per machine,
the mirror fed by the snapshot and the stream, the writes — behind a plain scaffolding screen
(an address, Connect, how the link stands, the tick, the node count, the cue in standby). The tabs
come next.

The link service runs as a `connectedDevice` foreground service, not `dataSync`: Android 15 caps a
`dataSync` service at six hours in twenty-four, shorter than a long day of rehearsal. Its
prerequisite is `CHANGE_WIFI_STATE`, a normal permission granted at install; checked on a Lenovo
TB370FU running Android 15.

## Building

Requirements: Android Studio (or the Android SDK with platform 36), and nothing else — the Gradle
wrapper fetches Gradle 9.6, and the daemon runs on a Temurin 17 it fetches itself when none is
installed (`gradle/gradle-daemon-jvm.properties`).

```
./gradlew test assembleDebug          # JVM tests, then app/build/outputs/apk/debug/
adb install -r app/build/outputs/apk/debug/godot-remote-*-debug.apk
```

`local.properties` (not in git) names the SDK: `sdk.dir=/path/to/Android/Sdk`. Android Studio
writes it on first open.

The toolchain is WFS-DIY's remote's (AGP 9.4, Kotlin 2.3, the Compose BOM 2026.01.01), so the
two apps build side by side.

## Contributing

- Every source file starts with the GPL-3 header block and `SPDX-License-Identifier:
  GPL-3.0-or-later`, as Go.dot's do.
- Both languages in step: a key added to `app/src/main/assets/lang/en.json` is added to `fr.json`
  in the same change; `LanguageParityTest` fails otherwise.
- Numbers written to Go.dot are written in no locale's way — a decimal point, never a comma —
  whatever the tablet's language. The engine refuses a decimal comma.

## Licence

GPL-3.0-or-later; see [LICENSE](LICENSE). OkHttp (Apache-2.0) is the one library outside AndroidX
and Kotlin.
