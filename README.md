# Podium Air — Windows Edition

A native Kotlin + Compose Desktop adaptation of the Android Podium Air app,
built by Prem Das aka Kaizen. This is a **local music preview**, not yet the full
YouTube Music client. It is a JVM desktop app with a native Windows installer,
not a web wrapper. Full feature parity is tracked in [FEATURE_PARITY.md](FEATURE_PARITY.md).

Implemented: local-file/folder imports, metadata/artwork, Home, Search, Library,
albums/artists, favorites, history, editable local playlists, queue management,
repeat/shuffle, real JavaFX audio controls, 0–12s equal-power crossfade at 1×,
sidecar/embedded lyrics, light/dark themes, speed, equalizer, sleep timer and tray.
**Real audio/device verification is pending until recorded in BUILD_REPORT.md.**

Google login, YouTube streaming/downloads and remote account sync are blocked
by the supported-provider integration gate. Automix, FLAC/Opus, animated canvas,
scrobbling and Windows SMTC are not available. See [PORTING_AUDIT.md](PORTING_AUDIT.md).

## Build on Windows 10/11 x64

Install JDK 21 and WiX Toolset 3.14.1 for MSI/EXE packaging. The application
bundles a runtime; end users do not install Java or VLC. From repository root:

```powershell
.\gradlew.bat :shared-domain:check :app-desktop:check
.\gradlew.bat :app-desktop:run
.\gradlew.bat :app-desktop:createDistributable :app-desktop:packageMsi :app-desktop:packageExe
```

Pinned tools: Kotlin 2.2.21, Compose 1.9.3, Gradle 8.14.3, OpenJFX 21.0.9,
JDK 21. No signing secrets are needed; installers are **unsigned test builds**.
Windows may show SmartScreen warnings. Do not publish a public release until
license and device acceptance gates in [BUILD_REPORT.md](BUILD_REPORT.md) pass.

## Get a test build

[Windows native build workflows](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/workflows/windows.yml)
produce `Podium-Air-Windows-x64`: MSI, EXE installer, portable ZIP, matching
source ZIP and SHA256SUMS. A successful run is required; workflow configuration
alone does not prove a package exists. Artifacts expire after 30 days.

The `Windows-verification` artifact contains test reports and a startup screenshot.
A separate opt-in `audio_smoke` workflow input runs real WAV playback/pause/seek
and crossfade. It requires an available audio output; cloud runners may lack one.
On your PC, the same check can be run against the portable executable:

```powershell
& '.\Podium Air\Podium Air.exe' --audio-smoke --result=audio-smoke.txt
Get-Content audio-smoke.txt
```

## Use

Import files with Ctrl+O, or a folder with Ctrl+Shift+O. Supported baseline
formats are MP3, PCM WAV, AIFF and compatible AAC/M4A. Files stay in their
original locations; moving them later requires reimporting. Menu → Play Next /
Add to Queue and Add to Playlist work on imported tracks. Sidecar `.lrc` files
should have the same basename as the audio file. Library state is stored in
`%LOCALAPPDATA%\PodiumAirWindows\library.json`; a corrupt file is preserved for
recovery. Playback resumes paused after reopening. Closing the window quits.
The tray provides Show, Play/Pause, Previous, Next and Quit.

Ctrl+F searches, Ctrl+Space toggles playback, Ctrl+Left/Right changes tracks,
Alt+Left/Right seeks ten seconds, and Esc returns from a collection.

## Credits and license

Adapted from the Android application [Podium Air](https://github.com/kaizen-flims/Podium-Air),
which is based on [BitChord](https://github.com/kushagrasinghx/BitChord) by Kushagra
Singh and its contributors. Original source and notices remain credited under
[GPLv3](LICENSE). The Windows adaptation was created on 9 October 2026.
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the bundled About →
Third-party licenses screen. SF Pro fonts and the separately licensed AGPL
Automix planner are not included. Matching application source accompanies every
CI test package. No Android releases, updates or files are modified by this repo.

Made with ❤️ by Prem.
