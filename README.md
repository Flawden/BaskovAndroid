# Baskov Android

External Android client for Baskov Music.

## v0.7.0 — Time Seek & Playback Progress

v0.7 connects the v0.5 recovery checkpoint and v0.6 Now Playing UI to BaskovDiscordBot v1.33 time-based streaming seek:

```text
Compose Now Playing
   ├─ live elapsed / duration
   ├─ seek slider
   └─ ±15 s
          ↓
MediaController
          ↓ restart current MediaItem URL
?startMillis=<absolute track position>
          ↓
MediaSessionService → ExoPlayer → authenticated HTTPS
          ↓
BaskovDiscordBot v1.33 → dedicated LavaPlayer stream → Ogg/Opus
```

### Implemented
- everything from v0.6: full Now Playing, MediaSession queue, queue jump/remove, background/system controls;
- live absolute playback position while a track is active;
- total duration captured from `X-Baskov-Playback-Duration-Millis`;
- slider-based seek and ±15 second seek controls;
- official Baskov Android launcher branding with legacy, round and adaptive icon resources;
- time seek by restarting the current provider-neutral stream with Product API `startMillis`;
- no fake HTTP byte-range support: the Ogg response remains `Accept-Ranges: none`;
- queue navigation resets non-current items to zero-start URLs so normal Previous/Next semantics stay intact;
- durable playback snapshots persist absolute position;
- process-death recovery hydrates the current stream URL with the saved `startMillis`, while still requiring explicit Play before audio resumes;
- Bearer credentials remain outside media URIs and persisted playback state;
- playback user agent is `BaskovAndroid/0.7.0`;
- Android app version is `0.7.0` / versionCode `7`.

### Recovery semantics
A killed/recreated process does not auto-play. The resumable mini-player shows the saved track and position. Pressing Play invokes Media3 playback resumption, rehydrates auth from encrypted SessionStore, rebuilds the saved queue and asks BaskovDiscordBot v1.33 for the current track beginning at the saved absolute time.

### Intentionally absent
- HTTP byte Range seek for the generated Ogg body;
- System UI post-reboot resume carousel / Android Auto browse tree (`MediaLibraryService`);
- Cast;
- remote Discord playback mutations;
- favorites mutations.

## Build prerequisites

- JDK 17
- Android SDK 36
- Gradle 8.13
- Android Gradle Plugin 8.13.0
- AndroidX Media3 1.8.0 (`media3-exoplayer` + `media3-session`)
- Kotlin coroutines 1.10.2 (`android` + `guava` bridge for Media3 async resumption)

The repository CI runs unit tests, lint, debug APK assembly and unsigned release APK assembly. The release artifact can be downloaded to Termux, aligned with `zipalign` and signed locally with the persistent Baskov release key.

## Backend

The v0.7 client requires BaskovDiscordBot `v1.33.0+` Product API behind HTTPS. The stream endpoint accepts `startMillis` for time-based server-side seek and returns the full track duration in `X-Baskov-Playback-Duration-Millis`. Do not expose raw Spring port `18080` publicly.
