package com.arsniper.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** בודק פעם אחת בפתיחת האפליקציה; אם יש גרסה חדשה מציג חלון עדכון */
@Composable
fun UpdateDialog() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<AppUpdater.Info?>(null) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var error by remember { mutableStateOf<String?>(null) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { info = AppUpdater.check(context) }

    val update = info ?: return
    if (dismissed) return
    val downloading = progress >= 0f

    AlertDialog(
        onDismissRequest = { if (!downloading) dismissed = true },
        title = { Text("UPDATE AVAILABLE") },
        text = {
            Column {
                Text("Build ${update.versionCode}" + if (update.size > 0) "  ·  ${update.size / 1_000_000} MB" else "", fontSize = 13.sp)
                if (update.notes.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(update.notes.take(300), fontSize = 12.sp)
                }
                if (downloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !downloading,
                onClick = {
                    if (!AppUpdater.canInstall(context)) {
                        error = "Allow \"Install unknown apps\" for SnapBoo, then tap UPDATE again"
                        AppUpdater.openInstallPermission(context)
                        return@TextButton
                    }
                    error = null
                    progress = 0f
                    scope.launch {
                        try {
                            val apk = AppUpdater.download(context, update) { p -> progress = p }
                            AppUpdater.install(context, apk)
                        } catch (t: Throwable) {
                            error = "Update failed: ${t.message ?: t.javaClass.simpleName}"
                        }
                        progress = -1f
                    }
                }
            ) { Text("UPDATE") }
        },
        dismissButton = {
            TextButton(enabled = !downloading, onClick = { dismissed = true }) { Text("LATER") }
        }
    )
}
