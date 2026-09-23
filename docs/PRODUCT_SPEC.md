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
- horizontal navigation between neighboring media items.

### Double-tap behavior and Cleanup mode

Double tap applies only to the currently opened media item.

Normal mode:
- first double tap: first zoom level;
- second double tap: maximum zoom level;
- third double tap: return to fit-to-screen;
- pinch zoom remains available at all times.

Cleanup mode:
- Cleanup mode is explicitly enabled by the user from the gallery menu;
- while Cleanup mode is active, double tap on the currently opened media item moves that item immediately into the FGallery recycle bin without an extra Android confirmation dialog;
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
- background thumbnail cache generation keyed by media modification time, so scrolling normally uses prebuilt previews instead of decoding on demand.

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
