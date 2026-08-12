# BaskovAndroid v0.10.0 — Local Music Library & Player

## What changed

- Browse audio already indexed by Android MediaStore.
- Search local tracks by title, artist or album.
- Play local `content://` media through the existing Media3 PlaybackService.
- Reuse Now Playing, queue, seek, background playback, notification, lock-screen and media buttons.
- Keep authenticated Baskov HTTP playback and Product API `startMillis` seek unchanged.
- Show local album artwork when the platform exposes it, with the Baskov mascot fallback.
- Persist local queue position/artwork for process-death recovery while still decoding v0.9 playback snapshots.
- Mark the active playback source as `PHONE` or `BASKOV SERVER` in Now Playing.

## Android permissions

Android 13+ requests `READ_MEDIA_AUDIO`. Android 12 and lower use the legacy read-storage permission. Audio files remain on the device and are opened through their MediaStore content URI.

## Smoke checklist

1. Upgrade from v0.9.0 without clearing app data.
2. Open **Музыка на телефоне** and grant audio access.
3. Confirm local tracks appear and local search filters title/artist/album.
4. Start a local track and verify Now Playing shows `PHONE`.
5. Verify play/pause, previous/next, queue jump/removal and ±15/native slider seek.
6. Background the app and verify notification/lock-screen/headset controls.
7. Kill/recreate the app and verify the saved local track/position is offered for resumption.
8. Return to a server track and verify remote artwork and Product API seek still work.
