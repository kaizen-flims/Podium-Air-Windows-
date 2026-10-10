# Porting audit — updated 10 October 2026

Source: `kaizen-flims/Podium-Air`, default branch `main`, commit
`48902e6b20fdcfeb1723e02d4744849e82d5067a` (Podium Air v1.0.1 revision 1).
Destination: `kaizen-flims/Podium-Air-Windows-`, initially empty. The original
audit initialized main with a README and implemented the app on
`feat/native-windows-port`. Prem has since authorized publishing the Windows
source and public preview assets; publication requires a successful Windows build.
The Android repository has not been modified.

The complete source-path/blob inventory is in `docs/android-source-inventory.json`.
The structural audit covered Gradle files, licenses, README, model definitions,
Theme, MainActivity, Home, Library, Settings, local media, queue shuffle,
crossfade, transition planning, settings, stream resolution, PO-token WebView, TTML/duet parsing, lyric clock/focus, listening stats,
Automix analysis/planning dependencies and native beat-model assets. Remaining integrations were identified from their
source paths, not assumed to have been fully reviewed line by line.

## Architecture and portability

The source is one Android `:app` module, Kotlin/Compose with Android Gradle
Plugin, SDK 37, JDK 17 bytecode and native CMake libraries. MainActivity is a
large Android orchestration layer. Services, Android framework classes, R
resources, encrypted preferences, MediaStore and Media3 cross module boundaries;
simply changing the Gradle target would not produce a desktop app.

| Source area | Classification | Desktop decision / evidence |
|---|---|---|
| `data/model/Models.kt` | Reusable as-is | Pure Kotlin, reused with original packages and comments |
| `data/lyrics/LyricLine`, `EnhancedLrc`, `BackgroundVocals`, `LyricGaps`, `LrcWriter`, `TtmlLyrics`, `LyricAlignments` | Reusable as-is | Pure Kotlin, reused; tested word timing, TTML duet/background vocals, entity decoding and external-entity rejection |
| `playback/QueueShuffle`, queue coordinator/history | Reusable after abstraction | Android Media3 player/control/session dependencies. Desktop entry identities preserve duplicate songs; reversible upcoming shuffle and queue mutations implemented independently |
| `ui/theme/Theme.kt` | Reusable after abstraction | Palette and typography sizes adapted; Android window/R-font code replaced. Source has both light/dark themes, white/black primary and red `#FA2D48` accent |
| Home / Explore / Search / Library / Detail / Player | Reusable after abstraction | Compose concepts retained, Android resource/activity/player bindings rewritten for desktop; local content only |
| Brand images | Reusable | Original mono mark and Glow Flow app icon retained; Windows icon derived |
| SF Pro Display OTF files | Blocked pending redistribution rights | Not bundled; desktop default system font used |
| `LocalMediaRepository` | Android-only / reimplement | MediaStore, URI permissions and MediaMetadataRetriever replaced with Windows Common Item Dialogs, cancellable filesystem scanning and jaudiotagger |
| `AppSettings`, SearchHistory, LastPlayed, ListeningStats | Android-only persistence bindings | Atomic JSON store, preferences, favorites and recent history implemented. Elapsed-time recorder and 7/30/365-day Replay implemented; summary PNG preview/export is now ported; the complete annual story-card family remains missing |
| PlaybackService / PlayerConnection / audio sink / output routing | Android-only / reimplement | JavaFX native audio backend on JVM, single-thread lifecycle, reactive StateFlow; Windows JavaFX native libraries packaged |
| `CrossfadeController` | Android-only orchestration | Two-player equal-power overlap implemented for local tracks at 1×; not claiming sample-accurate gaplessness |
| `smart/TransitionPlanner.kt` | Reusable after abstraction + separate license | Original policy/planner/analysis reused with AGPLv3-or-later headers and bundled license; source obligations retained under section 13 |
| Automix analyzer / WSOLA / on-device model | Reused/adapted and tested | Original native DSP, mel/STFT front ends, Beat This!/Open-unmix adapters and exact model bytes; custom bounded pitch-preserving desktop WSOLA; actual model/PCM and packaged transition checks passed 58-test run 38064057032 |
| YtMusicRepository / Innertube / StreamResolver / PO tokens | Blocked pending compatible supported provider integration | Private API/extraction pipeline and Android WebView path not ported under the prompt's provider-policy constraint |
| Google / Discord auth, EncryptedPrefs | Android-only / pending supported API | No cookies or credentials collected. No desktop auth/session sync claim |
| Sources, addon modules, JioSaavn, WebDAV, SMB | Pending provider/dependency audit and implementation | Not connected or advertised as functional |
| Canvas Apple/Spotify/Tidal/community | Pending asset/API rights and player integration | Embedded artwork, extracted dominant-color animated background and reduced-motion setting implemented; online canvas video is not connected |
| TTML / lyric clock / lyric focus | Reusable after minimal abstraction | Original parser, alignments and clock reused; clock/focus visibility made public across modules. Word growth, lift, bloom, overlaps, backing vocals, local offsets and click-to-seek implemented |
| Online lyrics providers / translation | Partial | Optional documented LRCLIB API with strict metadata/duration matching, bounded bodies, cancellation and offline cache; five local HTTP contract tests passed. Other providers/translation remain missing |
| DownloadService / Downloader / offline DASH/HLS | Android-only + provider policy | No streaming download feature in Windows preview |
| ListenBrainz | Adapted, additional checks pending | Documented API with actual audible-time thresholds, one-request-per-second pacing and rate headers; Windows Credential Manager stores user tokens. No real-account submission has been tested |
| Last.fm / Discord RPC / Listen Together | Pending supported desktop integration | Last.fm application setup, official native Discord RPC/application ID and a supported shared-listening service contract remain missing; Android Discord account-token gateway impersonation is not copied |
| Widgets / Android Auto / foreground notifications / haptics | Android-only | Desktop file chooser, standard window controls, AWT tray, optional notifications/startup and native Windows SMTC provided; physical media-key/device checks remain |

## Provider boundary

The source README advertises YouTube Music audio streaming, background playback
and downloads. The official YouTube API policies prohibit isolating audio,
background playback and unapproved downloads for API clients. The source
extraction path is not a supported equivalent for this build under the locked
prompt. It has not been represented as legally approved merely because the
code is open source. This blocks full streaming/account parity; local playback
is the explicitly authorized baseline. Account → Open YouTube Music uses the
user's normal browser and does not claim app sync.

Primary references checked:
- https://developers.google.com/youtube/terms/developer-policies
- https://developers.google.com/youtube/terms/developer-policies-guide
- https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html
- https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html
- https://openjfx.io/javadoc/21/javafx.media/javafx/scene/media/package-summary.html

## Chosen module boundaries

`shared-domain`: original Kotlin models/lyrics, clock/focus and platform-independent queue.
`app-desktop`: desktop state/data adapter, `AudioEngine` interface and JavaFX
implementation, bounded filesystem/metadata/state/codec adapters, Compose UI
and native AWT shell. `platform-windows` contains a separately compiled C++20
SMTC helper, packaged as a resource and extracted by content hash. Two Gradle modules are sufficient
for the current local preview; empty modules for unimplemented services would
not add functionality. StatePersistence and AudioEngine are injectable in tests.

Filesystem and library writes run on IO coroutines; mutable UI/queue commands
run on Swing EDT; MediaPlayer operations are confined to JavaFX thread. Google tokens and account sync remain deferred. ListenBrainz tokens use the
current Windows user's Credential Manager through a narrowly scoped JNI API;
no token is placed in the library state, logs, URLs or process arguments.
No automatic updates or Android release actions are present.

## Decoder and Windows integration assessment

JavaFX supplies native playback, volume, rate and equalizer. To avoid depending
on Windows optional AAC components, FLAC/Opus/AAC are decoded on cancellable IO
workers to temporary 16-bit WAV and cached on disk. Exact published unmodified
jFLAC 1.5.2, Concentus 1.0.2 and JAAD 0.8.7 JARs provide the codec algorithms;
new bounded Ogg and ISO-BMFF readers handle containers. Protected/external/
fragmented or unsupported MP4 variants and Ogg Vorbis/multichannel/chained Opus
are rejected with real errors. FLAC above 16 bits loses precision in this
pipeline; no lossless high-resolution or sample-accurate gapless claim is made.
The cache uses source path/size/mtime identities and a 750 MB per-track limit.
It prunes older WAVs before decoding when existing cache reaches 1.5 GB; this
is a pruning threshold, not a strict total-size cap.

The C++ helper owns a hidden HWND and publishes a real Windows system media
session. It handles metadata, artwork, timeline, play/pause, previous/next,
stop and seek through a bounded line protocol. CI's native self-test queries
the session manager and requests actual pause/seek callbacks. Kotlin command
coordinator tests cover the bridge's commands; physical keyboard behavior
still requires client-device acceptance. No global keyboard hook is installed.

All newly included decoder and C++/WinRT notices are bundled in the app; exact
decoder source JARs accompany the package. The original AGPL Automix planner,
DSP and model adapters are now included with notices and matching source. The
exact pinned Android Beat This! and Open-unmix weights and CPU ONNX Runtime
have their own MIT/third-party notices. JavaFX overlap supplies the two-player
handoff; measured DSP/neural analysis and WSOLA supply the transition evidence
and pitch-preserving preparation.

The Windows file/folder/import/export picker now uses COM `IFileOpenDialog` and
`IFileSaveDialog` in a separate STA helper mode. The dialog is owned by the
visible window found within the exact parent process, supports filesystem-only
multiselect/folder selection and overwrite confirmation, and returns UTF-8
paths as bounded hex lines. Cancellation/parent shutdown kills the helper and
removes the temporary response. Swing choosers remain only for non-Windows
development. Native Unicode file/folder/save selection, cancellation, packaged JVM protocol
and cancelling an open picker without leaving a helper passed run 37957264531.

## Supported scrobbling reference

ListenBrainz integration follows https://listenbrainz.readthedocs.io/en/latest/users/api/core.html
and https://listenbrainz.readthedocs.io/en/latest/users/api/index.html. It validates
a user token through an Authorization header, serializes requests at no more
than one per second, handles rate-limit reset headers, and sends only actual
qualifying listens. The UI reports failed submissions; offline retries are not
implemented. Windows native storage uses CredWriteW/CredReadW/CredDeleteW for
`PodiumAirWindows/` generic credentials in the current user's credential set:
https://learn.microsoft.com/en-us/windows/win32/api/wincred/nf-wincred-credwritew.
