package com.arsniper.ml

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * שמירת תוצאת ניתוח הסצנה בקובץ, לפי תוכן התמונה: אותה תמונה (גם אם בחרת אותה שוב מהגלריה)
 * לא מנותחת מחדש, והניתוח נטען תוך שניות. בלי תלות ב-Android: מקבל תיקייה וקובץ.
 */
object AnalysisCache {

    /** מעלים כשמשנים את אופן הניתוח (מודל, חלוקה לחלקים...), כדי שלא נטען תוצאה ישנה */
    private const val VERSION = 1
    private const val MAGIC = 0x41524E41 // "ARNA"
    private const val MAX_FILES = 12

    /** מפתח לפי תוכן הקובץ */
    fun keyFor(photoFile: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        photoFile.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return "v$VERSION-$hex"
    }

    private fun cacheDir(baseDir: File): File = File(baseDir, "analysis_cache")

    fun load(baseDir: File, key: String): SceneAnalysis? {
        val file = File(cacheDir(baseDir), "$key.bin")
        if (!file.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                if (input.readInt() != MAGIC) return null
                val width = input.readInt()
                val height = input.readInt()
                val tileCount = input.readInt()
                if (width <= 0 || height <= 0 || width.toLong() * height > 4_000_000L) return null
                val size = width * height
                val labels = ByteArray(size)
                input.readFully(labels)
                val depth = FloatArray(size)
                for (i in 0 until size) depth[i] = input.readFloat()
                file.setLastModified(System.currentTimeMillis())
                SceneAnalysis(width, height, labels, depth, tileCount)
            }
        } catch (e: Exception) {
            // קובץ פגום: מתעלמים ומנתחים מחדש
            file.delete()
            null
        }
    }

    fun save(baseDir: File, key: String, analysis: SceneAnalysis) {
        try {
            val dir = cacheDir(baseDir)
            dir.mkdirs()
            val temp = File(dir, "$key.tmp")
            DataOutputStream(BufferedOutputStream(temp.outputStream())).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(analysis.width)
                output.writeInt(analysis.height)
                output.writeInt(analysis.tileCount)
                output.write(analysis.labelBytes())
                for (value in analysis.depth) output.writeFloat(value)
            }
            val target = File(dir, "$key.bin")
            if (target.exists()) target.delete()
            temp.renameTo(target)
            trim(dir)
        } catch (e: Exception) {
            // שמירה היא בונוס: אם נכשלה, פשוט ינותח שוב בפעם הבאה
        }
    }

    /** משאירים רק את הניתוחים האחרונים, כדי לא למלא את הטלפון */
    private fun trim(dir: File) {
        val files = dir.listFiles { f -> f.name.endsWith(".bin") } ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }
}