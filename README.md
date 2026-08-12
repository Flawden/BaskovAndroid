# Baskov Android

External Android client for Baskov Music.

## v0.5.0 — Playback Resilience & Recovery

v0.5 hardens the v0.4 system player against process recreation and long-running background sessions:

```text
Compose UI
   ↓ MediaController
MediaSessionService
   ├─ durable queue / position snapshot
   ├─ playback resumption callback
   ├─ process-local Bearer bridge
   └─ encrypted SessionStore → token refresh rotation
   ↓
MediaSession → ExoPlayer → authenticated HTTPS → Baskov Product API v1.32+
```

### Implemented

- everything from v0.4: MediaSessionService background playback, notification, lock-screen and headset/Bluetooth controls;
- persists the provider-neutral playback queue, current item and approximate position while playback is active;
- restores a resumable mini-player after the app process is recreated without silently auto-playing audio;
- pressing Play on a resumable session invokes Media3 playback resumption and rebuilds the saved queue;
- declares Media3 `MediaButtonReceiver` so headset/Bluetooth media-button resumption can restart the service lifecycle;
- recovers the Bearer token from the encrypted device session before preparing restored stream URLs;
- centralizes access-token refresh rotation behind one process-wide mutex so UI API traffic and background playback cannot race the same rotating refresh token;
- proactively refreshes access tokens that are too close to expiry before starting a queue and while a playback queue remains active;
- keeps Bearer credentials out of persisted playback state and out of media item URIs;
- explicit Stop, guild switch and logout clear the resumable playback snapshot;
- adds codec tests for playback snapshot round-tripping and malformed-state rejection;
- bumps Android app version to `0.5.0` / versionCode `5`.

### Recovery semantics

A killed/recreated process does not start audio by itself. The UI surfaces the saved track as `можно восстановить`; pressing Play resumes through the MediaSession callback. Queue and current-item identity are restored. Product API v1.32 streams are intentionally non-seekable (`Accept-Ranges: none`), so the recovered current track restarts from its beginning; the checkpointed position is retained only as future-ready state until backend range/seek support exists.

### Intentionally absent

- System UI post-reboot resume carousel / Android Auto browse tree (`MediaLibraryService` is a later product step);
- automatic retry of a stream after server-side session revocation during the already-open HTTP request;
- Cast;
- seek/range UI;
- remote Discord playback mutations;
- favorites mutations.

## Build prerequisites

- JDK 17
- Android SDK 36
- Gradle 8.13
- Android Gradle Plugin 8.13.0
- AndroidX Media3 1.8.0 (`media3-exoplayer` + `media3-session`)
- Kotlin coroutines 1.10.2 (`android` + `guava` bridge for Media3 async resumption)

This source bundle does not contain a generated `gradle-wrapper.jar`. The bootstrap script uses an installed Gradle 8.13 when available, otherwise downloads the official Gradle 8.13 distribution and generates the wrapper:

```powershell
.\scripts\bootstrap-gradle-wrapper.ps1
```

Then run the full local Android gate:

```powershell
.\scripts\android-gate.ps1
```

The gate runs `testDebugUnitTest`, `lintDebug` and `assembleDebug`.

## Backend

The v0.5 client requires BaskovDiscordBot `v1.32.0+` Product API behind HTTPS. No backend release is required for v0.5. Do not expose raw Spring port `18080` publicly.
