# Go.dot remote

An Android app that puts a [Go.dot](https://github.com/pob31/go.dot) show in the operator's hands:
GO, Doh! and PANIC with the current and next cue on every screen, and five tabs - six with
Choufleur's script page.

- **Cues** — the cue list, its groups folded or open, and an inspector for the cue you tap: its
  level, sends, EQ, DCA, waits, speed, on/off switches, flag and notes, and the waveform of a sound.
- **Running** — everything playing now, a sound with its waveform, a wait counting down; stop,
  kill, advance, skip a member of a round, drag the playhead, ride a sound's trim.
- **Surface** — the show's control surfaces on the tablet: the faders, pads and SELECT of a D700
  left in the régie or of a Mackie that did not come, the selected sound's EQ and sends on
  rotaries, and "This tablet", a fader for every DCA of the show.
- **Mapping** — the video outputs' warps and zones, adjusted while standing at the wall.
- **Script** — [Choufleur](https://github.com/pob31/choufleur_prompt)'s script page for this
  operator's cue list, when its address is set on the Connections tab.
- **Connections** — the Go.dot machines this tablet knows, which one is on screen, the main and
  backup of a pair, the Choufleur address, and *Quit the remote*.

It is a second surface, not a second screen (Go.dot PRD §3.17). It may change levels, sends, EQ,
DCA trims, waits, speed, the on/off switches, flags and notes — never the shape of the cue list:
no cue is created, deleted, moved, renumbered or renamed from the tablet.

The app keeps its link to each machine open in the background, with a copy of that machine's whole
state, so swiping it away and back is instant, and switching between it and another app on the
same tablet costs nothing.

## What it needs

**A Go.dot that streams its state to a remote** (Go.dot's namespace draft §68, "The remote").
When the machine it is pointed at is older than that, the Connections tab says so in words.

What the app and Go.dot say to each other is written down in Go.dot's repository:
[`docs/godot-remote-protocol-0.1.md`](https://github.com/pob31/go.dot/blob/main/docs/godot-remote-protocol-0.1.md).

## Pointing it at a machine

On the **Connections** tab, *Add a connection*: a name, the address of the computer running Go.dot, and
its port, **5010** unless Go.dot was started with another. It is the address and port of Go.dot's
web console in a browser (`http://<address>:5010/ui`). The tablet and the computer must be on the
same network.

The tab also shows the tablet's own address and the name Go.dot knows it by; Go.dot lists it under
Show settings > Network.

### Main and backup

Add both machines of a pair. Both stay linked; the one **on screen** is the one the tabs show and
GO goes to. Each machine says what it is — "Main - sending", "Backup - following" — and the
*Take over*, *Hand over* and *Make this the main* buttons act on the machine they sit under.

### Finding the tablet

From Go.dot's Show settings > Network, *Find* makes this tablet flash its screen and its camera's
light, sound an alarm and vibrate until somebody touches it. The alarm sounds even when the
tablet's alarm volume is at zero: it is turned up for the alarm and put back after. For that to work while the app is in the background:

- notifications must be on for the app;
- Android must allow the app over the lock screen, or a locked tablet only sounds and vibrates.

The **Find this tablet** part of the Connections tab says which of the two is missing, with a button
to the Android setting, and a test that rings for three seconds.

## The Script tab (Choufleur)

[Choufleur](https://github.com/pob31/choufleur_prompt) follows the actors through the script and
warns each operator before their cues. Its page can sit in the remote, under GO, as the **Script**
tab. On the Connections tab, under *Script prompt (Choufleur)*, type the address of your cue list
as Choufleur's library screen gives it - `http://<number>:8080/list/<list>` - or press *Scan the
code* and aim at the code it draws. A host alone becomes `http://<host>:8080/`. Use the number:
Android does not find `.local` names.

The page stays loaded while you use the other tabs, so coming back to it costs nothing. The thin
bar above it has *Reload* and *Open in Chrome*. The wrist buzzer cannot pair inside the remote;
open the page in Chrome for it and follow Choufleur's instructions there. Choufleur's warning that
a plain address cannot keep the screen awake does not apply in the remote, which keeps the
screen on itself.

## Quitting

The app stays linked in the background - its notification says so - until it is quit:
*Quit the remote* at the foot of the Connections tab, or *Quit* on the notification. Both unlink
every machine and close the app. The machines stay remembered, each with its *Linked* switch,
so the next launch links them again.

## Building

Android Studio, or the Android SDK with platform 36. The Gradle wrapper fetches Gradle itself,
and a Java 17 for its daemon when none is installed.

```
./gradlew test assembleDebug
```

runs the tests and writes the app to `app/build/outputs/apk/debug/`. `local.properties` (not in
git) names the SDK — `sdk.dir=/path/to/Android/Sdk`; Android Studio writes it on first open.

## Trying it on a tablet over USB

With the tablet in developer mode and plugged into the computer that runs Go.dot:

```
adb install -r app/build/outputs/apk/debug/godot-remote-0.1.2-debug.apk
adb reverse tcp:5010 tcp:5010
```

`adb reverse` makes the computer's port 5010 the tablet's own, so on the Connections tab the address
is **127.0.0.1** and the port **5010** — no network needed.

## Looking at the screens without a tablet

```
./gradlew testDebugUnitTest -Pscreenshots
```

draws the header and every tab — tablet and phone, upright and on its side, English and French,
locked and stale — into `app/build/screenshots/`, from fixtures taken from a real Go.dot. The
plain `./gradlew test` leaves them out.

## Contributing

- Every source file starts with the GPL-3 header block and `SPDX-License-Identifier:
  GPL-3.0-or-later`, as Go.dot's do.
- Both languages in step: a key added to `app/src/main/assets/lang/en.json` is added to `fr.json`
  in the same change; `LanguageParityTest` fails otherwise.
- Numbers written to Go.dot are written in no locale's way — a decimal point, never a comma —
  whatever the tablet's language.

## Licence

GPL-3.0-or-later; see [LICENSE](LICENSE). Outside AndroidX and Kotlin, the app uses OkHttp and,
for scanning Choufleur's code, ZXing Android Embedded (both Apache-2.0).
