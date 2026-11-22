# Running

App Android (Kotlin + Jetpack Compose) para crear entrenos por bloques, registrar sesiones y llevar el control de km de las zapatillas.

> **Proyecto personal.** Lo desarrollo para mi propio uso y a mi ritmo. El código es público por si te resulta útil, pero no ofrezco soporte ni garantizo que acepte contribuciones.

## Modelo
- `Workout` → lista de `WorkoutItem`: `SingleItem` (un bloque) o `GroupItem` (×N repeticiones de varios bloques).
- `Block` → tipo (calentamiento, trabajo, recuperación, enfriamiento, descanso) + `EndCondition` (tiempo, distancia, manual) + `BlockTarget` opcional (ritmo, velocidad, pulsaciones).
- Persistencia en JSON local (`filesDir/workouts.json`).

## Desarrollo
```
export JAVA_HOME=~/dev/jdk ANDROID_HOME=~/dev/android-sdk
./gradlew assembleDebug
scripts/emulator.sh &            # AVD "running", ventana visible
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.running/.MainActivity
```
