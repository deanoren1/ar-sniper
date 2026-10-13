package com.arsniper.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object PhotoRepository {

    private const val FILE_NAME = "arena_photo.jpg"

    private fun photoFile(context: Context) = File(context.filesDir, FILE_NAME)

    /** קובץ התמונה הנוכחית (אם קיים): משמש לזיהוי תמונה שכבר נותחה */
    fun currentFile(context: Context): File? = photoFile(context).takeIf { it.exists() }

    fun save(context: Context, bitmap: Bitmap) {
        photoFile(context).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
    }

    fun load(context: Context): Bitmap? {
        val file = photoFile(context)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }
}