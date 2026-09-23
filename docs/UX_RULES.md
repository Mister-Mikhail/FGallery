# FGallery UX Rules

## Fixed interaction rules

### Album browser
- The primary structure is albums/folders, in the spirit of QuickPic.
- Avoid a permanent bottom navigation bar.
- Keep top-level controls compact.

### Thumbnail grid
- Tap once: open media.
- Double tap: no destructive action.
- Two views are supported:
  - uniform equal tiles;
  - mosaic tiles with different heights/aspect ratios.

### Open media
- Horizontal swipe: previous/next item.
- Pinch: zoom image.
- Pan: move around a zoomed image.
- Double tap: move the currently open media item to system Trash, after Android's required confirmation flow.
- A visible Trash control may coexist with the gesture.

### Search
- From album browser: search albums.
- From inside an album: search media in the current album.

### Destructive actions
- Use Android system confirmation/Trash mechanisms.
- Do not silently permanently delete media when the system Trash API is available.

## Visual direction

The reference mood is QuickPic/QP Gallery:
- restrained dark surfaces;
- small gaps;
- dense media presentation;
- minimal decorative UI;
- content over chrome.

FGallery-specific addition:
- mosaic presentation that respects source proportions and creates a less rigid visual rhythm than QuickPic's classic equal-tile grid.

### EXIF / details
- EXIF information is not permanently overlaid on a newly opened image.
- Single tap toggles the viewer chrome.
- If the Quick EXIF setting is enabled, showing the viewer chrome also shows a compact EXIF summary over the open image.
- A second single tap hides both the chrome and compact EXIF.
- Quick EXIF can be disabled from Settings for users who do not want metadata over the image.
- Disabling Quick EXIF affects only the compact overlay, never the full details view.
- The opened-media viewer must provide an information/details action.
- Details should surface EXIF and file metadata that actually exists in the source file.
- Keep the information presentation compact, dark-theme friendly, and close in spirit to QuickPic's details/EXIF presentation.
- Never synthesize camera, exposure, GPS, or other EXIF values that are not present.

## Viewer priorities

Viewer quality is a major product differentiator.

Priorities:
1. smooth swiping;
2. responsive pinch/pan;
3. sharp rendering;
4. graceful handling of very large images;
5. low memory pressure;
6. predictable gestures with no conflict between zoom/navigation/Trash.
