# Translating

Spanish is the default (`res/values/`); English is `res/values-en/`. To add a language (example `fr`):

1. Copy `app/src/main/res/values-en/strings.xml` to `app/src/main/res/values-fr/strings.xml`.
2. Translate, keeping `name=` and placeholders (`%1$s`, `%1$d`); use the plural forms of your language. Keep UPPERCASE where the original is. `feeds_language` selects the feed set (`es`/`en`); `maps_home_query` and `weather_web_query` are real searches.
3. Add `<locale android:name="fr" />` to `app/src/main/res/xml/locales_config.xml` (the only place the language picker reads).
4. `./gradlew test`: `TranslationsTest` fails on missing or extra keys, changed placeholders, or a mismatch between folders and `locales_config.xml`.

Full guide: [TRANSLATING.md](https://github.com/27Salex/usos-launcher/blob/main/TRANSLATING.md).
