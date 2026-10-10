# Podium Air — Windows Edition

A native Kotlin + Compose Desktop adaptation of the Android Podium Air app,
built by Prem Das aka Kaizen. The `feat/complete-native-parity` development branch
adds measured neural Automix, optional online lyrics and Replay PNG export to
the published native local-music preview. It bundles its runtime and uses real
Windows installers. **This is not yet the complete streaming-focused Android
port.** Full parity is tracked in [FEATURE_PARITY.md](FEATURE_PARITY.md).

Local file/folder imports, metadata/artwork, albums/artists, search, favorites,
history, editable playlists, M3U8 interchange, duplicate-safe queues, repeat/
shuffle, play/pause/seek/volume, 0–12 second equal-power crossfade, speed,
ten-band equalizer, sleep timer, TTML/LRC lyrics, lyric timing/word motion,
artwork color motion and Replay listening statistics are implemented.
The development branch also contains native DSP/Beat This!/Open-unmix analysis,
confidence-based transitions, pitch-preserving WSOLA, opt-in LRCLIB lyrics with
offline cache, frozen Replay PNG previews and opt-in ListenBrainz scrobbling. Replay supports
the source app's This month / This year / All time views and Intro, Minutes,
Artists, Songs, Albums, Genres, Habits and Recap cards. Empty album/genre cards
are hidden; old listening records do not invent hourly statistics. Windows shell file/folder dialogs,
system media controls, tray, optional notifications and optional startup are included.

Development commit `1b12da5` passed **58 tests with zero failures/errors** and
all packaged Windows gates in [run 38064057032](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/38064057032):
neural model inference, actual Automix overlap/seek, Replay export, six codecs,
15 navigation routes, keyboard/DPI/native media checks and MSI lifecycle.
ListenBrainz with Windows Credential Manager is implemented and undergoing
expanded Windows checks. Calendar Replay periods and all eight individual
story PNG cards are also implemented; their new checks are pending. See [BUILD_REPORT.md](BUILD_REPORT.md).

Google login, YouTube streaming/downloads and remote account sync require a
supported provider integration under the original master prompt. A Google
Data API key or OAuth app alone does not enable native audio playback/downloads.
See [the provider decision](docs/STREAMING_PROVIDER_DECISION.md).
Online canvas video, lyrics translation, Last.fm, Discord RPC and Listen Together
remain incomplete. The app discloses these gaps.

## Build on Windows 10/11 x64

Install Temurin JDK 21.0.12.1+1, WiX Toolset 3.14.1 and Visual Studio Build Tools with Desktop
development with C++ and a Windows SDK. In a Visual Studio x64 developer shell:

```powershell
.\platform-windows\build.ps1
.\gradlew.bat :shared-domain:check :app-desktop:check
.\gradlew.bat :app-desktop:run
.\gradlew.bat :app-desktop:createDistributable :app-desktop:packageMsi :app-desktop:packageExe
```

Pinned dependencies: Kotlin 2.2.21, Compose 1.9.3, Gradle 8.14.3, OpenJFX
21.0.9, jFLAC 1.5.2, Concentus 1.0.2, JAAD 0.8.7, JLayer 1.0.1 and ONNX
Runtime 1.28.0. Original Beat This! and Open-unmix model assets are pinned to
the Android source commit and checked by SHA-256 at build and extraction. CI records the exact
Temurin JDK build and hashes all runtime JARs. End users do not install Java,
VLC, FFmpeg or .NET. FFmpeg is used only in CI to generate test fixtures.

Installers are **unsigned test builds** and may show SmartScreen warnings.
Prem has authorized a public Windows preview. The release workflow publishes
only after the main-branch Windows build and package verification succeed.
Android releases and automatic updates remain unchanged.

## Download the published Windows preview

These assets are the older local-music preview. They do **not** contain the new
development-branch Automix/lyrics/Replay/ListenBrainz work. Full installers will
replace the website link after the complete streaming port is verified.

[Download Podium Air for Windows — MSI](https://github.com/kaizen-flims/Podium-Air-Windows-/releases/download/v0.2.0-preview.1/Podium-Air-Windows-0.2.0-x64.msi) ·
[All release assets and notes](https://github.com/kaizen-flims/Podium-Air-Windows-/releases/tag/v0.2.0-preview.1) ·
[Podium Air website](https://podium-air-website.pages.dev/)

The public **v0.2.0-preview.1** release includes the MSI, EXE installer, portable
ZIP, exact application source, dependency sources, third-party notices, build
evidence and SHA256SUMS. It is an unsigned local-music preview for Windows
10/11 x64. No separate Java installation is needed.

[Successful Windows checks](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37981495583) ·
[Verification reports and screenshots](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37981495583/artifacts/11640779965) ·
[Merged PR #1](https://github.com/kaizen-flims/Podium-Air-Windows-/pull/1)

The [Windows workflow](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/workflows/windows.yml)
also stores temporary CI packages for 30 days. `Windows-verification` holds
reports, screenshots, codec/native integration results, installer logs and
performance samples. Public release assets can be downloaded without signing in.

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
are a fallback. Settings can opt into the documented LRCLIB API; only track
metadata is sent, never audio. Matching cached results remain usable offline. Lyrics allow click-to-seek, per-track timing offsets, reload and
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
controls dark/light theme, volume, rate, equalizer, crossfade, sleep and Windows options.
Crossfade works at 1× and is cancelled by seeking. Automix measures actual PCM,
uses the original planner and bundled models, and falls back to DSP or ordinary
transitions when evidence is missing. It can operate with manual crossfade at
zero; tempo correction is restricted to measured confidence. Neural analysis
adds processing time and uses bounded head/tail regions rather than a continuous
real-time graph. Replay counts real elapsed
playing time; paused time and seek distance do not inflate listening minutes.

## Credits and license

Adapted from the Android application [Podium Air](https://github.com/kaizen-flims/Podium-Air),
based on [BitChord](https://github.com/kushagrasinghx/BitChord) by Kushagra Singh
and contributors. Original source and notices remain credited under [GPLv3](LICENSE).
The Windows adaptation was created on 9 October 2026. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and About → Third-party licenses.
Matching application source and exact decoder/library sources accompany each
CI package. SF Pro fonts are not included. Original Automix copyright headers and
AGPLv3-or-later obligations are retained in the GPLv3 combination permitted by
section 13. AGPL, model MIT and ONNX Runtime/third-party notices are bundled. No Android releases, updates or files are modified.

Made with ❤️ by Prem.

## Preview release publishing

`release/preview.json` records the explicitly approved Windows preview. After a successful main-branch Windows build, the release workflow validates all test results, integration reports, exact-source provenance and five package checksums; it uploads installers, portable app, corresponding sources, notices and checksums to a draft and then publishes it as a prerelease. An already published preview is never overwritten. Stable releases still require consumer-device acceptance.

ListenBrainz is configured from Account using the listener's own user token.
The token is stored in Windows Credential Manager, never `library.json`.
Listening shares track/artist/album metadata and timestamps only after opt-in.
Paused time and seek distances do not count; qualifying listens require half
the track or four minutes, whichever is lower, and tracks at or below 30 seconds
are excluded. Failed submissions are reported and are not saved for offline retry.
