#!/usr/bin/env bash
# SnapBoo robot play-test: installs the game on the emulator and plays it with random input.
# Usage: play_test.sh <path-to-apk>
set -u
PKG="com.arsniper"
OUT="play-test-results"
ROUNDS=10
EVENTS_PER_ROUND=300
APK="${1:-}"

mkdir -p "$OUT/screens"
: > "$OUT/summary.txt"

if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  echo "No APK found at '$APK'" | tee -a "$OUT/summary.txt"
  exit 1
fi
echo "APK: $APK" | tee -a "$OUT/summary.txt"

if ! adb install -r -g "$APK" 2>&1 | tee -a "$OUT/summary.txt" | grep -q "^Success"; then
  echo "INSTALL FAILED - game was not tested" | tee -a "$OUT/summary.txt"
  exit 1
fi

adb logcat -c
adb logcat -v time > "$OUT/logcat_full.txt" 2>&1 &
LOGCAT_PID=$!

shot() {
  adb exec-out screencap -p > "$OUT/screens/$1.png" 2>/dev/null || true
}

launch() {
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
  sleep 8
}

CRASHES=0
launch
shot "00_launch"

for i in $(seq 1 $ROUNDS); do
  echo "=== Round $i ===" >> "$OUT/monkey.txt"
  adb shell monkey -p "$PKG" \
    --throttle 150 \
    --pct-touch 55 --pct-motion 35 --pct-nav 0 --pct-majornav 0 \
    --pct-syskeys 0 --pct-appswitch 0 --pct-anyevent 0 \
    -s $((1000 + i)) -v $EVENTS_PER_ROUND >> "$OUT/monkey.txt" 2>&1
  RESULT=$?

  NAME=$(printf '%02d_round_%d' "$i" "$i")
  shot "$NAME"

  if [ $RESULT -ne 0 ] || ! adb shell pidof "$PKG" > /dev/null 2>&1; then
    CRASHES=$((CRASHES + 1))
    echo "Round $i: CRASH / app not running (screenshot $NAME)" | tee -a "$OUT/summary.txt"
    launch
    shot "${NAME}_relaunch"
  else
    echo "Round $i: OK" | tee -a "$OUT/summary.txt"
  fi
done

sleep 2
kill $LOGCAT_PID 2>/dev/null || true

grep -n -E "FATAL EXCEPTION|ANR in|Fatal signal" -A 40 "$OUT/logcat_full.txt" > "$OUT/crashes.txt" || true

echo "Total crashes: $CRASHES" | tee -a "$OUT/summary.txt"
if [ -s "$OUT/crashes.txt" ]; then
  echo "----- first crash details -----"
  head -n 80 "$OUT/crashes.txt"
fi

if [ "$CRASHES" -gt 0 ] || [ -s "$OUT/crashes.txt" ]; then
  echo "Play test found problems - see crashes.txt" | tee -a "$OUT/summary.txt"
  exit 1
fi
echo "Play test passed" | tee -a "$OUT/summary.txt"
