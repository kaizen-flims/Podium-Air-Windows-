# Porting audit — 9 October 2026

Source: `kaizen-flims/Podium-Air`, default branch `main`, commit
`48902e6b20fdcfeb1723e02d4744849e82d5067a` (Podium Air v1.0.1 revision 1).
Destination: `kaizen-flims/Podium-Air-Windows-`, initially empty. Main contains
only an initialization README; implementation lives on `feat/native-windows-port`.
The Android repository has not been modified.

The complete source-path/blob inventory is in `docs/android-source-inventory.json`.
The structural audit covered Gradle files, licenses, README, model definitions,
Theme, MainActivity, Home, Library, Settings, local media, queue shuffle,
crossfade, transition planning, settings, stream resolution, PO-token WebView
and lyric model/parsing. Remaining integrations were identified from their
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
| `data/lyrics/LyricLine`, `EnhancedLrc`, `BackgroundVocals`, `LyricGaps`, `LrcWriter` | Reusable as-is | Pure Kotlin, reused; tested word timing / entity decoding |
| `playback/QueueShuffle`, queue coordinator/history | Reusable after abstraction | Android Media3 player/control/session dependencies. Desktop entry identities preserve duplicate songs; reversible upcoming shuffle and queue mutations implemented independently |
| `ui/theme/Theme.kt` | Reusable after abstraction | Palette and typography sizes adapted; Android window/R-font code replaced. Source has both light/dark themes, white/black primary and red `#FA2D48` accent |
| Home / Explore / Search / Library / Detail / Player | Reusable after abstraction | Compose concepts retained, Android resource/activity/player bindings rewritten for desktop; local content only |
| Brand images | Reusable | Original mono mark and Glow Flow app icon retained; Windows icon derived |
| SF Pro Display OTF files | Blocked pending redistribution rights | Not bundled; desktop default system font used |
| `LocalMediaRepository` | Android-only / reimplement | MediaStore, URI permissions and MediaMetadataRetriever replaced with filesystem picker/scanner and jaudiotagger |
| `AppSettings`, SearchHistory, LastPlayed, ListeningStats | Android-only persistence bindings | Atomic JSON store, preferences, favorites and recent history implemented. Full Replay statistics remain pending |
| PlaybackService / PlayerConnection / audio sink / output routing | Android-only / reimplement | JavaFX native audio backend on JVM, single-thread lifecycle, reactive StateFlow; Windows JavaFX native libraries packaged |
| `CrossfadeController` | Android-only orchestration | Two-player equal-power overlap implemented for local tracks at 1×; not claiming sample-accurate gaplessness |
| `smart/TransitionPlanner.kt` | Reusable after abstraction + separate license | Orchard AGPLv3-or-later header within GPL source. Not copied into this port |
| Automix analyzer / WSOLA / on-device model | Reimplement / pending | Android decoding, JNI/model distribution and performance need separate audit. Automix not simulated |
| YtMusicRepository / Innertube / StreamResolver / PO tokens | Blocked pending compatible supported provider integration | Private API/extraction pipeline and Android WebView path not ported under the prompt's provider-policy constraint |
| Google / Discord auth, EncryptedPrefs | Android-only / pending supported API | No cookies or credentials collected. No desktop auth/session sync claim |
| Sources, addon modules, JioSaavn, WebDAV, SMB | Pending provider/dependency audit and implementation | Not connected or advertised as functional |
| Canvas Apple/Spotify/Tidal/community | Pending asset/API rights and player integration | Local embedded still artwork only |
| Online lyrics providers / translation / TTML | Pending API rights and implementation | Local sidecar LRC / enhanced LRC / embedded text only |
| DownloadService / Downloader / offline DASH/HLS | Android-only + provider policy | No streaming download feature in Windows preview |
| Last.fm / ListenBrainz / Discord RPC / Listen Together | Pending desktop integration | Explicit gaps recorded in parity checklist |
| Widgets / Android Auto / foreground notifications / haptics | Android-only | Desktop file chooser, standard window controls and AWT system tray provided; SMTC pending |

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

`shared-domain`: original Kotlin models/lyrics plus platform-independent queue.
`app-desktop`: desktop state/data adapter, `AudioEngine` interface and JavaFX
implementation, Compose UI and native AWT shell. Two modules are sufficient
for the current local preview; empty modules for unimplemented services would
not add functionality. StatePersistence and AudioEngine are injectable in tests.

Filesystem and library writes run on IO coroutines; mutable UI/queue commands
run on Swing EDT; MediaPlayer operations are confined to JavaFX thread. Tokens
and credential storage are deferred because no authenticated provider exists.
No automatic updates or Android release actions are present.
