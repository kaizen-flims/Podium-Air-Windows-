# Google desktop account setup

The published 0.4.0 preview adds an official system-browser OAuth flow and read-only YouTube Data API playlist browsing. **No registered project/client has been supplied, and no live Google account has been tested.** Packaged tests use an explicitly isolated loopback fixture and dummy account.

## Configure your own desktop client

1. In [Google Cloud](https://console.cloud.google.com/), choose your project and enable **YouTube Data API v3**.
2. Configure Google Auth Platform branding, audience and data access. For a project in testing, add the Google accounts that will test the app. Public distribution and sensitive scopes can require Google's verification.
3. Create an OAuth client with application type **Desktop app**, then download its JSON configuration. Android and Web application client files are not accepted.
4. Open Podium Air **Account → Connection setup → Import Desktop client JSON** and select that file through the native Windows dialog.
5. Click **Sign in with Google**. Complete authorization on Google's page in your normal browser. Allow profile and read-only YouTube playlist permission if you want to browse your playlists.

The downloaded client configuration stays under your control. Podium Air stores only its required client fields, refresh token and granted scope list in separate Windows Credential Manager entries. Tokens/client secret are absent from library JSON, release assets and normal logs. Access tokens live in memory and refresh when you explicitly browse or refresh. The app never asks for a Google password or browser cookies.

## What this connection does

- Uses a random loopback port bound to `127.0.0.1`, one-time state, S256 PKCE, bounded callback parsing, a three-minute timeout and explicit cancellation.
- Requests `openid email profile` and `https://www.googleapis.com/auth/youtube.readonly`. The Account screen shows the returned profile and only shows an email marked verified by Google's userinfo endpoint.
- Lists owned YouTube playlists and their accessible video items, including pagination and item order. A view is bounded to 500 entries and clearly reports further entries. Deleted/private items without accessible video metadata can be omitted.
- Handles denied playlist permission, expired/revoked grants, API setup/quota errors and transient network failures. Refresh credentials are retained across transient failures; no credentials are recreated by an operation cancelled during disconnect.
- **Disconnect** deletes the saved authorization and attempts Google's revocation endpoint. If remote revocation fails, the UI says so and directs you to remove access in your Google account. **Remove configuration** deletes the local configuration and authorization; it does not promise remote revocation.

YouTube Data API metadata is distinct from YouTube Music's private account/library data. Some Music-only lists are not exposed. **There is no native YouTube audio, downloads, playlist editing, or bidirectional/full YouTube Music sync.** An item explicitly opens on YouTube in the browser for service playback; no extracted media URL enters the native queue. The supported native-audio provider decision remains in [STREAMING_PROVIDER_DECISION.md](STREAMING_PROVIDER_DECISION.md).

## Evidence and limits

Protocol tests cover bad/duplicate state, declined authorization, PKCE/token fields, permission denial, header-only tokens, paginated/ordered metadata, refresh/disconnect races, redirects and response bounds. Packaged UI checks exercise the native Unicode JSON picker, the same OAuth/token/API implementation, Windows credential writes/deletes and both themes using the dummy loopback provider. **These checks do not establish successful live Google sign-in.** Project/client setup and authorized account acceptance remain the owner's next step.

Primary API documentation: [installed-app OAuth](https://developers.google.com/identity/protocols/oauth2/native-app), [OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect), [owned playlists](https://developers.google.com/youtube/v3/docs/playlists/list), [playlist items](https://developers.google.com/youtube/v3/docs/playlistItems/list).

Verified executable source: `2258dc63986876955efec6b447667b264388fb8c`; [Windows build](https://github.com/kaizen-flims/Podium-Air-Windows-/actions/runs/38245481117) and [release](https://github.com/kaizen-flims/Podium-Air-Windows-/releases/tag/v0.4.0-preview.1). All six named account regression methods and the packaged/native fixture flow passed. A real-account test still needs your registered Desktop client. One Google connection is saved per Windows user; changing the imported client clears its previous saved authorization.
