# Baskov Android

External Android client for Baskov Music.

## v0.3.0 — Local Playback

v0.3 turns the read-only v0.2 client into a foreground music player backed by BaskovDiscordBot v1.32.0:

```text
Home / Library / Mix
        ↓
provider-neutral TrackPreview
        ↓
GET /api/v1/playback/stream
        ↓
Baskov backend PlaybackResolver
        ↓
Ogg/Opus over authenticated HTTPS
        ↓
Media3 / ExoPlayer on Android
```

### Implemented

- everything from v0.2: pairing, encrypted device session, guild selection, Home, Library and Mixes;
- foreground AndroidX Media3 / ExoPlayer playback;
- local queue derived from the current recent/favorites/history/mix seed list;
- mini-player with play/pause, previous, next and stop;
- play actions directly from track cards and track details;
- Bearer-authenticated Ogg/Opus stream requests through the production HTTPS Product API;
- existing access-token refresh rotation is validated before a playback queue is handed to Media3;
- Android never performs YouTube/SoundCloud search or source extraction.

### Intentionally absent

- MediaSession and background playback service;
- notification / lock-screen playback controls;
- Bluetooth/headset transport actions;
- seek/range UI;
- remote Discord playback mutations;
- favorites mutations.

BaskovDiscordBot v1.32.0 keeps provider selection and fallback on the backend while the phone only consumes the authenticated audio stream.

## Build prerequisites

- JDK 17
- Android SDK 36
- Gradle 8.13
- Android Gradle Plugin 8.13.0

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

The v0.3 client requires BaskovDiscordBot `v1.32.0+` Product API behind HTTPS. Do not expose raw Spring port `18080` publicly.
