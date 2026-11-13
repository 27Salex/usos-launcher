# Usos launcher

Launcher Android (Kotlin + Jetpack Compose) descrito en PLAN.md. Implementadas las Fases 0 a 8 (plan completo).

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
`data.suggest` SuggestParser/SuggestFlow (puros), SuggestClient (interfaz) + OkHttpSuggestClient; `ui.widgets` HostedWidget/WidgetSizing/WidgetStore (puros + DataStore), WidgetsPage, WidgetPicker, UsosHostView (long-press), WidgetAdder (alta bind/configure).
`data.feeds` Feed/Article/FeedDao/FeedDatabase (Room), FeedParser (puro, XmlPullParser), FeedQueue (límite por hora, puro) + FeedFormat (antigüedad, política de refresco, URL), FeedRepository, OkHttpFeedFetcher, ThumbnailLoader (OkHttp + BitmapFactory + LruCache); `ui.feeds` FeedsViewModel, FeedsPage (cabecera, píldoras, tarjetas, pull-to-refresh propio), FeedManagerScreen; `data.info` InfoPieces/MosaicGrid (puros) + AgendaRepository (CalendarContract); `ui.home.InfoLauncher` intents de las piezas.

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

- Sugerencias: SuggestFlow.build es la única vía a la red; con `suggestionsOn` apagado o texto vacío emite vacío sin llamar al cliente (test con cliente falso). Espera 250 ms con flatMapLatest (cancela la anterior). Respuestas decodificadas como UTF-8.
- Búsqueda web: ACTION_WEB_SEARCH y, si no resuelve, ACTION_VIEW a google.com/search.
- Widgets: AppWidgetHost id fijo 0x5501 (UsosWidgetHost crea UsosHostView); startListening/stopListening en onStart/onStop de HomeActivity. Tamaños: bajo = minHeight declarado (suelo 72 dp), medio x1,5, alto x2. Ids huérfanos del host se borran al crear HomeViewModel. El id pendiente de un alta solo vive en memoria. Escala de grises = capa de hardware con ColorMatrix saturación 0 (`widgetsGrayscale`, por defecto true).

- Noticias: Room 2.8.5 (`feeds.db`, v1, sin exportar esquema), KSP room-compiler. Parser: `FeedParser.parse(XmlPullParser)` recibe el parser ya configurado (en la app `Xml.newPullParser()`, que es la KXmlParser de Android; en tests `org.kxml2.io.KXmlParser` porque el android.jar de los tests solo trae stubs: de ahí `testImplementation("net.sf.kxml:kxml2:2.3.0")`). Modo relaxed activado (entidades HTML sueltas). Imagen: media:thumbnail > media:content (imagen) > enclosure imagen > primera `<img>` de content/description.
- Límite por hora (`FeedQueue.plan`): cupo = feedsPerHour (3..10, defecto 5, Ajustes > Noticias) menos artículos con `revealedAt` dentro de la hora de reloj actual (los leídos cuentan). Los de la cola se desvelan del más reciente al más antiguo; el desvelado solo corre mientras el panel está en pantalla y visible (ojo no cerrado; WhileSubscribed). Decisión: un artículo desvelado sigue visible hasta leerlo; al leerlo desaparece salvo que tenga estrella (los guardados quedan siempre). Cabecera «N MÁS A LAS HH:MM» con N = min(cola, feedsPerHour) y la hora = próxima hora en punto. Las noticias no tocan el estado de ocio. Pastillas: el nº es de no leídos visibles. Cada feed conserva sus 100 artículos más recientes + los guardados.
- Refresco: al abrir el panel si han pasado >30 min (mínimo de `lastFetchedAt` de los feeds) y al tirar hacia abajo. PullToRefresh es propio (nestedScroll de foundation): material3 no es dependencia y no se añadió.
- Piezas de información: `MosaicGrid.place(shape, n)` coloca la pieza (2x1 Agenda, 1x2 Mapas) en la esquina superior izquierda y las apps rellenan el resto; con pieza caben 4 apps. Sin permiso/sin evento la pieza no se dibuja y las apps ocupan el hueco. Agenda: próximo evento no "todo el día" de hoy/mañana con `end > ahora` (Instances), refresco cada minuto; READ_CALENDAR se pide una sola vez al terminar el primer arranque (`calendar_asked`) y desde Ajustes (si el sistema ya no muestra el diálogo abre los ajustes de la app). Mapas: visible si Maps está instalado y sustituye a la app de Mapas en el mosaico de Moverse; `google.navigation:q=casa` (paquete Maps) y si falla `geo:0,0?q=casa`.

## Versiones fijadas
AGP 8.13.2, Gradle 8.14.3, Kotlin 2.4.20, KSP 2.3.12 (room-compiler), compose-bom 2026.06.01,
core-ktx 1.18.0, activity-compose 1.12.4, lifecycle 2.10.0, datastore-preferences 1.3.0,
kotlinx-serialization-json 1.11.0, coroutines 1.11.0, junit 4.13.2, okhttp 5.4.0 (5.5.x exige compileSdk 37; no subir sin AGP nuevo), room 2.8.5, coroutines-test 1.11.0 y kxml2 2.3.0 (solo tests). compileSdk/targetSdk 36, minSdk 33, JVM 17.
