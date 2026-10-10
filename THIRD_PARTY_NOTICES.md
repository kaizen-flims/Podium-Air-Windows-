# Third-party notices

Podium Air — Windows Edition is a modified adaptation, dated 9 October 2026,
by Prem Das aka Kaizen. Copyright © 2026 Prem Das and upstream contributors.
It is free software under GNU GPL version 3; there is no warranty. You may
redistribute it under that license. The complete license is included as LICENSE,
shown in About → Third-party licenses, and bundled under licenses/LICENSE.

## Android source and acknowledgments

Adapted from Podium Air, https://github.com/kaizen-flims/Podium-Air,
commit 48902e6b20fdcfeb1723e02d4744849e82d5067a. Podium Air is based on
BitChord by Kushagra Singh and contributors,
https://github.com/kushagrasinghx/BitChord. The upstream root license is GPLv3.

Reused files: data/model/Models.kt and data/lyrics/{LyricLine,EnhancedLrc,
BackgroundVocals,LyricGaps,LrcWriter,TtmlLyrics,LyricAlignments}.kt and
ui/player/{LyricClock,LyricFocus}.kt. Their package names and original
comments are preserved. Added provenance comments identify this adaptation.
The desktop theme and layout are adapted from Theme.kt and the Home, Library
and player screens. Brand images come from Podium Air's approved artwork;
icon.ico and icon.png are derived from podium_air_icon.jpg. No SF Pro fonts,
media files, provider credentials or Android native binaries are redistributed.
Upstream credits binimum/am-lyrics for lyric animation inspiration.

The Orchard-derived AGPL Automix planner, audio-analysis models and WSOLA
implementation are NOT included. A future port must preserve their file-level
licenses and notices; the root GPL license does not erase the AGPL headers.

## Runtime dependencies

| Component | Version | License / source |
|---|---|---|
| Kotlin / Compose compiler | 2.2.21 | Apache-2.0, https://github.com/JetBrains/kotlin |
| Compose Multiplatform / Material | 1.9.3 | Apache-2.0, https://github.com/JetBrains/compose-multiplatform |
| Kotlin coroutines | 1.10.2 | Apache-2.0, https://github.com/Kotlin/kotlinx.coroutines |
| Kotlin serialization | 1.9.0 | Apache-2.0, https://github.com/Kotlin/kotlinx.serialization |
| Skiko / Skia (transitive Compose renderer) | Skiko 0.9.22.2; Skia m132-a00c390e98-1 | Apache-2.0 / BSD and bundled notices, https://github.com/JetBrains/skiko, https://skia.googlesource.com/skia/ |
| OpenJFX base / graphics / media | 21.0.9 | GPL-2.0 with Classpath Exception; additional native-library licenses. https://github.com/openjdk/jfx21u |
| jaudiotagger | 3.0.1 | LGPL-2.1-or-later (source headers), LGPL in published POM. https://bitbucket.org/ijabz/jaudiotagger/ |
| Eclipse Temurin runtime | 21.0.12.1+1 | GPL-2.0 with Classpath Exception and runtime legal notices. https://adoptium.net/ |
| Gradle wrapper | 8.14.3 distribution | Apache-2.0, https://github.com/gradle/gradle |

The Gradle dependencyNotices task retains LICENSE, NOTICE, COPYING and legal
files from resolved dependency JARs under licenses/dependencies. OpenJFX 21.0.9+1 GPL, native-library legal files,
Classpath information and assembly exception are additionally bundled under
licenses/openjfx. The packaged JDK retains its runtime/legal notices.

Before a public release, check the resolved dependency inventory and all native
library notices in the actual Windows artifact. Preserve corresponding source
access for LGPL/GPL runtime components and ensure license texts match the
specific versions resolved. jaudiotagger LGPL-2.1 is additionally bundled. CI
archives jaudiotagger/OpenJFX source JARs and full OpenJFX 21.0.9+1 native
source beside the packages. Stable release requires this review and consumer audio/device testing. An explicitly authorized public preview may retain the documented physical-device acceptance gaps. The CI artifact includes this app's
matching source ZIP and dependency notices for review; it is a test build.

## Corresponding source

For this application: https://github.com/kaizen-flims/Podium-Air-Windows-.
Every CI package artifact includes podium-air-windows-source.zip from exactly
the packaged commit. Build scripts, tests, required resources and provenance
are included. Use that commit to rebuild; see README.md. Dependency source
locations above are provided for unmodified separately licensed libraries.

## Additional local codecs

- jFLAC 1.5.2 (`org.jflac:jflac-codec`) is dynamically loaded as a separate, replaceable JAR. Its project declares LGPL 2.1; individual source files also contain Library GPL 2-or-later and BSD notices. Full LGPL 2.1 and the upstream BSD notice are bundled under `licenses/jflac/`; the exact published sources are included in `dependency-sources.zip`. No codec source has been modified.
- Concentus 1.0.2 (`io.github.jaredmdobson:concentus`) is the Java Opus implementation originally ported by Logan Stromberg. Its BSD-style notices and decoder copyright header are bundled under `licenses/concentus/`. The exact published source JAR and artifact-provided notices are collected beside each build. No native Opus binaries are used.
- The Ogg container reader and PCM cache in this port are new GPL-3.0 code implementing RFC 3533 and RFC 7845. No code was extracted from the RFCs.

FLAC above 16-bit is converted to 16-bit PCM because this JavaFX pipeline accepts 8/16-bit WAV. Opus is decoded at 48 kHz with header gain, pre-skip and final-granule trimming. Multichannel, chained Ogg streams and Ogg Vorbis are explicitly rejected rather than advertised as supported.

- JAAD 0.8.7 (`de.sfuhrm:jaad`) provides local AAC/M4A decoding independently of Windows optional codecs. The exact `jaad-0.8.7` tag is `23a55187689cd5d0a857c49f945dcf16cf49868a`; its public-domain dedication is bundled under `licenses/jaad/LICENSE`. The published source JAR accompanies the build. Encrypted/protected files, external MP4 references and non-AAC M4A tracks are rejected. The desktop ISO-BMFF reader applies supported single edit-list encoder-delay/duration trims; sample-accurate gapless is not claimed.

The native Windows media helper is new GPL-3.0-only C++ code compiled against the Windows SDK with C++/WinRT. It uses the operating system SMTC and Common Item Dialog APIs and includes no third-party audio engine or .NET runtime. The Microsoft Visual C++ runtime is linked by the compiler; its distribution is subject to the Microsoft Visual Studio redistribution terms. Windows SDK/C++/WinRT source headers are under their applicable Microsoft SDK and MIT terms, and their notices must remain in any redistributed SDK source. The source archive includes this port's complete helper and build command.

The shared TTML parser, lyric alignment, overlapping-vocal focus and lyric clock reconciler are additionally reused from the audited Podium Air commit. Their implementation comments remain in place; only the focus/clock visibility is made public for the separate desktop module. The desktop artwork palette/motion renderer, elapsed-time statistics recorder, M3U8 handler and bounded MP4/AAC sample-table reader are new GPL-3.0-only implementations. Microsoft's C++/WinRT MIT license is bundled under `licenses/cppwinrt/`.


For the LGPL codec/tag libraries, the portable distribution keeps their JARs
separate in the application directory. You may replace them with compatible
modified versions and rebuild this application using the supplied scripts and
corresponding sources. They are not statically merged into application code.


The bundled Temurin runtime is pinned to 21.0.12.1+1. Its exact published source
archive (verified against the vendor SHA256), upstream source commit
`1c417fbfc2f70ab03a565b0af0a5a3c6f5e15ad6` and Temurin build scripts at
`e6ba7dec3d07654074559310376a3ae89da5f4ac` are included in the dependency source
bundle, with the JDK release metadata and source locations. The runtime keeps
its original legal notices. This supplements the OpenJFX and codec/tag sources.

## Renderer notice supplement

Skiko 0.9.22.2 is Apache-2.0. Its exact release LICENSE and NOTICE (including the Android Open Source Project acknowledgment) are bundled under `licenses/skiko/`. Skia m132-a00c390e98-1 uses the BSD copyright/license at commit a00c390e98, bundled under `licenses/skia/`. Corresponding upstream locations: https://github.com/JetBrains/skiko/tree/v0.9.22.2 and https://github.com/google/skia/tree/a00c390e98. The native renderer distribution is additionally scanned for supplied legal files during Windows packaging.

## Native Automix port (development branch)

The original Orchard/BitChord TrackAnalysis, TransitionPolicy and TransitionPlanner,
whole-track DSP analyzer, native resampler and JNI bridge are reused with their
complete original copyright headers. Those files remain AGPLv3-or-later within
the GPLv3 combination permitted by section 13 of both licenses. The AGPL text is
bundled at `licenses/automix/AGPL-3.0.txt`; all matching modified/unmodified source
is included in the application source archive. Original authors: SFG545 and
Kushagra Singh. Upstreams: https://github.com/SFG5453/Orchard and
https://github.com/kushagrasinghx/BitChord.

The Windows adapter, PCM preparation and WSOLA renderer are new GPLv3 code.
Neural ONNX models are not redistributed in this branch. DSP measurements do
not claim neural vocal/beat-model parity.

JLayer `javazoom:jlayer:1.0.1` provides MP3-to-PCM decoding for the analyzer/mixer;
it is LGPL-2.1 software by JavaZOOM contributors. The unmodified JAR remains
replaceable, its license is bundled at `licenses/jlayer/LGPL-2.1.txt`, and the
corresponding source accompanies the dependency-source archive.

## Neural Automix adapters and model assets

Windows ports of `BeatTracker`, `VocalTracker`, `MelSpectrogram`, the original C++
mel/STFT front ends and their JNI bridges retain their Orchard/BitChord
copyright headers and AGPL-3.0-or-later terms. The adapters remove Android
Context/Log/settings dependencies and use CPU ONNX Runtime with bounded
head/tail PCM, cancellation checks, checked resource extraction and native
session cleanup. The planner remains confidence-aware; inference is evidence,
not a guarantee of correct beat or vocal predictions.

The exact quantized assets from the Android source commit
`48902e6b20fdcfeb1723e02d4744849e82d5067a` are pinned and verified by SHA-256:

- `beat_this_int8.onnx`: `9dc29f1fcd713d18f48a2755109fce01429ba6d1639607af8ae5c7449b47070f`
- `vocals_umxhq_int8.onnx`: `a2be987b55a29bc149d3a6ae99b08175d81f85ee292a8ea21f96c3a473bc94cb`

Gradle downloads these committed bytes at build time; the installed application
loads its bundled copies and does not contact a model server. No training data
is included. Windows does not re-export or re-quantize the models.

Beat This! code and published model weights are MIT, Copyright (c) 2024 Institute
of Computational Perception, JKU Linz, Austria. Upstream license declaration:
<https://github.com/CPJKU/beat_this#license>. The full notice is bundled at
`licenses/beat-this/MIT.txt`.

Open-unmix's UMX-HQ weights (the vocals target, not the separate UMX-L model)
are attributed to Inria / Fabian-Robert Stöter / Antoine Liutkus. The Android
adapter identifies these as MIT and cites the UMX-HQ deposit:
<https://zenodo.org/records/3370489>. The upstream full MIT notice is bundled at
`licenses/open-unmix/MIT.txt`.

ONNX Runtime CPU Java 1.28.0 is MIT, Copyright (c) Microsoft Corporation.
Its full MIT notice and the exact release's complete ThirdPartyNotices.txt
are bundled at `licenses/onnxruntime/`. The original dependency JAR remains
separate and replaceable. Upstream:
<https://github.com/microsoft/onnxruntime/tree/v1.28.0>.

## Online lyrics

Optional LRCLIB lookup uses the service's public metadata `/api/get` and
`/api/search` endpoints. Networking and matching are a Windows implementation;
no LRCLIB server code or database is redistributed. Local TTML/LRC/embedded
lyrics take priority. Only title, artist, album and duration are sent after the
user enables the feature; returned lyrics are cached locally and attributed
in the lyrics view. No audio or account credentials are transmitted.

## Replay export

The Windows Replay image follows the Android ReplayPoster's 1080×1920 summary
format, 72px margins and content ordering. Android Canvas/Bitmap, Coil and
Palette are replaced with Java2D and already-imported local artwork. Only the
summary export is ported here; Android's full individual story-card family is
not claimed as implemented. Typography uses the Windows system Segoe UI font;
no Apple SF Pro font files are redistributed. The image is previewed before
export and saved through a native Windows PNG dialog with overwrite prompting.
No messaging or external sharing service is invoked.
