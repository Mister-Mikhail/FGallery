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
- Use two thumbnail tiers: a fast low-cost preview first, then a high-quality ~1440 px cached preview that replaces it for large tiles so high-resolution photos do not look pixelated.
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


## User clarification — 2026-10-04
- First double-tap zoom: fit scale increased by 30%; second: maximum; third: fit.
- On first launch explain all-files access and open its Android settings page. Permit denial and Back; keep the access entry in the menu.
- With access granted and extra move confirmation disabled (default), choosing a destination starts the move. Folder creation is available from the destination toolbar.
- Crop photo/video, then save a copy or explicitly confirm replacing the original; RAW has copy-only output.


### Latest viewer/edit requirements (2026-10-04)
Double tap: fit ×1.30 → 90% of maximum → fit. Pinch retains the point between fingers. PDF pages accept ordinary vertical drags. Transparent images must not retain a static backing copy during zoom. Visible live video tiles keep their player during scrolling; keep a poster until the first video frame. Crop offers concise copy/original choices once; choosing original authorizes replacement, with backup and recovery, without another app confirmation. RAW saves only a copy.


### 0.2.3 requirements
- Double tap uses the actual current scale: fit ×1.30 → 90% of maximum → fit. Pinching back to fit restarts the cycle. Raster and document viewers share the policy.
- Video spatial crop uses draggable corner handles. Time trim uses a filmstrip and pauses/seeks the real preview at the moved boundary. Save copy/original choices authorize the operation once.
- TIFF discovery, thumbnails and viewer use native TIFF decoding with bounded bitmap memory.
- Launcher icon uses the complete supplied artwork without another adaptive foreground inset.


### User clarification for 0.2.4
Device: Samsung Galaxy S23, Android 16. Zoom stops are 30% and 90% of the linear scale range between fit and configured maximum: fit + (maximum − fit) ×0.30 / ×0.90, then fit. This supersedes all earlier fit ×1.30 wording. SD cards and USB drives must refresh the library when connected/removed, and Cleanup must create a separate on-volume recycle bin for each drive. Disconnected drive records must survive removal and reappear when reconnected.


### 0.2.5 clarified pixel zoom and startup requirements
The user confirms the double-tap maximum for raster photos, TIFF and RAW is native 1:1 (one file pixel per screen pixel). Stops are fit + (native − fit) ×0.30 / ×0.90, then fit. Do not zoom smaller images below their fitted size. PDF retains its accepted screen-relative gesture range, with native rerendering of the visible area to avoid blur. Show a useful RAW/TIFF preview promptly while full detail becomes ready; preserve full sensor/native pixels. Reopening the app and opening albums must retain the catalogue and cached previews; refresh discovery in the background without clearing the visible grid.

### Pending next-build zoom policy — 2026-10-04
Use the existing pinch ceiling as the common zoom endpoint; the earlier native-1:1 tap endpoint is superseded. Small images must support fit → 2× fit → fit, with 2× measured in linear screen size. Large images have two enlarged stops before returning to fit. Working interpretation is first stop 2× fit for every raster and second stop at the earlier 90% of the common range for large images; confirm the small/large pixel threshold before implementing it. Preserve the actual-scale reset after pinch-to-fit. This is a discussion-only request: no runtime edits or build now.


## Authorized 0.2.6 changes — 2026-10-05
The user now explicitly requests implementation and a new APK build, superseding the earlier discussion-only pause. Keep debug builds; no production signing key exists.

- Remove forced ordinary/360 viewer selection. Determine spherical playback from ISO-BMFF spherical metadata and the selected Media3 track projection data; filenames and a 2:1 aspect ratio are not sufficient evidence. Unmarked video remains ordinary; device samples are needed to verify camera-specific metadata.
- Show the file location on its own line in the single-tap information overlay, including videos. Prefer the actual source path, including the storage volume; use the document URI only if the provider does not expose a filesystem path.
- Cleanup removal immediately excludes the moved source from the active catalogue and changes the viewer to the next file, or previous file when removing the last. Close the viewer if no files remain. Bind pager pages and players to file URIs, not mutable list positions.
- Ordinary Delete asks once in a compact confirm/cancel dialog. Show operation progress/fade during the move and a completion indication. Cleanup remains prompt-free. Failure preserves the current file and reports an error.
- In-app updates: automatically check the latest public GitHub release when opening/resuming (at most every six hours), offer optional download/install, remember a declined version, and add manual checking in Settings. Download with progress/cancellation; verify APK size, available SHA-256, package, increasing versionCode, and matching certificate. Android performs final installation and unknown-source consent. Future deliberately pushed version tags publish tested APKs; branch builds remain artifacts.
- Use a shared, explicitly public test-only debug certificate from 0.2.6 onward. Earlier randomly signed builds may require one initial reinstall. Production needs a separate private key; do not use this debug certificate for production.
- Raster tap and pinch share M = max(8, 8F). First tap is exactly 2F. Large-source second tap is F + 0.90(M−F); third returns to F. For this build, small sources are classified by 2F ≥ 1 (first tap already reaches native pixels) and return to F on the second tap. This pixel-based threshold is an implementation choice, not a separate user-confirmed definition. Documents use 2×, 7.3×, fit; a pinch back to fit resets the cycle.
- Preserve Phase One P65+ IIQ support. Stream full-resolution LibRaw output into PNG one row at a time using the same color curve, avoiding an additional full Android ARGB bitmap. This does not eliminate LibRaw's other full-size buffers.
- Large RGB/gray 8/16-bit contiguous TIFF strips receive a scanline fallback instead of rejection at the 12MP RGBA strip-buffer guard. Keep all source pixels. Libtiff may still allocate its own compressed-strip buffers; scanline output is not a promise of constant total decoder RAM. Huge tiled/unusual formats retain their existing guards and need samples.

Acceptance remains pending CI and physical-device testing. A synthetic 8984×6732 RGB16 single-strip TIFF models the roughly 350MB P65+ conversion, and streaming RAW color is compared against the previous bitmap output. Original P65+ IIQ / Canon 5DS R samples and actual phone peak memory/timings remain unavailable.


### Additional authorized requirement — recycle-bin viewing, 2026-10-05
The user requests opening files already moved into the recycle bin. A normal tap on a bin tile opens the existing full viewer using the bin item's actual URI, with zoom, video playback, document viewing and paging within the bin. Back returns to the bin. A long press starts selection; taps continue selection while it is active. Viewing does not restore the file or move it again; Cleanup double-tap deletion is disabled within the bin. Editing/rename/move/Trash actions are hidden there; restore and permanent deletion remain the existing bin-list actions. Include this change in the current requested build. Acceptance: actual physically moved photo/video files open, zoom/page, remain in their bin, and return to the list.


### Debug update delivery pipeline — 2026-10-05
For the user's test-build update/delivery requirement, the active-branch push workflow now publishes a public debug release only after both build and Android device tests pass. The release tag points to that exact source SHA, with direct arm64/universal APK assets. PR runs do not publish. A released version cannot be replaced from another commit; the next shipped change must increase versionName/versionCode. This replaces the earlier tag-only/branch-artifact plan and makes the in-app release checker usable for the ongoing debug test cycle.
