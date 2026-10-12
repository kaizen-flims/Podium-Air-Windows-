# Streaming provider input needed

The Windows app is a native Kotlin/Compose application. Its current verified
playback is a baseline, not completion of the streaming-focused Podium Air port.
The user explicitly requested the complete Android experience.

The original master prompt requires: “Respect service terms, copyright, access
controls, and third-party licenses.” It also requires source-supported streaming
and authentication only where authorized and compatible with provider terms,
and continued work on independent tasks when one integration is blocked.

The pinned Android source uses undocumented YouTube/Innertube requests, stream
extraction, PO-token support and Android WebView session cookies. Copying that
pipeline is not a supported native integration under the prompt's requirement.
Google's developer policies address undocumented services (III.D.7), unapproved
downloads (III.E.1), separating audio (III.I.7), and background playback (III.I.9):
https://developers.google.com/youtube/terms/developer-policies.

A normal YouTube Data API key or desktop Google OAuth registration can support
permitted account/metadata operations; neither grants permission to provide
native audio-only background streams or downloads. No OAuth client project or
provider authorization for the required audio capability has been supplied.
The Windows app therefore does not collect Google passwords or session cookies. The 0.4.0 continuation implements the permitted official desktop OAuth/userinfo and read-only YouTube playlist metadata flow. It requires a registered Desktop client JSON and owner's OAuth project/consent/API setup, and remains unverified against a live account. [Account setup and limits](GOOGLE_DESKTOP_SETUP.md). This progresses account functionality independently of the unsupported native-audio capability; it does not establish full YouTube Music sync.

To implement the remaining core, the owner must choose a supported native-audio
provider and supply its documented API/SDK and authorized application setup, or
provide the relevant provider permission for the requested YouTube capabilities.
The choice needs to cover playback, background operation, download rights,
search/catalog, user sign-in and playlist read/write scopes. A different provider
changes the locked product behavior and cannot be silently substituted.

Independent progress remains concrete: original neural Automix, optional LRCLIB
lyrics, Replay export and native Windows controls are verified; supported
ListenBrainz/native credential storage, all eight Replay cards/calendar periods,
and HTTPS WebDAV browsing/offline import are tested. Packaged WebDAV download,
secure credentials and actual native playback also passed. The desktop player
now adapts the source artwork blur, landscape panes and paused sleeve animation;
84 tests and the packaged/scaled player variants passed.
Canvas/translation, Last.fm, official native Discord presence,
shared listening, remaining network addons and device acceptance remain separate gaps.
The new [0.4.0 preview](https://github.com/kaizen-flims/Podium-Air-Windows-/releases/tag/v0.4.0-preview.1) adds the official desktop account/metadata flow to the native glass, lyrics and Automix/local features, with matching source and honest service gaps. Its Windows build passed the packaged loopback/native-credential/UI account checks; no real Google account was used. Website Windows links point to the new installer. This release is distinct from completion of the full streaming product; the provider decision above remains necessary.
