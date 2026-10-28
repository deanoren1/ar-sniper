package com.arsniper.pano

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.arsniper.data.PhotoLibrary
import com.arsniper.data.PhotoRepository
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * הכנת פנורמה למשחק. שלב א (לפני הסקירה): מקטינים ושומרים כתמונה הנוכחית.
 * שלב ב (אחרי הסקירה): בפנורמה 360 מוסיפים בכל קצה עותק של הקצה השני, כדי שהציור יהיה רציף כשעוטפים;
 * ושומרים לצד התמונה את פרטי הפנורמה (מעגל, הרחבה, רוחב וגובה ברדיאנים).
 */
object PanoGame {

    private const val EXTEND = 0.2f

    fun prepare(context: Context, radius: Float): Boolean {
        return try {
            val full = PanoramaStore.load(context) ?: return false
            val is360 = PanoramaStore.is360(context)
            val widthFactor = if (is360) 1f + 2f * EXTEND else 1f
            val scale = minOf(
                1f,
                1080f / full.height,
                sqrt(3_600_000f / (full.width.toFloat() * full.height * widthFactor))
            )
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    full, (full.width * scale).roundToInt().coerceAtLeast(1), (full.height * scale).roundToInt().coerceAtLeast(1), true
                )
            } else {
                full
            }
            PanoramaStore.setPendingGame(context, is360, full.width / radius, full.height / radius)
            PhotoRepository.save(context, bitmap)
            PhotoLibrary.addCurrent(context, finalizing = false)
            true
        } catch (e: Throwable) {
            false
        }
    }

    /** נקרא כשהסקירה אושרה: מסיים את הפנורמה (אם יש כזאת ממתינה) ומוסיף את התמונה לספרייה כנסקרה */
    fun finalizeReview(context: Context) {
        val pending = PanoramaStore.pendingGame(context)
        if (pending == null) {
            PhotoLibrary.addCurrent(context, finalizing = true)
            return
        }
        try {
            val (is360, spanH, spanV) = pending
            val bmp = PhotoRepository.load(context)
            if (bmp == null || bmp.width.toFloat() / bmp.height < 2.6f) {
                PhotoLibrary.addCurrent(context, finalizing = true)
                return
            }
            var ring = 1f
            var ext = 0f
            var h = spanH
            if (is360) {
                val w = bmp.width
                val e = (w * EXTEND).toInt()
                val out = Bitmap.createBitmap(w + 2 * e, bmp.height, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)
                c.drawBitmap(Bitmap.createBitmap(bmp, w - e, 0, e, bmp.height), 0f, 0f, null)
                c.drawBitmap(bmp, e.toFloat(), 0f, null)
                c.drawBitmap(Bitmap.createBitmap(bmp, 0, 0, e, bmp.height), (w + e).toFloat(), 0f, null)
                ring = w.toFloat() / out.width
                ext = e.toFloat() / out.width
                h = spanH * out.width / w
                PhotoRepository.save(context, out)
            }
            PhotoLibrary.addCurrent(context, finalizing = true)
            PhotoLibrary.markPano(context, is360, ring, ext, h, spanV)
        } finally {
            PanoramaStore.clearPendingGame(context)
        }
    }
}
