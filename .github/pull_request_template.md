## What and why

<!-- Qué cambia y por qué. Link the issue if any. -->

## Checklist

- [ ] Opened an issue or draft PR first (for big changes)
- [ ] `./gradlew test assembleDebug` passes
- [ ] Tested on a device/emulator (Android 13+); I understand every line I submit (AI-assisted or not)
- [ ] Follows the package structure in CLAUDE.md; colors/radii/type only from `ui.theme`
- [ ] New texts are in `values/strings.xml` (ES) and `values-en/strings.xml` (EN)
- [ ] Compatible with existing configs: no renamed DataStore keys, `PersistJson` defaults, Room `Migration` if schema changed
- [ ] No new dependency (or it is justified below)
- [ ] Tests added only for non-trivial logic
- [ ] No keys, secrets or `local.properties`
- [ ] I accept my contribution is published under PolyForm Noncommercial 1.0.0
