package app.running.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Lógica pura (sin Android) para decidir si hay una versión nueva a partir de la respuesta de la API de
 * releases de GitHub. Separada de [UpdateInstaller] para poder probarla con tests unitarios.
 */
object UpdateCheck {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GhAsset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
        val size: Long = 0,
        /** "sha256:<hex>" (GitHub lo calcula al subir el archivo). */
        val digest: String? = null,
    )

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tag: String,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GhAsset> = emptyList(),
    )

    data class Release(
        /** Versión normalizada, p. ej. "0.3.0" (sin la "v" del tag). */
        val version: String,
        val notes: String,
        val apkName: String,
        val apkUrl: String,
        val apkSize: Long,
        /** Suma SHA-256 en minúsculas si GitHub la ofrece; si no, null. */
        val sha256: String?,
    )

    private val VERSION = Regex("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?$")
    private val SHA256 = Regex("^[0-9a-f]{64}$")

    /** "v0.3.0" → [0, 3, 0]. Null si no es una versión de la forma x.y o x.y.z. */
    fun parseVersion(text: String): List<Int>? {
        val m = VERSION.matchEntire(text.trim()) ?: return null
        return listOf(m.groupValues[1], m.groupValues[2], m.groupValues[3].ifEmpty { "0" }).map { it.toIntOrNull() ?: return null }
    }

    /** ¿[candidate] es estrictamente más nueva que [current]? Si alguna no se entiende, false. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parseVersion(candidate) ?: return false
        val b = parseVersion(current) ?: return false
        for (i in 0 until 3) {
            if (a[i] != b[i]) return a[i] > b[i]
        }
        return false
    }

    /**
     * Interpreta la respuesta de `/releases/latest`. Devuelve null si no sirve: JSON inválido, borrador,
     * prerelease, versión ilegible o sin ningún APK descargable. Solo acepta URLs https salvo que
     * [allowInsecureUrls] (para probar en debug con un servidor local).
     */
    fun parseRelease(body: String, allowInsecureUrls: Boolean = false): Release? {
        val gh = runCatching { json.decodeFromString(GhRelease.serializer(), body) }.getOrNull() ?: return null
        if (gh.draft || gh.prerelease) return null
        val version = parseVersion(gh.tag)?.joinToString(".") ?: return null
        val asset = gh.assets.firstOrNull {
            it.name.endsWith(".apk", ignoreCase = true) &&
                (it.url.startsWith("https://") || (allowInsecureUrls && it.url.startsWith("http://")))
        } ?: return null
        val sha = asset.digest?.removePrefix("sha256:")?.lowercase()?.takeIf { SHA256.matches(it) }
        return Release(
            version = version,
            notes = gh.body.orEmpty().trim(),
            apkName = asset.name,
            apkUrl = asset.url,
            apkSize = asset.size,
            sha256 = sha,
        )
    }
}
