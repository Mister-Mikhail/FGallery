# FGallery: open bugs and release blockers

Last updated: 2026-10-04
Status source: latest user test report. The user reports that the latest APKs did not resolve the issues below. Treat every item as **unverified / still open** until tested against its acceptance criteria on a device.

## P0 — File move crashes or does not move
**Observed:** Choosing a destination can trigger an Android permission/confirmation prompt. The dialog text refers to “изменение файла” instead of “перемещение”. The user cannot cleanly cancel/leave the prompt; pressing Allow crashes the app and the file stays in the original folder.

**Acceptance checks:**
- Move one file and multiple files between folders; verify the destination and source contents after each operation.
- Exercise Android permission/confirmation grant, denial, and cancellation. No path may crash, strand the user in a dialog, lose the file, or report success before the move completes.
- Dialog/action text says “Перемещение” and describes the selected destination.
- Add a Settings option to enable/disable move confirmation; default is off as previously requested.
- Keep selection until success or explicit cancellation; show a clear result and allow recovery from failure.

## P0 — Image crop action crashes
**Observed:** Tapping the crop button crashes the app in the latest tested APK.

**Acceptance checks:**
- Open crop for a supported image, adjust the crop, save, reopen the result, and confirm the saved pixels and orientation.
- Handle unsupported formats, cancellation, storage errors, and permission/URI grant loss without crashing or corrupting the source.
- Preserve the original unless the user explicitly chooses an operation that modifies it.
- Keep the crop entry point functional and provide a useful error state if a format cannot be cropped.

## P0 — Video crop/edit path
**Requirement from prior user instruction:** Video cropping must support both saving as a separate file and modifying/replacing the original.

**Acceptance checks:**
- Crop a test video and save as a new file; verify playback, duration, and output location.
- Exercise the “modify original” path with a clear confirmation and safe recovery if export fails.
- Test cancel, low storage, and unsupported codec paths without crashing or replacing the source with a broken file.

## Clarified — “Notification” report was the move permission flow
**Latest clarification (2026-10-04):** The user was referring to the Android “modify file” dialog while moving media. There is no separately reported notification failure. Track the issue under file moves. The app does not request notification permission.

**Acceptance checks:**
- Identify which FGallery feature actually needs notifications and request permission only when that feature is invoked.
- On a fresh install, test Allow, Deny, and “don’t ask again”/system settings paths on supported Android versions.
- Denial must not block gallery browsing or crash the app. Explain the impact and provide a route to Android Settings only when relevant.
- Update this entry with the exact observed symptom after the next device test; do not guess what failed.

## P0 — Video thumbnails are black / 360° playback incomplete
**Observed:** Video previews still appear as black squares. The user also reports that 360° video viewing is not resolved. Earlier 360° playback orientation was corrected, but angle control was missing; gyro and pinch control are required.

**Acceptance checks:**
- Test thumbnails for several local MP4/H.264 and HEVC files, including files with rotation metadata; show a real representative frame rather than a black tile, or a visible fallback with a recoverable error.
- While a video is open, neighboring/precomposed video pages must remain paused and silent.
- Test equirectangular 360° clips: correct initial orientation, gyro-based looking, pinch zoom, touch interaction, and orientation changes. No upside-down, mirrored, or distorted playback.
- Test ordinary flat video before and after 360° playback to catch player-state leakage.

## P1 — SVG, PDF, and other visual/document formats
**Observed:** The user reports that SVG, PDF, and other visual formats are still not viewable as expected.

**Acceptance checks:**
- Discover SVG and PDF files in the library and identify them by MIME type/extension consistently.
- SVG: render vectors sharply at different zoom levels, preserve transparency/aspect ratio, and show a clear fallback for invalid files.
- PDF: open pages in the viewer, support vertical page navigation and usable zoom, and handle password-protected, malformed, and large documents without crashing.
- Verify supported common raster formats, animated GIF, RAW preview/fallback behavior, and supported video formats. Unsupported files must not appear as successful previews when they cannot be opened.
- Preserve swipe/viewer behavior for ordinary images and videos.

## Release gate
Do not close a blocker based only on compilation, screenshots of the happy path, or a new APK number. For each fix record:
- source commit and APK artifact;
- device/Android version and sample file details;
- steps and result for the acceptance checks;
- relevant CI/build result;
- any acceptance check that remains untested.

The current GitHub default branch is only the initial project scaffold. The active app work is in `feature/quickpic-masonry-mvp` and PR #2; check the repository before changing branches or treating the PR as merged.


## Additional requirements recovered from earlier FGallery chats

These requirements remain in scope even if a later APK or PR description claimed completion. Reopen them when the user reports a regression.

### Move picker and file operation details
- Destination selection must expose all folders, including empty folders, with search and create-folder actions. “Other folder” and “Move here” controls must stay above system navigation insets.
- The operation must execute after destination selection. Default move confirmation is off; a Settings toggle can enable it. If enabled, the prompt says “Перемещение”.
- Preserve selection while an operation is running and until success or user cancellation. Never show success if the URI operation did not complete.
- Rename is single-file only, defaults to no extra confirmation, and any prompt says “Переименование”.

### Image/video edit details
- Image crop must provide a functional editor and save result. If overwriting the original, explicitly ask first. RAW crop produces a JPEG copy.
- Video crop has two paths: save as a separate file, or replace the original with a separate warning/confirmation.
- Share uses Android Sharesheet; Samsung Quick Share should be reachable through that sheet.

### Viewer, video, and performance regressions to watch
- Ordinary double-tap zoom cycle is Fit → medium → maximum → Fit; pinch zoom remains available. Cleanup is a separate toggle, globally available on album root and inside folders.
- Video playback controls and file actions are synchronized. Show initially and after a tap, then hide together after 1.5 seconds.
- Do not let audio from a neighboring/precomposed video leak while a photo or different video is open.
- User reported jerky first folder entry, delayed thumbnail population, pixelated large DSLR previews/zoom, loss of scroll position, cleanup viewer closing instead of advancing, and incorrect full-screen rotation behavior. Retest these when changing caches, preloading, viewer paging, or orientation code.
- Large JPEG/RAW support must be tested with high-resolution camera files including Canon 5DSR where available. Use cached preview tiers/background decoding; do not block scrolling with frame extraction or file I/O.
- Earlier user requirement asks muted live previews, one video at a time, rotating about every 5 seconds. Latest black-square failure remains open; satisfy the requested preview behavior without audio leakage and with a useful fallback.

### Future backlog after release blockers
- Favorites; hidden folders/media; date grouping/navigation; complete recycle-bin screen (restore, multi-select, Select All, Clear Bin with Android protected permanent-delete confirmation); share and edit/open-in-editor actions; fast sharp decoding of very large stills and RAW.
- DWG and Phase One support are explicitly deferred. Do not add them to the current scope unless the user changes that instruction.

## Historical build failure (2026-10-03; check newer CI before delivery)
The latest GitHub Actions run for head SHA `f9132e060449a8ac7d9d3eb6e4bf0a9f1664ccde` completed with `failure`; no APK artifact was uploaded. The PR merge build failed in Kotlin compilation at `app/src/main/java/com/mistermikhail/fgallery/ui/GalleryScreen.kt:995` with unresolved references including `produceState`, `Bitmap`, `withContext`, `Dispatchers`, `MediaMetadataRetriever`, `asImageBitmap`, and related inferred types. Treat this as an open P0 build blocker. Inspect actual file imports and code before changing it; rebuild and verify CI on the resulting exact SHA before distributing an APK.


## Latest direct user report — 2026-10-04

This report supersedes conflicting older entries above. The exact APK/commit and device were not supplied.
- There are **no folder-entry jerks** in the user's current build; do not treat this as an active bug.
- **Gallery scroll-position loss is resolved** according to the user.
- Cleanup and screen rotation **appear to work**; retain regression checks without calling them current failures.
- Image zoom works but the first double tap enlarges too much (about 80%). Required cycle: **fit scale increased by 30% → maximum → fit**.
- The reported “notification permission” issue is actually **the move permission dialog**, not notifications. Android asks to modify a file, accepting does not move it, and Back cannot cancel cleanly.
- Move must run after choosing a destination, without per-file confirmation; users must be able to create a missing destination folder.
- Image crop still crashes/closes the app. Photo and video crop must save a copy or replace the original; RAW may save only a JPEG copy.
- User authorizes requesting the necessary broad file access **on first launch after installation**, to support automatic file discovery and direct moves without repeat prompts.

### Implementation under verification in this change
- First-launch all-files access flow, automatic PDF/SVG discovery with that access, and document picker fallback.
- Direct filesystem moves with destination/source verification and no per-file write dialog after all-files access; empty folders and create-folder UI.
- Own image-crop activity with explicit controls, error state, FileProvider URIs, and JPEG/PNG saves; RAW embedded-preview fallback and copy-only output.
- Video spatial crop and time trim via Media3 export, with copy or separately confirmed original replacement.
- Backup before overwrite; partial SAF deletion never rolls back the only surviving destination copy.
- First double tap uses fit scale increased by 30%, second reaches the configured maximum, third resets.
- Versioned video thumbnail cache, several frame positions, visible unavailable-preview fallback, one muted inline preview at a time.
- Current-page-only video playback, lifecycle pause/resume, 360 touch/gyro with bounded pinch field of view.
- SVG vector rendering and vertically paged PDF rendering/zoom with error states.

These are code changes awaiting CI and device acceptance, **not verified bug closures**. Test with the exact APK/commit delivered in the chat. In particular check all-files permission grant/deny/Back, actual move results, crop saved pixels and playback, overwrite failure recovery, 360 sensor orientation, RAW samples, and large/password-protected PDFs.


## Latest APK 0.2.0 device report — supersedes earlier symptoms
User downloaded the direct artifact link and tested the delivered 45637f5 build. Device/Android not supplied.
- PDF and SVG open. PDF pinch fails to retain the finger focus; dragging pages does not scroll the document.
- First double tap only enlarges about 3–5%. Required cycle is fit ×1.30, then 90% of maximum, then fit.
- Transparent PNG shows an unmoving original behind the zoomed image.
- Live video previews work, but start with black frames and restart when scrolling changes the visible candidates. A visible active tile must keep its player and playback clock.
- Photo crop opens without crashing. Upper corners cannot move down. Saving offers copy/original, then another unwanted window, and does not persist changes.
- Save choices must be concise: Сохранить копию / Заменить оригинал / Отмена. Choosing replace is the authorization; no second application confirmation, for either photo or video. RAW remains copy-only.

0.2.1 changes under verification: focal document transforms with single-finger page scrolling; removal of static PNG backing after decode; preserved preview lifetime and poster until first frame; crop handles without autozoom and byte-verified saving with rollback; concise single save choice and nonmodal progress. Android regression checks cover gestures, transparent PNG, upper handles, crop result, copy/replace bytes and failed overwrite recovery. No blocker closed before their checks pass; real sample files, video export/360 and device-specific behavior remain device acceptance checks.


## Launcher icon — 2026-10-04
User requires the supplied 1000267911.jpg artwork exactly, including all four corner brackets. Previous foreground coordinates extended beyond the adaptive icon safe area and brackets were clipped by launcher masks. 0.2.2 uses the unmodified reference JPEG with proportional safe-area insets, for both normal and round adaptive resources. Acceptance: verify all four brackets remain visible under circle and rounded-square masks; confirm the packaged APK contains the reference bytes. Launcher/device appearance remains a device acceptance check.

0.2.1 verification record: commit 0fef0e3497bb9f0b9c1cf5578992b450ccb8daae, GitHub run 37168505732 successful; 6 unit tests and 5 Android 15 Pixel 2 emulator tests passed (document gestures, 4000×4000 transparent PNG, upper crop handles, readable crop result, copy/replace and failed overwrite recovery). Real user video/RAW fixtures and device-specific storage behavior remain unchecked.


## Latest device report — 2026-10-04 (0.2.2 follow-up)
This supersedes prior emulator success for the real device. JPEG comes from Samsung Galaxy S23; Android version unknown.
- PDF/SVG zoom closes the app. Test real document viewers, pinch release and taps, with finite transforms and cancellation.
- S23 camera JPEG first double tap visually enlarges only 3–5%; first stop must be exactly fit ×1.30. Second stop is described as 90%; retain previous 90%-of-maximum requirement pending clarification. Third stop is fit. Manual pinch back to fit must restart the cycle. All supported still/document formats use the same policy.
- Video crop must have draggable corners, as in the photo editor. Time-trim controls must show the actual frame at the selected boundary.
- TIFF files do not display: implement discovery, thumbnail and full viewer decoding.
- Screenshot 1000267925.jpg shows an undersized icon with a second dark inner plate. Enlarge the artwork and use the source image as the whole icon background, preserving outer corner brackets.
All items remain open until repeatable acceptance checks pass.


0.2.3 first verification: commit 4fd861d built successfully with unit checks, but Android TIFF decoding crashed the process (5 earlier emulator checks passed). Do not treat that artifact as verified. The third-party JNI wrapper installs process-wide SIGSEGV handlers incompatible with ART. Replacement under verification uses libtiff directly through a small JNI bridge, bounded strip/tile decoding and orientation handling, without that wrapper or signal handlers. Added real PDF/SVG gestures, EXIF JPEG pixel-scale and video crop/trim/export checks.
