# Current zoom policy — 0.2.6

Implementation: first double tap = twice the fitted linear scale. For raster images let F = min(viewWidth/sourceWidth, viewHeight/sourceHeight), with source orientation handled by the image decoder. Tap and pinch now share the existing ceiling M = max(8, 8F), preserving pinch oversampling.

- First tap: 2F.
- Small image (2F ≥ 1): next tap returns to F.
- Large image (2F < 1): second tap targets F + 0.90(M−F), third returns to F.

The small-image definition is the implementation's pixel-based threshold: the first enlargement reaches or exceeds native 1:1. The user did not separately specify a threshold. The 90% second stop is a linear source-to-screen scale interpolation, not a percentage of image area. This can produce a large jump on high-resolution images because the retained pinch ceiling permits 8× native-pixel oversampling. Oversampling adds magnification, not detail.

Example: a 6000×4000 photo fitted to 1080px width has F=.18, first tap .36 (2× fit), second 7.218 (40.1× fit), and ceiling 8 (44.44× fit). The common endpoint removes the earlier tap/pinch mismatch but does not imply that the first and second visual jumps are similar.

There is no persistent tap counter: actual scale near fit (within 2%) selects the first stop; other large-image scales below 98% of the second stop select the second; otherwise return to fit. Small images return after any enlargement. A pinch back to fit always restarts. Raster animation lasts 260ms and uses the tap location as focus. PDF/SVG use the same first stop in screen-relative units, F=1 and M=8: 2× → 7.3× → 1×.

RAW/TIFF taps wait for full-source readiness; early previews cannot set full-source geometry. RAW full-source writing now reads processed LibRaw rows directly into PNG with no extra full ARGB Android bitmap. Native dimensions and the existing color curve are preserved; display PNGs remain 8 bits per color channel. Large contiguous RGB/gray TIFF strips use the scanline fallback; preview output still has its separate 6MP/edge cap. Full tiled TIFF working-buffer guards remain. LibRaw and libtiff internal buffers, source axis limits, Android region-decoder limits and the 1GiB full-source disk retention budget still apply. Physical camera-file memory/timing acceptance is pending.

---

# Historical 0.2.5 behavior (superseded for taps)

# Zoom algorithm in APK 0.2.5

Runtime source: d36b3d7d8bf04b704b204bbd4a41954bc074c448. This document describes existing behavior; it does not change the implementation.

## Raster photos, TIFF and RAW

Let the oriented, decoded source dimensions be W × H pixels, and the viewer area A × B screen pixels. The image library determines the initial fit scale F; for uniform Fit it corresponds to min(A/W, B/H). Source orientation is accounted for by the decoder. F = 1 means one source pixel per screen pixel. F < 1 means downscaling the source to show it completely.

The double-tap endpoint is M = max(1, F). Larger sources therefore use native 1:1 as the endpoint. If an image is already fitted at 1:1 or larger, tapping does not enlarge it further.

Tap targets are linear interpolation in scale:

- First: S1 = F + (M − F) × 0.30.
- Second: S2 = F + (M − F) × 0.90.
- Return: F.

These are fractions of the range from fit to native scale, not 30%/90% additions to the fitted width, image area, or logarithmic range. A relative magnification shown against the fitted view equals S/F.

Example, a 6000×4000 image in a 1080×2000 viewport:

| State | Source-to-screen scale | Relative to fit | Displayed image size |
| --- | --- | --- | --- |
| Fit | 0.180 | 1.00× | 1080×720 |
| First tap | 0.426 | 2.37× | 2556×1704 |
| Second tap | 0.918 | 5.10× | 5508×3672 |
| Native endpoint | 1.000 | 5.56× | 6000×4000 |

The viewport shows a portion of the larger displayed image. The first stop can therefore look substantially closer for a very large source, even though it remains exactly 30% through the agreed linear range.

## Choosing the next stop

There is no saved tap counter. The actual current scale C decides the next target in `nextDoubleTapZoom`:

1. If C/F ≤ 1.02, choose S1. An unspecified/nonfinite relative scale also takes this branch.
2. Otherwise, if C < S2 × 0.98, choose S2.
3. Otherwise return to F.

The 2% margins accommodate floating-point/gesture inaccuracies. Manually pinching back to fit restarts the cycle. A manual intermediate zoom usually makes the next tap select the second stop; a zoom at or above the second-stop threshold makes the next tap return to fit.

Raster transitions use a 260ms tween and the double-tap position as the zoom centroid. Pan is constrained to the scaled image edges by Telephoto. Raster taps are ignored during an active zoom animation, before image display, or with an unspecified transformation.

RAW and TIFF first show an available preview, then prepare/display the full source. Double-tap zoom is enabled only after the full request has decoded, so preview dimensions do not determine the native endpoint. RAW uses full-resolution LibRaw sensor processing; TIFF retains native dimensions in its prepared PNG. This controls geometry, not whether a particular camera file's full detail has been confirmed on a device.

Pinch remains separate: the current raster pinch ceiling is max(8, F×8) in absolute source scale. Thus the 1:1 endpoint applies to double taps; free pinch can enlarge beyond native pixels, where extra magnification cannot create more source detail.

## PDF and SVG

Documents use fitted-view units: F = 1, M = 8. The same interpolation gives 3.1×, 7.3× and 1×. The transition lasts 240ms. PDF has no unique native raster pixel count for vector text/paths, so its accepted screen-relative range remains separate from the raster native endpoint.

Pinch multiplies the current scale by the ratio of finger distances, constrained to 1…8. For a fixed container, preserving the focal point uses q = newScale/oldScale:

- newX = oldX×q + (focusX − containerWidth/2)×(1 − q) + panX;
- newY = oldY×q + (focusY − containerHeight/2)×(1 − q) + panY.

Offsets are clamped to ±containerWidth×(scale−1)/2 and the analogous height bound. At scale 1 the offsets reset to zero. An ordinary one-finger drag at fit scrolls the PDF page list. At zoom, unused vertical drag at a page edge is passed to the page list.

PDF initially renders a page preview with a longest edge of 1800px. During motion that preview is transformed. After the transform stays unchanged for 100ms, native rendering of the visible viewport starts; its output has at most a 2560px longest edge. It replaces the stretched preview when ready. Embedded low-resolution images within a PDF cannot gain detail from rerendering. SVG uses AndroidSVG vector drawing through the document viewer.

## Cleanup mode

For an opened raster image, Cleanup double-tap invokes Trash instead of this zoom policy. This remains a separate user-selected mode.

## Requested next-build policy (not implemented)

On 2026-10-04 the user supersedes the native-1:1 tap endpoint with the existing pinch ceiling, M = max(8, F×8) for raster sources. Small images must zoom to 2× fitted scale and return to fit on the next double tap. Large images have two zoom stops before the return. The working interpretation is S1 = 2F for every raster source and S2 = F + (M−F)×0.90 for large sources, retaining the previous second-stop percentage; the first-stop 30% interpolation would be replaced. The user has not specified the pixel threshold separating small and large. A possible threshold is whether 2F reaches native 1:1; this remains a proposal. The user explicitly requests discussion only, without a build or runtime changes at this stage.

## Existing source-resolution and rendering limits

These limits describe 0.2.5 source d36b3d7, independently of the requested zoom policy:

- Ordinary static raster viewing uses Telephoto/Android region decoding where supported. The app does not set a universal maximum source megapixel count. Android, format and library limits and available memory still apply; fallback previews cannot provide full native detail.
- Full RAW uses LibRaw with half-size disabled and then allocates a full ARGB_8888 bitmap before writing the disk source. No separate app megapixel ceiling is configured. That bitmap alone uses approximately width×height×4 bytes, in addition to decoder and processing buffers. Native allocation/library limits and device memory can therefore prevent decoding.
- Full TIFF rejects a source axis above 100,000 pixels. It preserves source dimensions when writing PNG, without a 6MP full-image ceiling. Its RGBA working-buffer allowance is 12,000,000 pixels: width×rowsPerStrip for strip decoding, or width×tileHeight + tileWidth×tileHeight for the tiled writer. Layouts exceeding those checks fail full preparation; the checks bound working buffers, not total output dimensions. Other library allocations remain additional.
- The separate TIFF preview decoder downsamples to at most 6,000,000 output pixels and the requested longest edge (4096px by default for the Coil TIFF fallback). Thumbnail tiers target 720px and 1920px longest edges. Those are preview limits, not full-source viewer limits.
- Prepared RAW/TIFF display sources currently use 8 bits per color channel, even when the original sensor/TIFF data has higher bit depth. Preserving native dimensions does not preserve original higher bit depth in the display source.
- PDF initially renders an 1800px page preview and rerenders the visible viewport up to a 2560px longest edge after zoom/pan settles. This is a viewport rendering limit, not a maximum PDF page size. SVG is drawn from vectors; there is no app-configured raster megapixel ceiling for it.
- Full-source disk retention has a soft 1GiB budget. The current source is retained even if it alone exceeds the budget; older prepared sources are removed. This does not reduce source resolution, but evicted sources need preparation again.
