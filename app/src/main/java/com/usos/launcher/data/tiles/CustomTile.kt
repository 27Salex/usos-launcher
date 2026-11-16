package com.usos.launcher.data.tiles

import com.usos.launcher.domain.AppEntry
import kotlinx.serialization.Serializable
import java.util.UUID

enum class TileKind { DEST, CONTACTS }

enum class ContactApp { WHATSAPP, TELEGRAM, SMS, CALL }

/** Icono de una pieza de destino. Se guarda su nombre (PIN, HOME...): no renombrar los valores. */
enum class DestinationIcon {
    PIN, HOME, WORK, CAR, PARKING, STAR, HEART;

    companion object {
        /** Icono guardado con [name]; si no existe (dato antiguo o de otra versión), la chincheta. */
        fun of(name: String): DestinationIcon = entries.firstOrNull { icon -> icon.name == name } ?: PIN
    }
}

@Serializable
data class TileContact(
    val name: String = "",
    val phone: String = "",
    val app: ContactApp = ContactApp.WHATSAPP,
    /** Chat elegido de los accesos directos de una app (grupos de WhatsApp, etc.); vacío = por teléfono. */
    val shortcutId: String = "",
    val shortcutPackage: String = "",
    /** Foto guardada en filesDir/tile_photos (copiada al elegir); vacío = iniciales. */
    val photo: String = "",
)

/** Pieza propia del mosaico ("app virtual"). Persistida como JSON (PersistJson); todo campo con valor por defecto. */
@Serializable
data class CustomTile(
    val id: String = "",
    val kind: TileKind = TileKind.DEST,
    val name: String = "",
    /** DEST: dirección y paquete de la app de navegación ("" = cualquiera). */
    val address: String = "",
    val appPackage: String = "",
    /** DEST: nombre de [DestinationIcon]. */
    val icon: String = "PIN",
    /** CONTACTS: 1..4 contactos. */
    val contacts: List<TileContact> = emptyList(),
)

object CustomTiles {
    const val PACKAGE_PREFIX = "usos.tile."
    const val MAX_CONTACTS = 4

    fun isVirtual(packageName: String): Boolean = packageName.startsWith(PACKAGE_PREFIX)

    fun idOf(packageName: String): String = packageName.removePrefix(PACKAGE_PREFIX)

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    /** Clave de AppConfig de una pieza (= AppEntry.configKey). */
    fun configKey(id: String): String = AppEntry.configKey(PACKAGE_PREFIX + id, -1L)
}

fun CustomTile.toAppEntry(): AppEntry = AppEntry(
    packageName = CustomTiles.PACKAGE_PREFIX + id,
    activityName = "",
    userSerial = -1L,
    label = name,
    category = -1,
    versionName = null,
)
