# Baskov Android

External Android client for Baskov Music.

## v0.6.0 — Now Playing & Queue Experience

v0.6 turns the resilient Media3 playback foundation from v0.5 into a real in-app player surface:

```text
Mini-player
    ↓ tap
Now Playing
    ├─ current track + playback state
    ├─ previous / play-pause / next / stop
    └─ visible queue
         ├─ jump to track
         └─ remove track
              ↓
         MediaController
              ↓
      MediaSessionService → ExoPlayer
```

### Implemented

- everything from v0.5: background/system playback, lock-screen/notification/headset controls, durable queue snapshots, process-death resumption and encrypted-session auth recovery;
- full-screen Now Playing opened from the persistent mini-player;
- current-track title, artist, queue position and connection/buffering/resumable state;
- visible queue with current-item highlighting;
- direct jump to any live queue item through the existing `MediaController`;
- queue item removal through Media3 without moving player ownership back into Compose/ViewModel;
- explicit protection for resumable snapshots: queue editing stays disabled until Media3 has rebuilt the live queue;
- Stop continues to clear both the live queue and durable resumable snapshot;
- playback HTTP user agent updated to `BaskovAndroid/0.6.0`;
- Android app version `0.6.0` / versionCode `6`.

### Queue semantics

The `MediaSessionService` remains the playback source of truth. Now Playing does not keep a second private queue. Selecting or removing an item mutates the live Media3 queue, and the existing v0.5 timeline listener persists the resulting state for later process recovery.

A process-recreated resumable queue is display-only until the user presses Play. This prevents UI queue edits from diverging from the not-yet-hydrated MediaSession.

### Intentionally absent

- seek/progress controls: Product API v1.32 streams are intentionally non-seekable (`Accept-Ranges: none`);
- drag-and-drop queue reordering;
- artwork/catalog image loading;
- System UI post-reboot resume carousel / Android Auto browse tree;
- Cast;
- remote Discord playback mutations;
- favorites mutations.

## Build prerequisites

- JDK 17
- Android SDK 36
- Gradle 8.13
- Android Gradle Plugin 8.13.0
- AndroidX Media3 1.8.0 (`media3-exoplayer` + `media3-session`)
- Kotlin coroutines 1.10.2 (`android` + `guava`)

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

The v0.6 client requires BaskovDiscordBot `v1.32.0+` Product API behind HTTPS. No backend release is required for v0.6. Do not expose raw Spring port `18080` publicly.
