package com.arsniper.ml

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.util.concurrent.TimeUnit

/** יצירת עולם AI ברקע (ממשיכה גם כשיוצאים מהאפליקציה), ומתריעה כשהעולם מוכן */
class AiWorldWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_PHOTO) ?: return Result.failure()
        val style = inputData.getString(KEY_STYLE) ?: return Result.failure()
        val src = sourceFile(ctx, key)
        if (!src.exists()) return Result.failure()
        if (AiWorld.cachedWorld(ctx, key, style) != null) return Result.success()
        val bmp = BitmapFactory.decodeFile(src.absolutePath) ?: return Result.failure()
        val made = AiWorld.createWorld(ctx, key, style, bmp)
        return if (made != null) {
            notifyReady()
            Result.success()
        } else if (runAttemptCount < 2) {
            Result.retry()
        } else {
            Result.failure()
        }
    }

    private fun notifyReady() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "AI worlds", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val pi = launch?.let { PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(ctx.applicationInfo.icon)
            .setContentTitle("SnapBoo")
            .setContentText("Your world is ready! Come play.")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify(1001, n)
    }

    companion object {
        private const val KEY_PHOTO = "photo"
        private const val KEY_STYLE = "style"
        private const val CHANNEL = "ai_world"

        fun sourceFile(context: Context, key: String) =
            File(File(context.filesDir, "ai_world").apply { mkdirs() }, "src_$key.jpg")

        private fun name(key: String, style: String) = "aiworld_${key}_$style"

        fun enqueue(context: Context, key: String, style: String, source: android.graphics.Bitmap) {
            val f = sourceFile(context, key)
            if (!f.exists()) {
                try {
                    f.outputStream().use { source.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
                } catch (_: Exception) {
                    return
                }
            }
            val req = OneTimeWorkRequestBuilder<AiWorldWorker>()
                .setInputData(workDataOf(KEY_PHOTO to key, KEY_STYLE to style))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(name(key, style), ExistingWorkPolicy.KEEP, req)
        }

        /** מתחיל יצירת עולם לתמונה הנוכחית ברגע שנבחרה ארנה (במקביל לניתוח); לא עושה כלום אם אין סגנון AI או שכבר קיים */
        fun startFor(context: Context, style: com.arsniper.game.progress.ArenaStyle) {
            val app = context.applicationContext
            val server = AiWorld.serverStyle(style) ?: return
            Thread {
                try {
                    val file = com.arsniper.data.PhotoRepository.currentFile(app) ?: return@Thread
                    val key = AnalysisCache.keyFor(file)
                    if (AiWorld.cachedWorld(app, key, server) != null) return@Thread
                    val bmp = com.arsniper.data.PhotoRepository.load(app) ?: return@Thread
                    enqueue(app, key, server, bmp)
                } catch (_: Throwable) {
                }
            }.start()
        }

        /** true אם העבודה נכשלה/בוטלה סופית (אין טעם להמתין) */
        fun isDead(context: Context, key: String, style: String): Boolean = try {
            val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name(key, style)).get()
            infos.isNotEmpty() && infos.all { it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED }
        } catch (_: Exception) {
            false
        }
    }
}
