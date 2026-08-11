# Backend contract

Baseline: BaskovDiscordBot `v1.30.0 — Android Gateway Foundation`.

Required Android v0.1 flow:

1. `POST /api/v1/auth/device/pair`
2. `GET /api/v1/auth/me`
3. `GET /api/v1/guilds`
4. `GET /api/v1/home?guildId=...`
5. `POST /api/v1/auth/refresh` when needed
6. `POST /api/v1/auth/logout` on explicit logout

Discord snowflakes are treated as strings at the wire boundary. `BaskovUser` and device session IDs are UUID strings.

The copied OpenAPI file is included under `docs/baskov-product-api-v1.yaml` so client changes can be reviewed against the exact backend contract used for this release.
