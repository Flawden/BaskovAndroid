# Baskov Android

External Android client for Baskov Music.

## v0.11.0 — Library Control & Playback Modes

Local music can now be filtered by the real MediaStore folder that owns each track. Folder choices persist locally, so call recordings, voice notes and other non-music directories can be excluded without moving files.

Now Playing also exposes persistent Media3 shuffle and repeat modes. Previous/next navigation follows Media3 playlist order, including shuffled queues, while local seek stays native and remote Baskov streams keep the existing server `startMillis` contract.

## v0.10.0 — Local Music Library & Player

BaskovAndroid now reads the device audio library through Android MediaStore and plays local `content://` audio through the same Media3 session used by remote Baskov playback. Local tracks therefore reuse Now Playing, queue controls, native seek, background playback and system media controls instead of introducing a second player stack.

Remote Baskov streams keep server-accurate `startMillis` seek and authenticated Product API transport. Local tracks use native Media3 seek and require no Baskov playback Bearer token.

## v0.9.0 — Artwork & Player Experience

BaskovAndroid v0.9 consumes artwork metadata from BaskovDiscordBot v1.34. Real artwork is loaded with Coil and propagated into Media3 metadata for the app and system media surfaces. If artwork is absent or fails to load, the official neon Baskov mascot remains the fallback.

All v0.8 behavior remains: zero-config pairing, server-accurate seek, live progress, ±15 controls, queue editing, background/system playback and absolute-position recovery.

## v0.8.0 — Zero Config & Visual Identity

v0.8 turns the working v0.7 player into a product-facing Baskov Android experience.

### Zero-config pairing

The production Product API is compiled into `BuildConfig.BASKOV_API_BASE_URL`:

```text
https://baskov.109-237-96-117.sslip.io
```

A normal user now runs `/device pair` in Discord and enters only the one-time code in Android. The selected base URL is still persisted through the existing encrypted session flow after successful pairing.

### Visual identity

- permanent dark Material 3 Baskov palette;
- deep navy surfaces with neon purple, cyan and magenta accents;
- redesigned Now Playing screen;
- large Baskov artwork hero;
- centered title/artist identity;
- live elapsed/total timeline and server-accurate seek;
- circular playback controls with ±15 second seek;
- visible Baskov server/playback status;
- existing queue remains directly below the player.

### Playback

All v0.7 behavior remains:
- BaskovDiscordBot v1.33 `startMillis` time seek;
- duration from `X-Baskov-Playback-Duration-Millis`;
- absolute-position process recovery;
- Media3 background, notification, lock-screen, headset/Bluetooth controls;
- queue jump/remove;
- no fake HTTP byte ranges.

The playback user agent is `BaskovAndroid/0.8.0`.

## Build prerequisites

- JDK 17
- Android SDK 36
- Gradle 8.13
- Android Gradle Plugin 8.13.0
- AndroidX Media3 1.8.0
- Kotlin coroutines 1.10.2

The repository CI runs unit tests, lint, debug APK assembly and unsigned release APK assembly. Release APKs are aligned and signed in Termux with the persistent Baskov release key.

## Backend

BaskovAndroid v0.8.0 targets BaskovDiscordBot `v1.33.0+` through the production HTTPS Product API. Raw Spring port `18080` remains private.
