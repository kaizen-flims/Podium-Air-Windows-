# Build report — 9 October 2026

**Native local-music preview: compiled, tested, packaged and started on Windows.
Full Android feature parity is not complete.**

## Verified Windows build

Implementation commit: `4c631f89875fefa067f3ae248cc8b04f8bca6923`.
Successful workflow:
https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37942605916

Download installer/portable/source artifact:
https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37942605916/artifacts/11622806566

Verification reports, screenshot, dependency inventory and runtime version:
https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37942605916/artifacts/11622636786

- `:shared-domain:check` and `:app-desktop:check`: **18 tests, zero failures/errors**.
- Native `createDistributable`, `packageMsi`, `packageExe`: passed.
- Packaged application startup: passed; all 14 routes and populated playlist/album details rendered.
- Packaged JavaFX audio smoke: **passed**. Generated PCM WAV playback advanced,
  pause worked, seek reached 2 seconds, resume worked, and the two-player
  crossfade entered a second queue entry and finished the overlap.
- Source collection and notices: passed. Matching application source ZIP,
  jaudiotagger/OpenJFX source JARs, full OpenJFX native source, native-library
  license texts, dependency SHA256 inventory and package checksums included.

Runner: Windows Server 2025 x64. JDK: Temurin 21.0.12+101.0. Kotlin 2.2.21,
Compose 1.9.3, Gradle 8.14.3, OpenJFX 21.0.9. These are automated runtime/state
checks, not a listening-quality or physical device hotplug assessment.

The populated UI smoke passed every navigation route and playlist/album detail
using actual generated WAV files. Long titles/renaming display and CI caching
are also included. A final fix preserves both crossfade volume envelopes when
preferences change during a transition, tracks manual license files as Gradle
inputs, and improves the file chooser label; the next workflow checks that fix.

## Implementation

Native Kotlin/Compose Desktop UI and AWT Windows shell; JavaFX native audio;
local file/folder library and metadata/artwork; local playlists, favorites and
history; atomic JSON persistence/recovery; duplicate-safe queue operations,
repeat and reversible upcoming shuffle; play/pause/seek/volume/next/previous;
speed, ten-band equalizer, sleep timer; local sidecar/embedded lyrics; two-player
equal-power crossfade; light/dark source palette, original brand assets; native
file chooser, tray and keyboard shortcuts. Original domain and lyrics code is
reused from Android source SHA `48902e6b20fdcfeb1723e02d4744849e82d5067a`.

Tests cover duplicate queue entries; removal/reordering while retaining current
selection; repeat policies; reversible shuffle after additions/removals; word
lyric timing/entity decoding; persistence round-trip/corruption recovery and
missing references; generated WAV import and LRC offsets; playback coordinator
commands, cleanup and stale end callbacks; crossfade queue handoff.

## Acceptance gates

| Gate | Result |
|---|---|
| A — source audit / reuse | Structural inventory and assessed reuse complete. GPL/AGPL/font/provider constraints documented; native notices and sources bundled. Full public-release dependency review remains |
| B — native Windows startup | Passed packaged startup and all 14 navigation routes plus populated detail smoke on Windows Server 2025 |
| C — audio plays/pauses/seeks/transitions | Passed packaged real JavaFX PCM WAV integration with crossfade; output quality and physical-device tests pending |
| D — honest feature parity | FEATURE_PARITY.md identifies all major incomplete/blocked source features |
| E — installable Windows CI artifact | Successful MSI/EXE/portable build and artifact upload verified |
| F — handoff / documentation | README, audit, parity, credits/notices, matching source, progress log and draft PR #1 provided |

## Remaining constraints and release status

YouTube Music streaming, Google login, remote playlists and bidirectional sync
are blocked by the source's private API/extraction pipeline and the prompt's
supported-provider requirement. The Account screen explicitly discloses this
and opens the official service in the default browser; it does not invent sync.
The first preview therefore uses the authorized local-playback baseline.

Automix requires separate AGPL reuse, decoder/model and beat/tempo pipeline
work. FLAC/Opus, animated canvas, translations/online lyric providers, remote
network sources, Replay, scrobbling, Discord, Listen Together, SMTC, global
media keys and notifications are not implemented. They are not advertised as
working features. Source light/dark themes and branding are adapted, but full
pixel/animation parity is not claimed; SF Pro fonts are not redistributed.

Windows 10/11 client installation/uninstallation, 100/150/200% DPI, interactive
keyboard/picker/tray checks, MP3/AIFF/M4A decoding, end-of-track across a large
collection, audio-device changes, performance/memory/idle CPU and listening
quality remain practical checks. Installers are unsigned; no code-signing
credentials were supplied. They may show SmartScreen warnings.

No merge to main, public release, Android changes or auto-update occurred.
The connected GitHub tools were used to create the feature branch and PR;
local dependency downloads were unavailable in the Linux workspace, so actual
compilation, tests and target-platform verification ran on Windows Actions.
