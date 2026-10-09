# Windows client acceptance

Cloud evidence is recorded in BUILD_REPORT.md. These remaining checks require
an interactive Windows 10/11 x64 PC and actual audio devices. Use the matching
installer/source/checksum artifact from the verified workflow. This is a test
build; it has no signing certificate or public release approval.

| Check | Procedure | Expected result |
|---|---|---|
| Install and upgrade | Install MSI to a chosen folder; update from the previous preview | Correct launcher/icon; library preserved; only the new app version installed |
| Picker/import | Ctrl+O for unicode paths and Ctrl+Shift+O for nested music; cancel a large import | Native chooser, responsive UI, correct artwork/tags and no partial library commit after cancel |
| Collection behavior | Favorite tracks; create/rename/delete playlist; add a duplicate; reorder/export/reimport | State/order/duplicates persist after reopening; audio originals remain untouched |
| Queue/transitions | Reorder/remove upcoming/current tracks; repeat one/all; shuffle then undo; natural end and crossfade | Correct current selection; no stale callback skipping; no stuck playback or unexpected simultaneous players |
| Listening quality | Play real WAV/MP3/AIFF/FLAC/Opus/AAC with volume, seek, rate, EQ and crossfade | No clipping/crackling/unexpected silence; documented PCM and format limits respected |
| Device routing | Switch default endpoint; unplug headphones/USB output; reconnect during playback/overlap | State/error is understandable; playback can recover; record whether JavaFX follows default endpoint |
| Media session | Physical multimedia keys and Windows media flyout; play/pause/next/previous/seek | Correct track/artwork/state/timeline and command behavior, including after stop/reopen |
| DPI/window | 100/150/200% real display scaling; narrow/short resize; move between differently scaled monitors | Navigation and mini player reachable; no clipped controls; native title bar/buttons usable |
| Lyrics | Local TTML/LRC with duet/backing words, seeks and offsets; pause/resume and reduced motion | Correct focus/word timing; no drift after seeks; offsets persist; reduced motion stops movement |
| Tray/notifications | Enable close-to-tray and notifications; close/show/quit; use tray transport | App follows the selected close behavior, metadata is current and Quit ends all processes |
| Startup | Enable startup in packaged app; sign out/in; disable then sign in again | Opt-in background launch; disable removes startup script; unicode install path works |
| Long-session resources | Play/seek/change tracks for hours, import a large collection, then leave paused | No sustained CPU spin/unbounded memory growth; bounded cache policy; responsive UI and complete shutdown |
| Recovery/uninstall | Copy library.json; test recovery on a copy; uninstall | Corrupt file preserved, no unexpected data loss; installed binaries removed; disable opted-in startup before uninstall |

Record OS build, display scale, output devices, codec/bit depth/sample rate,
package commit, symptoms and logs for any failure. Source/provider gaps in
FEATURE_PARITY.md stay unavailable until independently implemented and tested.
A public release or merge to main requires Prem's explicit approval.
