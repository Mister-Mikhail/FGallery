#!/usr/bin/env bash
set -euo pipefail

result=0
if [ "${GITHUB_REF_NAME:-}" = "feature/models-location" ]; then
    timeout --signal=INT --kill-after=30s 14m gradle :app:connectedMinifiedTestAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.timeout_msec=90000 || result=$?
else
    gradle :app:connectedMinifiedTestAndroidTest || result=$?
fi

destination=artifacts/duplicate-qa
source_dir=/data/local/tmp/fgallery-duplicate-qa
mkdir -p "$destination"
for file in exact-selection-large-font.png shared-svg-viewer.png visual-candidates-read-only.png \
    visual-comparison-viewer.png performance-100.csv performance-1200.csv performance-trash-64.csv; do
    adb pull "$source_dir/$file" "$destination/$file" || true
done

adb logcat -d -s FGallery3DQA:I > "$destination/model-performance.log"
adb logcat -d -s FGallery3D:W AndroidRuntime:E > "$destination/model-errors.log"
adb logcat -d -b events -s am_anr > "$destination/anr.log"
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
