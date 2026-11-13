# Design Tokens

- Colors, radii and typography come only from `ui.theme`. No shadows, no gradients (the only one lives in `ColorPicker`), no colored ripple.
- Surfaces: `#111111` cards with `#2A2A2A` border, radius 18; pills border `#4A4A4A`, radius 22, 36 dp.
- Accent: 4 presets plus custom (`ColorPicker`, HEX, 8 recent colors). `LocalOnAccent` (black or white by WCAG contrast, `ColorMath`) replaces any fixed on-accent color.
- Fonts: Space Grotesk (variable, wght fixed to 600) and JetBrains Mono (static). Optional `FontChoice` families are substituted by `TextStyle.themed()` inside `UsosText` and `BasicTextField`. Font licenses (OFL) in `THIRD_PARTY_NOTICES.md`.
- Animations: `usosTween(LocalAnimationsEnabled.current)` (250 ms, snap when system animations are off).
- Glyphs: `SettingsGlyph` (dot gear, 44 dp), `BackGlyph`, `AiGlyph`; screens use `ScreenHeader(title, onBack)`.
- Tile sizes `TileSize` 1x1/2x1/1x2/2x2; cell height 95 dp with 3 columns, proportional (min 76) with 4.
- Background: black by default; optional color/image/system (`AppBackground`), surfaces unchanged.
