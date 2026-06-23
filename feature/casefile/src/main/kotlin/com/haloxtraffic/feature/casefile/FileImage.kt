package com.haloxtraffic.feature.casefile

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.haloxtraffic.core.designsystem.theme.HaloxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads a sealed-evidence JPEG off the main thread and renders it; placeholder if missing. */
@Composable
fun FileImage(path: String?, modifier: Modifier = Modifier) {
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = path?.let { p ->
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(p)?.asImageBitmap() }.getOrNull() }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = "evidence image", modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(HaloxTheme.colors.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.BrokenImage, contentDescription = null, tint = HaloxTheme.colors.inkFaint)
        }
    }
}
