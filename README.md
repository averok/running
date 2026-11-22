# Running

App Android (Kotlin + Jetpack Compose) para crear entrenos por bloques, registrar sesiones y llevar el control de km de las zapatillas.

> **Proyecto personal.** Lo desarrollo para mi propio uso y a mi ritmo. El código es público por si te resulta útil, pero no ofrezco soporte ni garantizo que acepte contribuciones.

## Capturas

<p>
  <img src="docs/screenshots/entrenos.png" width="19%" alt="Lista de entrenos">
  <img src="docs/screenshots/editor.png" width="19%" alt="Editor de entrenos por bloques">
  <img src="docs/screenshots/historial.png" width="19%" alt="Historial en calendario">
  <img src="docs/screenshots/zapatillas.png" width="19%" alt="Zapatillas y desgaste">
  <img src="docs/screenshots/ajustes.png" width="19%" alt="Ajustes: copia de seguridad">
</p>

## Funciones
- **Entrenos por bloques**: calentamiento, trabajo, recuperación, enfriamiento y descanso, con repeticiones y objetivo opcional (ritmo, velocidad o pulsaciones).
- **Historial en calendario**: sesiones por día, con km y resumen mensual.
- **Zapatillas**: foto del modelo, km acumulados con límite personalizable y aviso al acercarse o alcanzarlo.
- **Copia de seguridad**: exporta e importa todos los datos (con las fotos) en un ZIP, para cambiar de teléfono.

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
