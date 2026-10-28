package com.arsniper.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ייבוא תמונה שנבחרה מהגלריה, ברמת התהליך ולא ברמת המסך.
 * כשחוזרים מהגלריה האפליקציה יכולה להיבנות מחדש (שינוי כיוון המסך): עבודה שחיה במסך נקטעת באמצע,
 * והמעבר למסך הסקירה לא קורה. כאן העבודה והתוצאה שורדות, והמסך החדש ממשיך מהתוצאה.
 */
object PickImport {
    const val IDLE = 0
    const val BUSY = 1
    const val OK = 2
    const val FAILED = 3

    private val stateHolder = mutableStateOf(IDLE)
    val state: Int get() = stateHolder.value

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context, uri: Uri) {
        if (state == BUSY) return
        val app = context.applicationContext
        stateHolder.value = BUSY
        scope.launch {
            val ok = try {
                PhotoImport.importFromUri(app, uri).also { if (it) PhotoLibrary.addCurrent(app) }
            } catch (t: Throwable) {
                PhotoImport.lastError = t.toString()
                false
            }
            stateHolder.value = if (ok) OK else FAILED
        }
    }

    /** המסך לקח את התוצאה */
    fun consume() {
        stateHolder.value = IDLE
    }
}
