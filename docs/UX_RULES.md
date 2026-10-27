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


### Shared-signature correction for final delivery — 2026-10-05
The shared development certificate starts with 0.2.7/versionCode9. Final extraction of the published 0.2.6 artifact showed that the CI default keystore path did not control Gradle’s selected key. Explicit Gradle signing configuration and a packaged-certificate CI check replace reliance on that default. All prior random-key debug builds need an initial reinstall; future 0.2.7+ debug updates share the intended key. Functional viewer/bin/update requirements are unchanged.



## Approved TZ v1 supersedes conflicting historical notes — 2026-10-27
- Authoritative next-cycle specification: `docs/FGallery_TZ_v1.md` (exact copy from `docs/tz-v1`). Its **СОГЛАСОВАНО** requirements override any earlier behavior described here. **РЕШЕНИЕ НЕ ПРИНЯТО** items are not authorized for implementation.
- New code branch: `feature/fgallery-0.3.0-stage1` from 0.2.9 SHA `7a9e290ac3642861274332464c17adbd92ba44fd`. Stage 1 / 0.3.0 is under development; no acceptance on physical Samsung S23 yet. Do not close earlier FG-01…FG-09 blockers without new direct user verification.
- In particular **previous linear .85 zoom**, video **standard Media3 PlayerView controls** and **emoji 🧹** are superseded by TZ v1 §2 / Stage 1. Legacy mentions above are historical; new criteria: logarithmic 0.80/0.85/0.90 diagnostic zoom, unified Compose video chrome, proper Material CleaningServices icon and Cleanup-on confirmation, LibRaw full RAW dimensions.
- TZ v1 stages 2+ are deferred until 0.3.0 stage 1 user-device verification. Release/store, license, editor, commercial protection remain **undecided** where marked so in TZ.



### Stage 1 / 0.3.0 approved UX supersession — 2026-10-27
This test APK follows `docs/FGallery_TZ_v1.md`: logarithmic p=80/85/90 zoom with absolute diagnostic scale overlay (remove after user choice), cleanup menu opt-in dialog and orange Material broom only when active, video player unified Compose chrome with 3-second hide ONLY during playback and sticky pause, centered vertical brightness/volume gestures with excluded system navigation margins, and LibRaw-derived RAW sensor dimensions in Quick EXIF/details. The historical one-line linear zoom, emoji broom and displaced Media3 controls above are no longer active UX requirements. Verified Android CI SHA `a5b7ee8f408ed804b87eba49203d337b5d1b1e42`, run `40426282849` 43/43; **not S23 accepted**.

### Clarification from user QA, 2026-10-27 — raster zoom applies to every large raster format
- **Decision confirmed without a new question:** User supplied desired **visible diagnostic indicator** readings, not a formula or a 0%-at-fit requirement. For **all large raster images that open in the image viewer**, regardless of file extension or camera (JPEG/JPG, PNG, HEIC/HEIF, WebP, TIFF, RAW CR2/CR3/NEF/DNG/IIQ/3FR/FFF, etc.), first double tap should show around **50% source-pixel scale**, second around **160% source-pixel scale**. Three supplied TIFF/JPEG/CR2 files are representative S23 examples, **not a format whitelist**. Small images retain the earlier fit→2F→fit rule; maximum pinch ceiling unchanged. These new user-confirmed targets supersede TZ v1 §2.1's fitted first/logarithmic second target **for raster only**; original TZ remains unchanged on file, later direct QA decision documented here.
- **Explanation of indicator:** fit-to-screen on a physically large raster image displays e.g. 18%, 19% or 23%, not zero, because the percentage means how many *display pixels per source pixel*; source 1:1 is 100%. Fit is not meant to read zero. Current diagnostic indicator is intentionally retained until the S23 retest.
- **PDF/SVG:** S23 reports zoom correct; preserve their existing formula and gestures. Diagnostic p=80/85/90 selection now explicitly labelled as affecting *documents only*, to avoid claiming it can change raster target levels.
- **P1 / Status:** targets reflected in `nextRasterDoubleTapZoom`, common `TiledZoomableImage` code path for every non-PDF/SVG raster; pinch rubber-banding **preserved** using the default Telephoto `ZoomSpec(maxZoomFactor = maximum)`. JVM tests for fit .18/.19/.23 and small images, instrumented TIFF/JPEG/PNG/pinch expectations updated. **In code, not device-accepted.** Exact final source SHA/CI to be recorded after build; S23 must recheck the real large TIFF, JPG and CR2 plus at least another large raster type, intermediate zoom and pinch, and preserve PDF/SVG positive result.

### User clarification — keep elastic raster pinch, 2026-10-27
- **Direct Samsung S23 clarification:** user's numbers ~1120% transient → 800% settled and visual ~5–8% were **observations, not a complaint**. The rubber-band/stretch beyond the stable zoom ceiling is an intentional, desirable animation. **Do not disable or alter this existing animation.**
- Reverted unintended `OverzoomEffect.Disabled`: common raster viewer now uses existing `ZoomSpec(maxZoomFactor = maximum)` default elastic behavior. No change to the actual stable pinch ceiling. This applies to all raster formats routed through the viewer; PDF/SVG zoom remains unchanged.
- Pending S23 validation concerns **only the first/second double-tap target readings ~50%/~160% across large images**, independent of image format. Keep visible diagnostic until user checks; do not close raster zoom task on CI alone.

### 2026-10-27 — S23 raster zoom acceptance and small-image ×2.5 amendment (FG-TZ-1.1 / P1)
- **Latest direct user QA:** zoom of large raster images now **works correctly** on Samsung Galaxy S23 / Android 16 after test APK 0.3.0, versionCode 13, source SHA `5a02aa7fffc3b57bee5e7febf2fc53b944f49eab`, Actions run `40435511861` (success). This confirms the **large raster double-tap behavior** (first indicator ≈50% native, second ≈160%, then fit) as user accepted; do not change it. This acceptance does **not** extend to the full viewer/RAW decode, video, Cleanup or entire Stage 1.
- **New explicit requirement for small raster images:** fit → **2.5× fitted linear size** on first double tap → fit on second. Previously the small raster first tap was 2×. Applies to all raster formats routed through `TiledZoomableImage` when `smallImage = fit * 2 >= 1` (current implementation threshold; not separately user-approved). Preserve the fitted reset and pinch behavior including default elastic overzoom animation. The separate PDF/SVG document zoom formula and gesture rules remain unchanged.
- **Status:** new ×2.5 raster small-image behavior implemented on `feature/fgallery-0.3.0-stage1`, pending Android CI and physical S23 verification on an actual small photo. JVM tests cover fitted scale from 0.5 to 2, including normal second-tap reset and pinch-to-fit restart; Android instrumentation updates small PNG double tap 250% / 100% and keeps large-image tests. Do not mark small raster zoom user-confirmed before next S23 report.
- **Work order:** continue with Stage 1.2 Cleanup (enable/cancel, orange broom visibility, exit without confirmation, safe double-tap deletion), Stage 1.3 video player/gestures and Stage 1.4 RAW sensor dimensions, all of which are in code and previously passed emulator CI but not yet accepted on the S23. Do not merge draft PR #6 without approval; do not start Stage 2 before Stage 1 acceptance.

### 2026-10-27 — S23 zoom accepted; Cleanup dual-entrypoint A/B test (FG-TZ-1.2 / P1)
- **New direct user S23 report:** downloaded and tested 0.3.0 versionCode 14 ZIP/APK, source SHA `7a15cc25db1652481658ee0fe789b65ea353d8c8`. **Both large raster 50% → 160% → fit and small raster ×2.5 → fit work correctly.** PDF/SVG zoom previously confirmed. Mark **Stage 1.1 raster tap levels user-accepted on device**; no unrelated acceptance implied. Elastic pinch preserved.
- **User correction to TZ v1 §1.2:** two alternative ways of enabling Cleanup must coexist for hands-on comparison. The top-bar **broom icon is ALWAYS visible**: grey when off, orange `#FF5A36` when on. When grey, tapping requests confirmation; only explicit `Включить` activates, cancel/dismiss keeps disabled. When orange, tapping **disables immediately, no confirmation**. **Menu `Режим уборки` activates immediately without confirmation**; menu off is immediate as before. This replaces old requirement of icon visible only while enabled and menu confirmation. Compare UX on S23 before choosing a single entry path; don't eliminate either now.
- **Orange tile spacing in Cleanup:** reduce existing 5dp gap/padding to **2.5dp**, including album/mosaic/uniform grids and mosaic manual horizontal spacers. Preserve normal-mode zero gap and orange `#FF5A36`.
- **Status:** implementation in draft PR #6, requires same-revision Android CI and S23 testing of each entry point, cancellation, safe deletion and new spacing. Keep Stage 1.3 Compose video UI/gestures and 1.4 LibRaw sensor resolution in current APK for further user testing; no Stage 2 and no final release until stage 1 accepted.

### 2026-10-27 — user S23 video player test / FG-TZ-1.3 P1
- Real Galaxy S23 / Android 16, app 0.3.0 build 16 (SHA `5930d5ab9b0cd26776684e8c218cbbf0daaa0ae1`), supplied landscape and portrait screenshots. Confirmed: vertical brightness/volume gesture start zones incorrectly extend to the centre; narrow each to the outer **20% of the screen width**, preserving vertical 20%–80% zone and current level/sensitivity; progress slider visually ~3× too thick; filename/time sit too far from slider; Play should be centered over video rather than in bottom row; Play at completed clip must seek to start and replay; while paused tapping empty video should toggle all chrome hidden/visible with fade, never force chrome visible; landscape menu overlaps Android's side navigation buttons and must respect safe side insets.
- Changes implemented in working 0.3.0 branch: 20% outer-only hit detection, 2dp visual slider track with 20dp touch height, centered Play, completed-video restart, paused manual chrome toggle, `safeDrawingPadding` top actions. Original swipe adjustment sensitivity untouched; zoom and Cleanup 1.1/1.2 untouched. UI layout and playback need next S23 recheck. The screenshots do not prove any unrelated 360/RAW regressions.
- **Status:** user's observed defects = reproduced by device screenshots and code; proposed fixes = published to GitHub but not user accepted. Follow-up CI on versionCode **17** required before sending exact APK; physically recheck vertical and horizontal videos, landscape navigation menu, completed replay, pause tap transitions, two 20% swipe zones, volume/brightness ranges and 360 gestures. Draft PR #6 remains unmerged. Stage 1.4 LibRaw true sensor dimensions are next.
