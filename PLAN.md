# Plan del launcher Android por usos

Nov 12, 2026 · @Alexander

## Resumen

Launcher de Android para uso personal en un Samsung Galaxy S25 que ordena las apps por uso y mantiene congeladas las de ocio hasta que se pide tiempo. Se construye en Kotlin con Jetpack Compose y se instala por ADB, sin pasar por Play Store.

- **Problema:** el inicio actual, lleno de iconos de color, invita a abrir apps sin necesidad.
- **Solución:** una sola pantalla negra con reloj de puntos, cinco secciones por uso que se despliegan en un mosaico, y una barra de búsqueda. Sin dock y sin fondo de pantalla. A los lados, dos paneles secundarios: widgets y noticias.
- **Antiprocrastinación:** la sección Ocio tiene un presupuesto diario de minutos y exige una espera antes de abrirse.
- **Sin mantenimiento:** las apps se clasifican solas y nada depende de horarios ni del calendario.

Existe un [prototipo visual interactivo](https://claude.ai/artifact/Wrxnpt9G6Y7dWosiz5hTZi), privado del autor. Claude Code no puede abrirlo, así que este documento describe todo lo necesario para implementarlo.

## Requisitos

Todo lo que sigue lo ha pedido o descartado el autor durante el diseño. Lo descartado no debe reaparecer.

**Pedido**

- Minimalista y que no incite a abrir apps: sin iconos de color y sin contadores de notificaciones.
- Fondo negro puro, todo en blanco y negro con un único color de acento.
- Apps separadas por uso y accesibles desde una sola pantalla. El usuario decide qué apps salen en inicio y en qué categoría.
- Sin configuración diaria y sin depender de horarios ni del calendario.
- Bloquear las apps que no necesita y poder abrirlas un tiempo limitado: 15, 30 o 60 minutos.
- Estable y sin interferir con gestos, recientes ni el resto del sistema.
- Una barra de búsqueda única que sirva para todo: apps y búsqueda web. Se abre con un toque, sube arriba y muestra todas las apps por categoría, con un grupo Otros para las no asignadas.
- Ficha de cada app con su tiempo de uso.
- Panel lateral de widgets.
- Panel lateral de noticias (feeds RSS).
- Estética: reloj de puntos, tipografía monoespaciada en mayúsculas, secciones en palabras grandes y mosaico de piezas de distinto tamaño.

**Descartado**

- Lista de texto pura al estilo de los launchers minimalistas existentes.
- Glassmorphism: sobre negro no se aprecia.
- Lente o menú radial por gestos.
- Espacios entre los que hay que cambiar, y «anclar foco».
- Mostrar u ocultar apps según el horario.
- Dock de iconos.

## Diseño: pantalla de inicio

La pantalla de inicio es una columna única sobre negro: reloj, fecha, semana, cinco secciones y la barra de búsqueda fija abajo. Las medidas son las del prototipo (390 dp de ancho); en el S25 el ancho útil ronda los 360 dp, así que todo debe calcularse a partir del ancho disponible con márgenes laterales de 24 dp.

**Navegación general:** un paginador horizontal de tres páginas. Widgets a la izquierda, Inicio en el centro (por defecto) y Noticias a la derecha. El botón o gesto de inicio vuelve siempre al centro.

### Componentes, de arriba abajo

1. **Reloj de puntos.** Hora en 24 h (HH:MM) dibujada con una matriz de 5 × 7 puntos por dígito. Punto de 5 dp, paso de 9 dp, 14 dp entre dígitos. El separador son dos puntos en las filas 2 y 4 (contando desde 0). Solo se dibujan los puntos encendidos, en blanco. Centrado, a unos 60 dp del borde superior.
2. **Fecha.** Texto como «JUEVES 12 NOVIEMBRE», monoespaciado 11 sp, espaciado 0,14 em, gris, centrado, 15 dp bajo el reloj.
3. **Tira semanal.** Lunes a domingo de la semana actual. Letra del día (L M X J V S D) en 11 sp gris; debajo el número en 13 sp dentro de un círculo de 30 dp. Hoy: círculo blanco con número negro. Resto: sin fondo, número #B0B0B0.
4. **Secciones.** Trabajo, Hablar, Moverse, Personal y Ocio, en ese orden. Cada una es una fila de 46 dp: el nombre a la izquierda (Space Grotesk 600, 30 sp, blanco) y un dato a la derecha (mono 11 sp, gris): el número de apps si está cerrada, un guion si está abierta.
5. **Mosaico.** Aparece justo debajo de la sección abierta y empuja a las siguientes.
6. **Barra de búsqueda.** Fija abajo, respetando los márgenes de la barra de gestos. Píldora de 52 dp de alto, fondo #111111, borde #2A2A2A, texto «BUSCAR» en mono 12 sp gris. Muestra también avisos breves, como «ABRIENDO SLACK…». Solo responde al toque; no hay gesto de deslizar hacia arriba.

### Comportamiento de las secciones

- Solo hay una sección abierta a la vez. Tocar la abierta la cierra.
- Se recuerda la última sección abierta entre sesiones.
- Al abrir, las filas inferiores se desplazan en 250 ms y las piezas entran con fundido y 8 dp de subida.
- Si el contenido no cabe, la zona de secciones hace scroll vertical; reloj y búsqueda no se mueven.
- La fila de Ocio se muestra en gris #8A8A8A mientras está congelada, y su dato va en color de acento: «CONGELADO», los segundos de espera o los minutos restantes.

### Mosaico

Rejilla de 3 columnas × 2 filas, con 11 dp entre columnas y 10 dp entre filas. Ancho de celda = (ancho útil − 22 dp) / 3; alto 95 dp. Hay dos tipos de pieza.

| Pieza | Aspecto | Contenido | Toque |
| --- | --- | --- | --- |
| App | Fondo #111111, borde 1 dp #2A2A2A, radio 18 dp, relleno 12 dp | Arriba, código de 2 letras (mono 22 sp, 500). Abajo, nombre en mayúsculas (mono 11 sp, gris) | Abre la app |
| Información | Fondo de acento, texto negro. Ocupa 2 × 1 o 1 × 2 celdas | Arriba, etiqueta (mono 11 sp). Abajo, dato (15 sp, 700) | Abre la app relacionada |

- El código son las dos primeras letras del nombre, editables por el usuario.
- Pulsación larga sobre una pieza de app: abre su ficha (ver más abajo).
- El mosaico muestra como máximo las apps que caben. El resto de la sección se abre desde la búsqueda. El usuario elige cuáles se muestran y en qué categoría va cada una, desde la ficha de la app.
- Si una pieza de información no tiene datos o permiso, no se dibuja y las apps ocupan su hueco.

### Disposición inicial propuesta

| Sección | Fila 1 | Fila 2 |
| --- | --- | --- |
| Trabajo | Agenda (2 columnas, acento) · Slack | Teams · Jira · GitHub |
| Hablar | WhatsApp · Telegram · Mensajes | Teléfono · Gmail |
| Moverse | Mapas (2 filas, acento) · Waze · smou | Cabify · T-mobilitat |
| Personal | Firefox · Cámara · Spotify | Claude · Lumi · Calendario |
| Ocio | Netflix · Disney+ · Prime Video | Crunchyroll · un juego · Congelar (acento) |

Sale de la pantalla de inicio actual del autor. Al no haber dock, Firefox y Cámara pasan a Personal. Todo lo demás (Outlook, Drive, Meet, Edge, Authenticator, Uber, Fotos, banca) se abre desde la búsqueda.

### Matriz del reloj

Cada dígito son 7 filas de 5 bits; 1 = punto encendido.

```text
0: 01110 10001 10011 10101 11001 10001 01110
1: 00100 01100 00100 00100 00100 00100 01110
2: 01110 10001 00001 00010 00100 01000 11111
3: 11110 00001 00001 01110 00001 00001 11110
4: 00010 00110 01010 10010 11111 00010 00010
5: 11111 10000 11110 00001 00001 10001 01110
6: 00110 01000 10000 11110 10001 10001 01110
7: 11111 00001 00010 00100 01000 01000 01000
8: 01110 10001 10001 01110 10001 10001 01110
9: 01110 10001 10001 01111 00001 00010 01100
```

## Diseño: Ocio congelado

Las apps de ocio no se pueden abrir hasta que el usuario pide una sesión, espera unos segundos y gasta minutos de un presupuesto diario.

- **Presupuesto diario:** 60 min por defecto, ajustable de 30 a 120 en pasos de 15. Se repone a medianoche, hora local.
- **Espera:** 20 s por defecto, ajustable de 5 a 60.
- **Consumo:** se descuenta el tiempo de reloj entre descongelar y congelar, se use la app o no. Es más simple y anima a congelar al terminar.

| Estado | Qué se ve al abrir la sección Ocio | Cómo se sale |
| --- | --- | --- |
| Congelado | Panel con borde discontinuo #4A4A4A, radio 18 dp. Arriba «CONGELADO» en acento y «QUEDAN N MIN HOY» en gris. Debajo, píldoras 15 / 30 / 60 y botón blanco «DESCONGELAR» | Pulsar Descongelar |
| Descongelando | El mismo panel con «DESCONGELANDO · N S», barra de progreso de 3 dp en acento y botón «CANCELAR» | Fin de la espera, Cancelar o salir del launcher (cancela) |
| Abierto | Mosaico normal con las apps de ocio y una pieza de acento «CONGELAR · N min» | Tocar Congelar o llegar a 0 |
| Sin presupuesto | Panel con el texto «Sin tiempo de ocio por hoy. Mañana se repone.» | Medianoche |

- Las píldoras miden 56 × 44 dp. La seleccionada es blanca con texto negro. Solo se muestran las duraciones que caben en el presupuesto; por defecto, 30.
- Al terminar una sesión con una app de ocio en primer plano, el sistema vuelve a inicio y la barra de búsqueda muestra «OCIO CONGELADO DE NUEVO».
- El prototipo acelera los tiempos (espera de 5 s, 1 s = 1 min). La app real usa tiempos reales.

## Diseño: búsqueda universal

Una sola barra sirve para abrir apps y para buscar en la web. Al tocarla, la barra sube a la parte superior y debajo aparecen todas las apps agrupadas por categoría.

- **Apertura:** un toque en la barra, nunca un gesto de deslizar. La barra viaja desde abajo hasta la parte superior en 250 ms y se convierte en el campo de texto; el teclado se abre a la vez.
- **Campo:** 56 dp de alto, radio 18 dp, fondo #111111, borde #2A2A2A, texto 18 sp.
- **Sin texto:** todas las apps agrupadas por la categoría que el usuario les ha asignado, en este orden: Trabajo, Hablar, Moverse, Personal, Otros y Ocio. Cada grupo lleva su nombre como cabecera (mono 11 sp, gris) y debajo sus apps en una rejilla de 4 columnas de piezas pequeñas, con código y nombre, en orden alfabético.
- **Otros:** agrupa las apps que no están asignadas a ninguna categoría.
- **Con texto:** la misma vista filtrada. Los grupos sin coincidencias desaparecen. El filtro ignora mayúsculas y acentos y busca por subcadena.
- **Sugerencias web:** debajo de las apps, hasta 5 sugerencias de búsqueda. Tocar una lanza la búsqueda en el navegador por defecto.
- **Intro:** si hay una sola app coincidente, la abre. Si no, lanza la búsqueda web del texto.
- **Pulsación larga:** abre la ficha de la app, donde se cambia su categoría y si sale en inicio.
- **Vacío:** «Nada con ese nombre.»
- **Salir:** gesto atrás. La barra vuelve a su sitio abajo.

**Apps de ocio en la búsqueda.** Forman el último grupo de la lista. Mientras estén congeladas se muestran en gris con la etiqueta «OCIO · CONGELADA» en acento, y tocarlas lleva a inicio con la sección Ocio abierta, sin abrir la app.

**Privacidad de las sugerencias.** Cada pulsación se envía al proveedor de sugerencias, así que la función es un ajuste que se puede apagar. Las peticiones esperan 250 ms sin teclear antes de salir. La búsqueda se lanza con el buscador por defecto del sistema. Las sugerencias vienen de Google salvo que se cambie el proveedor en ajustes.

## Diseño: ficha de app

La pulsación larga sobre cualquier app abre su ficha, que muestra cuánto se usa y permite configurarla.

- **Cabecera:** nombre en Space Grotesk 30 sp y versión en mono 11 sp gris.
- **Uso:** tiempo de hoy en grande; debajo, tiempo de esta semana y número de aperturas en 7 días.
- **Gráfico:** siete barras, una por día, en gris #4A4A4A. La de hoy, en color de acento. Debajo, «Últimos 7 días» y el almacenamiento que ocupa.
- **Interruptor «Mostrar uso en la pieza»:** añade los minutos de hoy a su pieza del mosaico.
- **Sección:** selector para moverla a Trabajo, Hablar, Moverse, Personal, Ocio u Otros. Junto a él, el interruptor «Mostrar en inicio» decide si la app sale en el mosaico de su categoría; si el mosaico está lleno, avisa de que hay que quitar otra antes.
- **Código:** campo para editar las dos letras.
- **Destacado:** cuatro estilos de pieza: normal, acento, blanca y solo borde.
- **Acciones:** «Info» abre la ficha del sistema y «Desinstalar» lanza el diálogo del sistema.

Si falta el permiso de acceso a datos de uso, el bloque de uso se sustituye por un botón que lleva al ajuste del sistema.

## Diseño: panel de widgets

La página a la izquierda de inicio aloja widgets reales de Android en una columna vertical con scroll.

- **Cabecera:** título «Widgets» en Space Grotesk 30 sp y botón «AÑADIR».
- **Cada widget:** ocupa todo el ancho útil dentro de un contenedor con fondo #111111, borde #2A2A2A y radio 18 dp. La altura es la mínima que declara el widget, ajustable en tres tamaños: bajo, medio y alto.
- **Añadir:** lista de widgets disponibles agrupados por app. Si el widget tiene pantalla de configuración, se lanza antes de colocarlo.
- **Editar:** pulsación larga sobre un widget muestra subir, bajar, cambiar tamaño y quitar. No hay arrastrar y soltar.
- **Escala de grises:** ajuste global, activado por defecto, que pinta todos los widgets sin color para mantener la estética.
- **Vacío:** «Sin widgets. Añade el primero.»

Usos previstos por el autor: agenda, tareas, mando de la tele y tiempo.

## Diseño: panel de noticias

La página a la derecha de inicio es un lector de feeds RSS y Atom.

- **Cabecera:** título «Noticias» y un botón de ojo que oculta o muestra el contenido del panel.
- **Filtros:** píldoras horizontales, «Todo» y una por feed, con el número de artículos sin leer. La activa va en color de acento.
- **Artículo:** tarjeta con fondo #111111 y radio 18 dp. Título en 15 sp 700 (máximo 3 líneas), resumen en 13 sp gris (2 líneas), fuente y antigüedad en mono 11 sp. Miniatura de 56 dp a la derecha, en escala de grises. Punto de acento si no está leído. Estrella para guardar.
- **Toque:** abre el artículo en el navegador y lo marca como leído.
- **Gestión de feeds:** pantalla para añadir una URL, renombrar y borrar.
- **Actualización:** al abrir el panel si han pasado más de 30 min, y al tirar hacia abajo.

**Límite por hora.** Las noticias no cuentan como ocio, pero el panel desvela como máximo 5 artículos nuevos por hora, ajustable de 3 a 10. El resto espera en cola, sin scroll infinito, y la cabecera indica cuándo llegan los siguientes, por ejemplo «5 MÁS A LAS 19:00». Los ya desvelados siguen visibles hasta leerlos.

## Tokens de diseño

Todo el launcher usa estos valores; ningún componente define colores o tipografías propias.

| Token | Valor | Uso |
| --- | --- | --- |
| Fondo | #000000 | Toda la pantalla |
| Superficie | #111111 | Piezas, barra de búsqueda, tarjetas |
| Borde | #2A2A2A | Contorno de superficies |
| Borde fuerte | #4A4A4A | Panel congelado, píldoras sin seleccionar |
| Texto | #FFFFFF | Texto principal y puntos del reloj |
| Texto secundario | #9A9A9A | Etiquetas, fecha, nombres de app |
| Texto terciario | #B0B0B0 | Números de la semana |
| Ocio congelado | #8A8A8A | Nombre de la sección Ocio |
| Acento | #FF6A3D | Piezas de información, estado de Ocio, no leídos |
| Texto sobre acento | #000000 | Dentro de piezas de acento |

- **Acento configurable:** #FF6A3D por defecto; alternativas #D7FF3F, #7FD4FF y #FFFFFF.
- **Tipografías:** Space Grotesk 600 para nombres de sección y títulos de panel; JetBrains Mono 400, 500 y 700 para todo lo demás. Ambas tienen licencia abierta (OFL) y se empaquetan en `res/font`.
- **Etiquetas:** siempre en mayúsculas, mono 11 sp, espaciado entre 0,06 y 0,14 em.
- **Radios:** 18 dp en piezas y tarjetas, 24 dp en la barra de búsqueda, 22 dp en píldoras y botones.
- **Zona táctil mínima:** 44 dp.
- **Animaciones:** 250 ms con curva de salida suave. Si el sistema tiene las animaciones reducidas, se omiten.
- **Nunca:** degradados, sombras, desenfoques, iconos de color ni contadores de notificaciones.

## Arquitectura técnica

App Android de una sola actividad en Kotlin y Jetpack Compose, con `minSdk 33` y `targetSdk` en la última versión estable. Usa las versiones estables actuales de cada librería; comprueba cuáles son antes de fijarlas.

### Dependencias permitidas

- Compose (con Material 3 solo como base; el tema es propio), Lifecycle y ViewModel.
- DataStore para ajustes y estado pequeño.
- Room para artículos y feeds.
- Kotlin Coroutines y Flow; kotlinx.serialization.
- OkHttp para feeds y sugerencias.
- Sin framework de inyección de dependencias: inyección manual con un contenedor simple.
- Cualquier otra dependencia debe justificarse antes de añadirla.

### Paquetes

| Paquete | Responsabilidad |
| --- | --- |
| `ui.home` | Inicio: reloj, semana, secciones, mosaico |
| `ui.search` | Búsqueda universal |
| `ui.appdetail` | Ficha de app |
| `ui.widgets` | Panel de widgets |
| `ui.feeds` | Panel de noticias |
| `ui.settings` | Ajustes y primer arranque |
| `ui.theme` | Tokens, tipografías, componentes comunes |
| `data.apps` | Lista de apps instaladas y lanzamiento |
| `data.sections` | Asignación de apps a secciones |
| `data.leisure` | Presupuesto y sesiones de ocio |
| `data.usage` | Estadísticas de uso |
| `data.feeds` | Descarga, análisis y almacenamiento de feeds |
| `data.suggest` | Sugerencias de búsqueda |
| `domain` | Modelos y clasificador |
| `service` | Servicio de accesibilidad de bloqueo |

### Manifest

- `HomeActivity` con filtro `MAIN` + `HOME` + `DEFAULT`, `launchMode="singleTask"`, `excludeFromRecents="true"`, orientación vertical y `windowSoftInputMode="adjustResize"`.
- Bloque `<queries>` con el intent `MAIN` / `LAUNCHER` para ver todas las apps.
- Servicio de accesibilidad protegido con `BIND_ACCESSIBILITY_SERVICE` y su XML de configuración: solo `typeWindowStateChanged`, sin leer contenido de ventanas.

### Permisos

| Permiso | Para qué | Cómo se concede |
| --- | --- | --- |
| Servicio de accesibilidad | Bloqueo real de apps de ocio | A mano, en Ajustes |
| `PACKAGE_USAGE_STATS` | Tiempo de uso y almacenamiento por app | A mano, en «Acceso a datos de uso» |
| `INTERNET` | Feeds y sugerencias | Automático |
| `READ_CALENDAR` | Pieza de agenda | Diálogo, opcional |
| `REQUEST_DELETE_PACKAGES` | Lanzar la desinstalación | Automático |
| `BIND_APPWIDGET` (vía diálogo) | Alojar widgets | Diálogo del sistema por widget |

### APIs clave

- **Apps:** `LauncherApps.getProfiles()`, `getActivityList()`, `startMainActivity()` y `LauncherApps.Callback` para altas, bajas y cambios.
- **Categoría:** `ApplicationInfo.category`.
- **Uso:** `UsageStatsManager` (`queryUsageStats`, `queryEvents`) y `StorageStatsManager`.
- **Bloqueo:** `AccessibilityService` con `performGlobalAction(GLOBAL_ACTION_HOME)`.
- **Widgets:** `AppWidgetHost`, `AppWidgetManager` y `AppWidgetHostView` dentro de `AndroidView`.
- **Ficha y desinstalación:** `LauncherApps.startAppDetailsActivity()` e `Intent.ACTION_DELETE`.
- **Búsqueda web:** `Intent.ACTION_WEB_SEARCH`, o `ACTION_VIEW` con la URL del buscador.
- **Feeds:** `XmlPullParser` para RSS 2.0 y Atom.

### Convivencia con el sistema

- No tocar recientes ni gestos. Dibujar de borde a borde respetando `WindowInsets`.
- El gesto atrás en inicio no hace nada; en búsqueda, ficha y ajustes, vuelve.
- El botón de inicio (`onNewIntent`) cierra lo que haya abierto y vuelve a la página central.
- En Samsung, pedir al usuario que marque la app como «nunca suspender» en los ajustes de batería, para que el servicio de accesibilidad no se detenga.
- Si Android no deja activar la accesibilidad por ser una app instalada fuera de la tienda, hay que permitir los «ajustes restringidos» desde la información de la app. Comprueba el paso exacto en el dispositivo.

## Modelo de datos y clasificación

Las apps se reparten solas en secciones la primera vez y cada vez que se instala una nueva; lo que el usuario mueva a mano no se vuelve a tocar.

### Modelos

| Modelo | Campos | Dónde se guarda |
| --- | --- | --- |
| `AppEntry` | `packageName`, `activityName`, `userSerial`, `label`, `category`, `versionName` | En memoria, desde `LauncherApps` |
| `Section` | `WORK`, `TALK`, `MOVE`, `PERSONAL`, `LEISURE`, `NONE` | Enumerado |
| `AppConfig` | clave (`packageName` + `userSerial`), `section`, `order`, `inMosaic`, `code`, `highlight`, `showUsage`, `manual` | DataStore |
| `LeisureState` | `dailyBudgetMin`, `spentTodayMin`, `day`, `sessionStartedAt`, `sessionEndsAt` | DataStore |
| `Settings` | `accent`, `lastOpenSection`, `waitSeconds`, `suggestionsOn`, `suggestProvider`, `widgetsGrayscale`, `feedsPerHour` | DataStore |
| `HostedWidget` | `appWidgetId`, `provider`, `order`, `size` | DataStore |
| `Feed` | `id`, `url`, `title`, `lastFetchedAt` | Room |
| `Article` | `id`, `feedId`, `title`, `summary`, `url`, `imageUrl`, `publishedAt`, `read`, `starred`, revealedAt | Room |

### Reglas de clasificación, en orden

1. La app pertenece a un perfil distinto del principal (perfil de trabajo): **Trabajo**.
2. El paquete está en la lista interna de distracciones conocidas (redes sociales de consumo y vídeo corto): **Ocio**.
3. `category` es `GAME`, `VIDEO` o `NEWS`: **Ocio**.
4. `category` es `SOCIAL`: **Hablar**. Esta categoría incluye mensajería y correo, por eso no va a Ocio.
5. `category` es `MAPS`: **Moverse**.
6. `category` es `AUDIO`, `IMAGE` o `PRODUCTIVITY`: **Personal**.
7. Cualquier otra: **sin sección**. Aparece en el grupo Otros de la búsqueda.

- La lista de distracciones conocidas vive en un único fichero y se puede ampliar.
- Dentro de cada sección, entran al mosaico las primeras apps por orden hasta llenarlo; el resto queda con `inMosaic = false`.
- Una app con `manual = true` nunca se reclasifica.
- Las apps de un perfil en pausa (`UserManager.isQuietModeEnabled`) se muestran atenuadas y no se abren.

## Lógica de bloqueo

El launcher decide si el ocio está abierto y un servicio de accesibilidad lo hace cumplir cuando una app de ocio se abre por otra vía, como una notificación o la pantalla de recientes.

**Fuente de verdad:** `LeisureRepository`, sobre DataStore. El ocio está abierto si `sessionEndsAt` existe y es posterior a ahora.

### Transiciones

| Desde | Evento | Hacia | Efecto |
| --- | --- | --- | --- |
| Congelado | Pulsar Descongelar con presupuesto ≥ duración | Descongelando | Arranca la cuenta de espera |
| Descongelando | Fin de la espera | Abierto | Guarda `sessionStartedAt` y `sessionEndsAt` |
| Descongelando | Cancelar o salir del launcher | Congelado | Nada |
| Abierto | Pulsar Congelar | Congelado | Suma a `spentTodayMin` los minutos transcurridos, redondeados hacia arriba |
| Abierto | Se alcanza `sessionEndsAt` | Congelado | Igual, y vuelve a inicio si hay una app de ocio delante |
| Cualquiera | Cambia el día | El mismo | `spentTodayMin = 0` |

### Servicio de accesibilidad

1. Escucha solo `TYPE_WINDOW_STATE_CHANGED` y guarda el paquete en primer plano.
2. Ignora el propio launcher, la interfaz del sistema y los teclados.
3. Si el paquete es de ocio y el ocio no está abierto: ejecuta `GLOBAL_ACTION_HOME` y avisa al launcher para que abra la sección Ocio con el aviso «CONGELADA».
4. Al abrirse una sesión, programa un temporizador hasta `sessionEndsAt`. Al cumplirse, cierra la sesión y, si hay una app de ocio delante, vuelve a inicio.
5. Al arrancar el servicio, lee el estado guardado y reprograma o cierra la sesión pendiente.

### Casos límite

- **Reinicio del móvil con sesión abierta:** al arrancar, si `sessionEndsAt` ya pasó, se cierra y se contabiliza entera.
- **Sesión que cruza la medianoche:** cuenta contra el día en que empezó.
- **Servicio desactivado:** el launcher sigue sin abrir apps congeladas, pero no puede impedir otras entradas. El panel de Ocio muestra «BLOQUEO INACTIVO · ACTIVAR», que lleva al ajuste.
- **Noticias:** no dependen del estado de ocio; tienen su propio límite de artículos por hora.
- **Límite asumido:** el usuario puede desactivar el servicio y saltarse el bloqueo. Es fricción, no un candado.

## Plan por fases

Nueve fases en orden; cada una deja la app instalable y usable. No se empieza una fase hasta cumplir los criterios de la anterior.

### Fase 0: proyecto base

- Crear el proyecto, el tema con los tokens y las tipografías, y `HomeActivity` con el filtro de inicio.
- Dibujar el reloj de puntos, la fecha y la tira semanal.
- **Hecho cuando:** la app compila, se puede elegir como app de inicio y muestra la hora correcta, que cambia cada minuto.

### Fase 1: apps, secciones y mosaico

- `AppRepository` con `LauncherApps`, incluido el perfil de trabajo y las altas y bajas en vivo.
- Clasificador y `AppConfig` en DataStore.
- Secciones en acordeón y mosaico con piezas de app.
- **Hecho cuando:** las cinco secciones muestran apps reales, se abren al tocarlas (también las de trabajo) y la última sección abierta se recuerda tras reiniciar.

### Fase 2: búsqueda de apps

- Pantalla de búsqueda que se abre al tocar la barra, con la barra subiendo arriba, las apps agrupadas por categoría más el grupo Otros, filtro sin acentos y apertura con Intro.
- **Hecho cuando:** cualquier app instalada se abre en menos de tres toques desde inicio.

### Fase 3: ocio congelado

- `LeisureRepository`, máquina de estados y panel de Ocio con sus cuatro estados.
- El launcher no abre apps congeladas, tampoco desde la búsqueda.
- **Hecho cuando:** los tests de la máquina de estados pasan con un reloj falso y el presupuesto se repone al cambiar el día.

### Fase 4: bloqueo real

- Servicio de accesibilidad, temporizador de fin de sesión y pantalla de primer arranque que guía por los permisos.
- **Hecho cuando:** abrir una app congelada desde una notificación devuelve a inicio en menos de un segundo, y al acabar la sesión la app de ocio se cierra sola.

### Fase 5: ficha de app y ajustes

- Ficha con uso, gráfico de 7 días, almacenamiento, sección, código, destacado, info y desinstalar.
- Ajustes: presupuesto, espera, acento, orden de apps por sección.
- **Hecho cuando:** los tiempos de la ficha coinciden con los de Bienestar digital del sistema con un margen de un minuto.

### Fase 6: búsqueda universal

- Sugerencias web con espera de 250 ms, fila de apps coincidentes y lanzamiento de la búsqueda en el navegador.
- Ajuste para apagar las sugerencias y elegir su proveedor.
- **Hecho cuando:** con las sugerencias apagadas no sale ninguna petición de red al teclear.

### Fase 7: panel de widgets

- `AppWidgetHost`, selector de widgets, configuración, tamaños, orden, quitar y escala de grises.
- **Hecho cuando:** un widget de calendario y otro de terceros se añaden, se actualizan solos y sobreviven a un reinicio del móvil.

### Fase 8: panel de noticias y piezas de información

- Lector RSS y Atom con Room, filtros, leídos, guardados, gestión de feeds y límite de artículos por hora.
- Pieza de agenda con `CalendarContract` y pieza «Ir a casa» con un intent de navegación.
- **Hecho cuando:** un feed RSS y otro Atom se añaden por URL y muestran sus artículos, y la pieza de agenda muestra el próximo evento real.

## Pruebas

La lógica se cubre con tests unitarios y el comportamiento con el sistema se comprueba a mano en el Galaxy S25.

### Tests automáticos

- Clasificador: una app por cada regla, más el caso `manual = true`.
- Máquina de estados de ocio con reloj falso: todas las transiciones, cambio de día, sesión que cruza la medianoche y reinicio a mitad de sesión.
- Reloj de puntos: cada dígito produce los puntos de su matriz.
- Filtro de búsqueda: mayúsculas, acentos y subcadenas.
- Analizador de feeds: un RSS 2.0 y un Atom de ejemplo guardados en el repositorio.
- Agregación de uso: eventos de ejemplo convertidos en minutos por día.

### Lista manual en el S25

- [ ] Se elige como app de inicio y el gesto de inicio vuelve a ella desde cualquier app.
- [ ] La pantalla de recientes y el gesto atrás funcionan como con el launcher de Samsung.
- [ ] Las apps del perfil de trabajo se abren, y se atenúan al pausar el perfil.
- [ ] Instalar y desinstalar una app actualiza secciones y búsqueda sin reiniciar.
- [ ] Una app congelada abierta desde una notificación devuelve a inicio.
- [ ] Al terminar una sesión, la app de ocio en pantalla se cierra.
- [ ] Tras reiniciar el móvil, el presupuesto gastado se conserva y el servicio sigue activo.
- [ ] Tras una noche sin usar el móvil, el servicio de accesibilidad sigue activo.
- [ ] Los widgets se actualizan y responden al toque.
- [ ] Con las animaciones del sistema desactivadas, nada se mueve.

### Comandos

```text
./gradlew test
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Fuera de alcance

Nada de esta lista debe implementarse sin que el autor lo pida.

- Publicación en Play Store. El uso del servicio de accesibilidad para bloquear apps exigiría una revisión de políticas aparte.
- Arrastrar y soltar, carpetas, varias páginas de inicio y fondos de pantalla.
- Contadores o puntos de notificaciones. Se excluyen a propósito.
- Iconos reales de las apps. Posible ampliación: usar la capa monocroma de los iconos adaptativos como alternativa al código de dos letras.
- Resaltar u ocultar secciones según la hora, la ubicación o el calendario.
- Filtro de notificaciones y escala de grises global del sistema.
- Protección contra manipular la hora del sistema o desactivar el servicio.
- Copias de seguridad en la nube y sincronización entre dispositivos.
- Pieza de tareas conectada a una app concreta. Depende de qué gestor de tareas use el autor.

### Decisiones del autor

- **Buscador:** el que tenga el sistema por defecto.
- **Noticias:** no cuentan como ocio; máximo 5 artículos nuevos por hora.
- **Mosaicos:** se parte de la disposición inicial propuesta; el autor la ajustará desde la ficha de cada app.

## Cómo usar este plan con Claude Code

Guarda este documento como `PLAN.md` en la raíz de un repositorio vacío y trabaja una fase por sesión.

### Antes de empezar

- Tener instalados Android Studio o el SDK de Android por línea de comandos, y un JDK compatible con la versión actual del plugin de Android.
- Activar la depuración USB en el S25 y comprobar que `adb devices` lo lista.
- Exportar este documento a Markdown y copiarlo al repositorio.

### Mensaje inicial

```text
Lee PLAN.md entero antes de escribir código. Es la especificación de un launcher
de Android en Kotlin y Jetpack Compose.

Reglas de trabajo:
1. Implementa solo la Fase 0. No adelantes nada de fases posteriores.
2. Respeta los tokens de diseño y las medidas del plan. No añadas degradados,
   sombras, iconos de color ni nada de la sección "Descartado".
3. Usa solo las dependencias permitidas, en su última versión estable.
   Compruébalas; no las escribas de memoria.
4. Al terminar, ejecuta ./gradlew test y ./gradlew assembleDebug, y arregla
   lo que falle.
5. Crea un CLAUDE.md con los comandos de compilación, la estructura de paquetes
   y las convenciones que hayas seguido.
6. Si algo del plan es ambiguo o contradictorio, pregunta antes de decidir.
7. Termina con un resumen: qué has hecho, cómo comprobarlo en el móvil y
   qué criterio de "Hecho cuando" queda por verificar a mano.
```

### Para cada fase siguiente

```text
Lee PLAN.md y CLAUDE.md. La Fase N-1 está terminada y verificada.
Implementa la Fase N con las mismas reglas de trabajo.
```

### Consejos

- Haz un commit al cerrar cada fase, después de probarla en el móvil.
- Si una fase resulta grande para una sesión, pídele que la divida en pasos y los proponga antes de empezar. Las fases 4, 7 y 8 son las candidatas.
- Cuando algo se vea distinto de lo esperado, envíale una captura del móvil junto con el apartado del plan que no se cumple.
- Las fases 0 a 4 ya dan un launcher completo y utilizable; el resto son ampliaciones.
