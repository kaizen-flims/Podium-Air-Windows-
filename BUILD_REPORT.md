# Build report — 9 October 2026

**Native Windows local-music preview: compiled, tested, packaged and installed.
Full Android/provider/Automix parity is not complete.**

## Recorded build evidence

Expanded implementation commit `6360d4ab14fa1d2a701c8aa9a7a71351cb689606` passed
[Windows run 37950405605](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37950405605):
31 tests with zero failures/errors, all 15 routes plus playlist/album detail,
native and packaged SMTC checks, real WAV/FLAC/Opus/MP3/AAC-M4A/AIFF playback,
pause/seek/resume/crossfade, and silent MSI install/run/uninstall. Its
[installer/portable/source artifact](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37950405605/artifacts/11626425694)
and [verification reports](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37950405605/artifacts/11625972342)
are available.

Follow-up commit `792ea3db86e3a5b3b2ecf0c4287603322d7fa8cc` runs at
[37953260863](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37953260863).
Its test/package, all-route rendering, Robot Ctrl+F/Ctrl+Space, 150/200% startup,
all codec, paused CPU/memory/helper-shutdown and MSI checks passed. [Packages and source](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37953260863/artifacts/11627980078)
and [verification](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37953260863/artifacts/11627242158)
uploaded successfully; final job conclusion is success, 32 tests passed. Paused
resource observation: **0.86% of one logical core, 226.03 MB peak sampled working
set** including direct helper processes. Fresh route/150/200% screenshots were
inspected; content and mini player fit the visible window and short layouts
scroll. DPI is simulated with Java2D scaling, not a physical mixed-monitor test. Subsequent
changes add bounded MP4 reference/descriptor validation, four security fixtures
and natural end/missing-file recovery integration; those changes need a final
passing Windows run before being advertised as verified.

Runner: Windows Server 2025 x64, Temurin 21.0.12+101.0, Kotlin 2.2.21, Compose
1.9.3, Gradle 8.14.3, OpenJFX 21.0.9. CI builds the C++20 native helper with the
Visual Studio x64 compiler and Windows SDK. The app bundles its runtime;
end users need no Java/VLC/FFmpeg/.NET installation. All installers are unsigned.

## Implemented scope

- Native Compose/AWT window, source brand/icon/palette, dark/light themes,
  compact navigation, scrollable narrow layouts, mini player and keyboard controls and Windows Common Item Dialog file/folder/import/export pickers
  in a cancellable STA helper (new picker validation pending).
- Real filesystem import with metadata, embedded artwork, progress/cancellation,
  accessible mapped/UNC paths, album-artist identity and disc/track ordering.
- Persisted local playlists/favorites/history/duplicate-safe queue, repeat,
  reversible upcoming shuffle and local M3U8/M3U import/export.
- JavaFX transport/rate/ten-band EQ, monotonic sleep timer and equal-power
  two-player crossfade at 1×. Seeking cancels overlap; other speeds disable it.
- Cancellable background FLAC/Opus/AAC decoding to disk-backed 16-bit PCM.
  Opus pre-skip/gain/end trimming and supported AAC edit-list trimming; explicit
  damaged/protected/unsupported errors. Originals are never modified.
- Original TTML/LRC/alignment/focus/clock reuse with timed word growth/lift/bloom,
  overlapping/duet/backing vocals, per-track offsets, reload and autoscroll.
- Embedded-art dominant-color motion with reduced-motion/paused behavior.
  Actual elapsed-time Replay by 7/30/365 days, tracks/artists/minutes/starts;
  pauses and seeking do not inflate listening minutes.
- Real Windows SMTC metadata/timeline/transport bridge, tray controls, optional
  notifications, close-to-tray and launch-at-sign-in (all opt-in).
- Atomic JSON writes and corrupt-original preservation/restore normalization.
  Shutdown cancels workers, disposes players, flushes state and closes helper.

Source commit `48902e6b20fdcfeb1723e02d4744849e82d5067a` remains untouched.
The GPL source provenance and required library/native notices are bundled and
visible from About. Matching application source, exact separately licensed
codec/tag/OpenJFX source JARs, full OpenJFX native source, dependency hashes and
package SHA256 inventory accompany each successful build. LGPL libraries remain
replaceable separate JARs. C++/WinRT MIT text is included. SF Pro fonts, AGPL
Automix code/models, provider secrets and Android binaries are not redistributed.

Tests cover queue/repeat/shuffle/selection/stale callbacks; state round-trip,
corruption/references/normalization; local WAV/lyrics import; actual Opus decode,
CRC/truncation/cancellation/cache invalidation; TTML security/duets and clock
seek/pause; elapsed-time statistics and M3U interchange; native-command coordinator
behavior. New MP4 fixtures cover inline media bounds, external/protected
references, truncated tables, deep descriptors and fragmented tracks.

## Acceptance gates

| Gate | Result |
|---|---|
| A — audit/reuse | Exact source inventory, structural audit, module/license/provider assessment and codec/native notices/source recorded |
| B — native startup | Packaged route/detail render passed; compact/DPI screenshots inspected; consumer client acceptance remains |
| C — real audio | Six codec controls/crossfade passed; final natural-end/error-recovery check pending; physical output/hotplug pending |
| D — honest parity | FEATURE_PARITY.md distinguishes tested local scope from missing source features |
| E — installable artifact | Verified MSI/EXE/portable build, installed launch/uninstall and artifacts; unsigned |
| F — documentation/handoff | README/audit/parity/notices/source/test instructions/progress and draft PR #1 provided; final evidence update pending |

## Remaining capabilities and limits

YouTube/Google streaming, login, downloads and remote sync need a supported
provider interface consistent with the locked prompt. The source private
API/extraction pipeline was not transplanted. Account clearly discloses this
and opens the official browser service without claiming app sync.

Automix requires Android/JNI/model analysis and a time-stretch/beat pipeline;
the AGPL headers must be retained if adapted. Online canvas video, remote
lyrics/translation, scrobbling, Discord RPC, Listen Together and WebDAV/SMB
account clients/addons are missing. Replay lacks the source's annual share
presentation. Source brand/colors and some lyric motion are adapted; exact
pixel/animation parity is not claimed.

FLAC above 16 bits is converted to 16-bit PCM. Opus supports mono/stereo mapping
family 0; Ogg Vorbis/chained/multichannel files are rejected. M4A supports
unencrypted nonfragmented AAC with the supported sample/edit tables, not
ALAC/DRM/external references. Decoded tracks are capped at 750 MB. Cache pruning
starts when existing WAVs reach 1.5 GB before a decode; this is not a strict total
cap. First preparation of long tracks takes time. No sample-accurate gapless or
high-resolution lossless playback claim is made.

Real Windows 10/11 consumer installation/upgrade, physical picker/tray/startup/
media keys/notifications, mixed-monitor DPI, audio routing/hotplug, long-session
resource use and listening quality require [client acceptance](docs/WINDOWS_ACCEPTANCE.md).
The paused resource sample is a short observation, not a leak or long-run proof.
Signing credentials were not supplied; SmartScreen warnings are expected.

No merge to main, public release, Android changes or auto-update occurred.
Implementation remains on `feat/native-windows-port` in
[draft PR #1](https://github.com/kaizen-flims/Podium-Air-Windows-/pull/1).
Compilation and target-platform checks ran through Windows Actions because
the Linux workspace could not download the needed build dependencies.
