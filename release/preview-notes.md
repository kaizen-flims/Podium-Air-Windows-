Podium Air Windows 0.3.0 preview — made with ❤️ by Prem. A native Kotlin/Compose adaptation of Podium Air Android, with its licensed Liquid Glass rendering and enhanced local music features. **This preview does not implement Google login, YouTube streaming/downloads or account playlist sync.**

Download **Podium-Air-Windows-0.3.0-x64.msi**, the setup EXE, or the portable ZIP. The Java runtime is included. Installers are unsigned. Version 0.2.0 and Android release/update channels are preserved.

What changed:
- Actual backdrop blur, saturation, theme tint, highlights and the original Android chromatic lens shader in floating navigation, top controls and mini player; red selection accents, light/dark themes, reduced blur and reduced motion. Full-resolution Skia processing replaces Android's reduced-resolution optimization. Remaining layout and motion differences are disclosed; pixel-exact Android parity is not claimed.
- Embedded ID3 USLT and millisecond SYLT, FLAC/Vorbis/Opus comments, M4A ©lyr and Android BITCHORD_LYRICS freeform/comment fields. Custom enhanced lyrics retain word timing. Empty or invalid sidecars fall back to tags; UTF-8/UTF-16 sidecars and untimed text work without invented synchronization. Real tagged audio is imported, restored, played/paused and rendered in both themes in the packaged UI. The user's own file still needs hands-on acceptance.
- Tagged MP3 uses the separately licensed JLayer decoder to prepare PCM; this fixes an observed JavaFX rejection of real USLT/SYLT-tagged files. Preparation runs in a cancellable background task.
- Original neural Automix/planner/DSP, Beat This!/Open-unmix models and bounded WSOLA tempo correction; original-track seeking and measured transition scheduling. Ordinary 0–12-second crossfade remains available.
- Optional LRCLIB lyrics, ListenBrainz with Windows Credential Manager, all eight Replay story cards/calendar periods and PNG sharing; read-only personal HTTPS WebDAV browsing and selected offline imports; native media keys/dialogs, playlists, queue, local albums/artists, history, EQ, rate and sleep timer.

Limits:
- The source's private YouTube extraction/cookie pipeline is not ported. A provider-authorized native playback/download integration and registered desktop OAuth setup are still needed. Translation/canvas service integration, Last.fm app setup, official Discord presence, shared listening, remaining network addons and complete Android UI/motion parity remain unfinished.
- WebM/Matroska audio and MPEG-frame-timestamp SYLT are not supported; protected/encrypted media is not decoded. Supported playback: MP3, WAV, AIFF, M4A/AAC, FLAC and Opus. Some formats are prepared as bounded 16-bit PCM. Direct WebDAV streaming/uploads and direct SMB account clients are not included.
- Windows cloud CI is distinct from physical Windows 10/11 acceptance. Test installation, your embedded-lyrics file, output-device switching, sleep/resume, mixed DPI, media keys and long sessions on your hardware. Exact gaplessness and full lossless output are not claimed.

GPLv3 application combined with separately headed AGPLv3-or-later Automix components. Corresponding application/dependency sources, model/native/dependency notices and SHA256 hashes accompany the binary assets.
