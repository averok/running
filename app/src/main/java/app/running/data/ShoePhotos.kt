package app.running.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Fotos de zapatillas. Se copian (reducidas) a almacenamiento interno de la app para que no dependan
 * de la galería y puedan incluirse en una exportación. En el modelo solo se guarda el nombre del archivo.
 */
object ShoePhotos {
    private const val DIR = "shoe-photos"
    private const val MAX_SIDE = 960

    fun dir(context: Context): File = File(context.filesDir, DIR).also { it.mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    /** Copia la imagen elegida a la carpeta interna. Devuelve el nombre del archivo, o null si no se pudo leer. */
    fun import(context: Context, uri: Uri): String? = runCatching {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longSide = max(bounds.outWidth, bounds.outHeight)
        require(longSide > 0)

        // Submuestreo grueso al decodificar para no cargar fotos de 12 MP enteras en memoria.
        var sample = 1
        while (longSide / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }!!

        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = (MAX_SIDE.toFloat() / max(decoded.width, decoded.height)).coerceAtMost(1f)
        val matrix = Matrix().apply { postRotate(degrees); postScale(scale, scale) }
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        val name = "${UUID.randomUUID()}.jpg"
        val target = file(context, name)
        try {
            target.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        name
    }.getOrNull()

    fun delete(context: Context, name: String?) {
        if (name != null) file(context, name).delete()
    }
}
