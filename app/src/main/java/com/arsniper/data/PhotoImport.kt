package com.arsniper.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlin.math.max

/**
 * ייבוא תמונה מהגלריה לזירה: קורא את התמונה, מתקן סיבוב לפי EXIF, מקטין לגודל שמתאים לצילום
 * מהמצלמה (הצלע הארוכה עד 1920), ושומר באותו מקום שבו נשמרת תמונה מהמצלמה.
 */
object PhotoImport {

    private const val TAG = "PhotoImport"
    private const val MAX_SIDE = 1920

    /** סיבת הכישלון האחרון (לאבחון) */
    var lastError: String = ""

    /** מחזיר true אם התמונה נקראה ונשמרה */
    fun importFromUri(context: Context, uri: Uri): Boolean {
        try {
            val resolver = context.contentResolver

            // קודם רק הגודל, כדי לקרוא את התמונה כבר מוקטנת ולא לבזבז זיכרון
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = resolver.openInputStream(uri) ?: return false
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decodeStream = resolver.openInputStream(uri) ?: return false
            val decoded = decodeStream.use { BitmapFactory.decodeStream(it, null, options) } ?: return false

            // כיוון התמונה לפי EXIF (תמונות מהגלריה לרוב שמורות מסובבות)
            var orientation = ExifInterface.ORIENTATION_NORMAL
            val exifStream = resolver.openInputStream(uri)
            if (exifStream != null) {
                exifStream.use {
                    orientation = ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            }

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }
                else -> {}
            }

            // אחרי הקטנה בחזקות של 2 הצלע הארוכה עדיין עד פי 2 מהיעד: מקטינים לגודל המדויק
            val longSide = max(decoded.width, decoded.height)
            if (longSide > MAX_SIDE) {
                val factor = MAX_SIDE.toFloat() / longSide
                matrix.postScale(factor, factor)
            }

            val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            PhotoRepository.save(context, bitmap)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import photo", e)
            lastError = e.toString().take(160)
            return false
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Photo too large", e)
            return false
        }
    }
}
