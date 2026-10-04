#!/usr/bin/env bash
gradle :app:connectedDebugAndroidTest
test_result=$?
mkdir -p app/build/reports/androidTests/connected
adb logcat -d > app/build/reports/androidTests/connected/logcat.txt
exit "$test_result"
