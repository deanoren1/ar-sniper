package com.arsniper.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * עדכון האפליקציה מתוך המשחק: כל בנייה ב-main מעלה את ה-APK לשרת (R2, דרך build.yml).
 * בפתיחה בודקים אם יש גרסה חדשה (versionCode = מספר הריצה של הבנייה), מורידים ומתקינים דרך PackageInstaller.
 * אנדרואיד מבקש מהמשתמש אישור התקנה (ובפעם הראשונה גם "התקנה ממקור זה") — אי אפשר לעקוף את זה.
 */
object AppUpdater {

    /** pages.dev נפתח גם ברשתות שחוסמות workers.dev (אותו שרת) */
    private const val BASE = "https://snapboo-front.pages.dev/app"

    data class Info(val versionCode: Long, val notes: String, val size: Long)

    fun currentVersion(context: Context): Long {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()
    }

    /** גרסה חדשה בשרת, או null (אין חדשה / אין רשת) */
    suspend fun check(context: Context): Info? = withContext(Dispatchers.IO) {
        try {
            val conn = URL("$BASE/latest.json?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode != 200) return@withContext null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val info = Info(json.getLong("versionCode"), json.optString("notes", ""), json.optLong("size", 0L))
            if (info.versionCode > currentVersion(context)) info else null
        } catch (_: Throwable) {
            null
        }
    }

    /** מוריד את ה-APK לקובץ זמני. onProgress: 0..1 */
    suspend fun download(context: Context, info: Info, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update.apk")
        val conn = URL("$BASE/snapboo.apk?v=${info.versionCode}").openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.size
        var done = 0L
        conn.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        if (total > 0 && done != total) throw IllegalStateException("download incomplete")
        file
    }

    /** אנדרואיד 8+: האפליקציה צריכה הרשאת "התקנה ממקור זה" (פעם אחת) */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** מתחיל התקנה; אנדרואיד מציג את חלון האישור (InstallReceiver פותח אותו) */
    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("snapboo.apk", 0, apk.length()).use { out ->
                    input.copyTo(out, 256 * 1024)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }
}

/** מקבל את מצב ההתקנה: כשנדרש אישור משתמש, פותח את חלון האישור של אנדרואיד */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirm)
        }
    }
}
