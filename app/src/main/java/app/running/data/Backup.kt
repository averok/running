package app.running.data

import android.content.Context
import app.running.model.Session
import app.running.model.Shoe
import app.running.model.Workout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Configuración JSON común a los archivos de la app y a las copias de seguridad. */
object AppJson {
    val instance = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        classDiscriminator = "_t"
    }
}

/**
 * Copia de seguridad en un ZIP: `backup.json` (manifiesto con la versión del esquema), `workouts.json`,
 * `shoes.json`, `sessions.json` y `shoe-photos/<archivo>.jpg`.
 *
 * Al subir [SCHEMA_VERSION] (cambio incompatible del formato), `read` debe migrar las copias antiguas.
 */
object Backup {
    const val SCHEMA_VERSION = 1

    private const val MANIFEST = "backup.json"
    private const val WORKOUTS = "workouts.json"
    private const val SHOES = "shoes.json"
    private const val SESSIONS = "sessions.json"
    private const val PHOTOS_PREFIX = "shoe-photos/"
    private const val MAX_ENTRY_BYTES = 10L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 100L * 1024 * 1024
    private val DATA_FILES = setOf(MANIFEST, WORKOUTS, SHOES, SESSIONS)

    /** Nombre de foto aceptable: sin separadores de ruta ni `..`, para no escribir fuera de la carpeta de fotos. */
    private val PHOTO_NAME = Regex("^[A-Za-z0-9_-][A-Za-z0-9._-]*\\.jpg$")

    fun isSafePhotoName(name: String) = PHOTO_NAME.matches(name)

    @Serializable
    private data class Manifest(val schemaVersion: Int, val appVersion: String, val exportedAt: String)

    class Contents(
        val workouts: List<Workout>,
        val shoes: List<Shoe>,
        val sessions: List<Session>,
        /** Fotos referenciadas por alguna zapatilla, por nombre de archivo. */
        val photos: Map<String, ByteArray>,
    )

    /** Error con un mensaje apto para enseñar al usuario. */
    class BackupException(message: String) : Exception(message)

    fun write(
        context: Context,
        out: OutputStream,
        appVersion: String,
        workouts: List<Workout>,
        shoes: List<Shoe>,
        sessions: List<Session>,
    ) {
        val json = AppJson.instance
        ZipOutputStream(out.buffered()).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            val manifest = Manifest(SCHEMA_VERSION, appVersion, Instant.now().toString())
            put(MANIFEST, json.encodeToString(Manifest.serializer(), manifest).toByteArray())
            put(WORKOUTS, json.encodeToString(ListSerializer(Workout.serializer()), workouts).toByteArray())
            put(SHOES, json.encodeToString(ListSerializer(Shoe.serializer()), shoes).toByteArray())
            put(SESSIONS, json.encodeToString(ListSerializer(Session.serializer()), sessions).toByteArray())
            shoes.mapNotNull { it.photo }.distinct().forEach { name ->
                val file = ShoePhotos.file(context, name)
                if (isSafePhotoName(name) && file.exists()) put(PHOTOS_PREFIX + name, file.readBytes())
            }
        }
    }

    /** Lee y valida la copia entera sin tocar los datos de la app. Lanza [BackupException] si no sirve. */
    fun read(input: InputStream): Contents {
        val files = HashMap<String, ByteArray>()
        val photos = HashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val photoName = entry.name.removePrefix(PHOTOS_PREFIX).takeIf { entry.name.startsWith(PHOTOS_PREFIX) }
                    val isPhoto = photoName != null && isSafePhotoName(photoName)
                    // Cualquier otra entrada (incluidas rutas sospechosas como "../x") se ignora sin leerla.
                    if (!isPhoto && entry.name !in DATA_FILES) continue
                    val bytes = readLimited(zip)
                    total += bytes.size
                    if (total > MAX_TOTAL_BYTES) throw BackupException("La copia es demasiado grande.")
                    if (isPhoto) photos[photoName!!] = bytes else files[entry.name] = bytes
                }
            }
        } catch (e: java.util.zip.ZipException) {
            // Android 14+ rechaza por sí mismo los ZIP con rutas tipo "../" ("Invalid zip entry path").
            // En versiones anteriores esas entradas llegan hasta el filtro de arriba, que las ignora.
            throw BackupException(
                if (e.message?.contains("entry path", ignoreCase = true) == true) "La copia contiene rutas no válidas y se ha rechazado."
                else "El archivo ZIP está dañado.",
            )
        } catch (e: SecurityException) {
            throw BackupException("La copia contiene rutas no válidas y se ha rechazado.")
        }

        val manifestBytes = files[MANIFEST]
            ?: throw BackupException("Este archivo no es una copia de seguridad de Running.")
        val manifest = parse(Manifest.serializer(), manifestBytes)
        if (manifest.schemaVersion > SCHEMA_VERSION) {
            throw BackupException("La copia se creó con una versión más reciente de la app. Actualiza la app e inténtalo de nuevo.")
        }

        fun <T> list(name: String, serializer: KSerializer<T>): List<T> =
            parse(ListSerializer(serializer), files[name] ?: throw BackupException("Falta $name en la copia."))

        val shoes = list(SHOES, Shoe.serializer()).map { shoe ->
            val photo = shoe.photo
            // Una referencia insegura o sin archivo en la copia se descarta.
            if (photo != null && (!isSafePhotoName(photo) || photo !in photos)) shoe.copy(photo = null) else shoe
        }
        val used = shoes.mapNotNull { it.photo }.toSet()
        return Contents(
            workouts = list(WORKOUTS, Workout.serializer()),
            shoes = shoes,
            sessions = list(SESSIONS, Session.serializer()),
            photos = photos.filterKeys { it in used },
        )
    }

    private fun readLimited(zip: ZipInputStream): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val n = zip.read(chunk)
            if (n < 0) break
            buffer.write(chunk, 0, n)
            if (buffer.size() > MAX_ENTRY_BYTES) throw BackupException("La copia contiene un archivo demasiado grande.")
        }
        return buffer.toByteArray()
    }

    private fun <T> parse(serializer: KSerializer<T>, bytes: ByteArray): T = try {
        AppJson.instance.decodeFromString(serializer, bytes.decodeToString())
    } catch (e: SerializationException) {
        throw BackupException("La copia está dañada o no es compatible.")
    } catch (e: IllegalArgumentException) {
        throw BackupException("La copia está dañada o no es compatible.")
    }
}
