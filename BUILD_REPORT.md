# Build report — 10 October 2026

**Podium Air Windows 0.2.0 is a working native local-music preview.** It compiled,
passed the Windows checks below, and produced MSI, EXE and portable packages.
Full Android, provider and Automix parity remains incomplete.


## Development branch verification

`feat/complete-native-parity` is tracked in [draft PR #2](https://github.com/kaizen-flims/Podium-Air-Windows-/pull/2).
Commit `1b12da522baaf920f8a33820c69087c649e5ea1a` passed
[Windows run 38064057032](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/38064057032)
with **58 tests, zero failures/errors and zero skipped tests**. The independent
verification ZIP from the same code's push run has SHA-256
`1514af05ab10f470f3be49a86ff2d03759d86a6335582b8bbf10737bdf3460d8`;
its XML reports were counted and actual Replay/Settings screenshots inspected.

Additional verified behavior:

- Original native DSP, mel and stereo STFT front ends, real bundled Beat This!
  and Open-unmix inference, cancellation, measured tempo/confidence and bounded
  pitch-preserving WSOLA tests.
- Packaged Automix using real PCM/model evidence, cancellation/replanning,
  manual crossfade at zero, a volume change during overlap, paused seek back to
  the original track clock and natural end.
- Optional documented LRCLIB retrieval, exact recording matching, offline
  cache, bounded response bodies and request cancellation via local HTTP tests.
- Real Replay summary rendering and 1080×1920 PNG export through the native
  Unicode save dialog, followed by reopening the saved PNG.
- All six codecs now also undergo actual PCM duration/format/non-silence checks.
  Existing 15-route/keyboard/native media/dialog/DPI/installer gates passed.

The same-code push-run observation was 1.04% of one logical core and 255.96 MB
peak sampled app/direct-helper working set while paused. This says nothing
about model peak memory or sustained listening quality.

Commit `dba37cb52e6fbd7b8b4decdcf21a4110dcadeba8` adds opt-in ListenBrainz,
audible-time thresholds, serialized one-per-second documented API calls,
rate-limit handling, masked account input and native Windows Credential Manager
read/write/overwrite/delete. Eight added state/HTTP/native tests plus a packaged
credential fixture gate are pending. The fixture's missing thumbnail argument
was corrected in `db8ab51`; test gates remain enabled. No real user's token or account submission
was used for automated tests; no real-account acceptance claim is made.

The website and permanent release still target the older local preview. A new
full installer has not been published: streaming, Google login/sync, remote
search/downloads and remaining source features are unresolved. The original
prompt's supported-provider requirement is described in
[STREAMING_PROVIDER_DECISION.md](docs/STREAMING_PROVIDER_DECISION.md).

## Verified published preview

Released source commit `ad91ba8ac4f960330c5986698072f34441be7c48` passed
[Windows run 37981495583](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37981495583).
**40 tests ran with zero failures/errors.**

[Preview v0.2.0-preview.1](https://github.com/kaizen-flims/Podium-Air-Windows-/releases/tag/v0.2.0-preview.1)
is public. [Publisher run 37982272174](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37982272174)
validated this exact source and uploaded eight permanent assets before publishing:
MSI, EXE, portable ZIP, application source, dependency sources, third-party notices,
build evidence and SHA256SUMS. The release tag points to the commit above.

[Download installers, portable app and matching sources](https://github.com/kaizen-flims/Podium-Air-Windows-/releases/tag/v0.2.0-preview.1) ·
[Verification reports and screenshots](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37981495583/artifacts/11640779965) ·
[Merged PR #1](https://github.com/kaizen-flims/Podium-Air-Windows-/pull/1)

| Check | Recorded result |
|---|---|
| Domain and desktop tests | 40 passed: queue identity/mutation/repeat/shuffle; state recovery/normalization/final-save ordering; callback/restart regressions; lyrics/security/clock; actual Opus decoding/cache and bounded MP4 parsing; statistics and M3U interchange |
| Packaged UI | All 15 routes plus populated playlist/album details rendered; Robot Ctrl+F and Ctrl+Space exercised actual search/play/pause |
| Windows media session | Native and packaged SMTC metadata/state/timeline exposed; actual Windows pause/seek requests reached callbacks |
| Windows shell dialogs | Actual Unicode music-file/folder/save selection and cancellation passed; packaged JVM/native protocol passed; cancelling an open dialog left no helper process |
| Real playback | WAV, FLAC, Opus, MP3, AAC/M4A and AIFF fixtures played, paused, sought, resumed, crossfaded and reached natural end; missing-file error and recovery passed |
| Resizing/DPI | Startup and route checks at simulated 150/200%; compact navigation and bounded/scrollable layouts; screenshots reviewed |
| CPU/memory/shutdown | Paused sample: 1.04% of one logical core, 237.75 MB peak sampled working set, including direct helper processes; helper exited with app |
| MSI lifecycle | Silent install to selected directory, installed route/keyboard smoke and silent uninstall passed |
| Source and packages | Exact runtime check, vendor-source checksum, dependency/native sources, application source, package SHA256 inventory and artifact uploads passed |

Runner: Windows Server 2025 x64, Temurin 21.0.12.1+1, Kotlin 2.2.21, Compose
1.9.3, Gradle 8.14.3 and OpenJFX 21.0.9. CI builds the C++20 helper with the
Visual Studio x64 compiler and Windows SDK. End users need no separate Java,
VLC, FFmpeg or .NET installation. All installers are unsigned.

The short paused observation is not proof of long-session resource behavior.
DPI is simulated, not a physical mixed-monitor check. Generated codec fixtures
exercise the packaged playback pipeline; listening quality and audio-device
routing still require a consumer PC.

## Implemented scope

- Native Compose/AWT window with original branding, palette and icon; dark/light
  themes, compact navigation, keyboard controls, responsive scrolling, mini
  player and volume available in Settings at narrow widths.
- Owned Windows Common Item Dialogs for file/folder and M3U import/export,
  with cancellable STA helper lifecycle and Unicode protocol handling.
- Real filesystem import with tags/artwork, progress/cancellation, accessible
  mapped/UNC folders, album-artist identity and disc/track ordering.
- Persisted local playlists, favorites, actual-start history, duplicate-safe
  queue, repeat, reversible upcoming shuffle and M3U8/M3U interchange.
- JavaFX transport/rate/ten-band EQ, monotonic sleep timer and equal-power
  two-player crossfade at 1×. Seeking cancels overlap; other speeds disable it.
- Cancellable background FLAC/Opus/AAC preparation to disk-backed 16-bit PCM,
  Opus pre-skip/gain/end trimming, supported AAC edit trimming, bounded container
  parsing and explicit damaged/protected/unsupported errors.
- Original TTML/LRC/alignment/focus/clock reuse: timed word growth/lift/bloom,
  overlapping/duet/backing vocals, offsets, reload and autoscroll.
- Embedded-art dominant-color motion and reduced-motion/paused behavior;
  elapsed-time Replay for 7/30/365 days with tracks/artists/minutes/starts.
  Pauses/seek distances do not inflate time; repeats/completed restarts count
  fresh playback sessions.
- Native SMTC metadata/timeline/transport, tray controls and opt-in notifications,
  close-to-tray and launch-at-sign-in.
- Atomic JSON recovery/normalization, ordered final shutdown save, idempotent
  close, cancelled workers and player/helper disposal.

## Provenance and distribution

Audited Android commit `48902e6b20fdcfeb1723e02d4744849e82d5067a` was read only.
The Windows branch preserves source attribution and GPL headers. About displays
“Made with ❤️ by Prem” and “Adapted from the Android application Podium Air,”
and exposes bundled third-party notices.

Each successful package contains matching application source, separately
licensed codec/tag/OpenJFX source JARs, complete OpenJFX native source, exact
Temurin source/build scripts, runtime metadata, dependency hashes and package
checksums. LGPL codec/tag libraries remain replaceable JARs. Original runtime
legal files and C++/WinRT MIT text are retained. Restricted SF Pro fonts,
Android binaries, secrets and separately licensed Automix code/models are not
redistributed in this older published preview. The development branch includes
Automix under its required AGPL/model/runtime notices. The published preview also includes the pinned Skiko 0.9.22.2
LICENSE/NOTICE, Skia BSD license and native renderer third-party legal files.
Application JAR and installer metadata both identify version 0.2.0.

The downloaded release package was independently inspected: its GitHub artifact
digest matched, and all five package hashes and sizes matched both the CI
inventory and the public GitHub release asset metadata. The source ZIP records
the released commit; all 83 tracked files match the tested source snapshot after
Windows line-ending normalization. The portable application includes the native
helper, versioned application JAR, 42 notice files (including 10 renderer legal
files) and 50 runtime legal entries. Codec/tag dependencies remain separate JARs.

The source archive describes the released commit above. Follow-up documentation
records the release and website results without changing packaged application
code. CI artifacts are temporary; the public prerelease supplies permanent
installer/source/checksum download URLs.

## Acceptance gates

| Gate | Result |
|---|---|
| A — audit/reuse | Exact source inventory, architecture/provider/license assessment and notices recorded |
| B — native startup | Packaged and MSI-installed routes/details/keyboard passed; consumer-device acceptance pending |
| C — real audio | Six codec controls/crossfade/natural-end and missing-file recovery passed; physical listening/hotplug pending |
| D — honest parity | FEATURE_PARITY.md identifies tested local scope and missing source capabilities |
| E — installable artifact | MSI/EXE/portable/source produced; MSI install/run/uninstall passed; unsigned |
| F — handoff | Main source and merged PR #1, public preview assets, permanent website downloads, notices/evidence and client acceptance guide provided |

## Remaining capabilities and limits

Google/YouTube streaming, authentication, downloads and account synchronization
need a supported provider interface consistent with the locked product policy.
The Android private API/extraction pipeline was not transplanted. Account
visibly explains this and opens the official browser service without collecting
credentials or claiming application sync.

The published preview excludes Automix. The development branch now retains the
original AGPL headers, native front ends and model notices with a verified
working desktop analysis/time-stretch pipeline. Optional LRCLIB lyrics and
Replay summary PNG export are verified. ListenBrainz is implemented and being
checked. Online canvas, translation, Last.fm, Discord RPC, Listen Together,
WebDAV/SMB account clients and addons remain missing. Replay still lacks the
complete annual story-card family.
Exact Android pixel/animation parity is not claimed.

FLAC above 16 bits converts to 16-bit PCM. Opus accepts mono/stereo mapping
family 0, not Vorbis, chained or multichannel streams. M4A accepts unencrypted,
nonfragmented AAC with supported sample/edit tables, not ALAC/DRM/external media
references. Decoded tracks are capped at 750 MB. Existing-cache pruning starts
at 1.5 GB before decode; this is not a strict total-cache cap. Long tracks take
time to prepare. No high-resolution-lossless or sample-accurate-gapless claim
is made. Audio originals remain unchanged.

[Windows client acceptance](docs/WINDOWS_ACCEPTANCE.md) covers consumer Windows
10/11 installation/upgrade, physical media keys, picker/tray/startup/notifications,
mixed-monitor DPI, output changes/hotplug, long-session resources and listening
quality. Signing credentials were not supplied; SmartScreen warnings may appear.

Prem explicitly requested publishing the Windows code and release assets. PR #1
is merged into main and the approved Windows preview is public. The website at
[https://podium-air-website.pages.dev/](https://podium-air-website.pages.dev/)
has separate “Download Podium Air for Android” and “Download Podium Air for
Windows” links with their platform logos. The Windows link targets the permanent
preview MSI; the existing Android release updater affects only Android links.

[Website verification run 38016631060](https://github.com/kaizen-flims/Podium-Air-Website/actions/runs/38016631060)
passed Chromium checks at 1440/768/390/320px, platform labels/logos/URLs,
Android-update isolation and a live production HTTP 200 check. Desktop and mobile
screenshots were inspected. Cloudflare reported successful deployment of website
commit `f3e79165643812ee56da96b08417396d6e19d9c6`.

Android source/releases and automatic updates remain unchanged. Target-platform
compilation and checks ran in Windows Actions; the Linux workspace could not
fetch required build dependencies. Consumer-device acceptance remains pending.


## Replay calendar periods and individual stories (checks pending)

Desktop adapters now cover the source's This month / This year / All time
periods, all eight ordered Intro/Minutes/Artists/Songs/Albums/Genres/Habits/Recap
PNG cards, original hue offsets and per-card content/rank order. Local tagged
albums remain distinct by album artist; genre cards use actual imported tags.
The desktop uses a scrollable card picker and frozen preview/native PNG export
rather than Android's touch/hold story sequence. Animated story presentation
and exact source pixel parity are not claimed.

Audible-time recording now additionally stores actual local listening hours.
Existing schema-1 state loads with an empty hour map; old records are never
assigned invented hours. Three added tests cover calendar boundaries/invalid
and future dates, all eight rendered cards/ranking/genre/album identities, and
pause-safe hour accumulation/backward-compatible statistics. The packaged
Replay gate saves and reopens all eight cards. These additions await the next
Windows run; the expected suite is 69 tests.
