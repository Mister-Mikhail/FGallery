#!/usr/bin/env bash
mkdir -p app/build/reports/androidTests/connected
capture_device_log() { adb logcat -d > app/build/reports/androidTests/connected/logcat.txt 2>&1 || true; }
trap capture_device_log EXIT
gradle :app:connectedDebugAndroidTest
exit "$?"
