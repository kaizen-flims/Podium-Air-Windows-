# Podium Air — Windows Edition

Native Kotlin/Compose Windows adaptation of [Podium Air Android](https://github.com/kaizen-flims/Podium-Air), made with ❤️ by Prem. This development branch prepares **0.3.0-preview.1**; the published 0.2.0 preview and Android releases are preserved. **Google login, YouTube playback/downloads and remote playlist sync are not implemented.**

## In this preview

- Android Liquid Glass parameters, original licensed chromatic lens shader, native Skia backdrop blur, floating navigation/top controls/mini player, red accents, artwork background and source landscape controls/lyrics/queue panes. Windows layout is adapted; exact Android motion/pixel parity is not claimed.
- Embedded ID3 USLT/SYLT milliseconds, FLAC/Vorbis/Opus comments, M4A ©lyr/freeform and Android `BITCHORD_LYRICS`. Word-timed custom tags, TTML/LRC/UTF-16 sidecars, backing/duet timing, offsets, seeking and scrolling; untimed text is never given invented timing. Invalid/empty sidecars fall back to embedded tags.
- Native local MP3/WAV/AIFF/M4A/FLAC/Opus playback, playlists/library/albums/artists/history/favorites, duplicate-safe queue, shuffle/repeat, equalizer/rate/sleep and 0–12-second crossfade. Tagged MP3 is decoded by replaceable LGPL JLayer into a cancellable disk PCM cache after an observed JavaFX failure.
- Original neural Automix/DSP/planner, Beat This!/Open-unmix models and WSOLA; optional LRCLIB lyrics, ListenBrainz with Windows Credential Manager, Replay calendar filters/eight PNG story exports, and personal HTTPS WebDAV browsing/offline imports.
- Native Windows media session/keys, file/folder/save dialogs, Unicode paths and opt-in tray/startup/notifications.

## Download and acceptance

The release pipeline publishes [0.3.0-preview.1](https://github.com/kaizen-flims/Podium-Air-Windows-/releases/tag/v0.3.0-preview.1) only after building EXE/MSI/portable packages, verifying the integrated app and reviewing actual screenshots. Until publication succeeds, use the existing [0.2.0 preview](https://github.com/kaizen-flims/Podium-Air-Windows-/releases/tag/v0.2.0-preview.1). The Java runtime is bundled; installers are unsigned.

The integrated tagged-audio checkpoint at `4285397150ffd33ce5602759248802abbaef403e` imported/persisted/restored/played/paused real MP3 USLT/SYLT, FLAC enhanced lyrics, Opus comments and M4A ©lyr, and rendered them in both themes. These are generated tagged audio files, not lyric text injected into the UI. [Windows run](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/38147720644). A final release build follows the remaining glass capture/theme correction. The user's own audio file still needs acceptance.

## Remaining integration dependencies

A provider-approved native-audio/download API and registered desktop OAuth setup are required for the streaming/account core. Android uses private Innertube/extraction and WebView session cookies; these are not silently transplanted. See [provider decision](docs/STREAMING_PROVIDER_DECISION.md). Translation/canvas service integration, Last.fm configuration, official native Discord presence, shared listening and additional network-source protocols remain unfinished. No mock buttons substitute for them.

WebM/Matroska and MPEG-frame timestamp SYLT are unsupported. Some codecs use bounded 16-bit PCM; lossless device output and sample-accurate gaplessness are not claimed. Physical Windows 10/11, mixed DPI, sleep/resume, output hotplug and long-session acceptance remain distinct from Windows cloud CI.

## Build and license

Windows x64, JDK 21, CMake/MSVC/C++/WinRT and WiX 3 are required. Run `platform-windows/build.ps1`, then `gradlew.bat :app-desktop:run` or `:app-desktop:packageMsi :app-desktop:packageExe`. The GitHub workflow includes the pinned runtime, native build, renderer notices and corresponding dependency sources.

GPLv3 app combined with separately headed AGPLv3-or-later Automix components under section 13. Original contributors/model/runtime/library notices and full licenses ship in About and all source distributions. [Third-party notices](THIRD_PARTY_NOTICES.md), [feature matrix](FEATURE_PARITY.md), [build report](BUILD_REPORT.md), [acceptance guide](docs/WINDOWS_ACCEPTANCE.md).
