# BaskovAndroid v0.11.1 — Repeat & Library UX Hotfix

## Fixes

- Remote Repeat ONE always restarts the track from 0:00, even after ±15 second or slider seeks.
- Remote automatic/repeat transitions normalize stale `startMillis` offsets before replaying a track.
- Repeat ALL gets an explicit end-of-queue fallback and app next/previous wraparound, including shuffled timelines.
- Local folder selection now has both “Выбрать все” and “Снять все”.
- The potentially huge MediaStore folder list is collapsed by default and can be shown/hidden from the folder card.

## Smoke checklist

1. Upgrade over v0.11.0 without clearing app data.
2. Open local music: folder card is compact until “Показать” is tapped.
3. Tap “Снять все”: zero local tracks remain; select only the wanted music folder(s).
4. Collapse/reopen the folder card and verify selection persists.
5. Local Shuffle remains functional.
6. Repeat ONE on a remote track: seek near the end, wait for repeat, verify playback restarts at 0:00.
7. Repeat ALL: let the queue reach the end and verify it wraps to the first item in the active playback order.
8. With Shuffle + Repeat ALL, verify wraparound still continues playback.
9. Verify local repeat modes still restart local content from the beginning.
10. Verify background/lock-screen playback and server playback are unchanged.
