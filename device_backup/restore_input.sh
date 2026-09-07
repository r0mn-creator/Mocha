#!/bin/bash
# Restores the Odin 2 controller mapping + settings.xml to the Mocha debug build.
# Only needed after a full uninstall (adb install -r preserves app data).
set -e
PKG=info.cemu.cemu.debug
DIR="$(cd "$(dirname "$0")" && pwd)"
FILES=/storage/emulated/0/Android/data/$PKG/files

adb push "$DIR/extracted/controllerProfiles/controller0.xml" /data/local/tmp/controller0.xml
adb shell run-as $PKG mkdir -p $FILES/controllerProfiles
adb shell run-as $PKG cp /data/local/tmp/controller0.xml $FILES/controllerProfiles/controller0.xml

adb push "$DIR/extracted/settings.xml" /data/local/tmp/settings.xml
adb shell run-as $PKG cp /data/local/tmp/settings.xml $FILES/settings.xml

echo "Restored controller mapping + settings. Restart the app to pick them up."
