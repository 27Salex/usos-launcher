# Contributing to Usos

Thanks for helping. Resumen en español al final.

## Workflow

- **Every change goes through a Pull Request.** No direct pushes to `main`.
- For big changes (new feature, new screen, refactor, new dependency), open an issue or a **draft PR** first so we can agree on the approach before you invest time.
- Any kind of contribution is welcome (fixes, features, translations, docs, design) as long as it follows the project structure below.

## AI tools

Using AI (Claude, Copilot, etc.) is allowed. The person who submits the PR is responsible for it: you must understand and have tested what you send. PRs that were clearly not read or run by their author will be closed.

## Project rules

- **Packages:** keep the layout described in [CLAUDE.md](CLAUDE.md) (`ui.*`, `data.*`, `domain`, `service`, `AppContainer`). Pure logic is separate from Android.
- **Design tokens:** colors, radii and typography only from `ui.theme`. No shadows, gradients (except the color picker) or colored ripples.
- **Strings:** no visible text in `.kt` files. Add every string to `res/values/strings.xml` (Spanish, default) **and** `res/values-en/strings.xml`.
- **Compatibility (mandatory):** updating must never break or reset a user's configuration.
  - DataStore: never rename keys; new keys have defaults; migrate old values.
  - JSON: use the single `data.PersistJson`; new fields get default values.
  - Room: bump the version with an explicit `Migration`, never `fallbackToDestructiveMigration`.
  - R8: keep rules in `app/proguard-rules.pro` for new serializable/Room types.
- **Dependencies:** none new without a justification in the PR (size, license, why the platform is not enough). Pinned versions are listed in CLAUDE.md.
- **Tests:** only for non-trivial logic (pure classes, migrations, parsers). No UI tests.
- **No secrets:** never commit keys, keystores, tokens or `local.properties`.

## Before opening the PR

```
./gradlew test assembleDebug
```

Both must pass. Test the change on a device or emulator (Android 13+).

## Commit style

Short imperative subject in the project's usual form, e.g. `Feeds: fix duplicate articles on refresh` or `Docs: update wiki`. One logical change per commit; explain the why in the body when it is not obvious.

## Translating

See [TRANSLATING.md](TRANSLATING.md). No code changes are needed to add a language.

## License

By contributing you agree that your contribution is published under the [PolyForm Noncommercial License 1.0.0](LICENSE), the same as the project.

## Wiki

Developer docs: [wiki](https://github.com/27Salex/usos-launcher/wiki) (sources in [docs/wiki](docs/wiki)).

---

## Resumen en español

Todo cambio entra por Pull Request (nada de push directo a `main`); abre antes un issue o borrador de PR para cambios grandes. Se permite IA, pero quien envía el PR responde de él: debe entenderlo y probarlo. Respeta la estructura (paquetes de CLAUDE.md, tokens en `ui.theme`, textos en `strings.xml` ES y EN, compatibilidad con migraciones de DataStore/Room y `PersistJson`, sin dependencias nuevas sin justificar, tests solo para lógica no trivial). Antes del PR ejecuta `./gradlew test assembleDebug`. Sin claves ni secretos. La contribución se publica bajo PolyForm Noncommercial 1.0.0. Traducir: [TRANSLATING.md](TRANSLATING.md).
