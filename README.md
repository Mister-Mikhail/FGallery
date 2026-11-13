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

`main` is the canonical source of truth and contains the current Android implementation. Begin every new development session by reading `AGENTS.md` and this README from `main`, then consult the linked specifications and open bugs. Use a short-lived branch per task; merge it into `main` only after the required CI checks pass. Do not treat an unverified APK as a tested release.
