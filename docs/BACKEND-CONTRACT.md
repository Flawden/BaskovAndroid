# Backend contract

Baseline: BaskovDiscordBot `v1.31.0 — Library and Mix Navigation Read API`.

Required Android v0.2 read flow:

1. `POST /api/v1/auth/device/pair`
2. `GET /api/v1/auth/me`
3. `GET /api/v1/guilds`
4. `GET /api/v1/home?guildId=...`
5. `GET /api/v1/library?guildId=...`
6. `GET /api/v1/mixes?guildId=...`
7. `GET /api/v1/mixes/{stationSlug}?guildId=...`
8. `POST /api/v1/auth/refresh` when an authenticated request returns `401`
9. `POST /api/v1/auth/logout` on explicit logout

Discord snowflakes remain strings at the wire boundary. `BaskovUser` and device session IDs remain UUID strings.

`Library.favoriteTracks` and `Library.historyTracks` are read-only navigation data for the Android Library screen.

`MixDetail.seedPreview` is a read-only station seed preview. It is **not** a predicted playback queue and must not be presented as one.

Remote mutations remain disabled for this release. Playback remains outside Android v0.2.

The copied OpenAPI file under `docs/baskov-product-api-v1.yaml` is the exact backend v1.31.0 contract used by this client RC.
