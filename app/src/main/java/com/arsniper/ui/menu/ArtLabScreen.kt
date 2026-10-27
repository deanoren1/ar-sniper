package com.arsniper.ui.menu

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PhotoRepository
import com.arsniper.ml.GeminiArt
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.NavyPanel
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.ScreenHeader
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PRESETS = listOf(
    "OIL" to "Turn this photo into a high-quality oil painting with rich brush strokes and vibrant colors. Keep the exact same composition, objects, proportions and positions. Do not add or remove anything.",
    "WATERCOLOR" to "Turn this photo into a delicate watercolor painting. Keep the exact same composition, objects, proportions and positions. Do not add or remove anything.",
    "COMIC" to "Turn this photo into a bold cartoon illustration with thick outlines and saturated colors. Keep the exact same composition, objects, proportions and positions. Do not add or remove anything."
)

/**
 * מעבדת ניסוי (ART LAB): שולחת את התמונה הנוכחית ל-Gemini ומציגה לפני ואחרי.
 * המפתח נשמר בטלפון בלבד. זה ניסוי לבדיקת איכות ושמירת המבנה, לא פיצ'ר לשחקנים.
 */
@Composable
fun ArtLabScreen(onBack: () -> Unit, onUsePhoto: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("artlab", Context.MODE_PRIVATE) }
    var key by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model", GeminiArt.DEFAULT_MODEL) ?: GeminiArt.DEFAULT_MODEL) }
    var prompt by remember { mutableStateOf(PRESETS[0].second) }
    val original = remember { PhotoRepository.load(context) }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val backup = remember { File(context.filesDir, "art_original_backup.jpg") }
    var hasBackup by remember { mutableStateOf(backup.exists()) }

    ResponsiveBox(modifier = modifier.background(GameColors.Navy900)) { ui ->
        com.arsniper.ui.components.MenuBackdrop()
        Column(modifier = Modifier.fillMaxSize().padding(ui.dp(12f))) {
            ScreenHeader(title = "ART LAB (TEST)", onBack = onBack)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                // צד שמאל: הגדרות
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it; prefs.edit().putString("key", it).apply() },
                        label = { Text("Gemini API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors()
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it; prefs.edit().putString("model", it).apply() },
                        label = { Text("Model") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((label, text) in PRESETS) {
                            GameButton(label, { prompt = text }, style = GameButtonStyle.Secondary, height = 34.dp, fontSize = 12.sp)
                        }
                    }
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Prompt") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors()
                    )
                    GameButton(
                        text = if (busy) "WORKING..." else "GENERATE",
                        onClick = {
                            val source = original
                            if (source == null) {
                                message = "No photo found. Pick a photo first."
                            } else if (key.isBlank()) {
                                message = "Enter your Gemini API key."
                            } else {
                                busy = true
                                message = null
                                scope.launch {
                                    val outcome = withContext(Dispatchers.IO) { GeminiArt.generate(key.trim(), model.trim(), prompt, source) }
                                    busy = false
                                    when (outcome) {
                                        is GeminiArt.Result.Image -> result = outcome.bitmap
                                        is GeminiArt.Result.Error -> message = outcome.message
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        style = GameButtonStyle.Primary,
                        height = 48.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (result != null) {
                        GameButton(
                            text = "USE RESULT AS PHOTO",
                            onClick = {
                                val r = result
                                if (r != null) {
                                    // שומרים את המקור לפני שמחליפים, כדי שאפשר יהיה לחזור
                                    if (!backup.exists() && original != null) {
                                        backup.outputStream().use { original.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                                    }
                                    PhotoRepository.save(context, r)
                                    hasBackup = backup.exists()
                                    onUsePhoto()
                                }
                            },
                            style = GameButtonStyle.Success,
                            height = 44.dp,
                            fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (hasBackup) {
                        GameButton(
                            text = "RESTORE ORIGINAL",
                            onClick = {
                                val bmp = android.graphics.BitmapFactory.decodeFile(backup.absolutePath)
                                if (bmp != null) {
                                    PhotoRepository.save(context, bmp)
                                    backup.delete()
                                    hasBackup = false
                                    message = "Original restored."
                                    onUsePhoto()
                                }
                            },
                            style = GameButtonStyle.Danger,
                            height = 40.dp,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    message?.let { OutlinedText(it, fontSize = 12.sp, color = Color(0xFFFF8A80), outline = null) }
                }
                // צד ימין: לפני ואחרי
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1.6f)) {
                    Preview("BEFORE", original, Modifier.weight(1f))
                    Preview("AFTER", result, Modifier.weight(1f), busy)
                }
            }
        }
    }
}

@Composable
private fun Preview(label: String, bitmap: Bitmap?, modifier: Modifier, busy: Boolean = false) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedText(label, fontSize = 14.sp, outline = null)
        NavyPanel(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            }
            if (busy) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}


@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Color(0xFF9CC4FF),
    unfocusedLabelColor = Color(0xFFB8C4E0),
    focusedBorderColor = Color(0xFF4DA3FF),
    unfocusedBorderColor = Color(0xFF6B7BA8),
    cursorColor = Color.White
)
