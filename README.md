# Podium Air — Windows Edition

A native Kotlin + Compose Desktop adaptation of the Android Podium Air app,
built by Prem Das aka Kaizen. This preview plays your **local music** on Windows
through JavaFX native audio. It bundles its runtime and uses a native Windows
installer. Full Android feature parity is tracked in [FEATURE_PARITY.md](FEATURE_PARITY.md).

Local file/folder imports, metadata/artwork, albums/artists, search, favorites,
history, editable playlists, M3U8 interchange, duplicate-safe queues, repeat/
shuffle, play/pause/seek/volume, 0–12 second equal-power crossfade, speed,
ten-band equalizer, sleep timer, TTML/LRC lyrics, lyric timing/word motion,
artwork color motion and Replay listening statistics are implemented. Windows
system media controls, tray, optional notifications and optional startup are included.

The expanded Windows build passed unit tests, all 15 navigation routes, real
playback/crossfade in WAV, FLAC, Opus, MP3, AAC/M4A and AIFF, native media-session
checks and MSI install/run/uninstall. See [BUILD_REPORT.md](BUILD_REPORT.md) for
exact build evidence and the current interaction/performance verification.

Google login, YouTube streaming/downloads and remote account sync require a
supported provider integration. Automix needs a beat-analysis/time-stretch
pipeline. Online canvas video, lyrics translation, scrobbling, Discord RPC and
Listen Together remain incomplete. The app discloses unavailable capabilities.

## Build on Windows 10/11 x64

Install JDK 21, WiX Toolset 3.14.1 and Visual Studio Build Tools with Desktop
development with C++ and a Windows SDK. In a Visual Studio x64 developer shell:

```powershell
.\platform-windows\build.ps1
.\gradlew.bat :shared-domain:check :app-desktop:check
.\gradlew.bat :app-desktop:run
.\gradlew.bat :app-desktop:createDistributable :app-desktop:packageMsi :app-desktop:packageExe
```

Pinned dependencies: Kotlin 2.2.21, Compose 1.9.3, Gradle 8.14.3, OpenJFX
21.0.9, jFLAC 1.5.2, Concentus 1.0.2 and JAAD 0.8.7. CI records the exact
Temurin JDK build and hashes all runtime JARs. End users do not install Java,
VLC, FFmpeg or .NET. FFmpeg is used only in CI to generate test fixtures.

Installers are **unsigned test builds** and may show SmartScreen warnings.
No public release or automatic update is configured.

## Get a test build

[Download the verified expanded build](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37953260863/artifacts/11627980078) ·
[Successful Windows checks](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37953260863) ·
[Draft PR #1](https://github.com/kaizen-flims/Podium-Air-Windows-/pull/1)

The [Windows workflow](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/workflows/windows.yml)
produces MSI, EXE installer, portable ZIP, matching application source ZIP,
dependency source bundle and SHA256SUMS in `Podium-Air-Windows-x64`. Artifacts
are temporary; the workflow requests 30-day retention. GitHub may require
sign-in to download them. `Windows-verification` holds reports, screenshots,
codec/native integration results, installer logs and performance samples.

The local audio checks can also be run against the portable executable:

```powershell
& '.\Podium Air\Podium Air.exe' --audio-smoke --result=audio-smoke.txt
& '.\Podium Air\Podium Air.exe' --platform-smoke --result=platform-smoke.txt
& '.\Podium Air\Podium Air.exe' --audio-smoke --media='C:\Music\song.flac' --result=flac-smoke.txt
```

The audio fixture check expects a track of at least six seconds. CI reports a
recognized missing-output error as Blocked only for its baseline WAV check;
unexpected errors and the explicit codec checks fail the build. The
`audio_smoke` workflow input also makes a missing output fail the baseline check.

## Use

Ctrl+O imports files; Ctrl+Shift+O imports a folder. Music stays in its original
location, including accessible mapped/UNC folders. Moving files requires
reimporting. Menus provide Play Next, Add to Queue, playlist actions and
favorites. Playlists support duplicates, reordering and local M3U8 import/export;
remote URLs inside imported playlists are skipped with a report.

WAV, MP3 and AIFF use JavaFX directly. FLAC, Ogg Opus and AAC/M4A prepare a
disk-backed 16-bit WAV on background workers. This makes first play of long
tracks slower and converts high-bit-depth FLAC to 16 bits. Mono/stereo FLAC
8–24 bit up to 192 kHz and Opus mapping family 0 are supported. Protected,
fragmented/non-AAC M4A, Ogg Vorbis, multichannel/chained Opus and decoded tracks
over 750 MB are explicitly rejected. No sample-accurate gapless claim is made.

Place a `.ttml` or `.lrc` with the same basename beside the audio file. TTML
supports timed words, overlapping/duet vocals and backing lines. Embedded lyrics
are a fallback. Lyrics allow click-to-seek, per-track timing offsets, reload and
auto-scroll. Reduced motion disables word/background motion.

Library state is in `%LOCALAPPDATA%\PodiumAirWindows\library.json`. Corrupt
state is copied for recovery before replacing it. Decoder/artwork/helper caches
live beside it. Playback restores paused. Closing quits by default; Settings
can opt into closing to tray, launching at sign-in and track notifications.
The tray offers Show, Play/Pause, Previous, Next and Quit. Windows system media
controls publish current metadata/timeline and accept transport commands.

Ctrl+F searches, Ctrl+Space plays/pauses, Ctrl+Left/Right changes tracks,
Alt+Left/Right seeks ten seconds and Esc leaves a collection. The navigation
collapses to icons at narrower widths and scrolls at small heights. Settings
controls dark/light theme, rate, equalizer, crossfade, sleep and Windows options.
Crossfade works at 1× and is cancelled by seeking. Replay counts real elapsed
playing time; paused time and seek distance do not inflate listening minutes.

## Credits and license

Adapted from the Android application [Podium Air](https://github.com/kaizen-flims/Podium-Air),
based on [BitChord](https://github.com/kushagrasinghx/BitChord) by Kushagra Singh
and contributors. Original source and notices remain credited under [GPLv3](LICENSE).
The Windows adaptation was created on 9 October 2026. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and About → Third-party licenses.
Matching application source and exact decoder/library sources accompany each
CI package. SF Pro fonts and the separately licensed AGPL Automix code/model
are not included. No Android releases, updates or files are modified.

Made with ❤️ by Prem.
