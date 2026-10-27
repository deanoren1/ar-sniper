package com.arsniper.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** מצב אחרון של הבורר (לאבחון על המסך): מה חזר מהגלריה */
object PickerDebug {
    var text: String by androidx.compose.runtime.mutableStateOf("")
}

/** בחירת תמונה מהטלפון */
class GalleryPicker(private val start: () -> Unit) {
    fun launch() = start()
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * בורר התמונות של המערכת. מסך הבחירה לא מגדיר כיוון משלו, ולכן הוא מקבל את הכיוון של המשחק (רוחב בלבד).
 * כדי שיסתובב עם הטלפון משחררים את הנעילה בזמן הבחירה, ומחזירים אותה כשחוזרים (גם בביטול).
 * onPicked מקבל את ה-Uri שנבחר.
 */
@Composable
fun rememberGalleryPicker(onPicked: (Uri) -> Unit): GalleryPicker {
    val callback = rememberUpdatedState(onPicked)
    val activity = LocalContext.current.findActivity()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        PickerDebug.text = "picker: code=${result.resultCode} data=${result.data?.data} clip=${result.data?.clipData?.itemCount}"
        val uri = if (result.resultCode == Activity.RESULT_OK) (result.data?.data ?: result.data?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri) else null
        if (uri != null) callback.value(uri)
    }
    return GalleryPicker {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        // אפליקציית הגלריה עצמה (סמסונג קודם), ולא בורר המסמכים שמציג את גוגל תמונות/דרייב
        // GET_CONTENT (ולא PICK): אפליקציית הגלריה מחזירה כתובת עם הרשאת קריאה, בלי צורך בהרשאת מדיה
        fun pick() = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val attempts = listOf(
            pick().setPackage("com.sec.android.gallery3d"),
            pick()
        )
        var started = false
        for (intent in attempts) {
            try {
                launcher.launch(intent)
                started = true
                break
            } catch (e: Exception) {
                // מנסים את הבא
            }
        }
        if (!started) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}
