package com.arsniper.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.arsniper.ml.AnalysisCache
import java.io.File
import kotlin.math.max

/**
 * ספריית תמונות אחרונות: כל תמונה שאושרה נשמרת (עד MAX), כדי לשחק שוב בלי לצלם מחדש.
 * הניתוח של כל תמונה כבר נשמר בנפרד לפי תוכן הקובץ, אז טעינה חוזרת מהירה.
 */
object PhotoLibrary {

    const val MAX = 10

    private fun dir(context: Context) = File(context.filesDir, "library").apply { mkdirs() }

    /** שומר עותק של התמונה הנוכחית (אם עוד אין כזאת) ומנקה ישנות */
    fun addCurrent(context: Context) {
        try {
            val current = PhotoRepository.currentFile(context) ?: return
            val target = File(dir(context), AnalysisCache.keyFor(current) + ".jpg")
            if (!target.exists()) current.copyTo(target, overwrite = true)
            target.setLastModified(System.currentTimeMillis())
            val files = list(context)
            for (old in files.drop(MAX)) old.delete()
        } catch (_: Exception) {
        }
    }

    /** הקבצים השמורים, החדש ראשון */
    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension == "jpg" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** הופך תמונה שמורה לתמונה הנוכחית של המשחק */
    fun select(context: Context, file: File) {
        file.copyTo(File(context.filesDir, "arena_photo.jpg"), overwrite = true)
        file.setLastModified(System.currentTimeMillis())
    }

    fun thumbnail(file: File, maxSide: Int = 320): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
