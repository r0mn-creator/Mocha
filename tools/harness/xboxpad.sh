#!/bin/bash
# Virtual Xbox gamepad for an Android test device, via /system/bin/uinput.
#
# Why: the Odin 2 has a real controller; a phone doesn't. Cemu/Mocha's on-screen
# overlay needs an emulated controller configured through the UI and only takes
# touch, which is awkward to drive from adb. A uinput device instead looks like a
# genuine Bluetooth Xbox pad to Android, so the emulator's normal controller
# path works and every press is scriptable.
#
# Needs root (/dev/uinput is uhid:uhid). Verified on a Magisk-rooted OnePlus 7 Pro.
#
#   ./xboxpad.sh start          bring the pad up (stays alive until stop)
#   ./xboxpad.sh press a        tap a button
#   ./xboxpad.sh hold start 1.5 hold it (many games ignore taps -- see below)
#   ./xboxpad.sh stick l 0 -20000 ; ./xboxpad.sh stick l 0 0
#   ./xboxpad.sh stop
#
# ⚠️ HOLD, don't tap. Same lesson as the Odin rig: menus and title screens often
# miss a press shorter than a real human's. Default hold is 250ms for that reason.
set -e

DEV="${DEV:-$(adb devices | awk 'NR==2{print $1}')}"
ADB="adb -s $DEV"
FIFO=/data/local/tmp/xboxpad.fifo
REG=/data/local/tmp/xboxpad_register.json
HERE="$(cd "$(dirname "$0")" && pwd)"

# Linux input codes. EV_KEY=1, EV_ABS=3, EV_SYN=0.
btn_code() {
  case "$1" in
    a) echo 304;; b) echo 305;; x) echo 307;; y) echo 308;;
    lb) echo 310;; rb) echo 311;;
    back|select) echo 314;; start) echo 315;; guide|mode) echo 316;;
    l3) echo 317;; r3) echo 318;;
    *) echo "unknown button: $1" >&2; exit 1;;
  esac
}

# Quoting here is load-bearing: the JSON has to survive bash -> adb -> sh -> su -> sh.
inject() { $ADB shell "su -c \"echo '$1' > $FIFO\""; }

case "${1:-}" in
  start)
    $ADB push "$HERE/xboxpad_register.json" "$REG" >/dev/null
    $ADB push "$HERE/xboxpad_up.sh" /data/local/tmp/xboxpad_up.sh >/dev/null
    $ADB shell 'su -c "chmod 755 /data/local/tmp/xboxpad_up.sh"'
    # setsid + full fd redirection, or adb waits on it and the pad dies with the shell.
    $ADB shell 'su -c "setsid sh /data/local/tmp/xboxpad_up.sh > /data/local/tmp/xboxpad.log 2>&1 < /dev/null &"' &
    sleep 5
    echo "pad up:"
    $ADB shell 'su -c "cat /proc/bus/input/devices" 2>/dev/null | grep -A2 -i "Xbox Wireless"' || echo "FAILED"
    ;;
  stop)
    $ADB shell "su -c 'pkill -f \"uinput -\"; pkill -f \"sleep 86400\"; rm -f $FIFO'" || true
    echo "pad down"
    ;;
  press|hold)
    code=$(btn_code "$2")
    dur="${3:-0.25}"
    inject "{\"id\":1,\"command\":\"inject\",\"events\":[1,$code,1,0,0,0]}"
    sleep "$dur"
    inject "{\"id\":1,\"command\":\"inject\",\"events\":[1,$code,0,0,0,0]}"
    ;;
  stick)
    # stick <l|r> <x> <y>   raw -32768..32767, 0 0 to centre
    case "$2" in l) ax=0; ay=1;; r) ax=3; ay=4;; *) echo "stick: l or r" >&2; exit 1;; esac
    inject "{\"id\":1,\"command\":\"inject\",\"events\":[3,$ax,$3,3,$ay,$4,0,0,0]}"
    ;;
  dpad)
    # dpad <x> <y>   each -1, 0 or 1
    inject "{\"id\":1,\"command\":\"inject\",\"events\":[3,16,$2,3,17,$3,0,0,0]}"
    ;;
  *)
    sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'
    exit 1;;
esac
