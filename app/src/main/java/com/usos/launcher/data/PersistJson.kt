package com.usos.launcher.data

import kotlinx.serialization.json.Json

/**
 * Único Json de todo lo persistido (DataStore): tolera claves desconocidas y valores inválidos (se usa el por
 * defecto), para que actualizar la app nunca rompa la configuración guardada. Todo campo nuevo lleva valor por defecto.
 */
val PersistJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
