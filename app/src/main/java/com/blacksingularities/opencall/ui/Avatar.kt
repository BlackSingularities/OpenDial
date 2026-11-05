package com.blacksingularities.opencall.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

/** Zdjęcia kontaktów w pamięci – lista przewija się płynnie bez ponownego dekodowania. */
private val photoCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

fun clearPhotoCache() = photoCache.evictAll()

/** Okrągły awatar: zdjęcie kontaktu albo inicjały na stonowanym tle (kolor stały dla nazwy). */
@Composable
fun Avatar(name: String?, photoUri: String?, size: Dp, modifier: Modifier = Modifier, business: Boolean = false) {
    val context = LocalContext.current
    var bmp by remember(photoUri) { mutableStateOf<ImageBitmap?>(photoUri?.let { photoCache.get(it)?.asImageBitmap() }) }
    LaunchedEffect(photoUri) {
        if (photoUri == null || bmp != null) return@LaunchedEffect
        val b = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
        if (b != null) {
            photoCache.put(photoUri, b)
            bmp = b.asImageBitmap()
        }
    }
    val label = name?.trim().orEmpty()
    val tint = Palette.AvatarTints[(label.hashCode().absoluteValue) % Palette.AvatarTints.size]
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (bmp != null) Color.Transparent else tint),
        contentAlignment = Alignment.Center,
    ) {
        val image = bmp
        when {
            image != null -> Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            business -> Icon(Icons.Rounded.Business, null, tint = Palette.TextDim, modifier = Modifier.size(size * 0.5f))
            label.isEmpty() || !label.first().isLetter() -> Icon(Icons.Rounded.Person, null, tint = Palette.TextDim, modifier = Modifier.size(size * 0.55f))
            else -> Text(
                PhoneText.initials(label),
                color = Palette.Fg,
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
