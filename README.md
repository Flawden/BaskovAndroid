# Baskov Android

External Android client for Baskov Music.

## v0.4.0 — System Playback & Background Experience

v0.4 moves playback ownership out of the Activity/ViewModel and into a Media3 `MediaSessionService`:

```text
Compose UI
   ↓ MediaController
MediaSessionService
   ↓
MediaSession
   ↓
ExoPlayer
   ↓ authenticated HTTPS
Baskov Product API v1.32+
```

### Implemented

- everything from v0.3: pairing, encrypted device session, guild selection, Home, Library, Mixes and real phone playback;
- background playback through `MediaSessionService`;
- automatic Media3 foreground media notification;
- Android system media controls and lock-screen controls;
- headset/Bluetooth/system transport commands through the MediaSession;
- automatic ExoPlayer audio-focus handling for media playback;
- automatic pause when an audio output becomes noisy, for example after headphones are disconnected;
- in-app mini-player now controls the same system playback session through `MediaController`;
- playback remains alive when the Activity is backgrounded or removed from recents while audio is active;
- reopening the UI reconnects to the existing MediaSession and reconstructs the current queue/state;
- provider selection remains entirely on BaskovDiscordBot; Android still consumes only authenticated Ogg/Opus stream URLs.

### Security and lifecycle

- persistent device tokens remain encrypted by `SessionStore` + Android Keystore;
- the current playback access token is copied only into process memory after the repository validates/rotates it for a queue;
- media item URIs do not contain the Bearer token;
- changing guild or logging out explicitly stops playback and clears the in-process playback token;
- the service is declared only as a `mediaPlayback` foreground service.

### Intentionally absent

- cold process-death playback resumption after Android kills the whole app process;
- access-token refresh while a very long already-running queue outlives its current access token;
- Android Auto browse tree / `MediaLibraryService`;
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

The v0.4 client requires BaskovDiscordBot `v1.32.0+` Product API behind HTTPS. Do not expose raw Spring port `18080` publicly.
