# Añadir un idioma

Español es el idioma por defecto (`res/values/`); inglés está en `res/values-en/`. Para añadir otro (ejemplo: francés, `fr`):

1. Copia `app/src/main/res/values-en/strings.xml` a `app/src/main/res/values-fr/strings.xml`.
2. Traduce los textos (no cambies `name=`, ni los marcadores `%1$s`/`%1$d`; usa las formas plurales de tu idioma en `<plurals>`).
   - Etiquetas en MAYÚSCULAS donde el original lo está. `feeds_language` decide el conjunto de feeds al instalar (`es`/`en`; otro valor usa el español), `maps_home_query` y `weather_web_query` son búsquedas reales (cámbialas por la palabra local).
   - `clock_date_format` fija el orden de la fecha: `%1$s` día de la semana, `%2$d` día del mes, `%3$s` mes.
3. Añade `<locale android:name="fr" />` a `app/src/main/res/xml/locales_config.xml`. Es el único sitio: el selector de Ajustes > SISTEMA > IDIOMA y Ajustes de Android > Idioma de la app leen esa lista.
4. `./gradlew test`: `TranslationsTest` falla si falta o sobra alguna clave, si cambian los marcadores o si `locales_config.xml` y las carpetas `values-xx` no coinciden.

No hace falta tocar código. Los nombres de día/mes y la primera letra de la semana salen de `java.time` en la locale.
