# Gestures and Editing

## Gestures
- Swipe up on home opens search (`Modifier.swipeVertical`, observes in the Initial pass without consuming). The axis is decided by `GestureAxis.decide` (pure, tested) after touch slop; it opens above 72 dp; ignored if it starts in the system gesture insets, or on the section list unless that cannot scroll further. Swipe down with the results at the top closes search.
- Pager: `userScrollEnabled = !editing && !widgetsEditing && !searchOpen` and nothing else (never toggle a lock mid-gesture). Opening search forces `requestScrollToPage(1)` without animation.
- Back closes overlay screens, same as the visible back arrow.

## Home editing
- Long press >= 500 ms on a tile (`Modifier.pressGestures`) or Settings > HOME > EDIT HOME enters edit mode (`HomeViewModel.editing`); exit with DONE, back or home.
- In edit mode tiles have dashed borders and the full grid shows ghost cells ("+" opens `AddPicker`). A tap selects a tile and shows floating bubbles (`BubbleRow`): RESIZE, SETTINGS, REMOVE.
- RESIZE shows handles (`ResizeHandles`; `MosaicEdit.snapSize` 1..2). Dragging the selected tile moves it with a live preview (`MosaicEdit.hoverCell`, `previewMove/previewResize`, pure); drop commits (`placeTile/resizeTile`). Dropping on another section name = manual section change; on REMOVE FROM HOME = `inMosaic` false.
- Resizing is never rejected: what does not fit is hidden, `inMosaic` untouched.
