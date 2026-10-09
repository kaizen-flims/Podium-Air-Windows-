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
BackgroundVocals,LyricGaps,LrcWriter}.kt. Their package names and original
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
| Skiko / Skia (transitive Compose renderer) | resolved by Compose; see packaged inventory | Apache-2.0 / BSD and bundled notices, https://github.com/JetBrains/skiko, https://skia.googlesource.com/skia/ |
| OpenJFX base / graphics / media | 21.0.9 | GPL-2.0 with Classpath Exception; additional native-library licenses. https://github.com/openjdk/jfx21u |
| jaudiotagger | 3.0.1 | LGPL; verify exact artifact license in dependency notices/POM. https://bitbucket.org/ijabz/jaudiotagger/ |
| Eclipse Temurin runtime | JDK 21 build resolved by setup-java | GPL-2.0 with Classpath Exception and runtime legal notices. https://adoptium.net/ |
| Gradle wrapper | 8.14.3 distribution | Apache-2.0, https://github.com/gradle/gradle |

The Gradle dependencyNotices task retains LICENSE, NOTICE, COPYING and legal
files from resolved dependency JARs under licenses/dependencies. OpenJFX GPL,
Classpath information and assembly exception are additionally bundled under
licenses/openjfx. The packaged JDK retains its runtime/legal notices.

Before a public release, check the resolved dependency inventory and all native
library notices in the actual Windows artifact. Preserve corresponding source
access for LGPL/GPL runtime components and ensure license texts match the
specific versions resolved. Public release remains blocked until this review
and audio/device testing are complete. The CI artifact includes this app's
matching source ZIP and dependency notices for review; it is a test build.

## Corresponding source

For this application: https://github.com/kaizen-flims/Podium-Air-Windows-.
Every CI package artifact includes podium-air-windows-source.zip from exactly
the packaged commit. Build scripts, tests, required resources and provenance
are included. Use that commit to rebuild; see README.md. Dependency source
locations above are provided for unmodified separately licensed libraries.
