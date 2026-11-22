#!/usr/bin/env bash
# Arranca el emulador con ventana visible (usa XWayland si estás en Wayland).
export ANDROID_HOME=${ANDROID_HOME:-$HOME/dev/android-sdk}
export QT_QPA_PLATFORM=${QT_QPA_PLATFORM:-xcb}
exec "$ANDROID_HOME/emulator/emulator" -avd running -gpu auto -no-snapshot -no-boot-anim -netdelay none -netspeed full "$@"
