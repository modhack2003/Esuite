#!/usr/bin/env bash
# The exact signed production APK, downloaded from the signing job. No credentials.
set -euo pipefail
apk=$(find release-assets -maxdepth 1 -name 'ESuite-*.apk' -print -quit)
test -n "$apk"
adb install "$apk"
adb logcat -c
adb shell am start -W -n dev.esuite.app/.MainActivity
# Wait for the encrypted workspace to open, then require native home controls.
ready=false
for attempt in $(seq 1 20); do
  adb shell uiautomator dump /sdcard/esuite-ui.xml >/dev/null
  adb pull /sdcard/esuite-ui.xml release-ui.xml >/dev/null
  if python3 -c 'import xml.etree.ElementTree as E; r=E.parse("release-ui.xml"); assert any(n.get("text")=="Your workspace." for n in r.iter("node"))'; then ready=true; break; fi
  sleep 1
done
test "$ready" = true
adb shell am force-stop dev.esuite.app
adb shell am start -W -n dev.esuite.app/.MainActivity
adb shell uiautomator dump /sdcard/esuite-ui.xml >/dev/null
adb pull /sdcard/esuite-ui.xml release-ui.xml >/dev/null
python3 -c 'import xml.etree.ElementTree as E; r=E.parse("release-ui.xml"); assert any(n.get("text")=="Your workspace." for n in r.iter("node"))'
adb exec-out screencap -p > release-home.png
adb logcat -d -b crash > release-crash.txt
if grep -q 'Process: dev.esuite.app' release-crash.txt; then cat release-crash.txt; exit 1; fi
