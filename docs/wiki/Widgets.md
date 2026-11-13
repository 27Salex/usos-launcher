# Widgets

## Android widgets
- `AppWidgetHost` with fixed id `0x5501` (`UsosWidgetHost` creates `UsosHostView`); start/stopListening in `HomeActivity` onStart/onStop. Orphan host ids are deleted when `HomeViewModel` is created.
- `HostedWidget(id, heightDp legacy, cols, rows, col=-1, row=-1, kind, config)`; `cols/rows` are spans, `col/row` the top-left corner (-1 = unplaced).
- Free-position grid (`WidgetGrid`: `place`, `resize`, `normalize`, `firstFree`, tested): the moved widget stays where asked; overlapped ones are pushed down in cascade, never up or sideways, and nothing compacts. Columns are global (3/4), rows unlimited.
- Editing only via the EDIT button: tap selects, bubbles RESIZE / RECONFIGURE (only if `info.configure != null`) / REMOVE; drag moves.
- Picker (`WidgetPicker`): search field, one collapsible header per app, lazy previews (`previewLayout` -> `loadPreviewImage` -> icon). Grayscale optionally via a hardware layer (`widgetsGrayscale`).
- Migration: `WidgetSizing.resolve` turns legacy order-packed layouts into positions once.

## Own widgets
`ui.widgets.OwnWidgets/WidgetKind`: `kind` and `config` (`key=value;...`) in `HostedWidget`. They use negative ids (`OwnWidgets.nextId`), bypass the host, and share grid, editing and persistence. Minimum 2x1. Kinds: MUSIC (active `MediaSession` via `NotificationWatcher`, procedural `MusicBars`), CALENDAR (month + list when height >= 240 dp, else agenda), DOT CLOCK (`tz=` zone). The picker shows a "USOS" group first with sample previews.
