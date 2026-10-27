# FGallery Stage 2 / 0.3.1 — release optimization (draft)

**Priority P1; status: implemented in GitHub (initial R8 build); CI pending; NOT verified on Galaxy S23.**

Parent source is the unmerged Stage 1 working branch at SHA `cef72ed6cf4731e9483fa3b483cb956e84c5c236`. Stage 1 build 21 still requires exact-head CI and physical QA of the new horizontal "OPEN RAW" loading animation. Stage 1 video remains under user observation. No PR merge or final release is authorized.

## Implemented on independent draft working branch
- `app/build.gradle.kts`: 0.3.1 versionCode22; Android release type with R8 `isMinifyEnabled = true`, Android resource shrink, release APK signed with the **existing development-only test certificate** for update compatibility. This is not a production distribution signing key.
- `app/proguard-rules.pro`: JNI and reflective protections for AndroidLibRaw, PDFium, TIFF decoder classes and Media3 extension points. Keep native method names.
- Android CI Stage2 branch: assembles debug + **minified release** APK, verifies signing of debug, archives `mapping.txt`, collects release APK size and uploads minified arm64 artifact.
- Android 16 instrumentation specifically targets `connectedMinifiedTestAndroidTest` on this branch (`testBuildType = "release"`); debug branch continues its existing `connectedDebugAndroidTest` path.
- The repository still embeds the shared debug key for older compatibility. **Do not make repo private or change GitHub secrets without a dedicated authorization and migration**. No separate store signing key has been created.

## Next items in approved TZ §2 Stage2
- Verify build success and all Android 16 tests on the **minified** release variant. Resolve R8 runtime/JNI regressions before device QA.
- Baseline Profile Gradle **module and capture test are implemented** (AndroidX plugin 1.5.0): startup, grid scroll and viewer swipe; real generated profile and benchmark numbers are **not yet captured**, fixture-backed viewer/RAW coverage requires seeded media. Do not fabricate benchmark claims.
- Keep mapping for each delivered SHA; compare arm64 APK size to the last verified 0.3.0 debug APK.
- Check S23 originals and operations: X2D 3FR, P65+ IIQ, Canon CR2, large TIFF, PDF, SVG, video/360, cleaning/trash/restore, album navigation, smooth scrolling and install-over-update.
- **Acceptance:** no Stage2 release before successful CI, user QA on Samsung Galaxy S23, and stage 1 follow-up. Do not merge stacked draft PR without approval.

### Development CI correction
- Initial Stage2 Android CI on SHA `d88e513d2556391848ea1c08b8df786e4c408abb` failed because Gradle/AGP did not expose `connectedReleaseAndroidTest` on the production nondebuggable release variant. A separate `minifiedTest` buildType preserves release-equivalent R8/minified/resource-shrunk code while permitting instrumentation on the Android 16 emulator.
- CI on subsequent branch SHA with the `minifiedTest` variant initially found Kotlin DSL misuse `initWith(getByName("release"))`; corrected by explicitly configuring the test variant minification and keep rules. **Recheck the exact next CI SHA before declaring success**.
- `:baselineprofile` producer added, wired into app ProfileInstaller and manual `generateBaselineProfile` task. Profile generation and measured improvements not yet tested. No stable release promotion.
