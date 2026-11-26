#!/usr/bin/env bash
set -euo pipefail

result=0
api="${1:-36}"
destination=artifacts/duplicate-qa
source_dir=/data/local/tmp/fgallery-duplicate-qa
mkdir -p "$destination"
if [ "${GITHUB_REF_NAME:-}" = "feature/models-location" ]; then
    # Keep a live copy even if the virtual device disconnects.
    adb logcat -v brief -s FGallery3D:V FGallery3DQA:I AndroidRuntime:E libc:F > "$destination/model-device.log" &
    log_pid=$!
    trap 'kill "$log_pid" 2>/dev/null || true' EXIT
    test_args=(-Pandroid.testInstrumentationRunnerArguments.timeout_msec=90000)
    if [ "$api" = 33 ]; then
        test_args+=(-Pandroid.testInstrumentationRunnerArguments.class=com.mistermikhail.fgallery.VideoContentUriPlaybackTest,com.mistermikhail.fgallery.ModelViewerTest,com.mistermikhail.fgallery.CaptureLocationTest)
    fi
    timeout --signal=INT --kill-after=30s 14m gradle :app:connectedMinifiedTestAndroidTest "${test_args[@]}" || result=$?
else
    gradle :app:connectedMinifiedTestAndroidTest || result=$?
fi

if [ "$result" -ne 0 ] && [ "${GITHUB_REF_NAME:-}" = "feature/models-location" ]; then
    adb devices -l > "$destination/runner-devices.log" || true
    sudo dmesg --ctime | tail -120 > "$destination/runner-kernel.log" || true
    free -m > "$destination/runner-memory.log" || true
fi
if [ "$api" = 33 ]; then
    exit "$result"
fi

for file in exact-selection-large-font.png shared-svg-viewer.png visual-candidates-read-only.png \
    visual-comparison-viewer.png performance-100.csv performance-1200.csv performance-trash-64.csv; do
    adb pull "$source_dir/$file" "$destination/$file" || true
done

adb logcat -d -s FGallery3DQA:I > "$destination/model-performance.log" || true
adb logcat -d -b events -s am_anr > "$destination/anr.log" || true
if grep -q 'am_anr:.*com\.mistermikhail\.fgallery' "$destination/anr.log"; then
    cat "$destination/anr.log"
    result=1
fi

if [ "$result" -eq 0 ]; then
    for name in exact-selection-large-font shared-svg-viewer visual-candidates-read-only visual-comparison-viewer; do
        test -s "$destination/$name.png"
    done
    for count in 100 1200 trash-64; do
        test -s "$destination/performance-$count.csv"
        cat "$destination/performance-$count.csv"
    done
fi
exit "$result"
