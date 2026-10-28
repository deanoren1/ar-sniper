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

    const val MAX = 40

    private fun dir(context: Context) = File(context.filesDir, "library").apply { mkdirs() }

    /** מפתח התמונה שנשמרה מיד אחרי בחירה מהגלריה, לפני שעברה סקירת אנשים (תוחלף בגרסה המאושרת) */
    @Volatile private var pendingKey: String? = null

    fun clearPending() { pendingKey = null }

    /**
     * שומר עותק של התמונה הנוכחית (אם עוד אין כזאת) ומנקה ישנות.
     * finalizing = התמונה עברה סקירת אנשים ואושרה: מסומנת כסקורה, ומחליפה את העותק שנשמר לפני הסקירה.
     */
    fun addCurrent(context: Context, finalizing: Boolean = false) {
        try {
            val current = PhotoRepository.currentFile(context) ?: return
            val key = AnalysisCache.keyFor(current)
            val target = File(dir(context), "$key.jpg")
            if (!target.exists()) current.copyTo(target, overwrite = true)
            target.setLastModified(System.currentTimeMillis())
            if (finalizing) {
                val old = pendingKey
                if (old != null && old != key) {
                    File(dir(context), "$old.jpg").delete()
                    File(dir(context), "$old.ok").delete()
                    File(dir(context), "$old.pano").delete()
                }
                pendingKey = null
                File(dir(context), "$key.ok").writeText("2")
            } else {
                pendingKey = key
            }
            val files = list(context)
            for (old in files.drop(MAX)) delete(old)
        } catch (_: Exception) {
        }
    }

    /** האם התמונה עברה סקירת אנשים (חובה לפני משחק) */
    fun isReviewed(file: File): Boolean {
        val marker = File(file.parentFile, file.nameWithoutExtension + ".ok")
        if (!marker.exists()) return false
        // פנורמות שסומנו כנסקרו בגרסה הישנה (בלי סקירה אמיתית) חייבות לעבור סקירה מחדש
        if (panoLabel(file) != null) return try { marker.readText().trim() == "2" } catch (_: Exception) { false }
        return true
    }

    /** הקבצים השמורים, החדש ראשון */
    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension == "jpg" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun delete(file: File) {
        try {
            file.delete()
            File(file.parentFile, file.nameWithoutExtension + ".ok").delete()
            File(file.parentFile, file.nameWithoutExtension + ".pano").delete()
        } catch (_: Exception) { }
    }

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

    /** מסמן את התמונה הנוכחית בספרייה כפנורמה (180 או 360 מעלות) */
    fun markPano(context: Context, is360: Boolean, ring: Float = 1f, ext: Float = 0f, spanH: Float = 0f, spanV: Float = 0f) {
        try {
            val current = PhotoRepository.currentFile(context) ?: return
            val key = AnalysisCache.keyFor(current)
            File(dir(context), "$key.pano").writeText("${if (is360) "360" else "180"}|$ring|$ext|$spanH|$spanV")
        } catch (_: Exception) {
        }
    }

    /** "180" / "360" לפנורמה שצולמה באפליקציה, "PANO" לתמונה רחבה מהגלריה, אחרת null */
    fun panoLabel(file: File): String? {
        try {
            val marker = File(file.parentFile, file.nameWithoutExtension + ".pano")
            if (marker.exists()) return marker.readText().trim().substringBefore('|')
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outHeight > 0 && bounds.outWidth.toFloat() / bounds.outHeight >= 2.6f) return "PANO"
        } catch (_: Exception) {
        }
        return null
    }

    /** פרטי הפנורמה של התמונה הנוכחית: ring = כמה מרוחב העולם הוא המעגל המלא (1 = בלי עטיפה), ext = ההרחבה בכל קצה, ורוחב/גובה ברדיאנים */
    class PanoInfo(val is360: Boolean, val ring: Float, val ext: Float, val spanH: Float, val spanV: Float)

    fun panoInfo(context: Context): PanoInfo? {
        try {
            val current = PhotoRepository.currentFile(context) ?: return null
            val marker = File(dir(context), AnalysisCache.keyFor(current) + ".pano")
            if (!marker.exists()) return null
            val t = marker.readText().trim().split('|')
            val spanH = t.getOrNull(3)?.toFloatOrNull() ?: 0f
            if (t.size < 5 || spanH <= 0f) return null
            return PanoInfo(t[0] == "360", t[1].toFloat(), t[2].toFloat(), spanH, t[4].toFloat())
        } catch (_: Exception) {
            return null
        }
    }
}
