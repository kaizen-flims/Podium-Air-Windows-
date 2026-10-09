# Feature parity

**Verified** means recorded passing automated evidence for the stated scope.
**Partial** means implemented but missing source behavior or broader device
acceptance. **Blocked** identifies a supported integration or missing pipeline.
A passing smoke is not proof of every feature or full Android parity.

Expanded integration evidence: Windows run
[37957264531](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37957264531),
source commit `8cb187791faa6013c55be33c98a79a595e833ba7`.
This run also passed Robot Ctrl+F/Ctrl+Space, simulated 150/200% startup,
paused resource/shutdown checks and MSI install/run/uninstall. Fresh screenshots
were inspected: navigation/content/mini player stay within the visible area;
short layouts scroll. All six codecs reached natural end; missing-file recovery, bounded MP4 tables/
references/descriptors and cancelled-pick cleanup passed. Native Unicode
file/folder/save selection and JVM protocol checks also passed. Follow-up
repeat-one statistics and shutdown-save regression tests await the final run.

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
| Automix/beat/tempo transitions | Blocked | Android/JNI/model analysis and WSOLA pipeline not adapted; AGPL obligations require deliberate reuse work |
| Speed/equalizer/sleep | Partial | Actual JavaFX rate/ten-band EQ and monotonic pause timer; listening/device acceptance remains |
| TTML/LRC/embedded lyrics | Partial | Original TTML/alignment/focus/clock reused; words/duets/backing vocals, per-track offsets, click-to-seek, autoscroll and reduced motion; parser/clock/security tests pass |
| Word motion/artwork background | Partial | Timed growth/lift/bloom and dominant embedded-art palette motion; faithful source pixel/motion parity remains |
| Online lyrics/translation/canvas video | Blocked | No supported remote provider implementation or animated video integration |
| Local formats | Verified | Packaged real controls/crossfade in WAV, FLAC, Opus, MP3, AAC/M4A and AIFF; documented mono/stereo/PCM/cache/container limits |
| Replay/statistics | Partial | Real elapsed-time 7/30/365-day tracks/artists/minutes/starts; pause/seek/suspension tests; source annual share cards missing |
| Google/YouTube streaming/downloads/sync | Blocked | Private source API/extraction pipeline not ported under locked supported-provider requirement; official service opens in browser with no sync claim |
| Last.fm/ListenBrainz/Discord/Listen Together | Blocked | Credentials/protocol adaptation and actual desktop implementation missing |
| Network library sources | Partial | Existing accessible mapped/UNC folders can be imported; WebDAV/SMB account clients/addons not implemented |
| Native SMTC | Verified | C++ real Windows session metadata/status/pause/seek self-test and packaged helper test; Kotlin command coordinator tests; physical media keys remain |
| Keyboard/picker/tray/notifications/startup | Partial | Actual implementation and opt-in preferences; Robot Ctrl+F/play/pause and actual native Unicode file/folder/save/cancellation passed; opt-in tray/startup/notifications need physical acceptance |
| DPI/resizing/accessibility | Partial | Compact icon navigation, bounded/maximized initial window, labels/shortcuts/scrolling; 100/150/200% startup screenshots inspected; physical/mixed-monitor DPI remains |
| State persistence/recovery | Verified | Atomic saves, corrupt original preservation, restore filtering, duplicate normalization/offset clamps tests |
| CPU/memory/shutdown/device routing | Partial | Cancellable jobs and disposed player/helper lifecycle; paused sample 0.69% of one core, peak 258.16 MB including direct helper; physical hotplug/long-run leak checks remain |
| Windows installer/portable/source | Verified | MSI/EXE/portable and corresponding source produced; MSI install/run/uninstall verified on Windows Server 2025; unsigned/client acceptance remains |

Screens visibly disclose missing account/Automix functionality. No playback,
streaming, auth, downloads or statistics are simulated to imply parity. Source
branding, icons and dark/light colors are retained; restricted SF Pro fonts are
replaced with platform typography. Physical Windows 10/11 device listening,
keyboard/tray/picker/startup, hotplug and full source animation parity require
acceptance beyond the cloud runner.
