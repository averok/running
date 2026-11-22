# CLAUDE.md

App Android **Running** (`app.running`): registro de entrenos de running. Permite diseñar entrenos por bloques, registrar sesiones realizadas y llevar el control de km por zapatillas con aviso de límite. Kotlin + Jetpack Compose, un solo módulo (`:app`). UI y textos en español.

## Alcance (decisiones del usuario)

- Es **solo un registro**: el entreno en directo (GPS, avisos de voz, servicio en segundo plano) se descartó. Para correr usa la app de Polar.
- La integración con Polar/Strava (importar sesiones, `gear_id` de Strava) está **aparcada**. Si se retoma, la vía preferida era la API de Strava (Polar Flow ya sincroniza con Strava), pidiendo antes al usuario crear la app en `strava.com/settings/api`.
- Aún no hay release firmado ni icono propio: solo APK debug.

## Comandos

```
export JAVA_HOME=~/dev/jdk ANDROID_HOME=~/dev/android-sdk   # JDK y SDK compartidos con otros proyectos del usuario
./gradlew assembleDebug                                      # APK en app/build/outputs/apk/debug/app-debug.apk
scripts/emulator.sh &                                        # AVD "running" (Android 34 x86_64, KVM), ventana visible vía XWayland
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.running/.MainActivity
```

- El AVD `running` es propio de este proyecto; **no tocar** el AVD de `ticketplus`.
- Si se usa el emulador, la ventana debe quedar visible para el usuario.
- No hay tests ni lint configurados. Se verifica compilando y probando en el emulador.
- Versiones: AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, compileSdk/targetSdk 35, minSdk 26, JVM 17.

## Arquitectura

Paquete `app.running` en `app/src/main/java/app/running/`:

- `model/Workout.kt`: `Workout` → lista de `WorkoutItem`, que es `SingleItem` (un bloque) o `GroupItem` (×N repeticiones de varios bloques). `Block` = tipo (calentamiento, trabajo, recuperación, enfriamiento, descanso) + `EndCondition` (tiempo, distancia, manual) + `BlockTarget` opcional (ritmo, velocidad, pulsaciones). Calcula estimaciones: si hay ritmo o velocidad deduce el tiempo o la distancia que falta, y la UI lo marca con «≈».
- `model/Gear.kt`: `Shoe` (límite de km, 700 por defecto; `initialKm` para km previos; `retired`; `photo` = nombre del archivo de la foto) y `Session` (fecha, entreno opcional, zapatilla, distancia en metros). Los km de cada zapatilla **se derivan de las sesiones** con `Shoe.totalKm`; no se guardan acumulados.
- `model/Samples.kt`: entrenos de ejemplo (6×400, rodaje, fartlek, progresivo) para el botón «Cargar ejemplos».
- `data/JsonListStore.kt`: persistencia genérica en JSON local (`filesDir`, escritura atómica vía `.tmp` + rename), expuesta como `StateFlow`. Migrable a Room si crece.
- `data/ShoePhotos.kt`: las fotos de zapatillas se copian reducidas (máx. 960 px, JPEG, respetando EXIF) a `filesDir/shoe-photos/`; en el modelo solo va el nombre. Se eligen con el selector del sistema (`PickVisualMedia`, sin permisos). `ShoePhoto` (en `ui/`) las muestra con un icono si no hay. El ViewModel borra el archivo al quitar/cambiar la foto o borrar la zapatilla, y el diálogo borra la foto recién elegida si se cancela. La futura exportación debe incluir esta carpeta.
- `ui/WorkoutViewModel.kt`: único ViewModel. Mantiene los stores de entrenos, zapatillas y sesiones, el borrador del editor (`_draft`) y el aviso de límite (`_alert`).
- `ui/`: pantallas Compose (`WorkoutListScreen`, `WorkoutEditorScreen`, `BlockDialog`, `SessionsScreen`, `ShoesScreen`), barra de pestañas (`HomeBar`: Entrenos / Historial / Zapatillas), `MonthCalendar` (cuadrícula mensual desde el lunes), `Timeline` (cronograma de colores), `Format` y `Theme`. `SessionsScreen` es la pestaña **Historial**: calendario + resumen del mes + sesiones del día elegido, y el registro de sesión hereda el día seleccionado. Los nombres de meses y días usan `EsLocale` (español fijo, como el resto de textos).

## Estilo visual (inspirado en Polar Flow)

- Tema fijo en `ui/Theme.kt` (sin colores dinámicos del sistema): rojo Polar `#D0002A` como único color de marca, fondo blanco/gris, formas casi rectas (2–6 dp), claro y oscuro.
- Los colores de bloque usan la paleta de zonas de Polar: calentamiento ámbar, trabajo rosa, recuperación verde, enfriamiento azul, descanso gris.
- Componentes compartidos en `ui/PolarComponents.kt`: `PolarTopBar` (título centrado + línea roja), `SectionHeader`, `StatBlock` (cifra grande + unidad pequeña + etiqueta), `PolarCard` (plana, borde fino) y `PolarFab`. Usarlos en pantallas nuevas en vez de `Card`/`TopAppBar`/FAB de Material.
- Los botones deben llevar `shape = MaterialTheme.shapes.medium` para que no salgan en píldora.
- Las capturas de referencia están en `polar-screenshots/` (ignorada por git: son de Polar).

## Capturas del README

- Están en `docs/screenshots/` (`entrenos`, `editor`, `historial`, `zapatillas`; 540 px de ancho) y se muestran en el README.
- **Al cambiar la interfaz de forma visible, regenerarlas** y, si se añade una pantalla importante, incluirla en el README.
- Datos de ejemplo en `scripts/demo-data/` (`shoes.json`, `sessions.json`, fechas de noviembre de 2026, y `photos/` con las imágenes de las zapatillas). Para cargarlos en el emulador: `adb shell am force-stop app.running`, luego `adb shell "run-as app.running sh -c 'cat > files/shoes.json'" < scripts/demo-data/shoes.json` (igual con `sessions.json`; las fotos van a `files/shoe-photos/<nombre>.jpg`) y abrir la app. Con `run-as`, los globs (`*`) hay que escaparlos dentro de comillas dobles o el shell del dispositivo los expande antes. Captura con `adb exec-out screencap -p` y reducir con `magick … -resize 540x`.
- Si el mes actual ya no es noviembre de 2026, navegar al calendario a ese mes antes de capturar.

## Reglas de negocio a respetar

- Aviso de zapatillas al registrar una sesión: «casi al límite» a partir del 90 %, y «límite alcanzado» cada vez que una sesión deja la zapatilla en el límite o por encima. Barra de progreso naranja ≥ 90 %, roja al llegar al límite.
- Borrar una sesión resta sus km automáticamente (por estar derivados).
- Las zapatillas retiradas salen aparte y no se ofrecen al registrar sesiones.
- Al registrar una sesión con un entreno, la distancia se rellena con la estimada y se preselecciona la zapatilla de la última sesión.
- El editor pregunta antes de descartar cambios sin guardar.

## Gotchas

- `Target` choca con `kotlin.annotation.Target`: por eso la clase se llama `BlockTarget`.
- Cuidado con `trimNumber` en `ui/Format.kt`: una versión anterior recortaba ceros de enteros (10 → «1»).
- `local.properties` y `build/` están en `.gitignore`. Repo git en la rama `main`.
