# FGallery UX Rules

## Fixed interaction rules

### Album browser
- The primary structure is albums/folders, in the spirit of QuickPic.
- Avoid a permanent bottom navigation bar.
- Keep top-level controls compact.
- Returning from an opened folder must restore the exact previous scroll position of the folder browser.
- Sorting has its own top-bar button and is global across the folder browser and all opened folders.

### Thumbnail grid
- Tap once: open media.
- Mosaic mode must densely fill rows without large black gaps; source aspect ratios should drive varying tile widths/heights.
- Long press: enter multi-selection mode and select that tile.
- While selection mode is active, tap toggles additional selections instead of opening media.
- Double tap: no destructive action.
- Two views are supported:
  - uniform equal tiles;
  - mosaic tiles with different heights/aspect ratios.

### Open media
- Horizontal swipe: previous/next item.
- Pinch: zoom image.
- Pan: move around a zoomed image, but never beyond the actual scaled image edges.
- In normal mode, double tap smoothly animates first zoom -> maximum zoom -> fit-to-screen.
- In Cleanup mode, double tap moves the currently open media item immediately into the FGallery recycle bin with no extra confirmation dialog.
- Cleanup deletion keeps the viewer open: advance to next item, or previous if the removed item was last.
- Cleanup mode can be toggled from both the album browser and album contents; its menu item uses the cleanup red/orange accent.
- A visible Trash control may coexist with the gesture.

### Search
- From album browser: search albums.
- From inside an album: search media in the current album.

### Multi-selection and file operations
- Long press starts selection mode.
- Tap toggles selection while selection mode is active.
- The selection app bar shows the selected count.
- Rename is available for one selected item.
- Move, Share, and Trash may operate on multiple selected items.
- File operations must preserve selection until the operation succeeds or the user cancels.

### Recycle bin
- FGallery must expose a dedicated recycle-bin screen.
- The recycle bin is app-managed so sending an item there is instant and does not require a system confirmation dialog.
- The recycle-bin menu provides Select All and Clear Bin.
- Selected recycle-bin items can be restored immediately.
- Permanent deletion / Clear Bin uses Android system confirmation when the platform requires it.

### Destructive actions
- Never silently permanently delete media.
- Do not require a system confirmation merely to move an item into the FGallery recycle bin.
- Use Android system confirmation for permanent deletion when required.
- Cleanup mode must have a persistent red/orange visual indicator while active.

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
- Swiping to another image while chrome / Quick EXIF is visible keeps that state visible and refreshes the metadata for the current image.
- Quick EXIF displays the current file name including its extension.
- A second single tap hides both the chrome and compact EXIF.
- Single tap must continue to toggle chrome / Quick EXIF even while the image is zoomed; pan recognition must not swallow stationary taps.
- Quick EXIF can be disabled from Settings for users who do not want metadata over the image.
- Disabling Quick EXIF affects only the compact overlay, never the full details view.
- The opened-media viewer must provide an information/details action.
- Details should surface EXIF and file metadata that actually exists in the source file.
- Keep the information presentation compact, dark-theme friendly, and close in spirit to QuickPic's details/EXIF presentation.
- Never synthesize camera, exposure, GPS, or other EXIF values that are not present.

### Motion / perceived performance
- QuickPic is the behavioral benchmark for perceived speed.
- Opening a media file uses a short, subtle transition rather than an abrupt screen replacement.
- Closing a media file returns smoothly to the preserved gallery position.
- Thumbnail generation and video-frame extraction must be cached/off the scroll-critical path so fling/scroll remains smooth.
- Generate thumbnails automatically in the background and invalidate cached previews when the underlying media modification time changes.
- Prioritize album covers, update tiles reactively as thumbnail batches finish, and prewarm media for an album when it is opened.
- Video thumbnails may become muted live previews one at a time; each active live preview runs for about 5 seconds before rotating to the next video.

## Viewer priorities

Viewer quality is a major product differentiator.

Priorities:
1. smooth swiping;
2. responsive pinch/pan;
3. sharp rendering;
4. graceful handling of very large images;
5. low memory pressure;
6. predictable gestures with no conflict between zoom/navigation/Trash;
7. neighboring precomposed video pages must never leak audio before they become current.
