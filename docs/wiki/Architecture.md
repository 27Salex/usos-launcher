# Architecture

Single module, package `com.usos.launcher`. No Material 3, no DI framework.

## Packages
| Package | Role |
|---|---|
| `ui.theme` | Design tokens, fonts, `UsosText`, `Modifier.tap`, shared glyphs/pickers |
| `ui.home` | Home (clock, week strip, sections accordion, tile mosaic), `HomeViewModel`, `Route` overlay (Home/Onboarding/Settings/Detail), `LeisurePanel`, edit mode |
| `ui.search` | `SearchFilter`, `SearchClassifier` (pure), `SearchOverlay` |
| `ui.appdetail`, `ui.settings` | App detail sheet; Settings and first-run guide |
| `ui.widgets` | Widgets page, grid logic, picker, own widgets |
| `ui.feeds` | News page and feed manager |
| `data.apps` | `AppRepository` (LauncherApps) |
| `data.sections` | `AppConfig`, assigner, initial layout, `MosaicPacker`, DataStore stores |
| `data.leisure` | `LeisureMachine` (pure), `LeisureRepository` |
| `data.feeds` | Room DB, `FeedParser`, `ReadTokens`, repository, fetcher, thumbnails |
| `data.usage`, `data.weather`, `data.info`, `data.icons`, `data.ai`, `data.suggest`, `data.update`, `data.background`, `data.notify` | Feature data layers |
| `domain` | `AppEntry`, `Section`, classifier and distraction list |
| `service` | `BlockerService` (AccessibilityService), `NotificationWatcher` |

## Data flow
System sources (LauncherApps, UsageEvents, Calendar, network) -> repositories (`data.*`, with DataStore/Room for persistence) -> `StateFlow`s -> `HomeViewModel` -> Compose UI. User actions go back through the ViewModel to the repositories and stores. Pure logic (`DotMatrix`, `DateText`, `SectionAssigner`, `SectionLayout`, `AppClassifier`, `LeisureMachine`, `ReadTokens`, `WidgetGrid`...) has no Android dependency and is unit tested with injected clocks.

## AppContainer
`AppContainer.kt` is manual dependency injection, created by `UsosApp`. It holds the repositories, stores, `updateManager`, `pendingAlert` (blocker -> launcher, same process, no intents) and `appContext`. ViewModels and the accessibility service take what they need from it.
