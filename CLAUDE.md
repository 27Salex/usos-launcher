# Usos launcher

Launcher Android (Kotlin + Jetpack Compose) descrito en PLAN.md. Implementadas las Fases 0 y 1.

## Comandos
- `./gradlew test` tests unitarios; `./gradlew assembleDebug` APK debug.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- JDK 17 y SDK fijados en `gradle.properties` / `local.properties` (no versionado).

## Paquetes (com.usos.launcher)
`ui.theme` tokens, tipografías, `UsosText`, `Modifier.tap`; `ui.home` inicio (reloj, semana, acordeón, mosaico, ViewModel);
`data.apps` AppRepository (LauncherApps); `data.sections` AppConfig, asignador, disposición inicial, stores DataStore;
`domain` AppEntry, Section, clasificador + lista de distracciones (un solo fichero). `AppContainer.kt` = inyección manual (UsosApp).
Paquetes de fases futuras (search, appdetail, widgets, feeds, settings, leisure, usage, suggest, service) aún no existen.

## Convenciones
- Colores, radios y tipografías solo desde `ui.theme`; nada de sombras, degradados ni ripple de color.
- Animaciones con `usosTween(LocalAnimationsEnabled.current)` (250 ms; snap si el sistema las desactiva).
- Lógica pura (DotMatrix, SpanishDate, SectionAssigner, SectionLayout, AppClassifier) separada de Android y con tests.
- Sin Material 3 ni DI framework. `manual = true` en AppConfig nunca se reclasifica.
- Space Grotesk es fuente variable (eje wght fijado a 600); JetBrains Mono son TTF estáticos.

## Versiones fijadas
AGP 8.13.2, Gradle 8.14.3, Kotlin 2.4.20, KSP 2.3.12 (aún sin procesadores), compose-bom 2026.06.01,
core-ktx 1.18.0, activity-compose 1.12.4, lifecycle 2.10.0, datastore-preferences 1.3.0,
kotlinx-serialization-json 1.11.0, coroutines 1.11.0, junit 4.13.2. compileSdk/targetSdk 36, minSdk 33, JVM 17.
