# Full-size RAW targets and memory estimates

Discussion-only requirements and code review, 2026-10-04. No runtime implementation or build changes.

## Required camera classes

The user identifies the Phase One back as P65+ and requires full native detail from RAW and converted TIFF files, including TIFF files around 350MB. Canon EOS 5DS R RAW is an additional target.

On 2026-10-05 the user confirms original P65+ RAW files are `.IIQ`. Current extension classification selects the LibRaw full-preparation path for them. The TIFF-container routing caveat below remains relevant to other inputs, not these confirmed IIQ files. Actual prepared PNG dimensions and successful full-source display on the phone remain unmeasured.

| Camera | Standard full-image baseline | Exact pixel count | Full ARGB_8888 buffer | Uncompressed RGB, 16 bits/channel |
| --- | --- | --- | --- | --- |
| Phase One P65+ | 8984×6732 (60.5MP class) | 60,480,288 | 241.9MB | 362.9MB / 346.1MiB |
| Canon EOS 5DS R | 8688×5792 (50.6MP class) | 50,320,896 | 201.3MB | 301.9MB |

The camera class designation is rounded and is not always identical to the exported image pixel count. Rotation swaps axes; legitimate crops and decoder treatment of optical-black/sensor-border pixels can alter dimensions. Preserve the full active image returned by the decoder without arbitrary downsampling; do not enlarge a smaller/cropped TIFF to the camera maximum. The previously shown 4679×6460 TIFF has 30,226,340 pixels and does not validate full P65+ detail. Manufacturer pages could not be retrieved from the restricted workspace during this discussion; actual camera RAW samples and decoder results remain necessary for acceptance.

## Inspected decoder versions and allocations

FGallery uses AndroidLibRaw 2.0.7. Its `libraw/src/main/cpp/LibRaw` submodule is pinned to `9646d776c7c61976080a8f2be67928df0750493e`.

- Pinned `libraw/libraw_const.h` defines `LIBRAW_MAX_ALLOC_MB_DEFAULT` as 2048L, and `src/utils/init_close_utils.cpp` initializes `imgdata.rawparams.max_raw_memory_mb` from it. This is library allocation protection, not a guarantee that Android will allow a 2GiB live process or bitmap. No lower override was found in the wrapper CMake file or app preparation path.
- `libraw_types.h` stores `imgdata.image` as `ushort (*)[4]`, or 8 bytes per internal image pixel. `src/preprocessing/raw2image.cpp` allocates that full array with half-size disabled.
- AndroidLibRaw's `AndroidLibRaw.cpp` creates the full Android bitmap from `sizes.iwidth/iheight` while the internal image remains alive, then copies/converts its pixels. FGallery requests ARGB_8888, adding about 4 bytes per pixel.
- A typical unpacked Bayer source additionally needs roughly 2 bytes per raw sensor pixel. Sensor borders and decoder-specific temporary data make exact allocations differ. Neither the library's nominal allocation limit nor this calculation establishes the observed phone peak.

Approximate simultaneous buffer accounting at the baseline dimensions:

| Buffer | P65+ | 5DS R |
| --- | --- | --- |
| Bayer data, approximately 2 bytes/pixel | 121.0MB | 100.6MB |
| Internal image, 8 bytes/pixel | 483.8MB | 402.6MB |
| Android bitmap, 4 bytes/pixel | 241.9MB | 201.3MB |
| Combined baseline | 846.7MB | 704.5MB |

These are decimal MB and estimates, not measured peak RAM or a fixed requirement for every decoder. They exclude other app objects, previews, decoder temporaries, metadata and allocator overhead. Managed-heap limits, native allocation pressure and system-wide memory pressure remain distinct constraints. Native allocations are not all governed by Java `Runtime.maxMemory()`.

## Acceptance and next investigation

Current Phase One routing needs file-type confirmation. `.iiq` selects the RAW path, which processes sensor data using LibRaw with half-size disabled before writing the full PNG. System/embedded previews, or a half-size LibRaw decode subsequently reduced to a 720px longest edge, can provide an initial quick view. Source classification is not uniform for proprietary RAW with `.tif`: MediaStore RAW MIME hints may select RAW, while direct filesystem discovery uses extension and treats ordinary TIFF as IMAGE. The ordinary TIFF native reader opens the first TIFF directory without searching for a full sensor RAW directory. Successful viewing of a TIFF-container image therefore does not alone establish full P65+ sensor decoding. Actual decoded dimensions for the user's original RAW remain unmeasured; EXIF/source dimensions are not a measurement of the prepared viewer source.

Compare original dimensions, full decoder output dimensions, prepared-PNG dimensions and actual viewer source/readiness. Inspect exact full-source errors and measure managed/native/total process memory during preparation. Check detail at 1:1 independently of the current free-pinch oversampling. Do not claim a specific user-file memory failure without reproducing it. The current RAW path should reduce simultaneous full-frame allocations, with direct streaming of the processed native image to a disk source as an option to investigate; preserve native dimensions and Phase One behavior. TIFF already writes a disk source by strips/tiles, but its 12-million-pixel working-block guard needs separate layout acceptance for large converted TIFFs.

Inspected sources:

- https://github.com/dburckh/AndroidLibRaw/tree/2.0.7
- https://github.com/dburckh/AndroidLibRaw/blob/2.0.7/libraw/src/main/cpp/AndroidLibRaw.cpp
- https://github.com/LibRaw/LibRaw/blob/9646d776c7c61976080a8f2be67928df0750493e/libraw/libraw_const.h
- https://github.com/LibRaw/LibRaw/blob/9646d776c7c61976080a8f2be67928df0750493e/src/preprocessing/raw2image.cpp
- https://github.com/RawTherapee/RawTherapee/blob/dev/rtengine/camconst.json (5DS R sensor/crop information and P65+ camera-family entry)


## 0.2.6 implementation under verification
Full RAW source generation no longer creates the extra width×height×4 Android bitmap: native writing reads the pinned LibRaw internal image with the existing output color curve and emits one RGBA PNG row at a time. The estimated removed allocation is about 242MB (231MiB) for 8984×6732, or 201MB (192MiB) for 8688×5792. These savings are arithmetic estimates, not measured phone peaks; LibRaw internal 16-bit image/unpacked buffers remain. TIFF single-strip layouts above the 12MP working-buffer guard can use a contiguous RGB/gray scanline path, with original dimensions; libtiff internal strip buffers may still be large. A synthetic full P65+ resolution RGB16 TIFF regression is included, but successful synthetic decoding must not be reported as actual IIQ camera acceptance.
