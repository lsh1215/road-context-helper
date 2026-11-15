#!/bin/sh
set -eu
APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
WORKSPACE=$(CDPATH= cd -- "$APP_DIR/../.." && pwd)
# Explicit SDK settings work in an independent clone. Reuse this workspace's
# optional emulator installation only when no SDK was supplied.
if [ -z "${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}" ] && [ -d "$WORKSPACE/.test-runtime/android-sdk" ]; then
 export ANDROID_SDK_ROOT="$WORKSPACE/.test-runtime/android-sdk"
 export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$WORKSPACE/.test-runtime/android-user}"
 export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$WORKSPACE/.test-runtime/avd}"
else
 export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
fi
if [ ! -x "$ANDROID_SDK_ROOT/emulator/emulator" ]; then
 echo 'Set ANDROID_HOME or ANDROID_SDK_ROOT to an Android SDK with the emulator installed.' >&2
 exit 1
fi
ROAD_SHOW_WINDOW=false
ROAD_GPS_REPLAY=false
for option in "$@"; do
 case "$option" in
  --window) ROAD_SHOW_WINDOW=true ;;
  --gps-replay) ROAD_GPS_REPLAY=true ;;
  *) echo "Usage: $0 [--window] [--gps-replay]" >&2; exit 2 ;;
 esac
done
set --
if [ "$ROAD_SHOW_WINDOW" = false ]; then set -- "$@" -no-window; fi
if [ "$ROAD_GPS_REPLAY" = true ]; then set -- "$@" -grpc 8561; fi
exec "$ANDROID_SDK_ROOT/emulator/emulator" -avd RoadContext -no-snapshot "$@" -no-audio -gpu host -port 5556
