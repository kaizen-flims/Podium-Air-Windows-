# Podium Air — Windows Edition

Native Kotlin/Compose Windows adaptation of [Podium Air Android](https://github.com/kaizen-flims/Podium-Air), made with ❤️ by Prem. **0.3.0-preview.1 is published**, with the old Windows preview and Android releases preserved. **Google login, YouTube playback/downloads and remote playlist sync are not implemented.**

## In this preview

- Android Liquid Glass parameters, original licensed chromatic lens shader, native Skia backdrop blur, floating navigation/top controls/mini player, red accents, artwork background and source landscape controls/lyrics/queue panes. Windows layout is adapted; exact Android motion/pixel parity is not claimed.
- Embedded ID3 USLT/SYLT milliseconds, FLAC/Vorbis/Opus comments, M4A ©lyr/freeform and Android `BITCHORD_LYRICS`. Word-timed custom tags, TTML/LRC/UTF-16 sidecars, backing/duet timing, offsets, seeking and scrolling; untimed text is never given invented timing. Invalid/empty sidecars fall back to embedded tags.
- Native local MP3/WAV/AIFF/M4A/FLAC/Opus playback, playlists/library/albums/artists/history/favorites, duplicate-safe queue, shuffle/repeat, equalizer/rate/sleep and 0–12-second crossfade. Tagged MP3 is decoded by replaceable LGPL JLayer into a cancellable disk PCM cache after an observed JavaFX failure.
- Original neural Automix/DSP/planner, Beat This!/Open-unmix models and WSOLA; optional LRCLIB lyrics, ListenBrainz with Windows Credential Manager, Replay calendar filters/eight PNG story exports, and personal HTTPS WebDAV browsing/offline imports.
- Native Windows media session/keys, file/folder/save dialogs, Unicode paths and opt-in tray/startup/notifications.

## Download and acceptance

[Download the Windows x64 MSI](https://github.com/kaizen-flims/Podium-Air-Windows-/releases/download/v0.3.0-preview.1/Podium-Air-Windows-0.3.0-x64.msi). The [0.3.0 release](https://github.com/kaizen-flims/Podium-Air-Windows-/releases/tag/v0.3.0-preview.1) also contains an EXE installer, portable ZIP, matching app/dependency sources, notices, build evidence and SHA-256 checksums. The Java runtime is bundled; installers are unsigned. The [website](https://podium-air-website.pages.dev/) Windows buttons have been updated to this MSI; Android downloads are unchanged.

Released executable source `584160d88cb0be0a6f3409bd8feb5e09c3126a6d` passed the [integrated Windows build](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/38148562182), including actual tagged-file import/persistence/restore/playback and visible lyrics, glass/theme/player inspection, native integrations, six codecs and MSI install/launch/uninstall. The [publication run](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/38149562724) promoted these exact files without rebuilding. Windows cloud CI does not replace the user's own-file and physical-device acceptance. See the [build report](BUILD_REPORT.md).

## Remaining integration dependencies

A provider-approved native-audio/download API and registered desktop OAuth setup are required for the streaming/account core. Android uses private Innertube/extraction and WebView session cookies; these are not silently transplanted. See [provider decision](docs/STREAMING_PROVIDER_DECISION.md). Translation/canvas service integration, Last.fm configuration, official native Discord presence, shared listening and additional network-source protocols remain unfinished. No mock buttons substitute for them.

WebM/Matroska and MPEG-frame timestamp SYLT are unsupported. Some codecs use bounded 16-bit PCM; lossless device output and sample-accurate gaplessness are not claimed. Physical Windows 10/11, mixed DPI, sleep/resume, output hotplug and long-session acceptance remain distinct from Windows cloud CI.

## Build and license

Windows x64, JDK 21, CMake/MSVC/C++/WinRT and WiX 3 are required. Run `platform-windows/build.ps1`, then `gradlew.bat :app-desktop:run` or `:app-desktop:packageMsi :app-desktop:packageExe`. The GitHub workflow includes the pinned runtime, native build, renderer notices and corresponding dependency sources.

GPLv3 app combined with separately headed AGPLv3-or-later Automix components under section 13. Original contributors/model/runtime/library notices and full licenses ship in About and all source distributions. [Third-party notices](THIRD_PARTY_NOTICES.md), [feature matrix](FEATURE_PARITY.md), [build report](BUILD_REPORT.md), [acceptance guide](docs/WINDOWS_ACCEPTANCE.md).
