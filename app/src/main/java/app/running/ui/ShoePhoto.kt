package app.running.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.running.data.ShoePhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Foto de una zapatilla; si no hay (o no se puede leer) muestra un icono sobre fondo gris. */
@Composable
fun ShoePhoto(photo: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, photo) {
        value = if (photo == null) null else withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(ShoePhotos.file(context, photo).path)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val img = bitmap
        if (img != null) {
            Image(img, contentDescription = "Foto de la zapatilla", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.Hiking, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
