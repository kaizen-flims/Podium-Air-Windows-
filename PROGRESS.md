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
