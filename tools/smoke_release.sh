#!/usr/bin/env bash
# The exact signed production APK, downloaded from the signing job. No credentials.
set -euo pipefail
apk=$(find release-assets -maxdepth 1 -name 'ESuite-*.apk' -print -quit)
test -n "$apk"
adb install "$apk"
adb logcat -c

wait_for_workspace() {
  for attempt in $(seq 1 20); do
    if adb shell uiautomator dump /sdcard/esuite-ui.xml >/dev/null &&
       adb pull /sdcard/esuite-ui.xml release-ui.xml >/dev/null &&
       python3 - <<'PY'
import sys
import xml.etree.ElementTree as ET

nodes = list(ET.parse("release-ui.xml").iter("node"))
resources = {node.get("resource-id") for node in nodes}
required = {"nav_browser", "browser_url", "open_settings"}
browser_selected = any(node.get("resource-id") == "nav_browser" and node.get("selected") == "true" for node in nodes)
sys.exit(0 if required <= resources and browser_selected else 1)
PY
    then
      return 0
    fi
    sleep 1
  done
  printf '%s\n' 'The signed APK did not reach the Browser address bar.' >&2
  return 1
}

adb shell am start -W -n dev.esuite.app/.MainActivity
wait_for_workspace
adb shell am force-stop dev.esuite.app
adb shell am start -W -n dev.esuite.app/.MainActivity
wait_for_workspace
adb exec-out screencap -p > release-home.png
adb logcat -d -b crash > release-crash.txt
if grep -q 'Process: dev.esuite.app' release-crash.txt; then cat release-crash.txt; exit 1; fi
