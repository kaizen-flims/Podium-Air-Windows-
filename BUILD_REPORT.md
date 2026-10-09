# Build report — 9 October 2026

**Status: implementation prepared; Windows CI not yet verified. Not a finished
Android-parity release.**

Implemented code: Kotlin/Compose desktop UI and native shell, JavaFX audio
engine, file/folder library, local playlists/favorites/history, atomic persistence,
queue edits/repeat/shuffle, speed/EQ/sleep timer, local lyrics and two-player
crossfade. Reused original domain and lyric code from source SHA
48902e6b20fdcfeb1723e02d4744849e82d5067a.

Authored tests cover duplicate queue identity, shuffle restoration, current
selection across removal/reordering, repeat policies, lyric timing/entity parsing,
state round-trip/recovery, missing references, WAV import, local LRC offsets,
playback coordinator behavior and stale callbacks. Test execution is pending.

The current Linux workspace has JDK 17 but no cached Gradle/JDK 21/dependencies.
Direct GitHub/Maven/Gradle downloads were unavailable under its network policy;
repository content was accessed using the connected GitHub tools. Windows
GitHub Actions is configured to run the actual build/tests and package/startup
smoke. No compile or Windows runtime success is asserted until Actions returns.

## Acceptance gates

| Gate | State |
|---|---|
| A — source audit / legal reuse | Structural audit and reuse assessment recorded; runtime-native notice verification remains before public release |
| B — native Windows startup | Pending packaged startup smoke |
| C — audio plays/pauses/seeks/transitions | Engine implemented and coordinator tests authored; hardware smoke pending |
| D — parity accurately tracked | FEATURE_PARITY.md records local preview and major blocked/missing integrations |
| E — installable Windows CI artifact | Pending actual workflow success |
| F — documentation, credits, reports | Prepared; final CI evidence to be appended |

Full streaming/account parity is blocked by the source's provider integration
and the prompt's supported-API requirement. Automix requires separate AGPL
reuse/decoder/model assessment. SMTC, hardware keys, device hotplug, performance,
DPI, offline errors across real formats, runtime-native licenses, signing and
installer install/uninstall behavior remain to be tested. No public release,
main merge or auto-update has been performed.
