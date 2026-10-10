# Progress log

- 2026-10-09: Read locked Windows master prompt and verified destination.
- Audited Android main SHA 48902e6b20fdcfeb1723e02d4744849e82d5067a; recorded structural inventory and reuse constraints.
- Bootstrapped empty destination with README; created feat/native-windows-port.
- Implemented substantive native local-music preview and meaningful tests.
- Added Windows tests, native MSI/EXE packaging, portable/source artifacts and packaged startup smoke; validation pending CI.
- Initial Windows build failed on Gradle's java-extension name shadowing; corrected explicit ZipFile/File imports.
- Commit 42911db passed unit checks, MSI/EXE packaging and packaged window startup in Windows Actions run 37940753717.
- Expanded bundled notices with OpenJFX 21.0.9+1 native legal files and jaudiotagger LGPL, source bundles, dependency hashes and license selector. Adding a real-audio smoke attempt with explicit cloud-device blocker reporting.

- Commit 2704a24 passed 18 tests, package startup, real WAV playback/pause/seek/crossfade, installer/portable/source packaging and notice/source collection in run 37941765127.
- Added populated UI smoke traversal across all routes and playlist/album details; follow-up validation pending.
- Commit 4c631f8 passed all 14 UI routes and populated playlist/album details, all 18 tests, audio smoke and packaging in run 37942605916.
- Final correction preserves crossfade gain envelopes during volume changes; audio smoke now changes volume mid-transition. Manual license files are explicit Gradle task inputs.
- Final implementation ab5b172 passed 18 tests (zero failures/errors), all 14 routes plus populated playlist/album detail, real WAV controls/crossfade with mid-transition volume change, MSI/EXE packaging and all artifact/source uploads in run 37943444938.
- Draft PR #1 holds the implementation; reporting commit records the final verified build. No main merge, release publication, Android modification or auto-update.

- Expanded the native port with bounded background FLAC/Opus/AAC decoding, native Windows SMTC, optional startup/tray notifications, original TTML/duet/lyric clock/focus reuse, word animation/timing controls, artwork color motion, Replay and M3U8 import/export.
- Added decoder corruption/cancellation, TTML/security/clock, elapsed-time statistics, playlist interchange and native command coordinator tests; 31-test expanded build 6360d4a passed all six codec smokes, native SMTC self-tests, packaged UI routes, MSI install/run/uninstall and source/notice uploads in run 37950405605.
- Tightened state recovery/normalization, album identity/ordering, monotonic sleep timing and actual-start history recording. Added a 32nd state-normalization test, real Robot keyboard checks and paused CPU/memory/helper-shutdown measurements.
- Visual review found a window extending beyond the runner desktop. Corrected initial maximized sizing, small-window bounds, compact navigation, import controls and mini player. Robot checks also exposed missing root focus; corrected focus handling and made smoke failures produce explicit reports instead of timing out. Follow-up CI pending.

- Commit 792ea3d passed 32 tests, Robot Ctrl+F/Ctrl+Space play/pause, simulated 150/200% startup, all six codecs, native/packaged SMTC, MSI install/run/uninstall and all uploads in run 37953260863. Fresh screenshots visually confirmed visible mini player and bounded/scrollable layouts. Paused sample: 0.86% of one logical core and 226.03 MB peak app/direct-helper working set; helper exited with app.
- Added four independent ISO-BMFF security fixtures and stricter inline reference/table/descriptor bounds. Expanded audio integration to natural end and missing-file error/recovery; final verification pending.

- Final audit corrected the earlier native-picker claim: Swing JFileChooser was the previous implementation. Replaced it on Windows with owned COM Common Item Dialogs for multiselect files, folders and playlist import/export. Added actual Unicode file/folder/save selection and cancellation self-tests, including the packaged helper. Validation pending.

- Added packaged JVM-to-native dialog protocol checks for real Unicode selections and save path, explicit cancellation, and cancelling an open dialog without leaving a helper process. Added parser cancellation checkpoints and logarithmic media-data lookup to avoid pathological MP4 sample-table work. Validation pending.

- Native picker self-test found an 8.3/full-path alias comparison mismatch; fixture paths now expand to their long form and JVM tests compare filesystem identities. Pinned the actual runtime to Temurin 21.0.12.1+1 and added its checksum-verified vendor source archive and exact build-script sources to the package source bundle. Validation pending.

- Added playback session identities so Replay counts repeat-one/restarting the same queue entry while pause/resume does not add a start. Added a coordinator regression test and corrected inclusive 7/30/365-day date boundaries. Final suite now expects 37 tests; validation pending.

- Added source-branded icon/version resources and per-monitor DPI metadata to the native Windows integration helper, so its shell surfaces retain Podium Air identity. Final validation pending.

- Hardened shutdown persistence: a cancelled older IO save cannot overwrite the final snapshot, close is idempotent, and normal cancellation does not display a save error. Added a concurrent regression test with a deliberately blocked disk write. Final suite now expects 38 tests; validation pending.

- Commit 8cb1877 passed the full Windows workflow 37957264531: 36 tests, native Unicode shell selections/cancellation, packaged JVM/native protocol and open-dialog cancellation cleanup, all routes/keyboard/DPI smokes, six codec natural-end checks, bad-file recovery, MSI install/run/uninstall and all uploads. Pinned runtime/source checksum/build-source collection passed. Paused sample: 0.69% of one core, 258.16 MB peak sampled app/direct-helper working set.

- Implementation 84df7fb passed all 38 tests, all native/packaged controls/dialogs, keyboard/DPI/six-codec natural-end/recovery smokes, MSI install/run/uninstall and source/package uploads in run 37958815393. Paused sample: 1.38% of one core and 226.34 MB peak sampled working set. A final small-screen correction exposes volume in Settings when the compact mini player hides its slider; packaging validation pending.

- Added a regression for an already-started crossfade callback arriving after its incoming entry was removed: the active player reconciles with the remaining queue, while older callbacks remain ignored. Serialized helper extraction and disabled delayed batch expansion for startup paths. Final suite expects 39 tests; final validation pending.

- Completed-track state now survives a rewind until Play starts a fresh session, so Replay counts manual restarts too. End callbacks retain the ended session identity and cannot advance a newer restart of the same queue entry. Covered completed/rewound restart counting in the coordinator regression.

- The volume correction passed the complete Windows run 37960508751 with all 38 tests and packaging/integration checks. Added a queued-UI regression for an old end callback delivered after the user restarts the same queue entry; the final suite now expects 40 tests.

- Final implementation 52d7fec passed run 37962422355: 40 tests with zero failures/errors; all routes/details/keyboard/DPI, native/packaged SMTC/dialog selections/cancellation/cleanup, all six codecs through natural end, missing-file recovery, MSI install/run/uninstall and source/package uploads. Paused sample: 1.38% of one logical core and 226.43 MB peak sampled app/direct-helper working set. Downloadable artifact IDs: package 11632847114 and verification 11632452249. Final reports/PR updated without merging or publishing.
- Independently downloaded and inspected the final packages: GitHub artifact digest and all five package checksums matched; source ZIP commit and 59 code/build/resource files verified (Windows line endings normalized); runtime/source metadata, native helper, 32 notices, 51 runtime legal entries and separate codec/tag JARs verified. Fresh Settings/player/200% screenshots reviewed.

- Prem explicitly authorized publishing Windows release assets and website download buttons. Added exact Skiko 0.9.22.2 LICENSE/NOTICE and Skia copyright/license before public preview packaging; source/API release preparation and website updates are in progress.
- Main commit 4948feb passed all 40 tests and Windows packaging/integration checks in run 37980037577. Release preparation validated exact-source provenance and all package hashes. Fixed draft publishing: upload through the release ID after validating its target; a draft Git tag exists only after publication. No partial release was published.

- 2026-10-10: Resumed the complete native-port scope after the public local preview. Added the original AGPL Automix analysis/policy/planner and native DSP/JNI, bounded PCM preparation including MP3/AIFF, pitch-preserving overlap preparation, original-track clock mapping, confidence-based fallback and adaptive EQ handoffs. The Linux native analyzer test measured 120.005 BPM from generated PCM, measured content boundaries and rejected silence for beat matching. Kotlin tests and packaged Windows Automix control/seek/cleanup checks are pending CI. This development branch is not a complete release; online account/streaming/provider and neural-model parity remain unresolved.

### Automix Windows validation and remaining app work — 10 October 2026

Feature commit c2e63ec passed Windows run 38018280923: 48 tests, zero failures;
actual packaged Automix PCM preparation and overlap; original-track seek while
paused; all six codecs; 15 UI routes and keyboard shortcuts; native media
controls/dialogs; installer lifecycle. Paused observation: 1.73% of one logical
core and 254.07 MB sampled working set. This verifies the DSP/WSOLA change,
not full Android parity.

Opt-in LRCLIB networking, strict recording/duration matching, body limits,
cancellation, provider status and bounded offline cache are now implemented.
Neural Automix adapters, original mel/STFT front ends, the two exact pinned
Android model assets and CPU ONNX Runtime are being verified on Windows.
No public full release has been declared. Streaming/login/sync and remaining
network/social features are still unresolved. The website still points to the
previous explicitly labeled local preview.

Replay summary PNG export now includes a frozen preview, selected-period
filtering (excluding future records), original poster dimensions/margins,
local artwork, Unicode native PNG save dialog and atomic output. Unit and
packaged export checks are included. Native model front ends also passed the
standalone C++ self-test locally. A Gradle Java-extension name collision found
by Windows CI was fixed; full neural/lyrics/Replay verification is pending.
