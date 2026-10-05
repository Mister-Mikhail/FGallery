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
