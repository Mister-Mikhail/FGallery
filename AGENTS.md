# FGallery project instructions

## Read first on every new chat/task
1. `docs/PROJECT_STATUS.md` — live handoff, last published APK, working PR, priorities and what is not verified.
2. `docs/OPEN_BUGS.md` — reproduction, user test evidence and acceptance criteria.
3. `docs/PRODUCT_SPEC.md` and `docs/UX_RULES.md` — agreed product and interaction rules.
4. `docs/CHATGPT_PROJECT_INSTRUCTIONS.md` — durable ChatGPT-project instructions.
5. The actual GitHub PR, commit SHA, latest CI, code and releases; never assume `main` equals the active app.

## Fundamental safeguards
- The **latest direct user test outranks old assistant statements, emulator results, PR text and unverified claims**.
- Distinguish: discussed / local-unpublished / committed / CI pass / user-device verified. Only mark the last as accepted.
- Record every user-reported change or new bug in `PROJECT_STATUS.md` and the detailed tracker; include priority, steps, expected/observed, build SHA, release and remaining checks. Update the handoff before ending work.
- Respect latest product decisions, including supported Phase One P65+ IIQ; do not resurrect older blanket deferrals. DWG remains deferred.
- Do not remove important capabilities to conceal bugs. Preserve media across permission denial, cancel, low storage, USB removal and failed exports. Avoid unrelated refactors.

## Working branches, builds, APKs
- The app previously lives in `feature/quickpic-masonry-mvp` / PR #2; recheck before changes. `main` may be scaffold only.
- Debug 0.2.7 is the last documented published/tested release. New builds use a higher versionCode/versionName and the shared test certificate to update it in place.
- A **push to the active app branch can automatically publish a GitHub release** after CI. Do not push documentation-only changes to that branch at an already-released version; use a separate documentation PR or coordinate with a versioned app release.
- Run targeted tests/Gradle build and Android instrumentation checks. Keep real Samsung S23/Android 16 validation separate from emulator success. Do not claim a crash fixed until observed acceptance.
- When an APK is actually available, supply the exact APK link/file, commit, CI result and a focused real-device test list. Never invent artifacts or present an earlier SHA as the newly fixed build.

## FGallery UX and technology
- Offline-first Kotlin/Jetpack Compose gallery; MediaStore/SAF, album-first QuickPic style, dark compact UI, dense/uniform grids.
- Preserve precise scroll and paging, Cleanup double-tap with on-volume recycle bins, Quick EXIF, restrained chrome, sharp huge image/RAW/TIFF rendering, animated GIF and muted video previews, true spherical-video metadata/gyro/touch/pinch, SVG/PDF.
- Move and crop are release-critical. One/multi-file moves must truly finish; cancel/deny/grant and large batches must not crash, data must be verified. Crop save-copy/replace paths must persist output and protect originals; RAW crop JPEG-copy only. Keep video controls synchronized at 1.5 seconds.
- Follow detailed current acceptance requirements in `docs/PROJECT_STATUS.md` and `docs/OPEN_BUGS.md`, not a stale summary.
