# Feature parity

**Verified** means recorded passing automated evidence for the stated scope.
**Partial** means implemented but missing source behavior or broader device
acceptance. **Blocked** identifies a supported integration or missing pipeline.
A passing smoke is not proof of every feature or full Android parity.

Published executable code `584160d88cb0be0a6f3409bd8feb5e09c3126a6d` passed
[run 38148562182](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/38148562182):
**89 tests, zero failures/errors/skips**, actual tagged-file playback/lyrics, Liquid Glass theme/artwork capture, bundled neural inference, measured
Automix/seek, all eight Replay exports, secure scrobbling/WebDAV contract tests,
packaged WebDAV download/playback and every existing native/UI/installer gate.
The shipped portable JAR/models/notices and matching source were inspected.
The [0.3.0 preview](https://github.com/kaizen-flims/Podium-Air-Windows-/releases/tag/v0.3.0-preview.1) contains these exact files. Full streaming/Android parity remains incomplete.

Historical 0.2-preview evidence: Windows run
[37981495583](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37981495583),
commit `ad91ba8ac4f960330c5986698072f34441be7c48`, **40 tests with zero
failures/errors**. This run passed all packaged routes/details, Robot
Ctrl+F/Ctrl+Space, native and packaged Windows media/dialog checks, simulated
150/200% startup, six codec natural-end checks, missing-file recovery,
paused resource/shutdown checks and MSI install/run/uninstall.

Fresh route and DPI screenshots were reviewed. Navigation/content/mini player
fit the visible area, short layouts scroll, and Settings exposes volume when
compact controls hide its mini-player slider. Queue callback/restart statistics
and concurrent final-save regressions passed. The source/package artifact is
[available here](https://github.com/kaizen-flims/Podium-Air-Windows-/releases/tag/v0.2.0-preview.1);
[verification reports and screenshots](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37981495583/artifacts/11640779965)
record the results.

| Feature | Status | Implementation and evidence |
|---|---|---|
| Native Compose window/navigation | Verified | Packaged Windows render across 16 routes plus populated playlist/album detail; consumer client acceptance remains |
| Home/Explore/Search | Partial | Real imported recents/favorites/albums/artists and local search; personalized provider feed/search unavailable |
| Library/favorites/history | Partial | Atomic local state and actual-start history; coordinator/state tests pass; large-collection interaction remains |
| Playlist CRUD/reorder/duplicates | Partial | Persisted local playlists with queue-compatible duplicates; remote sync unavailable |
| M3U8/M3U interchange | Verified | Round-trip duplicate/unicode/local-path tests; blocked remote URLs reported; interactive picker acceptance remains |
| Album/artist detail | Partial | Album + album-artist identity and disc/track ordering; tagged local content; remote pages unavailable |
| Now Playing/mini player/controls | Partial | Source landscape panes/1100dp cap, Controls/Lyrics/Queue selection, dedicated volume and 0.88 paused sleeve scale/500ms easing; packaged light/dark, reduced-motion, playing/paused and compact/DPI variants passed. Physical output/volume and remaining source UI behavior remain |
| Queue/repeat/shuffle | Verified | Duplicate-safe entry IDs, mutation/current selection, repeat and reversible upcoming shuffle tests; stale callbacks covered |
| Crossfade | Partial | Real two-player equal-power 0–12s overlap at 1× verified across six format fixtures; no sample-accurate gaplessness or beat alignment |
| Automix/beat/tempo transitions | Partial | Original AGPL planner/native DSP/mel/STFT and bundled Beat This!/Open-unmix models; confidence-based cues/EQ, bounded WSOLA and original-track seek. 84-test/model and actual packaged transition gates passed; cloud evidence does not establish all Android behavior or consumer listening quality |
| Speed/equalizer/sleep | Partial | Actual JavaFX rate/ten-band EQ and monotonic pause timer; listening/device acceptance remains |
| TTML/LRC/embedded lyrics | Partial | New container reader: ID3 USLT/SYLT milliseconds, FLAC/Vorbis/Opus comments, M4A ©lyr/freeform, custom Android word tags and UTF-16 sidecars; empty/invalid sidecars fall back. Real tagged fixtures imported/restored/played/paused and rendered in both themes at 4285397. User file acceptance remains.  Original TTML/alignment/focus/clock reused; words/duets/backing vocals, per-track offsets, click-to-seek, autoscroll and reduced motion; parser/clock/security tests pass |
| Word motion/artwork background | Partial | Timed growth/lift/bloom; original three-pass 128px full-artwork blur, eight-cover cache and finite bitmap fades with reduced motion. Five neutral/spatial-color/cache/bounded-input tests passed; portrait mesh/shader, video canvas and exact source motion parity remain |
| Online lyrics | Partial | Opt-in documented LRCLIB provider, validated title/artist/duration, bounded response/cache and cancellation. Five actual local HTTP contract tests passed; live catalog availability and other source providers are not guaranteed |
| Lyrics translation/canvas video | Blocked | Source translation/canvas provider rights/auth and native video implementation remain unresolved |
| Local formats | Verified | Packaged real controls/crossfade in WAV, FLAC, Opus, MP3, AAC/M4A and AIFF; documented mono/stereo/PCM/cache/container limits |
| Replay/statistics | Partial | Real elapsed-time 7/30/365-day tracks/artists/minutes/starts; pause/seek/suspension and repeat/completed-restart tests; 1080×1920 frozen summary PNG preview/native save/atomic write verified; all eight source story PNG types and month/year/all-time filters verified, with real hourly habits and backward-compatible records; desktop picker/preview replaces the mobile story gestures |
| Google/YouTube streaming/downloads/sync | Blocked | Private source API/extraction pipeline not ported under locked supported-provider requirement; official service opens in browser with no sync claim |
| Google desktop authorization / YouTube playlist metadata | Partial | 0.4.0 adds official external-browser PKCE/loopback sign-in, refresh/disconnect/native credential handling, read-only paginated playlist/item browsing and owner Desktop-client JSON setup. Live project/client/account acceptance is unverified; no native YouTube audio or playlist write/full Music sync claim. See docs/GOOGLE_DESKTOP_SETUP.md |
| ListenBrainz | Partial | Documented API, audible-time thresholds, opt-in account UI and native credential storage implemented; eight state/HTTP/native tests and packaged credential read/write/delete passed; real-account end-to-end acceptance remains |
| Last.fm/Discord/Listen Together | Blocked | Supported desktop login/RPC/shared-session implementations and application/service configuration remain missing |
| Network library sources | Partial | Mapped/UNC folders and HTTPS WebDAV browsing/selected offline import implemented; nine actual HTTP/state tests and packaged authenticated Unicode download/play/pause/seek/end passed. One saved account, bounded folder/import sizes; direct remote streaming, upload, sidecar covers, direct SMB accounts/addons and real-server acceptance remain |
| Native SMTC | Verified | C++ real Windows session metadata/status/pause/seek self-test and packaged helper test; Kotlin command coordinator tests; physical media keys remain |
| Keyboard/picker/tray/notifications/startup | Partial | Actual implementation and opt-in preferences; Robot Ctrl+F/play/pause and actual native Unicode file/folder/save/cancellation passed; opt-in tray/startup/notifications need physical acceptance |
| DPI/resizing/accessibility | Partial | Compact icon navigation, bounded/maximized initial window, labels/shortcuts/scrolling; 100/150/200% startup screenshots inspected; physical/mixed-monitor DPI remains |
| State persistence/recovery | Verified | Atomic saves, corrupt original preservation, restore filtering, duplicate normalization/offset clamps and ordered shutdown-save tests |
| CPU/memory/shutdown/device routing | Partial | Cancellable jobs and disposed player/helper lifecycle; development paused expanded-player sample 0.69% of one core, peak 266.14 MB including direct helper; physical hotplug/long-run leak checks remain |
| Windows installer/portable/source | Verified | MSI/EXE/portable and corresponding source produced; MSI install/run/uninstall verified on Windows Server 2025; unsigned/client acceptance remains |

Screens visibly disclose missing Google/streaming functionality and actual
Automix/lyrics/scrobbling status. No playback,
streaming, auth, downloads or statistics are simulated to imply parity. Source
branding, icons and dark/light colors are retained; restricted SF Pro fonts are
replaced with platform typography. Physical Windows 10/11 device listening,
keyboard/tray/picker/startup, hotplug and full source animation parity require
acceptance beyond the cloud runner.

## 0.3.0 integrated release

Native Liquid Glass uses the compatible Apache-2.0 KMP backdrop renderer and the original Android Kyant0/backdrop chromatic shader. Saturation 1.5, blur 8dp, refraction height/amount 24dp, tint 0.4 and theme highlights match the Android integration. Full-resolution Windows processing replaces Android's 0.33 optimization. Floating chrome is excluded from its own backdrop capture; current-track artwork extends behind glass. Theme/blur changes recreate cached native effect layers. The final release screenshots were inspected in both themes; tagged MP3/FLAC/Opus/M4A playback and lyric UI passed. Paused player/helper use measured 0.35% of one logical core and 271.05 MB peak sampled working set. Noctis/Tunetastic were audited but not copied.

The source for account/online discovery/search/downloads still calls private `data/innertube/Innertube.kt` and `auth/YtMusicLoginScreen.kt` cookie capture. `data/lyrics/LyricsTranslation.kt` uses non-Cloud translation endpoints; canvas sources impersonate service mobile clients. `data/discord/DiscordRPC.kt` uses personal bearer tokens. None is silently substituted into a supported desktop account implementation. Registered app/provider permission and protocol selection remain required for the corresponding integrations.
