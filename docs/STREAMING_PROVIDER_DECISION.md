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
The Windows app therefore does not collect Google passwords or session cookies.

To implement the remaining core, the owner must choose a supported native-audio
provider and supply its documented API/SDK and authorized application setup, or
provide the relevant provider permission for the requested YouTube capabilities.
The choice needs to cover playback, background operation, download rights,
search/catalog, user sign-in and playlist read/write scopes. A different provider
changes the locked product behavior and cannot be silently substituted.

Independent progress remains concrete: original neural Automix, optional LRCLIB
lyrics, Replay export and native Windows controls are verified; supported
ListenBrainz and secure Windows credential storage are now being tested. Full
source story cards, canvas/translation, Last.fm, official native Discord presence,
shared listening, network addons and device acceptance remain separate gaps.
The complete installer release and website-link replacement follow end-to-end
verification of the agreed product. The older public local preview is clearly
identified as a preview and is not described as full parity.
