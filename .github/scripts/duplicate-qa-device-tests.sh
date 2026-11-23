#!/usr/bin/env bash
set -euo pipefail

result=0
gradle :app:connectedMinifiedTestAndroidTest || result=$?

destination=artifacts/duplicate-qa
source_dir=/sdcard/Android/data/com.mistermikhail.fgallery/files/duplicate-qa
mkdir -p "$destination"
if ! adb pull "$source_dir/." "$destination/"; then
    adb exec-out run-as com.mistermikhail.fgallery tar -C "$source_dir" -cf - . |
        tar -C "$destination" -xf - || true
fi

adb logcat -d -b events -s am_anr > "$destination/anr.log"
if grep -q 'am_anr:.*com\.mistermikhail\.fgallery' "$destination/anr.log"; then
    cat "$destination/anr.log"
    result=1
fi

if [ "$result" -eq 0 ]; then
    for name in exact-selection-large-font shared-svg-viewer visual-candidates-read-only; do
        test -s "$destination/$name.png"
    done
    for count in 100 1200; do
        test -s "$destination/performance-$count.csv"
        cat "$destination/performance-$count.csv"
    done
fi
exit "$result"
