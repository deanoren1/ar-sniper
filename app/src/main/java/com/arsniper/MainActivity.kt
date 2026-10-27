package com.arsniper

import android.os.Bundle
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // מסך מלא: מסתיר את סרגלי המערכת (נחשפים בהחלקה)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        com.arsniper.data.PlayerProfile.init(this)

        // יומן קריסות: שומר את סיבת הקריסה לקובץ, ובהפעלה הבאה מציג אותה (לצילום מסך)
        val crashFile = File(filesDir, "last_crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                crashFile.writeText(
                    "time=" + java.util.Date() + "\nthread=" + thread.name + "\n" +
                        android.util.Log.getStackTraceString(error).take(4000)
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
        val lastCrash = try {
            if (crashFile.exists()) crashFile.readText() else null
        } catch (_: Throwable) {
            null
        }

        setContent {
            MaterialTheme {
                AppNavigation()
                var crashText by androidx.compose.runtime.remember { mutableStateOf(lastCrash) }
                if (crashText != null) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("Last crash") },
                        text = {
                            Text(
                                text = crashText ?: "",
                                fontSize = 9.sp,
                                modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                try { crashFile.delete() } catch (_: Throwable) { }
                                crashText = null
                            }) { Text("OK") }
                        }
                    )
                }
            }
        }
    }
}
