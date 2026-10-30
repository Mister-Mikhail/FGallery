# Stage 1.4 — supplied RAW originals verification (2026-10-27)

Status: **Linux original-file analysis completed; Android CI / S23 device acceptance still separately required**. Do not confuse native desktop LibRaw 0.21.4 results with the Android library's embedded LibRaw 0.21.2.

The four original files were supplied by the user for compatibility testing, examined **read-only** inside this conversation, and **not committed or uploaded to the public GitHub repository**. No camera serial numbers or embedded pictures are published here.

| Sample | Format | Observed camera | Embedded preview / container | LibRaw 0.21.4 iwidth×iheight (unrotated) | Sensor active crop / result |
|---|---|---|---|---|---|
| B0005625.3FR | 3FR, 211,718,144 bytes | Hasselblad X2D 100C | root IFD JPEG **3888×2918**, Orientation=6; RAW SubIFD **11904×8842** | **11904×8842** (orientation flip=6) | RAW SubIFD TIFF tag 50720 `DefaultCropSize` **11656×8742**, tag 50719 `DefaultCropOrigin` **128×96**; **oriented output 8742×11656**. Do not use preview or overscanned RAW buffer dimensions. |
| CF003296.IIQ | IIQ, 51,093,653 bytes | Phase One P65+ | root TIFF 296×220 previews | **8984×6732** (raw buffer 9036×6784; top/left margins 6/36), flip=0 | Valid visible RAW area from LibRaw, not root TIFF 296×220. |
| IMG_2395.CR2 | CR2, 23,398,638 bytes | Canon EOS 5D Mark II | root IFD **5616×3744** | **5634×3752** (raw buffer 5792×3804), flip=0 | LibRaw reports larger useful area than JPEG IFD. Needs S23 expectation/format QA; do not silently substitute embedded JPEG dimensions. |
| CF003241.tif | TIFF RGB, 90,713,616 bytes | Phase One P65+ | **4679×6460** RGB, Orientation=1 | Not RAW (`libraw_open_file` returns unsupported format -2) | Handle via normal TIFF image path, not Stage 1.4 RAW path. |

### Performance and safety
- Desktop test host: LibRaw **0.21.4** (not an S23 measurement). `open_file()` metadata-only is a few milliseconds; AndroidLibRaw 2.0.7 JNI `open()` and `openFd()` also invoke `unpack()`, so prior FGallery metadata code unnecessarily allocates the decoded raw array.
- Observed desktop full unpack peak process RSS: **~295 MB** for 3FR, **~212 MB** for IIQ, **~137 MB** for CR2 (this process includes roughly 94 MB before unpack). These are not measured APK memory peaks.
- New `RawDefaultCropReader` reads at most **256 KiB** from the TIFF wrapper; finds raw SubIFD and validates `DefaultCropSize` against raw bounds and crop origin; handles orientation; on invalid/unavailable tags, falls back to LibRaw. Only RAW 3FR/FFF/DNG eligible for this fast path.
- New `RawSensorDimensions` cache namespace **v2**, invalidating old X2D overscan dimensions, and a global metadata probe `Mutex` to avoid simultaneous high-memory JNI unpack operations for other RAW formats. Never use the root IFD preview for RAW dimensions.
- Regression tests include a synthetic Hasselblad-style TIFF wrapper (3888×2918 preview, 11904×8842 buffer, 11656×8742 valid crop), corrupted/truncated tags, cache correctness, and concurrent DNG metadata requests.

### Remaining acceptance
- Run JVM and Android 16 instrumentation CI at exact code SHA; check no regressions in zoom, Cleanup and held video player.
- Physically verify the actual four user originals on Samsung Galaxy S23 / Android 16. Expected X2D rotated resolution **8742×11656** in detail view, not 3888×2918 or 8842×11904; IIQ **8984×6732**; Canon CR2 requires user-device comparison of visible/camera-advertised dimensions; TIFF **4679×6460** and display behavior.
- Benchmark peak S23 memory and open latency, including simultaneous multiple details requests and repeated openings.
- Stage 1.3 video player remains *held for user testing* at build 17 UX and does not receive further changes. PR #6 remains draft/unmerged; Stage 2 is not authorized yet.
