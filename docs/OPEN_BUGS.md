# FGallery: open bugs and release blockers

Last updated: 2026-10-03  
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

## P0 — Notification permission/request flow
**Observed:** The latest APK still has an unresolved notification-permission problem; the exact user-visible failure has not yet been specified.

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
