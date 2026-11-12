# Usos launcher

Launcher Android (Kotlin + Jetpack Compose) descrito en PLAN.md. Implementadas las Fases 0 a 5.

## Comandos
- `./gradlew test` tests unitarios; `./gradlew assembleDebug` APK debug.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- JDK 17 y SDK fijados en `gradle.properties` / `local.properties` (no versionado).

## Paquetes (com.usos.launcher)
`ui.theme` tokens, tipografías, `UsosText`, `Modifier.tap`; `ui.home` inicio (reloj, semana, acordeón, mosaico, ViewModel);
`data.apps` AppRepository (LauncherApps); `data.sections` AppConfig, asignador, disposición inicial, stores DataStore;
`ui.search` SearchFilter (puro) + SearchOverlay (la barra viaja arriba; progreso animado en HomeScreen); `data.leisure` LeisureMachine (pura, Clock inyectable),
LeisureRepository (DataStore; fuente de verdad del ocio; la espera "Descongelando" vive solo en memoria), LeisureState/LeisureStatus/LeisureLimits;
`ui.home.LeisurePanel` panel de Ocio. `domain` AppEntry, Section, clasificador + lista de distracciones (un solo fichero). `AppContainer.kt` = inyección manual (UsosApp).
`service` BlockerService (AccessibilityService; BlockerRules pura, LauncherAlert, isBlockerEnabled); `data.usage` UsageAggregator (puro, desde UsageEvents) + UsageRepository (queryEvents, StorageStatsManager);
`ui.appdetail` ficha de app; `ui.settings` Ajustes y primer arranque; `ui.home.Route` pantalla superpuesta (Home/Onboarding/Settings/Detail) que gestiona HomeViewModel.
Paquetes de fases futuras (widgets, feeds, suggest) aún no existen.

## Convenciones
- Colores, radios y tipografías solo desde `ui.theme`; nada de sombras, degradados ni ripple de color.
- Animaciones con `usosTween(LocalAnimationsEnabled.current)` (250 ms; snap si el sistema las desactiva).
- Lógica pura (DotMatrix, SpanishDate, SectionAssigner, SectionLayout, AppClassifier) separada de Android y con tests.
- Sin Material 3 ni DI framework. `manual = true` en AppConfig nunca se reclasifica.
- Space Grotesk es fuente variable (eje wght fijado a 600); JetBrains Mono son TTF estáticos.

- Ocio: HomeActivity hace tick cada 500 ms mientras esta STARTED y cancela la descongelacion en onStop. HomeViewModel.openApp rechaza siempre apps de ocio congeladas (abre la seccion Ocio). Sin presupuesto = restante < 15 min.

- Ajustes: pulsación larga sobre el reloj de puntos. Ficha: pulsación larga en una pieza del mosaico o de la búsqueda. Atrás en ficha/ajustes vuelve; en primer arranque lo salta.
- Bloqueo: el servicio avisa al launcher por `AppContainer.pendingAlert` (mismo proceso, sin intents) y ejecuta GLOBAL_ACTION_HOME; HomeViewModel ignora el HOME que acompaña al aviso durante 2 s. El servicio observa `LeisureRepository.status` (load() es idempotente y compartido).
- Uso: tiempo con `queryEvents` (RESUMED/PAUSED por actividad, sesión = primera resumida a última pausada, cortes a medianoche local); "esta semana" = desde el lunes; solo perfil principal.
- Atrás en búsqueda: el teclado se come la primera pulsación; SearchOverlay cierra la búsqueda cuando el IME se oculta estando abierta.
- Primer arranque (`onboarding_done` en DataStore) se muestra hasta que se termina o salta; se repite desde Ajustes.

## Versiones fijadas
AGP 8.13.2, Gradle 8.14.3, Kotlin 2.4.20, KSP 2.3.12 (aún sin procesadores), compose-bom 2026.06.01,
core-ktx 1.18.0, activity-compose 1.12.4, lifecycle 2.10.0, datastore-preferences 1.3.0,
kotlinx-serialization-json 1.11.0, coroutines 1.11.0, junit 4.13.2. compileSdk/targetSdk 36, minSdk 33, JVM 17.
