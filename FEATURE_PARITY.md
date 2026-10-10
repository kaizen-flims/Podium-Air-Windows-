# Feature parity

**Verified** means recorded passing automated evidence for the stated scope.
**Partial** means implemented but missing source behavior or broader device
acceptance. **Blocked** identifies a supported integration or missing pipeline.
A passing smoke is not proof of every feature or full Android parity.

Development commit `1b12da522baaf920f8a33820c69087c649e5ea1a` passed
[run 38064057032](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/38064057032):
**58 tests, zero failures/errors**, actual bundled neural inference and native
front ends, packaged Automix/Replay export, all six codec PCM conversions and
existing Windows UI/integration/installer gates. This is a tested development
build, not a complete streaming release. ListenBrainz/native credential storage
is implemented in the following commit and awaits its eight added tests and
packaged credential gate.

Published-preview evidence: Windows run
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
| Native Compose window/navigation | Verified | Packaged Windows render across 15 routes plus populated playlist/album detail; consumer client acceptance remains |
| Home/Explore/Search | Partial | Real imported recents/favorites/albums/artists and local search; personalized provider feed/search unavailable |
| Library/favorites/history | Partial | Atomic local state and actual-start history; coordinator/state tests pass; large-collection interaction remains |
| Playlist CRUD/reorder/duplicates | Partial | Persisted local playlists with queue-compatible duplicates; remote sync unavailable |
| M3U8/M3U interchange | Verified | Round-trip duplicate/unicode/local-path tests; blocked remote URLs reported; interactive picker acceptance remains |
| Album/artist detail | Partial | Album + album-artist identity and disc/track ordering; tagged local content; remote pages unavailable |
| Now Playing/mini player/controls | Partial | Packaged real play/pause/seek/resume; volume changed mid-crossfade; physical output/volume assessment remains |
| Queue/repeat/shuffle | Verified | Duplicate-safe entry IDs, mutation/current selection, repeat and reversible upcoming shuffle tests; stale callbacks covered |
| Crossfade | Partial | Real two-player equal-power 0–12s overlap at 1× verified across six format fixtures; no sample-accurate gaplessness or beat alignment |
| Automix/beat/tempo transitions | Partial | Original AGPL planner/native DSP/mel/STFT and bundled Beat This!/Open-unmix models; confidence-based cues/EQ, bounded WSOLA and original-track seek. 58-test/model and actual packaged transition gates passed; cloud evidence does not establish all Android behavior or consumer listening quality |
| Speed/equalizer/sleep | Partial | Actual JavaFX rate/ten-band EQ and monotonic pause timer; listening/device acceptance remains |
| TTML/LRC/embedded lyrics | Partial | Original TTML/alignment/focus/clock reused; words/duets/backing vocals, per-track offsets, click-to-seek, autoscroll and reduced motion; parser/clock/security tests pass |
| Word motion/artwork background | Partial | Timed growth/lift/bloom and dominant embedded-art palette motion; faithful source pixel/motion parity remains |
| Online lyrics | Partial | Opt-in documented LRCLIB provider, validated title/artist/duration, bounded response/cache and cancellation. Five actual local HTTP contract tests passed; live catalog availability and other source providers are not guaranteed |
| Lyrics translation/canvas video | Blocked | Source translation/canvas provider rights/auth and native video implementation remain unresolved |
| Local formats | Verified | Packaged real controls/crossfade in WAV, FLAC, Opus, MP3, AAC/M4A and AIFF; documented mono/stereo/PCM/cache/container limits |
| Replay/statistics | Partial | Real elapsed-time 7/30/365-day tracks/artists/minutes/starts; pause/seek/suspension and repeat/completed-restart tests; 1080×1920 frozen summary PNG preview/native save/atomic write verified; full annual story-card family missing |
| Google/YouTube streaming/downloads/sync | Blocked | Private source API/extraction pipeline not ported under locked supported-provider requirement; official service opens in browser with no sync claim |
| ListenBrainz | Partial | Documented API, audible-time thresholds, opt-in account UI and native credential storage implemented; eight additional contract/state/native tests and packaged credential gate pending; real-account end-to-end acceptance remains |
| Last.fm/Discord/Listen Together | Blocked | Supported desktop login/RPC/shared-session implementations and application/service configuration remain missing |
| Network library sources | Partial | Existing accessible mapped/UNC folders can be imported; WebDAV/SMB account clients/addons not implemented |
| Native SMTC | Verified | C++ real Windows session metadata/status/pause/seek self-test and packaged helper test; Kotlin command coordinator tests; physical media keys remain |
| Keyboard/picker/tray/notifications/startup | Partial | Actual implementation and opt-in preferences; Robot Ctrl+F/play/pause and actual native Unicode file/folder/save/cancellation passed; opt-in tray/startup/notifications need physical acceptance |
| DPI/resizing/accessibility | Partial | Compact icon navigation, bounded/maximized initial window, labels/shortcuts/scrolling; 100/150/200% startup screenshots inspected; physical/mixed-monitor DPI remains |
| State persistence/recovery | Verified | Atomic saves, corrupt original preservation, restore filtering, duplicate normalization/offset clamps and ordered shutdown-save tests |
| CPU/memory/shutdown/device routing | Partial | Cancellable jobs and disposed player/helper lifecycle; paused sample 1.04% of one core, peak 237.75 MB including direct helper; physical hotplug/long-run leak checks remain |
| Windows installer/portable/source | Verified | MSI/EXE/portable and corresponding source produced; MSI install/run/uninstall verified on Windows Server 2025; unsigned/client acceptance remains |

Screens visibly disclose missing Google/streaming functionality and actual
Automix/lyrics/scrobbling status. No playback,
streaming, auth, downloads or statistics are simulated to imply parity. Source
branding, icons and dark/light colors are retained; restricted SF Pro fonts are
replaced with platform typography. Physical Windows 10/11 device listening,
keyboard/tray/picker/startup, hotplug and full source animation parity require
acceptance beyond the cloud runner.
