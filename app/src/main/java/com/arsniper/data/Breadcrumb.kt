package com.arsniper.data

import android.content.Context
import java.io.File

/**
 * סימון השלב האחרון שהושלם (לאבחון קריסות שלא נתפסות כחריגה, כמו נגמר הזיכרון או הריגת התהליך).
 * נכתב לקובץ בכל שלב, ומוצג במסך PLAY אחרי שהאפליקציה עולה מחדש.
 */
object Breadcrumb {
    private fun file(context: Context) = File(context.filesDir, "breadcrumb.txt")

    fun mark(context: Context, stage: String) {
        try {
            val rt = Runtime.getRuntime()
            val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            val nativeMb = android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
            file(context).writeText("$stage | heap ${usedMb}/${rt.maxMemory() / (1024 * 1024)}MB native ${nativeMb}MB")
        } catch (_: Throwable) {
        }
    }

    fun read(context: Context): String = try {
        val f = file(context)
        if (f.exists()) f.readText() else ""
    } catch (_: Throwable) {
        ""
    }
}
