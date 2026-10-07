package com.arsniper.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.arsniper.data.PhotoRepository

@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val photo = remember { PhotoRepository.load(context)?.asImageBitmap() }

    Box(modifier = modifier.fillMaxSize()) {
        BackgroundLayer(photo = photo)
        CrosshairOverlay()
    }
}
