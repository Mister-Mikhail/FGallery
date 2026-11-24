#!/usr/bin/env bash
set -euo pipefail

result=0
gradle :app:connectedMinifiedTestAndroidTest || result=$?

destination=artifacts/duplicate-qa
source_dir=/data/local/tmp/fgallery-duplicate-qa
mkdir -p "$destination"
for file in exact-selection-large-font.png shared-svg-viewer.png visual-candidates-read-only.png \
    visual-comparison-viewer.png performance-100.csv performance-1200.csv; do
    adb pull "$source_dir/$file" "$destination/$file" || true
done

adb logcat -d -b events -s am_anr > "$destination/anr.log"
if grep -q 'am_anr:.*com\.mistermikhail\.fgallery' "$destination/anr.log"; then
    cat "$destination/anr.log"
    result=1
fi

if [ "$result" -eq 0 ]; then
    for name in exact-selection-large-font shared-svg-viewer visual-candidates-read-only visual-comparison-viewer; do
        test -s "$destination/$name.png"
    done
    for count in 100 1200; do
        test -s "$destination/performance-$count.csv"
        cat "$destination/performance-$count.csv"
    done
fi
exit "$result"
