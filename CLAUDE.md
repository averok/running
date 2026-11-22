# CLAUDE.md

App Android **Running** (`app.running`): registro de entrenos de running. Permite diseñar entrenos por bloques, registrar sesiones realizadas y llevar el control de km por zapatillas con aviso de límite. Kotlin + Jetpack Compose, un solo módulo (`:app`). UI y textos en español.

## Alcance (decisiones del usuario)

- Es **solo un registro**: el entreno en directo (GPS, avisos de voz, servicio en segundo plano) se descartó. Para correr usa la app de Polar.
- La integración con Polar/Strava (importar sesiones, `gear_id` de Strava) está **aparcada**. Si se retoma, la vía preferida era la API de Strava (Polar Flow ya sincroniza con Strava), pidiendo antes al usuario crear la app en `strava.com/settings/api`.
- Los releases se publican en GitHub Releases, firmados con una clave propia (ver «Releases y actualizaciones»). La app ya no tiene que desinstalarse para actualizarse. El icono propio ya existe (ver «Estilo visual»).

## Comandos

```
export JAVA_HOME=~/dev/jdk ANDROID_HOME=~/dev/android-sdk   # JDK y SDK compartidos con otros proyectos del usuario
./gradlew assembleDebug                                      # APK en app/build/outputs/apk/debug/app-debug.apk
scripts/emulator.sh &                                        # AVD "running" en el puerto 5580 → serial emulator-5580 (Android 34 x86_64, KVM), ventana visible vía XWayland
export ANDROID_SERIAL=emulator-5580                          # SIEMPRE: así adb nunca toca el emulador de otro proyecto
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.running/.MainActivity
```

- El AVD `running` es propio de este proyecto; **no tocar** el AVD de `ticketplus`.
- Si se usa el emulador, la ventana debe quedar visible para el usuario.
- Tests unitarios solo de la lógica pura de actualizaciones (`./gradlew testDebugUnitTest`, `UpdateCheckTest` y `CheerTest`; el primero incluye una respuesta real de GitHub guardada en `app/src/test/resources/`); no hay lint. El resto se verifica compilando y probando en el emulador.
- **Hay otro proyecto del usuario (ticketplus, con su propio AVD `ticketplus`) trabajando a la vez con otra sesión de Claude.** Convención para no chocar: este proyecto usa solo el AVD `running` en `emulator-5580` (`scripts/emulator.sh` ya fija el puerto) y todos los comandos `adb` llevan `ANDROID_SERIAL=emulator-5580` (en zsh, `adb -s ...` guardado en una variable no se separa en palabras; usar la variable de entorno). No tocar nunca otros serials (`emulator-5554`, `emulator-5556`…) ni matar emuladores que no hayamos arrancado nosotros. Si `adb devices` muestra el AVD `running` en otro puerto, lo está usando otra sesión: no usarlo. Ya hubo un choque: la otra sesión lanzó `app.ticketplus` en el emulador de este proyecto y las capturas salieron corruptas.
- `adb install -r` cierra la app: relanzarla antes de pulsar. Android 14 rechaza por sí mismo los ZIP con rutas `../` (`ZipException: Invalid zip entry path`); en Android 8–13 (minSdk 26) solo actúa el filtro de `Backup.read`, que no se ha podido probar en el emulador (API 34).
- Versiones: AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, compileSdk/targetSdk 35, minSdk 26, JVM 17.

## Arquitectura

Paquete `app.running` en `app/src/main/java/app/running/`:

- `model/Workout.kt`: `Workout` → lista de `WorkoutItem`, que es `SingleItem` (un bloque) o `GroupItem` (×N repeticiones de varios bloques). `Block` = tipo (calentamiento, trabajo, recuperación, enfriamiento, descanso) + `EndCondition` (tiempo, distancia, manual) + `BlockTarget` opcional (ritmo, velocidad, pulsaciones). Calcula estimaciones: si hay ritmo o velocidad deduce el tiempo o la distancia que falta, y la UI lo marca con «≈».
- `model/Gear.kt`: `Shoe` (límite de km, 700 por defecto; `initialKm` para km previos; `retired`; `photo` = nombre del archivo de la foto) y `Session` (fecha, entreno opcional, zapatilla, distancia en metros). Los km de cada zapatilla **se derivan de las sesiones** con `Shoe.totalKm`; no se guardan acumulados.
- `model/Samples.kt`: entrenos de ejemplo (6×400, rodaje, fartlek, progresivo) para el botón «Cargar ejemplos».
- `model/Cheer.kt` (lógica pura, testeada): mensajes de ánimo tras una sesión. Compara una distancia con una tabla de referencias (`Named`: ciudades, alturas, travesías; `Countable`: portaviones, torre Eiffel, campo de fútbol, maratón, pista) y devuelve la frase que sigue a «Has corrido …». Las distancias entre ciudades **se calculan** (círculo máximo) desde coordenadas y son «en línea recta»; no escribir kilómetros de memoria (ya hubo un recuerdo erróneo: Barcelona–Girona son 85 km, no 76). Las medidas fijas están verificadas en fuentes (ver el comentario de `refs`). La elección es estable por sesión (semilla = id), toma al azar entre las 6 mejor encajadas y no repite referencia entre las líneas del mismo diálogo. `ui/SessionResultDialog.kt` lo muestra junto con el aviso de límite de zapatillas, en un único diálogo.
- `data/JsonListStore.kt`: persistencia genérica en JSON local (`filesDir`, escritura atómica vía `.tmp` + rename), expuesta como `StateFlow`. Migrable a Room si crece.
- `data/ShoePhotos.kt`: las fotos de zapatillas se copian reducidas (máx. 960 px, JPEG, respetando EXIF) a `filesDir/shoe-photos/`; en el modelo solo va el nombre. Se eligen con el selector del sistema (`PickVisualMedia`, sin permisos). `ShoePhoto` (en `ui/`) las muestra con un icono si no hay. El ViewModel borra el archivo al quitar/cambiar la foto o borrar la zapatilla, y el diálogo borra la foto recién elegida si se cancela. La futura exportación debe incluir esta carpeta.
- `data/Backup.kt`: exportar/importar en un ZIP (`backup.json` con `schemaVersion`, `workouts.json`, `shoes.json`, `sessions.json` y `shoe-photos/*.jpg`). `read` valida **todo** antes de tocar nada y solo acepta nombres conocidos; ignora cualquier otra entrada y descarta referencias de foto inseguras (`isSafePhotoName`). Importar **reemplaza** los datos (con diálogo de confirmación que muestra las cantidades). `AppJson` es la configuración JSON común. **Al cambiar un modelo de forma incompatible hay que subir `Backup.SCHEMA_VERSION` y migrar copias antiguas en `read`**; añadir campos con valor por defecto es compatible y no lo requiere.
- `ui/WorkoutViewModel.kt`: único ViewModel. Mantiene los stores de entrenos, zapatillas y sesiones, el borrador del editor (`_draft`) y el aviso de límite (`_alert`).
- `ui/`: pantallas Compose (`WorkoutListScreen`, `WorkoutEditorScreen`, `BlockDialog`, `SessionsScreen`, `ShoesScreen`), barra de pestañas (`HomeBar`: Entrenos / Historial / Zapatillas / Ajustes), `SettingsScreen` (copia de seguridad y versión; aquí irá también la comprobación de actualizaciones), `MonthCalendar` (cuadrícula mensual desde el lunes), `Timeline` (cronograma de colores), `Format` y `Theme`. `SessionsScreen` es la pestaña **Historial**: calendario + resumen del mes + sesiones del día elegido, y el registro de sesión hereda el día seleccionado. Los nombres de meses y días usan `EsLocale` (español fijo, como el resto de textos).

## Releases y actualizaciones

- **Versión**: se cambia solo `appVersion` en `app/build.gradle.kts`; `versionCode` se deriva (major*10000 + minor*100 + patch) y el tag es `vX.Y.Z`. Un release solo se instala encima del anterior si su `versionCode` es mayor.
- **Firma**: clave propia en `~/.config/running-signing/` (`running-release.jks` + `keystore.properties`, permisos 600, **fuera del repo**; Gradle la lee de ahí, o de `RUNNING_SIGNING_PROPS`). **Si se pierde, ninguna instalación existente podrá actualizarse** (habría que exportar los datos, desinstalar e importar): el usuario debe guardar una copia de esa carpeta a salvo. La huella pública está en `app/release-cert.sha256`. La clave de debug es otra: un APK debug no actualiza a uno release ni al revés (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- **Publicar**: `scripts/release.sh` pasa los tests, compila, comprueba que la firma coincide con `app/release-cert.sha256` y deja `build/release/running-X.Y.Z.apk` (exige árbol limpio y tag nuevo). **No publica** salvo `--publish` (necesita `gh`, ya instalado en `~/.local/bin` y con sesión iniciada). Las notas del release salen de `docs/release-notes/X.Y.Z.md` si existe (la app muestra su principio al avisar), si no se autogeneran. Subir un release es una acción pública: pedir confirmación al usuario antes.
- **En la app**: `data/UpdateCheck.kt` (lógica pura y testeada: interpretar el JSON y comparar versiones), `data/UpdateInstaller.kt` (red, descarga, verificación e instalador), `UpdateState` en el ViewModel, y la UI en `SettingsScreen` (sección «Actualizaciones») + `UpdateDialogs`. Consulta `api.github.com/repos/averok/running/releases/latest`: el release debe tener tag `vX.Y.Z`, no ser borrador ni prerelease y llevar un asset `.apk`. Antes de abrir el instalador del sistema verifica tamaño y SHA-256 (el `digest` que da GitHub), paquete, `versionCode` mayor y misma firma. La comprobación automática es como mucho una vez al día y se puede desactivar. El usuario concede una vez «instalar apps desconocidas» a Running y confirma cada instalación.
- **Probar sin publicar nada**: compilar debug con `-PupdateUrl=http://10.0.2.2:PUERTO/latest.json` (solo en debug; el tráfico http solo se permite en debug), servir en `127.0.0.1` un JSON con formato de GitHub (`tag_name`, `assets[].browser_download_url/size/digest`) y APK de versión mayor (compilar con un `appVersion` temporal y restaurar con `git checkout`). Casos ya probados: actualización correcta, firma distinta, SHA-256 incorrecto, enlace roto, al día, sin APK y 404.
- `adb install -r -d` permite bajar de versión solo en debug; desinstalar en el emulador borra sus datos (restaurar con `scripts/demo-data/`).

## Estilo visual (inspirado en Polar Flow)

- Tema fijo en `ui/Theme.kt` (sin colores dinámicos del sistema): rojo Polar `#D0002A` como único color de marca, fondo blanco/gris, formas casi rectas (2–6 dp), claro y oscuro.
- Los colores de bloque usan la paleta de zonas de Polar: calentamiento ámbar, trabajo rosa, recuperación verde, enfriamiento azul, descanso gris.
- Componentes compartidos en `ui/PolarComponents.kt`: `PolarTopBar` (título centrado + línea roja), `SectionHeader`, `StatBlock` (cifra grande + unidad pequeña + etiqueta), `PolarCard` (plana, borde fino) y `PolarFab`. Usarlos en pantallas nuevas en vez de `Card`/`TopAppBar`/FAB de Material.
- Los botones deben llevar `shape = MaterialTheme.shapes.medium` para que no salgan en píldora.
- **Icono**: adaptativo y vectorial (barras de intensidad de un entreno, con los colores de zona, sobre fondo oscuro y una línea roja). Se genera con `python3 scripts/icon/generate.py`, que escribe `res/drawable/ic_launcher_*.xml`, `res/mipmap-anydpi-v26/ic_launcher*.xml` (incluye capa monocroma para los iconos temáticos de Android 13+) y `docs/icon.png`. Para cambiarlo, editar el script y regenerar, no los XML a mano. Si cambia la paleta de zonas de `Theme.kt`, actualizar los colores del script.
- Las capturas de referencia están en `polar-screenshots/` (ignorada por git: son de Polar).

## Capturas del README

- Están en `docs/screenshots/` (`entrenos`, `editor`, `historial`, `zapatillas`; 540 px de ancho) y se muestran en el README.
- **Al cambiar la interfaz de forma visible, regenerarlas** y, si se añade una pantalla importante, incluirla en el README.
- Datos de ejemplo en `scripts/demo-data/` (`shoes.json`, `sessions.json`, fechas de noviembre de 2026, y `photos/` con las imágenes de las zapatillas). Para cargarlos en el emulador: `adb shell am force-stop app.running`, luego `adb shell "run-as app.running sh -c 'cat > files/shoes.json'" < scripts/demo-data/shoes.json` (igual con `sessions.json`; las fotos van a `files/shoe-photos/<nombre>.jpg`) y abrir la app. Con `run-as`, los globs (`*`) hay que escaparlos dentro de comillas dobles o el shell del dispositivo los expande antes. Captura con `adb exec-out screencap -p` y reducir con `magick … -resize 540x`.
- Si el mes actual ya no es noviembre de 2026, navegar al calendario a ese mes antes de capturar.

## Reglas de negocio a respetar

- Al registrar una sesión sale un único diálogo: la línea de la sesión, la del total acumulado (suma de sesiones) y la de la zapatilla usada (sus km incluyen los previos), cada una con su comparación; debajo, si toca, el aviso de zapatillas. Para añadir una referencia: verificar el dato en una fuente, ponerlo en `Cheer.refs` y que no deje huecos en la cobertura (hay un test que barre de 100 m a 90.000 km).
- Aviso de zapatillas al registrar una sesión: «casi al límite» a partir del 90 %, y «límite alcanzado» cada vez que una sesión deja la zapatilla en el límite o por encima. Barra de progreso naranja ≥ 90 %, roja al llegar al límite.
- Borrar una sesión resta sus km automáticamente (por estar derivados).
- Las zapatillas retiradas salen aparte y no se ofrecen al registrar sesiones.
- Al registrar una sesión con un entreno, la distancia se rellena con la estimada y se preselecciona la zapatilla de la última sesión.
- El editor pregunta antes de descartar cambios sin guardar.

## Gotchas

- `Target` choca con `kotlin.annotation.Target`: por eso la clase se llama `BlockTarget`.
- Cuidado con `trimNumber` en `ui/Format.kt`: una versión anterior recortaba ceros de enteros (10 → «1»).
- `local.properties` y `build/` están en `.gitignore`. Repo git en la rama `main`.
