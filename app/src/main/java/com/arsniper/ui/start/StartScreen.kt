package com.arsniper.ui.start

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PhotoImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * מסך פתיחה. START = צילום במצלמה האחורית. PICK PHOTO = בחירת תמונה מהגלריה
 * (כך אפשר לבדוק שוב ושוב את אותה תמונה בלי לצלם מחדש).
 */
@Composable
fun StartScreen(
    onStart: () -> Unit,
    onPhotoPicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            busy = true
            failed = false
            scope.launch {
                val ok = withContext(Dispatchers.IO) { PhotoImport.importFromUri(context, uri) }
                busy = false
                if (ok) onPhotoPicked() else failed = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "AR SNIPER",
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onStart,
                    enabled = !busy,
                    modifier = Modifier
                        .width(220.dp)
                        .height(64.dp)
                ) {
                    Text(text = "START", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(24.dp))
                Button(
                    onClick = { picker.launch("image/*") },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                    modifier = Modifier
                        .width(220.dp)
                        .height(64.dp)
                ) {
                    Text(text = if (busy) "LOADING..." else "PICK PHOTO", fontSize = 20.sp)
                }
            }
            if (failed) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Could not open that photo",
                    color = Color(0xFFFF8A80),
                    fontSize = 16.sp
                )
            }
        }
    }
}
