#!/usr/bin/env bash
mkdir -p app/build/reports/androidTests/connected
capture_device_log() {
    adb logcat -d > app/build/reports/androidTests/connected/logcat.txt 2>&1 || true
    adb pull /sdcard/Pictures/FGalleryTests/video-controls-failure.png app/build/reports/androidTests/connected/video-controls-failure.png >/dev/null 2>&1 || true
}
trap capture_device_log EXIT
if [[ "${GITHUB_REF:-}" == "refs/heads/feature/fgallery-0.3.1-stage2-build" || "${GITHUB_BASE_REF:-}" == "feature/fgallery-0.3.0-stage1" ]]; then
  gradle :app:connectedReleaseAndroidTest
else
  gradle :app:connectedDebugAndroidTest
fi
exit "$?"
