# Baskov Android v0.1.0 — Pairing, Auth, Guild Selection & Home

First external Baskov Music client.

## Scope

- device pairing from Discord one-time code;
- encrypted access/refresh token persistence;
- refresh rotation on authenticated 401;
- account bootstrap;
- authenticated guild discovery;
- persisted guild selection;
- personalized Home rendering in Jetpack Compose;
- HTTPS-only release network policy;
- CI gate for unit tests, lint and debug APK build.

## Non-goals

No playback, Media3, background service, remote music mutations or favorite/mix mutations in this release.

## Backend requirement

BaskovDiscordBot v1.30.0+ Product API behind TLS/reverse proxy.
