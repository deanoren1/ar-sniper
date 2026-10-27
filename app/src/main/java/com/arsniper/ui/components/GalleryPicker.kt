package com.arsniper.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

/** בחירת תמונה מהגלריה של הטלפון */
class GalleryPicker(private val start: () -> Unit) {
    fun launch() = start()
}

/**
 * פותח את אפליקציית הגלריה של המכשיר (ACTION_PICK), שמסתובבת עם הטלפון.
 * אם אין אפליקציית גלריה, חוזר לבורר הקבצים של המערכת. onPicked מקבל את ה-Uri שנבחר.
 */
@Composable
fun rememberGalleryPicker(onPicked: (Uri) -> Unit): GalleryPicker {
    val callback = rememberUpdatedState(onPicked)
    val fallback = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) callback.value(uri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (uri != null) callback.value(uri)
    }
    return GalleryPicker {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply { type = "image/*" }
        try {
            gallery.launch(intent)
        } catch (e: Exception) {
            fallback.launch(arrayOf("image/*"))
        }
    }
}
