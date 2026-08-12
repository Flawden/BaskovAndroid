# BaskovAndroid v0.12.0 — Search & Now Playing Navigation

## What changed
- Added a global Search entry point on Home.
- Search sends the text query to BaskovDiscordBot Product API and shows up to five remote candidates.
- The same screen also searches local audio from the folders currently selected in Library Control.
- Remote results use the existing authenticated Baskov playback stream; local results keep native Media3 playback.
- Tapping the Android system media notification now opens BaskovAndroid directly on the current Now Playing screen.
- MainActivity uses singleTop intent delivery so repeated notification taps do not stack duplicate activities.

## Compatibility
- Requires backend v1.35.0 or newer for remote search.
- Local search still works from MediaStore-selected folders.
- Existing v0.11.1 shuffle/repeat/folder settings and playback snapshot formats are unchanged.
