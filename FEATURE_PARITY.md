# Feature parity

Statuses mean: **Verified** = a recorded passing check; **Partial** = implemented
but missing source behavior or runtime/device evidence; **Blocked** = requires
provider rights, a dependency, hardware verification or further implementation.
No claim of full Android parity is made.

| Feature | Status | Implementation / evidence needed |
|---|---|---|
| Native Compose desktop window / navigation | Verified | Packaged window and all 14 routes plus populated playlist/album detail rendered in Windows Server 2025 run 37943444938; consumer Windows interaction/DPI still pending |
| Home / Explore | Partial | Real local recents, added tracks, favorites, albums/artists; provider feed blocked |
| Search | Partial | Local title/artist/album search; online search blocked |
| Library / favorites / history | Partial | Implemented with persisted local state; coordinator/storage tests pass, interactive UI checks pending |
| Playlists create/rename/delete/add/remove/move | Partial | Local playlists and duplicates; remote sync blocked |
| Album / artist detail | Partial | Groups imported tagged music; no remote detail pages |
| Now Playing / mini player / volume / seek | Partial | Packaged WAV integration passed play/pause/seek/resume in run 37943444938; physical controls/volume checks pending |
| Queue / repeat / shuffle / duplicates | Verified | Domain and coordinator tests passed in run 37943444938; real device transitions remain pending |
| Crossfade 0–12 seconds | Partial | Packaged real JavaFX two-player WAV crossfade passed in run 37943444938; timing/quality across formats remains partial |
| Sample-accurate gapless / Automix beat/tempo transitions | Blocked | No beat analysis or time-stretch pipeline; not simulated |
| Speed / equalizer / sleep timer | Partial | Actual JavaFX rate/equalizer and coroutine pause timer; device checks pending |
| Lyrics | Partial | Local line/enhanced-word LRC, embedded text, click-to-seek; source word-growth timing reused but full animation not ported |
| Artwork / colors / icon | Partial | Embedded still art, original logo/icon, source light/dark palette; dynamic mesh/canvas pending |
| Local formats | Partial | JavaFX MP3/WAV/AIFF/M4A (AAC) baseline; decoder/platform checks pending. FLAC/Opus not claimed |
| Google login / YouTube streaming / downloads / sync | Blocked | Source private API/extraction route incompatible with prompt's policy gate |
| Discord login/RPC, scrobbling, Listen Together, sources | Blocked | No supported desktop implementation yet |
| Replay / statistics / translations / remote network libraries | Blocked | Not implemented |
| Keyboard / native picker / standard window controls / tray | Partial | Implemented; interactive Windows checks pending |
| Windows multimedia keys / SMTC / device changes / notifications | Blocked | Native Windows bridge and device tests pending |
| DPI / accessibility / responsiveness | Partial | Logical dp layout, labels, keyboard shortcuts, minimum size, scrolling; 100/150/200% DPI checks pending |
| Atomic save / corrupt-file recovery | Verified | State round-trip, corrupt-file preservation and filtered restore tests passed in run 37943444938 |
| Windows installer / portable bundle | Verified | MSI, EXE and portable/source artifacts produced in run 37943444938; installer installation/uninstallation still pending |

The source inventory lists additional Android surfaces (Sources, Listen
Together, Discord, Replay, Equalizer, Local Music, Account/Scrobbling, canvas
auth). Desktop availability is disclosed instead of inventing working controls.
