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
