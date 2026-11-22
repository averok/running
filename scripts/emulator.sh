#!/usr/bin/env bash
# Arranca el emulador con ventana visible (usa XWayland si estás en Wayland).
export ANDROID_HOME=${ANDROID_HOME:-$HOME/dev/android-sdk}
export QT_QPA_PLATFORM=${QT_QPA_PLATFORM:-xcb}
# Puerto fijo (serial emulator-5580) para no coincidir con los emuladores de otros proyectos (5554, 5556...).
exec "$ANDROID_HOME/emulator/emulator" -avd running -port 5580 -gpu auto -no-snapshot -no-boot-anim -netdelay none -netspeed full "$@"
