package com.arsniper.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

/** בחירת תמונה מהגלריה של הטלפון */
class GalleryPicker(private val start: () -> Unit) {
    fun launch() = start()
}

/**
 * בורר הקבצים של המערכת (OpenDocument), שמסתובב עם הטלפון.
 * ניסינו את בורר התמונות המודרני ואת אפליקציית הגלריה (ACTION_PICK), ושניהם לא הסתובבו
 * כשהמשחק נעול לרוחב. onPicked מקבל את ה-Uri שנבחר.
 */
@Composable
fun rememberGalleryPicker(onPicked: (Uri) -> Unit): GalleryPicker {
    val callback = rememberUpdatedState(onPicked)
    val fallback = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) callback.value(uri)
    }
    return GalleryPicker { fallback.launch(arrayOf("image/*")) }
}
