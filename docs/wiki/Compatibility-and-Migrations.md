# Compatibility and Migrations

Mandatory in every version: updating with `install -r` must never break or reset the user configuration.

- **DataStore:** never rename keys. New keys have defaults. Migrate old values (e.g. `HostedWidget.heightDp` -> cells; limit 200 -> 20).
- **JSON:** one shared `data.PersistJson` (`ignoreUnknownKeys`, `coerceInputValues`, `encodeDefaults`) for everything persisted; new fields have defaults. Test: `PersistJsonTest` (1.0.0 format).
- **Room (`feeds.db`):** bump the version with an explicit `Migration` (currently v2, `MIGRATION_1_2`). Never `fallbackToDestructiveMigration`. If the schema need not change, do not touch it (`Article.revealedAt` is unused for this reason).
- **Enums:** persisted enum names never change; unknown values fall back to a safe default (e.g. widget `kind` unknown -> ANDROID).
- **R8:** keep rules in `app/proguard-rules.pro` for `$$serializer`, Companion, enums and Room entities.
- **Locale:** stored by the system (`LocaleManager`), not DataStore.
- `versionCode` must always increase; the updater checks package, signing certificate and version code.
