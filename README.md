# FGallery

FGallery is a local-first Android gallery inspired by the speed and visual restraint of QuickPic, with a more flexible photo layout and a modern viewer.

## Current direction

- Kotlin + Jetpack Compose.
- QuickPic-like minimal interface: content first, no permanent bottom navigation.
- Two grid modes: **Mosaic** (variable-height tiles) and **Uniform** (classic square grid).
- Local photo/video loading through `MediaStore`.
- RAW recognition for DNG, CR2/CR3, NEF, ARW, ORF, RW2, RAF, PEF and more.
- Search by file name and album.
- Filters for all media, photos, video and RAW.
- Single tap opens the viewer.
- Double tap on the opened media item sends it to Android system Trash on Android 11+.
- Full-screen image viewer with pinch zoom and pan; double tap is reserved for the Trash shortcut.

## Active branch

`feature/quickpic-masonry-mvp`

## Next milestones

1. Video playback with Media3.
2. Album/folder browser in the QuickPic style.
3. Better RAW preview fallback for formats the platform decoder cannot render.
4. Date grouping and sorting controls.
5. Favorites, hidden folders and recycle-bin management.
6. Viewer polish: preloading, metadata, share/edit actions and tiled rendering for very large images.
