package com.arsniper.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale

@Composable
fun BackgroundLayer(
    photo: ImageBitmap?,
    modifier: Modifier = Modifier,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
