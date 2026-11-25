package com.arsniper.ui.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * פגיעה "בעדשה" (שכבת מסך, מעל העולם): סלע שנזרק בקשת או טיל סודקים את הזכוכית, פצצה וטיל משאירים הבזק אש.
 * הציורים מ-ChatGPT על רקע שחור, מצוירים במיזוג Screen (השחור נעלם). x, y בשברי מסך.
 */
class LensImpact(val glass: Boolean, val x: Float, val y: Float, val bornNanos: Long, val rotation: Float, val scale: Float) {
    val seconds: Float get() = if (glass) GLASS_SECONDS else SCORCH_SECONDS

    companion object {
        const val GLASS_SECONDS = 2.4f
        const val SCORCH_SECONDS = 0.9f
    }
}

private class LensArt(val glass: ImageBitmap?, val scorch: ImageBitmap?)

private fun loadLensArt(context: Context): LensArt {
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    fun open(name: String): ImageBitmap? {
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (id == 0) return null
        return runCatching { BitmapFactory.decodeResource(context.resources, id, options)?.asImageBitmap() }.getOrNull()
    }
    return LensArt(open("fx_lens_glass"), open("fx_lens_scorch"))
}

@Composable
fun LensImpactOverlay(impacts: List<LensImpact>, nowNanos: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val art = remember { loadLensArt(context) }
    if (impacts.isEmpty()) return
    Canvas(modifier.fillMaxSize()) {
        for (fx in impacts) {
            val age = (nowNanos - fx.bornNanos) / 1e9f
            if (age < 0f || age > fx.seconds) continue
            val img = (if (fx.glass) art.glass else art.scorch) ?: continue
            val k = age / fx.seconds
            // זכוכית: מופיעה מיד ודועכת בשליש האחרון. אש: מתפשטת מהר ודועכת
            val alpha = if (fx.glass) (if (k < 0.66f) 1f else 1f - (k - 0.66f) / 0.34f) else (1f - k) * (1f - k)
            val grow = if (fx.glass) 0.92f + 0.08f * minOf(1f, age * 12f) else 0.6f + 0.7f * kotlin.math.sqrt(k)
            val side = size.minDimension * fx.scale * grow
            val c = Offset(fx.x * size.width, fx.y * size.height)
            rotate(fx.rotation, pivot = c) {
                drawImage(
                    image = img,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset((c.x - side / 2f).toInt(), (c.y - side / 2f).toInt()),
                    dstSize = IntSize(side.toInt(), side.toInt()),
                    alpha = alpha.coerceIn(0f, 1f),
                    blendMode = BlendMode.Screen
                )
            }
        }
    }
}
