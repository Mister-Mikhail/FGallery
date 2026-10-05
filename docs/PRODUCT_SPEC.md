# FGallery Product Specification

## 1. Product goal

FGallery is a fast, local-first Android gallery for browsing and viewing media stored on the device.

The visual and interaction baseline is QuickPic / QP Gallery: compact controls, strong focus on content, fast navigation through folders/albums, and no permanently visible bottom navigation bar.

FGallery is not intended to be a visual clone. The main deliberate visual extension is an optional mosaic layout with media tiles of different sizes based on the aspect ratio of the source media.

## 2. Core principles

- Local-first and offline-first.
- Fast startup and fast scrolling.
- QuickPic is the interaction-performance benchmark: scrolling, opening media, closing media, and horizontal paging should feel immediate and continuous rather than like screen rebuilds.
- Minimal chrome: media should occupy most of the screen.
- Folder/album-oriented navigation.
- Dark interface as an important default direction, with light theme support.
- No mandatory account, cloud backend, subscription, or network dependency for the gallery itself.
- Modern Android storage APIs.
- FGallery uses an app-managed recycle-bin state for fast cleanup without per-item Android confirmation. Permanent deletion still uses Android's protected delete confirmation when required.

## 3. Main navigation

### Albums screen

The app opens on the album/folder browser.

Each album shows:
- cover thumbnail;
- album/folder name;
- media count.

Top app bar:
- FGallery title;
- search;
- a dedicated sorting action;
- overflow menu.

Sorting is global. The selected sort mode applies consistently to the album/folder browser and media inside every album/folder, and is remembered across app restarts.

No permanent bottom navigation.

### Album contents screen

Opening an album shows its media.

Top app bar:
- back to albums;
- album name;
- search;
- grid-mode switch;
- overflow menu.

Supported grid modes:
1. Uniform grid — classic equal square tiles.
2. Mosaic grid — dense justified rows with variable tile width and height based on source aspect ratios. The layout must fill available width without large black holes or unintentional empty cells.

A single tap on a thumbnail opens the media viewer.

Double tap on a thumbnail must NOT delete or move media.

## 4. Media viewer

The viewer is full-screen and optimized for images and video.

### Images

Required behavior:
- swipe horizontally between media items in the current album/filter;
- pinch to zoom;
- pan while zoomed;
- preserve image quality as much as possible;
- later milestone: tiled/region decoding for very large images so large photos do not require one huge bitmap in memory.

### Videos

- full-screen playback;
- normal playback controls;
- horizontal navigation between neighboring media items;
- only the currently visible viewer page may play audio/video; precomposed neighboring video pages must stay paused and muted;
- video tiles in folder grids use one-at-a-time muted live previews, rotating every 5 seconds.

### Double-tap behavior and Cleanup mode

Double tap applies only to the currently opened media item.

Normal mode:
- first double tap: smoothly animate to the first zoom level;
- second double tap: smoothly animate to maximum zoom;
- third double tap: smoothly animate back to fit-to-screen;
- pinch zoom remains available at all times.

Cleanup mode:
- Cleanup mode is explicitly enabled by the user from the gallery menu;
- while Cleanup mode is active, double tap on the currently opened media item moves that item immediately into the FGallery recycle bin without an extra Android confirmation dialog;
- after moving the current item to the recycle bin, the viewer stays open and advances to the next media item; if the removed item was last, it moves to the previous item; only an empty remaining set closes the viewer;
- Cleanup mode can be enabled or disabled both inside a folder and from the top-level folder browser; its menu action uses the same warm red/orange warning color as the cleanup grid spacing;
- thumbnail double tap must never delete media;
- the gallery visually indicates Cleanup mode with warm red/orange spacing/accent so the destructive gesture cannot be forgotten.

A visible Trash action may also remain available in the viewer.

## 5. Media types

Required:
- common image formats supported by Android;
- animated GIF playback;
- video;
- spherical / equirectangular 360° video playback when the media is identified as 360 content;
- broad RAW recognition.

RAW formats targeted for recognition include:
- DNG;
- CR2 / CR3;
- NEF / NRW;
- ARW / SR2 / SRF;
- ORF;
- RW2;
- RAF;
- PEF;
- RAW;
- 3FR;
- FFF;
- IIQ.

RAW support has two levels:
1. discover and classify the file;
2. render a useful thumbnail/preview when the device decoder supports it, with fallback work planned for unsupported RAW variants.

## 6. Search and filters

Search should work against:
- album/folder names;
- media file names.

Filters:
- all media;
- photos;
- videos;
- RAW.

## 7. EXIF and media details

FGallery must expose metadata for the currently opened media item, following the QuickPic-style information/details experience.

For images, show all useful metadata that is actually present, including as available:
- file name and path/location;
- file size;
- pixel dimensions and orientation;
- capture date/time;
- camera maker and model;
- lens information;
- focal length;
- aperture;
- shutter/exposure time;
- ISO;
- exposure compensation;
- flash state;
- white balance;
- GPS/location coordinates when embedded;
- image format / MIME type;
- RAW-specific metadata when available.

Do not fabricate missing EXIF values. Fields that are absent in the source file should be omitted or shown as unavailable.

For video, the details view should show available technical metadata such as duration, dimensions, file size, codec/container information when available, frame rate when available, and creation date/time.

The exact visual arrangement should remain compact and consistent with the QuickPic reference screenshots: readable metadata without taking over the media viewer.

Quick EXIF behavior in the full-screen image viewer:
- EXIF is hidden when the media is first opened;
- one single tap toggles the viewer controls;
- when the controls are shown and the Quick EXIF setting is enabled, a compact EXIF summary is shown over the image;
- if the user swipes to another image while Quick EXIF is visible, it must stay visible and update to the metadata of the newly opened image;
- another single tap hides the controls and compact EXIF again;
- the compact EXIF overlay includes the current file name with extension;
- users can disable this automatic compact EXIF overlay in Settings;
- disabling Quick EXIF must not remove access to full file details;
- the full information/details view must always remain available from the viewer and must show all available file/EXIF metadata.

## 8. File management and multi-selection

FGallery must support file-management operations directly from the album grid.

Selection behavior:
- long press a media tile to enter selection mode;
- tap additional tiles to add/remove them from the selection;
- show the number of selected items in the top app bar;
- leaving selection mode clears the selection.

Required operations:
- move one or many selected files to another folder;
- move one or many selected files to the FGallery recycle bin without per-item confirmation;
- rename a single selected file;
- share one or many selected files;
- permanent deletion uses Android confirmation / MediaStore delete APIs where required; moving into the FGallery recycle bin itself is non-destructive and immediate.

Rename is available only for a single selected item. Move and Trash must work for multiple selected files.

## 9. Planned functional scope

The following remain part of the planned application scope:
- sorting controls;
- date grouping/navigation;
- favorites;
- hidden folders/media;
- recycle-bin management with a dedicated in-app screen, restore, Select All, and Clear Bin / permanent delete through Android system confirmation;
- metadata/details;
- share;
- edit/open-in-editor actions;
- reliable large-image viewing;
- performance tuning and preloading.
- two-tier background thumbnail cache keyed by media modification time: a fast ~480 px preview for immediate display and a high-quality ~1440 px preview for large mosaic tiles, so scrolling uses cached previews without visibly pixelating DSLR/high-resolution images;
- cache generation starts automatically after media discovery, prioritizes album covers, refreshes visible tiles reactively as batches complete, and prewarms the currently opened album.

## 10. Android implementation direction

- Kotlin.
- Jetpack Compose.
- MediaStore for local media discovery.
- Android system Trash APIs where available.
- Media3 for video playback.
- Coil for ordinary image thumbnail/display loading, with specialized large-image/RAW handling added where needed.

## 11. Source of truth

This file records agreed product behavior.

When implementation details, old README text, generated mockups, or remembered chat context conflict with this document, update this document deliberately first and then align the code to it.


## Branding

Current FGallery brand direction:
- use the selected middle logo concept as the working visual identity;
- the compact upper F mark inside the frame is the launcher/app icon direction;
- keep supporting brand words minimal: only "FAST" and "RAW READY";
- do not use additional slogans or descriptive taglines in the current logo treatment.


## User clarification — 2026-10-04
- Normal double-tap zoom stops at fit scale increased by 30%, then maximum, then fit.
- Request all-files access at first launch after installation. Granting it enables automatic PDF/SVG discovery, empty-folder listing, folder creation, and direct file moves without repeat Android write prompts. Denial keeps basic browsing/document-picker fallback available.
- Photo and video crop offer copy or original replacement (with explicit overwrite confirmation); RAW crop saves only a JPEG copy.


### Latest viewer/edit requirements (2026-10-04)
Double tap: fit ×1.30 → 90% of maximum → fit. Pinch retains the point between fingers. PDF pages accept ordinary vertical drags. Transparent images must not retain a static backing copy during zoom. Visible live video tiles keep their player during scrolling; keep a poster until the first video frame. Crop offers concise copy/original choices once; choosing original authorizes replacement, with backup and recovery, without another app confirmation. RAW saves only a copy.


### Launcher icon reference (2026-10-04)
Use the user's 1000267911.jpg artwork itself: metallic gold/silver F on black, with all four outer corner brackets visible. Preserve aspect ratio and use an adaptive foreground inset that keeps the brackets inside circular and rounded-square launcher masks. This supersedes the simplified vector approximation.


### 0.2.3 requirements
- Double tap uses the actual current scale: fit ×1.30 → 90% of maximum → fit. Pinching back to fit restarts the cycle. Raster and document viewers share the policy.
- Video spatial crop uses draggable corner handles. Time trim uses a filmstrip and pauses/seeks the real preview at the moved boundary. Save copy/original choices authorize the operation once.
- TIFF discovery, thumbnails and viewer use native TIFF decoding with bounded bitmap memory.
- Launcher icon uses the complete supplied artwork without another adaptive foreground inset.


### User clarification for 0.2.4
Device: Samsung Galaxy S23, Android 16. Zoom stops are 30% and 90% of the linear scale range between fit and configured maximum: fit + (maximum − fit) ×0.30 / ×0.90, then fit. This supersedes all earlier fit ×1.30 wording. SD cards and USB drives must refresh the library when connected/removed, and Cleanup must create a separate on-volume recycle bin for each drive. Disconnected drive records must survive removal and reappear when reconnected.


### 0.2.5 clarified pixel zoom and startup requirements
The user confirms the double-tap maximum for raster photos, TIFF and RAW is native 1:1 (one file pixel per screen pixel). Stops are fit + (native − fit) ×0.30 / ×0.90, then fit. Do not zoom smaller images below their fitted size. PDF retains its accepted screen-relative gesture range, with native rerendering of the visible area to avoid blur. Show a useful RAW/TIFF preview promptly while full detail becomes ready; preserve full sensor/native pixels. Reopening the app and opening albums must retain the catalogue and cached previews; refresh discovery in the background without clearing the visible grid.


### Phase One preservation requirement — 2026-10-04
The user confirms successful display of RAW files from a Phase One camera with the current handling combination. Preserve this valuable behavior when changing RAW/TIFF decoders, discovery, caches or viewer sources. Full native detail and speed still need their own device checks; successful display alone must not be expanded into a claim that every Phase One model/RAW variant is supported. Current zoom behavior is described in `docs/ZOOM_BEHAVIOR.md`.

### Next-build zoom request — discussion only, 2026-10-04
The user now requests the same ceiling for double-tap targets and the existing pinch ceiling, superseding the native-1:1 tap endpoint. Preserve the current pinch allowance; do not lower pinch to 1:1. Even small images must enlarge to twice their fitted linear scale on the first double tap. Small-image cycle: fit → 2× fit → fit. Large-image cycle: fit → first enlargement → second enlargement → fit. Working interpretation: first enlargement is 2× fit for all raster sizes, replacing the previous 30% first stop; the previous 90%-of-range requirement remains the proposed second stop for large images until clarified. The small/large pixel threshold has not been specified; using whether 2× fit reaches native 1:1 is a proposal, not an accepted threshold. A pinch back to fit must continue to restart the cycle. The user explicitly prohibits a build now; record requirements only, without runtime changes or CI dispatch.

### Full-resolution camera acceptance targets — 2026-10-04
The user identifies the Phase One back as P65+ and requires correct full native detail, including its roughly 60.5MP full-image baseline of 8984×6732 pixels and large TIFF conversions around 350MB. Canon EOS 5DS R full RAW is an additional required target (roughly 50.6MP class; standard full image 8688×5792). Preserve full active-image dimensions when preparing TIFF/PNG sources; allow legitimate source crops, rotation and decoder treatment of sensor borders, without replacing full detail with a thumbnail or forcing a cropped source up to the camera maximum. The previously shown 4679×6460 TIFF is about 30.2MP and does not establish full-size P65+ acceptance. Memory investigation and estimates for the pinned RAW decoder are documented in `docs/RAW_MEMORY_TARGETS.md`. This remains discussion and requirements recording; no runtime changes or build now.


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
