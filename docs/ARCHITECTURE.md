# Architecture — v0.1.0

```text
Compose UI
   ↓
BaskovViewModel
   ↓
BaskovRepository
   ├── SessionStore ── DataStore + Android Keystore
   └── BaskovApiClient ── Baskov Product API
```

The Android client owns presentation, device-local session material and transport orchestration. It does not own recommendation math, Discord guild authorization, music history, favorites, track identity or playback-provider selection.

## Session handling

Plain access/refresh tokens are never written directly into DataStore. `KeystoreCipher` encrypts token values with an Android Keystore AES-256/GCM key and only ciphertext is persisted.

The backend remains authoritative for revocation and expiry. On the first authenticated `401`, the repository rotates the refresh token under a process-local mutex, persists the rotated token pair and retries the original read once.

## Network policy

Release builds accept only `https://` API base URLs. Debug builds allow cleartext HTTP so the Android emulator can target a local development gateway.

The Android client never assumes that port `18080` is publicly reachable. Production should terminate TLS at the VPS reverse proxy configured around the Baskov Product API boundary.


## v0.10 local media playback

```text
Android MediaStore
      ↓
LocalMusicRepository
      ↓
BaskovViewModel
      ↓
LocalPlaybackController
      ↓
Media3 MediaSessionService
      ↓
DefaultDataSource
  ├── content://  → local device audio
  └── http(s)://  → authenticated Baskov Product API stream
```

There is still exactly one playback owner. Remote Baskov streams keep Product API time-seek semantics by rebuilding the HTTP stream URL with `startMillis`. Device-local `content://` items never receive Baskov query parameters and use Media3 native seek positions instead.
## v0.11 local library control and playback modes

`LocalMusicRepository` reads the MediaStore folder path alongside track metadata. `LocalMusicSettingsStore` persists an optional allow-list of folder paths; an unset filter means all MediaStore music remains visible. The filter only changes the Android library/queue and never mutates or moves files.

Shuffle and repeat are Media3 player properties owned by the same `MediaSessionService`. `PlaybackModeStore` persists them independently of the resumable queue snapshot so they survive app/service recreation. Native Media3 previous/next navigation is used so shuffle and repeat semantics remain authoritative in the player.


## Shared playlists (v0.13)

`BaskovApiClient` talks to the owner-scoped Product API playlist surface. The backend reuses the existing Discord `MusicLibraryRepository`; Android does not maintain a second playlist database. Playlist tracks are provider-neutral in the mobile model and are resolved/persisted server-side. Phone-local `content://` tracks remain outside shared playlists until the future upload/handoff transport.
