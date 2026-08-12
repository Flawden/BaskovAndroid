# Changelog
## 0.11.0 — Library Control & Playback Modes
- Added persistent local music folder filtering.
- Added shuffle and repeat playback modes.
- Improved Media3 navigation so shuffled queues use native next/previous semantics.
## 0.10.0 — Local Music Library & Player
- Added an on-device music library backed by Android MediaStore.
- Added Android 13+ `READ_MEDIA_AUDIO` and legacy read-storage permission handling.
- Added title/artist/album filtering for local tracks.
- Added direct `content://` playback through the existing Media3 MediaSessionService.
- Reused the same Now Playing, queue, seek, background, lock-screen and media-button surfaces for local audio.
- Added local album-art URI propagation with the Baskov mascot as fallback.
- Generalized the Media3 data source so authenticated HTTP Baskov streams and local content URIs coexist without creating a second player.
- Preserved Product API time seek (`startMillis`) for remote streams while local tracks use native Media3 seeking.
- Extended playback snapshots to preserve artwork and local playback positions while remaining backward-compatible with v0.9 snapshots.
- Updated playback HTTP user agent to `BaskovAndroid/0.10.0`.
- Bumped Android app version to `0.10.0` / versionCode `10`.
## 0.9.0 — Artwork & Player Experience
- Consumed BaskovDiscordBot v1.34 `X-Baskov-Playback-Artwork-Url` metadata from the resolved playback stream.
- Added Coil 3.5.0 Compose/OkHttp image loading for track artwork.
- Show real artwork in Now Playing and Mini Player with the neon Baskov mascot as fallback.
- Propagated artwork into Media3 `MediaMetadata.artworkUri` for system media surfaces.
- Preserved active playback during metadata-only MediaItem updates.
- Kept zero-config pairing, server seek, progress, queue editing and recovery unchanged.
- Updated playback HTTP user agent to `BaskovAndroid/0.9.0`.
- Bumped Android app version to `0.9.0` / versionCode `9`.
## 0.8.0 — Zero Config & Visual Identity
- Polished the compact Now Playing transport row for narrow phones: ±15 second controls stay circular and never wrap.
- Embedded the production Baskov Product API base URL in generated `BuildConfig`.
- Pairing is now zero-config for normal users: enter only the one-time `/device pair` code.
- Kept the persisted SessionStore/backend URL flow intact after successful pairing.
- Added the first Baskov dark Material 3 color system: deep navy, neon purple, cyan and magenta.
- Redesigned Now Playing around artwork, centered track identity, a cleaner timeline, circular transport controls and Baskov server status.
- Reused the official v0.7 launcher artwork as the first in-app playback hero until per-track artwork is available.
- Preserved v0.7 server-accurate seek, ±15 second controls, queue editing, MediaSession background playback and absolute-position recovery.
- Updated playback HTTP user agent to `BaskovAndroid/0.8.0`.
- Bumped Android app version to `0.8.0` / versionCode `8`.
## 0.7.0 — Time Seek & Playback Progress
- Added time-based seeking through BaskovDiscordBot v1.33 `startMillis` streaming.
- Added live absolute playback position and full track duration in Now Playing.
- Added a seek slider plus ±15 second controls.
- Added the official neon Baskov Android launcher artwork as legacy, round and adaptive icons.
- Added duration capture from `X-Baskov-Playback-Duration-Millis` without pretending the Ogg body supports HTTP byte ranges.
- Playback navigation normalizes queue items back to zero-start streams so revisiting a track does not inherit an old seek offset.
- Playback snapshots now persist absolute track position and resume the current stream from the saved time after process death.
- Preserved MediaSession/background/lock-screen/notification and queue editing behavior.
- Updated the playback HTTP user agent to `BaskovAndroid/0.7.0`.
- Bumped Android app version to `0.7.0` / versionCode `7`.
## 0.6.0 — Now Playing & Queue Experience
- Added a full-screen Now Playing surface opened directly from the persistent mini-player.
- Added visible playback queue with current-track highlighting and queue position.
- Added direct jump-to-track controls backed by the existing MediaSession queue.
- Added queue item removal without rebuilding the queue or bypassing system playback ownership.
- Kept resumable post-process-death snapshots read-only until Media3 playback resumption hydrates the live queue.
- Preserved background, lock-screen, notification, headset/Bluetooth and process-recovery behavior from v0.5.
- Kept seek/progress controls intentionally absent while Product API v1.32 streams remain non-seekable.
- Updated the playback HTTP user agent to `BaskovAndroid/0.6.0`.
- Bumped Android app version to `0.6.0` / versionCode `6`.

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
