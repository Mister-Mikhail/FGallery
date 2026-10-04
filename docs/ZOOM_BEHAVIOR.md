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
