# Build report — 11 October 2026

**The native development build works; the complete streaming-focused Windows
edition is unfinished.** The public website still downloads the older local
preview. No new full release or Android update has been published.

## Latest verified development build

[Draft PR #2](https://github.com/kaizen-flims/Podium-Air-Windows-/pull/2) contains
the implementation on `feat/complete-native-parity`. Tested executable code:
`9415dc7ac454f3b0b861a4a0b3b24525f7c1dc6f`. [Windows run 38143937527](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/38143937527)
completed successfully with **84 tests, zero failures/errors and zero skips**.
A later Markdown-only checkpoint records these results without changing executable
code; it is not represented as a newly tested binary.

[Verification reports, screenshots, exact app JAR and package manifest](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/38143937527/artifacts/11703669092)
have archive SHA-256 `955d479f41fe468d77d566df2584b7f12757f0d4f63d2c9df5f2c48e471aff70`. Their XML results were independently
counted. Actual Replay, Sources and the new player screenshots in both themes/panes and play states were reviewed.

| Check | Verified scope |
|---|---|
| Native analysis and models | Original DSP, resampling, mel/stereo STFT, pinned Beat This!/Open-unmix inference and cancellation; measured PCM WSOLA preserves pitch |
| Automix | Original policy/planner, actual PCM/model preparation and two-player overlap; paused original-clock seek, cancellation/replanning, manual crossfade zero, volume change and natural end |
| Lyrics | Local TTML/LRC/embedded priority; opt-in documented LRCLIB, strict title/artist/duration matching, bounded body/cache, offline use and cancellation through five actual HTTP tests |
| Replay | Month/year/all-time and legacy rolling periods; eight distinct 1080×1920 story cards, native Unicode save and reopen; real elapsed-time/hour recording and legacy state compatibility |
| ListenBrainz | Eight elapsed-time/HTTP/state/native tests; opt-in thresholds, metadata-only sharing, rate pacing/reset headers, secure token round-trip and disconnect cancellation |
| WebDAV | Nine path/XML/actual HTTP/state tests; folder/Unicode handling, Basic auth, origin/folder confinement, redirect refusal, bounded bodies, interrupted atomic transfer and disconnect races |
| Packaged WebDAV | Packaged JVM/native credentials, authenticated PROPFIND and Unicode conditional GET from a loopback server; actual downloaded WAV metadata, play/pause/seek/natural end and credential deletion |
| Playback formats | WAV, FLAC, Opus, MP3, AAC/M4A and AIFF: real play/pause/seek/resume/crossfade/end; actual PCM format/duration/non-silence; missing-file recovery |
| Native/UI | All 16 routes and populated album/playlist detail; artwork-backed Controls/Lyrics/Queue in both themes, reduced-motion panes and playing/paused sleeve scale; Robot Ctrl+F/Ctrl+Space; actual SMTC callbacks, native Unicode file/folder/save/cancel and helper disposal |
| Artwork pipeline | Source three-pass blur, 128px worker decode, eight-cover cache and bounded local inputs; five neutral/color-balance/transparency/cache/invalid-image regression tests |
| DPI/performance | Simulated 150/200% startup and scrolling; paused expanded player 0.69% of one logical core, 266.14 MB peak sampled app/direct-helper working set; shutdown passed |
| Installer | MSI install at a selected directory, installed UI/keyboard smoke and uninstall passed |
| Actual package payload | Five file hashes; all tracked source bytes match Git; portable JAR matches tested app; both model SHA-256s, native PE resources, required licenses, replaceable JARs and runtime legal files checked |

Runner: Windows Server 2025 x64, Temurin 21.0.12.1+1, Kotlin 2.2.21, Compose
1.9.3, Gradle 8.14.3, OpenJFX 21.0.9 and ONNX Runtime 1.28.0. MSVC builds the
native Windows bridge/analysis/credentials DLL. End users need no separate Java,
VLC, FFmpeg or .NET. The test fixture server and FFmpeg are CI tools. Installers
remain unsigned. Cloud tests do not establish physical Windows 10/11 hotplug,
mixed-monitor DPI, long-run resource use, listening quality or exact source motion.

## Downloadable development packages

The run exposes separate **Windows-installers**, **Windows-portable**,
**Windows-corresponding-source** and **Windows-dependency-sources** artifacts,
alongside the original combined bundle and Windows-verification. Packages expire
after 30 days; verification expires after 90 days. They are test builds, not a
full public streaming release. Use the run's Artifacts section for the actual files.

The connector's large installer-archive transfer was incomplete, so an independent
local inspection of the entire installers is not claimed. The corresponding-source archive was independently downloaded, its SHA-256
verified, and all **134 tracked source blobs** matched the remote Git tree at
`9415dc7`. The final Windows package inspection checks the actual shipped JAR,
model/native/license payload and portable distribution. The exact app JAR was
also independently inspected: both pinned model hashes, x64 PE resources and
all 49 bundled notices match the manifest; the portable runtime contains 50 legal
files, and JLayer remains a separate, replaceable JAR.

## Desktop player behavior and limits

The source landscape layout is adapted with anchored artwork, a 1100dp cap,
switchable Controls/Lyrics/Queue, the original pane fade timings and a 0.88
paused sleeve scale with 500ms easing. Compact windows use the same panes with
scrolling. The player has its own volume row; the mini player is hidden while
Now Playing is open. All variants were exercised in the packaged UI, including
both themes, reduced motion and 150/200% small-window scaling.

The original full-artwork three-pass box blur is prepared from a bounded 128px
local decode on an IO worker and cached for eight covers. Cover changes fade
between bitmaps; no perpetual artwork animation or screen-sized blur runs.
The source's original comments and reuse notices are retained. Desktop
light/dark scrims and mouse/keyboard pane choices replace phone gestures.
The portrait mesh/shaders, video canvas and remaining exact UI/motion parity
are still incomplete. The resource sample above observes the paused expanded player and its native
helper after startup; it does not establish multi-hour leak or physical-device
acceptance.

## WebDAV behavior and limits

Sources connects a personal HTTPS music-folder URL, browses one folder at a time,
and imports up to 100 selected tracks / 5 GB of actual bytes, capped at 750 MB per
file. Completed files atomically replace their own URL-hash cache entry; interrupted
partials are removed and completed earlier tracks remain in Library. Tagged
metadata/artwork is read locally, with filename/parent-folder fallback. No server
write operations or unsolicited startup scans occur. Credentials live in Windows
Credential Manager; URL/username are ordinary preferences. Disconnect removes the
saved credentials and retains downloaded music.

Depth 1 DAV multistatus properties with success status are used; directories stay
inside the configured origin/folder. HTTPS must have a certificate trusted by the
bundled runtime. Redirects, credential-bearing/query URLs, traversal, external XML
entities and excessive listing/file bodies are rejected. Read-only Basic-auth
servers are supported. Direct remote streaming, uploads, cover-sidecar traversal,
browser SSO, direct SMB accounts and addon sources remain incomplete. Actual NAS
acceptance has not been performed; only generated loopback fixtures were used.

## Source, notices and published preview

Android source `48902e6b20fdcfeb1723e02d4744849e82d5067a` remains unchanged.
About retains “Made with ❤️ by Prem” and “Adapted from the Android application
Podium Air,” with bundled third-party licenses. GPLv3 plus original AGPLv3-or-later
Automix headers/section-13 obligations, exact corresponding source, Beat This!/
Open-unmix MIT model licenses and ONNX/runtime/dependency/native notices remain.
Restricted SF Pro fonts are absent; logical platform fallback renders Unicode.

The public [v0.2.0-preview.1](https://github.com/kaizen-flims/Podium-Air-Windows-/releases/tag/v0.2.0-preview.1)
still targets `ad91ba8ac4f960330c5986698072f34441be7c48`, verified by
[run 37981495583](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/37981495583)
with 40 tests. Its eight permanent assets include installers, portable app, both
source archives, notices, build evidence and checksums. It does not contain the
new development features.

Website source checked on 11 October at `582476fa64ca8ff3ac74d611d54e04d76cbaec78`
still has separate “Download Podium Air for Android” and “Download Podium Air for
Windows” anchors with their platform logos. Windows points to the older preview
MSI and is labeled a local preview. Android links remain scoped independently.
The earlier [website QA run 38016631060](https://github.com/kaizen-flims/Podium-Air-Website/actions/runs/38016631060)
verified its labels/URLs/platform isolation, four responsive widths and live HTTP
200 at its tested revision. This report does not claim a new live deployment.

## Remaining product work

1. Supported streaming/catalog/search/download integration.
2. Google sign-in and playlist/account sync, with authorized application setup.
3. Remaining source integrations, network sources and faithful UI/motion behavior:
   canvas/translation, Last.fm, official Discord presence, shared listening, direct
   SMB/addons and remaining source differences.
4. Whole-product integration and physical Windows 10/11 acceptance.
5. Full installer publication, followed by the website link replacement.

The original master prompt requires supported, authorized provider access.
[The exact provider dependency](docs/STREAMING_PROVIDER_DECISION.md) remains
unresolved; ordinary Google OAuth/Data API access does not grant native audio-only
background/download capability. No cookie login or private extraction path is
claimed. This is a genuine core input dependency, while the other listed source
differences are still incomplete work.

[FEATURE_PARITY.md](FEATURE_PARITY.md) records scope-specific statuses and
[Windows acceptance](docs/WINDOWS_ACCEPTANCE.md) records physical-device checks.
No source/feature scaffolding is presented as a complete Windows Podium Air release.
