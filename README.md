# Baskov Android

First external client for Baskov Music.

## v0.1.0 — Pairing, Auth, Guild Selection & Home

The app intentionally does **not** implement playback yet. It proves the new multi-client architecture end-to-end:

```text
Discord /device pair
        ↓
Android pairing
        ↓
BaskovUser + encrypted device session
        ↓
GET /auth/me
        ↓
GET /guilds
        ↓
select guild
        ↓
GET /home
        ↓
Jetpack Compose UI
```

### Implemented

- Kotlin + Jetpack Compose app shell.
- Pairing via `POST /api/v1/auth/device/pair`.
- Access + refresh token persistence encrypted with Android Keystore AES/GCM.
- Automatic refresh-token rotation on authenticated `401` and one retry.
- `GET /api/v1/auth/me` account bootstrap.
- `GET /api/v1/guilds` guild discovery.
- Persisted guild selection.
- `GET /api/v1/home` personalized read model.
- Home sections for Today, For You, themes, library stats, recent tracks and taste maturity.
- Logout / local session cleanup.
- Release builds reject cleartext API URLs; debug builds may use HTTP for local emulator development.

### Intentionally absent

- audio playback;
- Media3 / MediaSession;
- background service;
- remote Discord playback mutations;
- favorites mutations;
- mix start/stop controls.

Those belong to later releases. BaskovDiscordBot v1.30.0 still reports `mutationsEnabled=false`.

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

The Android client requires BaskovDiscordBot `v1.30.0+` and its Product API behind HTTPS. Do not expose raw Spring port `18080` publicly.
