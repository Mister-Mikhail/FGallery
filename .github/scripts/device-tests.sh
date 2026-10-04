#!/usr/bin/env bash
mkdir -p app/build/reports/androidTests/connected
capture_device_log() {
    adb logcat -d > app/build/reports/androidTests/connected/logcat.txt 2>&1 || true
    adb exec-out run-as com.mistermikhail.fgallery cat files/video-controls-failure.png > app/build/reports/androidTests/connected/video-controls-failure.png 2>/dev/null || rm -f app/build/reports/androidTests/connected/video-controls-failure.png
}
trap capture_device_log EXIT
gradle :app:connectedDebugAndroidTest
exit "$?"
