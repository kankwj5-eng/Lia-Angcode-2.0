#!/usr/bin/env bash
set -euo pipefail

APK="${1:-android/app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE="com.kankwj.angcode"
ACTIVITY=".MainActivity"
OUT_DIR="${2:-device-smoke-artifacts}"

mkdir -p "$OUT_DIR"
test -f "$APK"

adb wait-for-device
adb install -r "$APK"

set +e
START_OUTPUT="$(adb shell am start -W -n "$PACKAGE/$ACTIVITY" 2>&1)"
START_CODE=$?
set -e

printf '%s\n' "$START_OUTPUT" | tee "$OUT_DIR/am-start.txt"
if [[ $START_CODE -ne 0 ]] || ! grep -q "Status: ok" "$OUT_DIR/am-start.txt"; then
  adb logcat -d -t 500 > "$OUT_DIR/logcat.txt" || true
  adb exec-out screencap -p > "$OUT_DIR/screenshot.png" || true
  echo "MainActivity no arrancó correctamente" >&2
  exit 20
fi

sleep 4

PID="$(adb shell pidof "$PACKAGE" | tr -d '\r' | xargs || true)"
printf 'pid=%s\n' "$PID" | tee "$OUT_DIR/process.txt"
if [[ -z "$PID" ]]; then
  adb logcat -d -t 500 > "$OUT_DIR/logcat.txt" || true
  adb exec-out screencap -p > "$OUT_DIR/screenshot.png" || true
  echo "El proceso AngCode terminó después del arranque" >&2
  exit 21
fi

adb shell dumpsys activity activities > "$OUT_DIR/activity.txt"
adb shell dumpsys meminfo "$PACKAGE" > "$OUT_DIR/meminfo.txt" || true
adb logcat -d -t 700 > "$OUT_DIR/logcat.txt" || true
adb exec-out screencap -p > "$OUT_DIR/screenshot.png" || true

if ! grep -q "$PACKAGE" "$OUT_DIR/activity.txt"; then
  echo "AngCode no aparece en dumpsys activity" >&2
  exit 22
fi

# Scope crash checks to lines around the AngCode process/package rather than
# failing on unrelated emulator/system crashes.
if grep -A8 -B3 -E "FATAL EXCEPTION|AndroidRuntime" "$OUT_DIR/logcat.txt"   | grep -q "$PACKAGE"; then
  echo "Se detectó crash de AngCode en logcat" >&2
  exit 23
fi

echo "Smoke test Android OK"
