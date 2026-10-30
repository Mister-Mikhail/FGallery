# FGallery project instructions

## Read before changing code
Before each task, read:
1. `docs/PRODUCT_SPEC.md` for product behavior and priorities.
2. `docs/UX_RULES.md` for interaction requirements.
3. `docs/OPEN_BUGS.md` for the latest user-tested status and release blockers.
4. The relevant source files, build configuration, current branch, and recent CI result.

The latest direct test report from the user takes precedence over old notes, PR descriptions, generated APKs, and assumptions in code. Update `docs/OPEN_BUGS.md` whenever the user reports a new result. Keep resolved items only after verifying their acceptance criteria; include the build/commit tested and device or reproducible test used.

## Product goals
- FGallery is a fast, local-first Android gallery for photos, videos, folders, and other visual media.
- Keep the agreed QuickPic-inspired navigation and compact controls, while supporting both uniform and dense mosaic grids.
- Preserve the agreed viewer behavior, file-management safety, RAW support, and smooth scrolling in the product documents.
- Do not remove or silently weaken an agreed feature to hide a bug. Discuss a genuine scope tradeoff with the user before changing the requirement.

## Implementation rules
- Use Kotlin, Jetpack Compose, MediaStore/Storage Access Framework, and the existing project architecture unless code inspection shows a concrete reason to change it.
- Keep file work asynchronous where needed; do not block scrolling or viewer interactions with disk, decoding, or permission work.
- Request Android permissions only when the user starts a feature that needs them. Handle grant, denial, and cancellation without crashes or data loss.
- Keep system confirmation dialogs understandable in Russian and specific to the operation being performed.
- Preserve user data. Never permanently delete files without the required system confirmation.
- Avoid unrelated refactors while fixing a reported bug. Make the smallest complete change that solves the reproduced failure.
- Follow the active implementation branch and its open PR. Do not assume `main` contains the current app; check the branch state before editing.

## Completion and APK rules
- A successful Gradle build alone does not prove a user-visible bug is fixed.
- For every reported failure, reproduce it or define a repeatable test, verify the successful path and cancellation/denial/error paths, and check for regressions in adjacent media operations.
- Run the relevant tests and build the debug APK from the exact commit being delivered. Check the CI run and do not present an artifact from a failed or different commit as verified.
- Report the tested commit, build result, and what could not be checked. Never say a feature is fixed until its acceptance criteria have passed.
- Give the user the APK artifact directly when one is produced and attach it to the exact source revision.

## Current source of truth
`docs/PRODUCT_SPEC.md` and `docs/UX_RULES.md` define agreed behavior. `docs/OPEN_BUGS.md` records the current test status and acceptance checks. Keep these documents aligned when requirements change.


## Authoritative approved specification (2026-10-27)
- Authoritative next-cycle specification: `docs/FGallery_TZ_v1.md` (exact copy from `docs/tz-v1`). Its **СОГЛАСОВАНО** requirements override any earlier behavior described here. **РЕШЕНИЕ НЕ ПРИНЯТО** items are not authorized for implementation.
- New code branch: `feature/fgallery-0.3.0-stage1` from 0.2.9 SHA `7a9e290ac3642861274332464c17adbd92ba44fd`. Stage 1 / 0.3.0 is under development; no acceptance on physical Samsung S23 yet. Do not close earlier FG-01…FG-09 blockers without new direct user verification.
- In particular **previous linear .85 zoom**, video **standard Media3 PlayerView controls** and **emoji 🧹** are superseded by TZ v1 §2 / Stage 1. Legacy mentions above are historical; new criteria: logarithmic 0.80/0.85/0.90 diagnostic zoom, unified Compose video chrome, proper Material CleaningServices icon and Cleanup-on confirmation, LibRaw full RAW dimensions.
- TZ v1 stages 2+ are deferred until 0.3.0 stage 1 user-device verification. Release/store, license, editor, commercial protection remain **undecided** where marked so in TZ.

## User-confirmed functionality freeze (2026-10-30)
- A user-confirmed working function is **accepted and frozen**: do not redesign or re-implement it in later tasks unless the user reports a new concrete fault or explicitly requests a change. Automated non-mutating regression tests are still appropriate.
- The most recent direct Samsung Galaxy S23/Android 16 result outranks historical screenshots, old task lists, emulator output and an assistant's implementation claims. Keep separate acceptance statuses per function rather than closing entire subsystems.
- Before making changes to any adjacent code, identify protected behavior, keep the diff narrowly scoped, and run targeted regression checks. In particular preserve approved zoom, Cleanup UX, RAW display dimensions, OPEN RAW indicator and video-player testing hold.
