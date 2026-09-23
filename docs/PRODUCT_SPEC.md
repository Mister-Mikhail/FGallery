# FGallery Product Specification

## 1. Product goal

FGallery is a fast, local-first Android gallery for browsing and viewing media stored on the device.

The visual and interaction baseline is QuickPic / QP Gallery: compact controls, strong focus on content, fast navigation through folders/albums, and no permanently visible bottom navigation bar.

FGallery is not intended to be a visual clone. The main deliberate visual extension is an optional mosaic layout with media tiles of different sizes based on the aspect ratio of the source media.

## 2. Core principles

- Local-first and offline-first.
- Fast startup and fast scrolling.
- Minimal chrome: media should occupy most of the screen.
- Folder/album-oriented navigation.
- Dark interface as an important default direction, with light theme support.
- No mandatory account, cloud backend, subscription, or network dependency for the gallery itself.
- Modern Android storage APIs and system Trash behavior.

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
- overflow menu.

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
2. Mosaic grid — variable tile height based on the original media aspect ratio.

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

### Double-tap shortcut

Double tap applies only to the currently opened media item.

Action:
- request moving the current item to Android system Trash.

This gesture is intentionally reserved for Trash and must not be used for thumbnail deletion or double-tap zoom.

A visible Trash action may also remain available in the viewer.

## 5. Media types

Required:
- common image formats supported by Android;
- video;
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
- another single tap hides the controls and compact EXIF again;
- users can disable this automatic compact EXIF overlay in Settings;
- disabling Quick EXIF must not remove access to full file details;
- the full information/details view must always remain available from the viewer and must show all available file/EXIF metadata.

## 8. Planned functional scope

The following remain part of the planned application scope:
- sorting controls;
- date grouping/navigation;
- favorites;
- hidden folders/media;
- recycle-bin management;
- metadata/details;
- share;
- edit/open-in-editor actions;
- reliable large-image viewing;
- performance tuning and preloading.

## 9. Android implementation direction

- Kotlin.
- Jetpack Compose.
- MediaStore for local media discovery.
- Android system Trash APIs where available.
- Media3 for video playback.
- Coil for ordinary image thumbnail/display loading, with specialized large-image/RAW handling added where needed.

## 10. Source of truth

This file records agreed product behavior.

When implementation details, old README text, generated mockups, or remembered chat context conflict with this document, update this document deliberately first and then align the code to it.
