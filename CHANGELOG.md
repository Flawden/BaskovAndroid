# Changelog

## 0.5.0 — Playback Resilience & Recovery

- Added durable provider-neutral playback snapshots for queue, current item, approximate position and play intent.
- Added Media3 playback resumption so a recreated service can rebuild the saved queue after process death; the current track restarts from its beginning because Product API v1.32 streams are intentionally non-seekable.
- Added a resumable mini-player state: process recreation never silently auto-plays; the user explicitly presses Play to resume.
- Added Media3 `MediaButtonReceiver` for headset/Bluetooth media-button service restart and resumption.
- Added encrypted-session auth rehydration before restored stream URLs are prepared.
- Centralized access-token refresh rotation behind a shared process-wide coordinator used by both the UI repository and PlaybackService.
- Added proactive access-token refresh before playback and during long-running active queues.
- Kept Bearer tokens out of persisted playback snapshots and media item URIs.
- Explicit Stop, guild switch and logout continue to clear playback and now also clear resumable state.
- Added playback snapshot codec unit tests.
- Added `kotlinx-coroutines-guava` for asynchronous Media3 resumption.
- Bumped Android app version to `0.5.0` / versionCode `5`.

## 0.4.0 — System Playback & Background Experience

- Moved ExoPlayer ownership from the Activity/ViewModel into a Media3 `MediaSessionService`.
- Added Android background playback with the Media3-managed foreground media notification.
- Added system/lock-screen/headset/Bluetooth playback controls through `MediaSession`.
- In-app playback now uses a `MediaController` connected to the same system playback session.
- Added automatic media audio-focus handling and audio-becoming-noisy pause behavior.
- Reopening the app reconnects to the active session and reconstructs queue/playback state.
- Preserved provider-neutral playback: Android still receives only authenticated Baskov Ogg/Opus stream URLs.
- Persistent auth remains encrypted; the playback Bearer token stays process-local and is cleared on explicit stop/logout/guild switch.
- Added `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` declarations and a `mediaPlayback` service type.
- Added `androidx.media3:media3-session:1.8.0` without changing the existing Media3 ExoPlayer version.
- Bumped Android app version to `0.4.0` / versionCode `4`.

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
