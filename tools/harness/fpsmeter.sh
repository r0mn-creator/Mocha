#!/bin/bash
# Measure real presented-frame rate from SurfaceFlinger, independent of the emulator's own counter.
# usage: fpsmeter.sh <tag> <duration_seconds> [sample_interval]
set -u
DEV=3a478943
TAG=${1:-fps}
DUR=${2:-180}
INT=${3:-5}
LAYER='info.cemu.cemu.debug/info.cemu.cemu.MainActivity#78744'
OUT="$(dirname "$0")/${TAG}_frames.txt"
: > "$OUT"

N=$(( DUR / INT ))
for i in $(seq 1 $N); do
  adb -s $DEV shell "dumpsys SurfaceFlinger --latency '$LAYER'" 2>/dev/null >> "$OUT"
  echo "--MARK--" >> "$OUT"
  command sleep "$INT"
done
echo "wrote $OUT"
