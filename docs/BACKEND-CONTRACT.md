# Backend contract

Baseline: BaskovDiscordBot `v1.32.0 — Mobile Playback Stream API`.

Required Android v0.3 flow:

1. `POST /api/v1/auth/device/pair`
2. `GET /api/v1/auth/me`
3. `GET /api/v1/guilds`
4. `GET /api/v1/home?guildId=...`
5. `GET /api/v1/library?guildId=...`
6. `GET /api/v1/mixes?guildId=...`
7. `GET /api/v1/mixes/{stationSlug}?guildId=...`
8. `GET /api/v1/playback/stream?guildId=...&artist=...&title=...` with Bearer auth and `Accept: audio/ogg`
9. `POST /api/v1/auth/refresh` when an authenticated API request returns `401`
10. `POST /api/v1/auth/logout` on explicit logout

The mobile playback stream is foreground media delivery, not a Discord guild playback mutation. Android provides only provider-neutral artist/title fields. BaskovDiscordBot constructs `TrackIdentity`, chooses a healthy playback provider through the existing `PlaybackResolver`, and streams Ogg/Opus.

Android must not synthesize `ytsearch:`/`scsearch:` identifiers and must not perform provider search/extraction independently.

Discord snowflakes remain strings at the JSON wire boundary. `BaskovUser` and device-session IDs remain UUID strings.

`Library.favoriteTracks` / `Library.historyTracks` remain read-only navigation data. `MixDetail.seedPreview` remains a seed preview, not a promised playback queue; Android may use the visible preview as a **local UI queue context** only after the user explicitly chooses a track.

Remote mutations remain disabled. MediaSession/background transport remains outside Android v0.3.

The copied OpenAPI file under `docs/baskov-product-api-v1.yaml` is the backend v1.32.0 contract used by this client RC.
