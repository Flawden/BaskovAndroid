# Baskov Android

External Android client for Baskov Music.

## v0.2.0 — Library & Mixes

v0.2 extends the proven v0.1 pairing/auth/Home vertical slice with real read-only navigation backed by BaskovDiscordBot v1.31.0:

```text
Discord /device pair
        ↓
Android encrypted device session
        ↓
Home
 ├── Library
 │    ├── Favorites
 │    ├── History
 │    └── Recent
 ├── Mixes
 │    ├── Today
 │    ├── For You
 │    └── Themes
 └── Mix detail
      └── seedPreview
```

Tracks can be opened as read-only detail cards. Playback is intentionally deferred to v0.3.

### Implemented

- Kotlin + Jetpack Compose app shell.
- Pairing via `POST /api/v1/auth/device/pair`.
- Access + refresh token persistence encrypted with Android Keystore AES/GCM.
- Automatic refresh-token rotation on authenticated `401` and one retry.
- Account bootstrap and guild discovery.
- Persisted guild selection.
- Personalized Home.
- Library reads from `GET /api/v1/library` including favorites/history track lists.
- Mix navigation from `GET /api/v1/mixes`.
- Read-only mix detail from `GET /api/v1/mixes/{stationSlug}`.
- `seedPreview` is presented only as station seed data, never as a promised playback queue.
- Read-only track detail.
- Manual and Android system back navigation for v0.2 screens.
- Empty/error/loading behavior and explicit refresh actions.
- Release builds reject cleartext API URLs; debug builds may use HTTP for local development.

### Intentionally absent

- local audio playback;
- Media3 / MediaSession;
- background playback service;
- remote Discord playback mutations;
- favorites mutations;
- mix start/stop controls.

BaskovDiscordBot v1.31.0 still exposes this Android surface as authenticated read-only Product API.

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

The v0.2 client requires BaskovDiscordBot `v1.31.0+` Product API behind HTTPS. Do not expose raw Spring port `18080` publicly.
