package app.running.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Parte con red y con Android de las actualizaciones: consultar el último release, descargar el APK,
 * comprobarlo y abrir el instalador del sistema. Es el usuario quien confirma la instalación.
 */
object UpdateInstaller {
    /** Error con un mensaje apto para enseñar al usuario. */
    class UpdateException(message: String) : Exception(message)

    private const val MAX_JSON_BYTES = 1_000_000
    private const val MAX_APK_BYTES = 200L * 1024 * 1024
    private const val USER_AGENT = "Running-app-updater"

    suspend fun fetchLatest(url: String, allowInsecure: Boolean): UpdateCheck.Release = withContext(Dispatchers.IO) {
        val conn = open(url).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            when (val code = conn.responseCode) {
                200 -> {
                    val body = conn.inputStream.use { readLimited(it, MAX_JSON_BYTES) }.decodeToString()
                    UpdateCheck.parseRelease(body, allowInsecure)
                        ?: throw UpdateException("La última versión publicada no incluye un APK utilizable.")
                }
                404 -> throw UpdateException("Todavía no hay versiones publicadas.")
                403, 429 -> throw UpdateException("GitHub ha limitado las consultas. Prueba de nuevo más tarde.")
                else -> throw UpdateException("GitHub respondió con el error $code.")
            }
        } catch (e: IOException) {
            throw UpdateException("No se pudo conectar. Revisa tu conexión.")
        } finally {
            conn.disconnect()
        }
    }

    /** Descarga el APK a la caché, verificando tamaño y SHA-256 si se conocen. [onProgress] recibe 0..1 (null = tamaño desconocido). */
    suspend fun download(context: Context, release: UpdateCheck.Release, onProgress: (Float?) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // fuera descargas anteriores
            val target = File(dir, "running-${release.version}.apk")
            val part = File(dir, target.name + ".part")

            val conn = open(release.apkUrl)
            try {
                if (conn.responseCode != 200) throw UpdateException("No se pudo descargar el APK (error ${conn.responseCode}).")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize.takeIf { it > 0 }
                if (total != null && total > MAX_APK_BYTES) throw UpdateException("El APK es demasiado grande.")
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                val buffer = ByteArray(32 * 1024)
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            done += n
                            if (done > MAX_APK_BYTES) throw UpdateException("El APK es demasiado grande.")
                            onProgress(total?.let { (done.toFloat() / it).coerceAtMost(1f) })
                        }
                    }
                }
                if (release.apkSize > 0 && done != release.apkSize) {
                    throw UpdateException("La descarga quedó incompleta. Inténtalo de nuevo.")
                }
                val hex = digest.digest().joinToString("") { "%02x".format(it) }
                if (release.sha256 != null && hex != release.sha256) {
                    throw UpdateException("La descarga está corrupta (la suma de verificación no coincide).")
                }
                if (!part.renameTo(target)) throw UpdateException("No se pudo guardar el APK descargado.")
                target
            } catch (e: IOException) {
                throw UpdateException("Se interrumpió la descarga. Revisa tu conexión.")
            } finally {
                conn.disconnect()
                part.delete()
            }
        }

    /**
     * Comprobaciones previas a abrir el instalador, para dar un error claro en vez del genérico de Android:
     * que sea un APK de esta app, más nuevo que el instalado y firmado con la misma clave.
     */
    fun checkApk(context: Context, file: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else signaturesFlag()
        val archive = pm.getPackageArchiveInfo(file.path, flags)
            ?: throw UpdateException("El archivo descargado no es un APK válido.")
        if (archive.packageName != context.packageName) throw UpdateException("El APK descargado no es de esta app.")
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (code(archive) <= code(installed)) throw UpdateException("La versión descargada no es más nueva que la instalada.")
        val a = signers(archive)
        val b = signers(installed)
        if (a.isNotEmpty() && b.isNotEmpty() && a != b) {
            throw UpdateException(
                "El APK está firmado con otra clave y Android no lo instalaría como actualización. " +
                    "Haz una copia de seguridad, desinstala la app e instala la versión nueva.",
            )
        }
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Abre la pantalla de Android donde se concede a esta app el permiso «instalar apps desconocidas». */
    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Abre el instalador del sistema con el APK; el usuario confirma ahí la actualización. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", USER_AGENT)
    }

    private fun readLimited(input: java.io.InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > max) throw UpdateException("La respuesta de GitHub es demasiado grande.")
        }
        return out.toByteArray()
    }

    @Suppress("DEPRECATION")
    private fun signaturesFlag() = PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun code(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<Signature> =
        (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)?.toSet().orEmpty()
}
