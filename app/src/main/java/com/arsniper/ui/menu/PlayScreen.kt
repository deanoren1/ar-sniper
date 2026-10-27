package com.arsniper.ui.menu

import androidx.activity.compose.rememberLauncherForActivityResult
import com.arsniper.ui.components.rememberGalleryPicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PhotoImport
import com.arsniper.data.PhotoLibrary
import com.arsniper.data.PlayerProfile
import com.arsniper.ui.components.CurrencyBar
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.GameIcon
import com.arsniper.ui.components.NavyPanel
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.ScreenHeader
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * מסך PLAY: צילום חדש, תמונה מהגלריה, משחק מהיר (תמונה אקראית מהשמורות)
 * או בחירה מהתמונות האחרונות. אין כאן לוגיקת משחק, רק ניווט.
 */
@Composable
fun PlayScreen(
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onGalleryPicked: () -> Unit,
    onPickSaved: (File) -> Unit,
    onQuick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    PlayerProfile.revision.hashCode()
    var refresh by remember { mutableStateOf(0) }
    var pendingDelete by remember { mutableStateOf<Pair<File, ImageBitmap?>?>(null) }

    val saved by produceState<List<Pair<File, ImageBitmap?>>>(emptyList(), refresh) {
        value = withContext(Dispatchers.IO) {
            PhotoLibrary.list(context).map { it to PhotoLibrary.thumbnail(it)?.asImageBitmap() }
        }
    }

    val picker = rememberGalleryPicker { uri ->
        run {
            busy = true
            failed = false
            scope.launch {
                val ok = withContext(Dispatchers.IO) { PhotoImport.importFromUri(context, uri) }
                busy = false
                if (ok) onGalleryPicked() else failed = true
            }
        }
    }

    ResponsiveBox(modifier = modifier.background(GameColors.Navy900)) { ui ->
        com.arsniper.ui.components.MenuBackdrop()
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.arsniper.R.drawable.play_deco_camera),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.2f)
        )
        Column(modifier = Modifier.fillMaxSize().padding(ui.dp(12f))) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                com.arsniper.ui.components.GradientTitle("PLAY", fontSize = (ui.factor * 50f).sp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                com.arsniper.ui.components.GameIconButton(GameIcon.Back, onClick = onBack, style = GameButtonStyle.Primary, size = ui.dp(54f))
                Spacer(Modifier.weight(1f))
                CurrencyBar(PlayerProfile.coins, PlayerProfile.diamonds, modifier = Modifier.offset(y = ui.dp(10f)))
            }
            }
            Spacer(Modifier.height(ui.dp(14f)))
            Row(
                horizontalArrangement = Arrangement.spacedBy(ui.dp(14f)),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(ui.dp(12f)),
                    modifier = Modifier.width(ui.dp(290f))
                ) {
                    GameButton(
                        text = "TAKE PHOTO",
                        onClick = onTakePhoto,
                        icon = GameIcon.Photo,
                        enabled = !busy,
                        height = ui.dp(68f),
                        fontSize = (ui.factor * 29f).sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    GameButton(
                        text = if (busy) "LOADING..." else "FROM GALLERY",
                        onClick = { picker.launch() },
                        style = GameButtonStyle.Secondary,
                        icon = GameIcon.Gallery,
                        enabled = !busy,
                        height = ui.dp(68f),
                        fontSize = (ui.factor * 29f).sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    GameButton(
                        text = "QUICK GAME",
                        onClick = onQuick,
                        icon = GameIcon.Play,
                        style = GameButtonStyle.Success,
                        enabled = saved.isNotEmpty() && !busy,
                        height = ui.dp(68f),
                        fontSize = (ui.factor * 29f).sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedText(
                        "Quick game: a random saved photo and arena.\nShoot a NEW photo for a coin bonus!",
                        fontSize = (ui.factor * 14f).sp,
                        color = Color(0xFFC8D4F0),
                        outline = null
                    )
                    if (failed) OutlinedText("Could not open that photo\n" + com.arsniper.data.PhotoImport.lastError, fontSize = 12.sp, color = Color(0xFFFF8A80))
                    if (com.arsniper.ui.components.PickerDebug.text.isNotEmpty()) OutlinedText(com.arsniper.ui.components.PickerDebug.text, fontSize = 9.sp, color = Color(0xFF9FB4E8), outline = null)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1050B8), Color(0xFF0A3A94)))
                        )
                        .border(3.5.dp, Color(0xFF3CB4FF), RoundedCornerShape(22.dp))
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(com.arsniper.R.drawable.play_deco_photos),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                        modifier = Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.34f)
                    )
                    Column(modifier = Modifier.padding(ui.dp(14f))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedText("MY PHOTOS", fontSize = (ui.factor * 32f).sp)
                            if (saved.isNotEmpty()) {
                                Spacer(Modifier.width(ui.dp(10f)))
                                OutlinedText("Long-press a photo to delete it", fontSize = (ui.factor * 14f).sp, color = Color(0xFFB8C4E0), outline = null)
                            }
                        }
                        Spacer(Modifier.height(ui.dp(8f)))
                        if (saved.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                OutlinedText(
                                    "No saved photos yet. Take or pick a photo and it will show up here.",
                                    fontSize = (ui.factor * 14f).sp,
                                    color = Color(0xFFB8C4E0),
                                    outline = null
                                )
                            }
                        } else {
                            for (rowItems in saved.chunked(5)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(ui.dp(8f)),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = ui.dp(8f))
                                ) {
                                    for ((file, thumb) in rowItems) {
                                        Thumb(thumb, Modifier.weight(1f), onLong = { pendingDelete = file to thumb }) { onPickSaved(file) }
                                    }
                                    repeat(5 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        pendingDelete?.let { (file, thumb) ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xB3081028))
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { pendingDelete = null }
            ) {
                NavyPanel(modifier = Modifier.width(ui.dp(300f))) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ui.dp(8f)),
                        modifier = Modifier.padding(ui.dp(14f))
                    ) {
                        OutlinedText("DELETE PHOTO?", fontSize = (ui.factor * 29f).sp)
                        if (thumb != null) {
                            Image(
                                bitmap = thumb, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.width(ui.dp(120f)).aspectRatio(1.4f).clip(RoundedCornerShape(10.dp))
                            )
                        }
                        OutlinedText("It will be removed from your saved photos.", fontSize = (ui.factor * 12f).sp, color = Color(0xFFB8C4E0), outline = null)
                        GameButton(
                            "DELETE",
                            { PhotoLibrary.delete(file); pendingDelete = null; refresh++ },
                            style = GameButtonStyle.Danger, height = ui.dp(42f), fontSize = (ui.factor * 18f).sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GameButton(
                            "CANCEL", { pendingDelete = null },
                            style = GameButtonStyle.Secondary, height = ui.dp(42f), fontSize = (ui.factor * 18f).sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Thumb(image: ImageBitmap?, modifier: Modifier, onLong: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(GameColors.Navy800)
            .border(2.dp, GameColors.Yellow, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
