#!/usr/bin/env bash
# Cold-start benchmark over adb. Run the SAME script for each browser on the SAME device, same state, same network.
# NOT YET RUN: no device was available when this project was written. Results must be measured, never assumed.
# usage: scripts/bench_startup.sh <package> <activity> [runs]   e.g. com.zaeem.browser com.zaeem.browser/.MainActivity 10
set -euo pipefail
PKG=$1; ACT=$2; RUNS=${3:-10}
for i in $(seq 1 "$RUNS"); do
  adb shell am force-stop "$PKG"; adb shell "echo 3 > /proc/sys/vm/drop_caches" 2>/dev/null || true; sleep 2
  adb shell am start -W -n "$ACT" | awk -F: '/TotalTime/ {print $2}'
done | sort -n | awk '{a[NR]=$1} END {print "runs="NR" median_ms="a[int((NR+1)/2)]" min_ms="a[1]" max_ms="a[NR]}'
echo "Memory (PSS) after load: adb shell dumpsys meminfo $PKG | grep -E 'TOTAL|Native Heap|Java Heap'"
