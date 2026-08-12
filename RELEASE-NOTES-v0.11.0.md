# BaskovAndroid v0.11.0 — Library Control & Playback Modes

## Highlights

- Device library now exposes the real MediaStore folders containing audio.
- Users can include/exclude folders, so call recordings and voice-note directories can stay out of the music queue.
- Folder selection persists across app restarts; “Все” restores the unfiltered library.
- Shuffle is now a first-class Media3 playback mode.
- Repeat cycles through OFF → ALL → ONE and persists across restarts.
- Local seek uses native Media3 seek while remote Baskov streams keep the existing `startMillis` time-seek contract.
- Previous/next now use Media3 playlist navigation so shuffle order is respected.

## Smoke checklist

1. Upgrade over v0.10.0 without clearing app data.
2. Open “Музыка на телефоне” and verify folder list/counts.
3. Disable the call-recording folder and confirm its files disappear immediately.
4. Reopen the app and confirm folder selection persists.
5. Start a local track and enable Shuffle; skip through several tracks.
6. Cycle Repeat OFF → ALL → ONE and verify Now Playing state.
7. Seek a local track; verify no playback restart/glitch beyond normal seek.
8. Play a remote Baskov track and verify remote seek still uses the server path correctly.
9. Background/lock-screen controls still work.
