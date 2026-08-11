# Changelog

## 0.3.0 — Local Playback

- Added foreground local audio playback with AndroidX Media3 / ExoPlayer.
- Track actions now build a local queue from the current Home/Library/Mix context and play through the authenticated Baskov backend stream.
- Added mini-player controls for play/pause, previous, next and stop.
- Added direct play action on track detail and play buttons on track cards.
- Android sends only provider-neutral track metadata; YouTube/SoundCloud selection and fallback stay on BaskovDiscordBot v1.32.0.
- Playback uses authenticated `audio/ogg` stream responses from `/api/v1/playback/stream`; no Android-side provider search/extraction was added.
- Playback remains foreground-only: MediaSession, background service, notification/lock-screen and headset controls are deferred.
- Bumped Android app version to `0.3.0` / versionCode `3`.
- Updated bundled Product API contract baseline to BaskovDiscordBot v1.32.0.

## 0.2.0 — Library & Mixes

- Added Library navigation with favorites, personal history and recent tracks.
- Added Mixes navigation for Today, For You and taste themes.
- Added read-only mix detail using BaskovDiscordBot v1.31.0 `seedPreview`.
- Added read-only track detail cards without playback.
- Added in-app back navigation and Android system back handling for new screens.
- Added refresh actions and explicit empty states for Library and Mixes views.
- Preserved automatic access-token refresh rotation and authenticated retry for all new read endpoints.
- Bumped Android app version to `0.2.0` / versionCode `2`.
- Updated the bundled Product API contract baseline to BaskovDiscordBot v1.31.0.

## 0.1.0 — Pairing, Auth, Guild Selection & Home

- Added initial Android application and Compose UI.
- Added Baskov device pairing flow.
- Added encrypted access/refresh session storage using Android Keystore.
- Added automatic refresh rotation and authenticated retry.
- Added account bootstrap and guild discovery.
- Added persisted guild selection.
- Added personalized Baskov Home rendering.
- Added explicit HTTPS-only policy for release builds.
- Added CI gate for unit tests, lint and debug APK assembly.
