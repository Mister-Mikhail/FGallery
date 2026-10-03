# FGallery

FGallery is a fast, local-first Android gallery for browsing folders and viewing photos, videos, and other visual media. The interaction baseline is QuickPic: compact controls, fast navigation, and content-first screens. The app adds uniform and variable-size mosaic grids.

## Product goals

- Kotlin and Jetpack Compose.
- Folder-oriented browsing with uniform and dense mosaic grid modes.
- Smooth scrolling, quick viewer entry/exit, image swipe, pinch zoom, and bounded pan.
- Common image formats, animated GIF, RAW discovery/previews, video, and 360° video.
- Reliable file operations with clear Android permission and confirmation flows.
- Full media details and EXIF when metadata exists.
- No required account, cloud service, or network connection for core gallery use.

## Source of truth

- [Product specification](docs/PRODUCT_SPEC.md) — agreed features and product behavior.
- [UX rules](docs/UX_RULES.md) — gestures, navigation, and presentation.
- [Open bugs and release blockers](docs/OPEN_BUGS.md) — latest user-tested status and pass criteria.
- [Project instructions](AGENTS.md) — workflow, verification, and APK delivery rules.

Read these documents before implementation. User-reported failures remain open until the documented checks pass on a device.

## Active implementation

The current Android app implementation and open PR are on `feature/quickpic-masonry-mvp`. The default `main` branch is currently only the initial project scaffold. Check the current branch and CI status before building or distributing an APK.
