package com.arsniper.ui.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PhotoLibrary
import com.arsniper.game.progress.ArenaStyle
import com.arsniper.ml.AiWorld
import com.arsniper.ml.CommunityWorlds
import com.arsniper.ui.arena.CommunityState
import com.arsniper.ui.arena.SavedWorld
import com.arsniper.ui.arena.allSavedWorlds
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * מצב הבורר המשותף: מה נבחר (עולם AI או תמונה קלאסית), איזה לשונית פתוחה והודעת שגיאה.
 * מי שמשתמש בבורר (קו-אופ, משחק סולו) מחזיק מופע אחד וקורא ממנו את הבחירה.
 */
internal class WorldPickerState(startTab: Int = 0) {
    var world by mutableStateOf<SavedWorld?>(null)
    var photo by mutableStateOf<File?>(null)
    var tab by mutableStateOf(startTab)
    var rolling by mutableStateOf(false)
    var error by mutableStateOf("")

    /** משהו נבחר */
    val hasChoice: Boolean get() = world != null || photo != null

    /** קובץ המקור של הבחירה (תמונה מקורית של העולם, או התמונה הקלאסית) */
    val source: File? get() = world?.source ?: photo

    fun pickWorld(w: SavedWorld) { world = w; photo = null; error = "" }
    fun pickPhoto(f: File) { photo = f; world = null; error = "" }
}

/**
 * בורר עולמות משותף: RANDOM WORLD מהקהילה, MY WORLDS (עולמות AI שמורים), MY PHOTOS (קלאסי, עם מצלמה וגלריה).
 * לא מתחיל משחק בעצמו. רק מעדכן את [state].
 */
@Composable
internal fun WorldPicker(
    state: WorldPickerState,
    library: List<File>,
    onNewPhoto: (camera: Boolean) -> Unit,
    onGallery: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    /** false: בלי שורת הלשוניות (מסך שמציג לשונית אחת בלבד) */
    showTabs: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val worlds = remember { allSavedWorlds(context) }
    val muted = Color(0xFFB8C4E0)

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        if (showTabs) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for ((i, label) in listOf("🎲 RANDOM WORLD", "MY WORLDS", "MY PHOTOS").withIndex()) {
                Box(
                    modifier = Modifier
                        .weight(if (i == 0) 1.5f else 1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (state.tab == i) (if (i == 0) Color(0xFFFF8A2B) else Color(0xFF2B5BD7)) else Color(0xFF1B2A55))
                        .border(if (state.tab == i) 2.dp else 1.dp, if (state.tab == i) GameColors.Yellow else Color(0xFF3D4C7A), RoundedCornerShape(8.dp))
                        .clickable { state.tab = i }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { OutlinedText(label, fontSize = 12.sp) }
            }
        }
        when (state.tab) {
            1 -> {
                if (worlds.isEmpty()) {
                    OutlinedText("No saved worlds yet. Create one from a photo first, or use RANDOM", fontSize = 12.sp, color = muted, outline = null)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    for (w in worlds) {
                        val bmp = remember(w.world.path) { PhotoLibrary.thumbnail(w.world, 200)?.asImageBitmap() }
                        val selected = state.world?.world?.path == w.world.path
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(width = 96.dp, height = 64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(if (selected) 3.dp else 1.dp, if (selected) GameColors.Yellow else Color(0xFF3D4C7A), RoundedCornerShape(8.dp))
                                    .clickable { state.pickWorld(w) }
                            ) {
                                if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            OutlinedText(w.style.title, fontSize = 9.sp, color = muted, outline = null)
                        }
                    }
                }
            }
            0 -> {
                val w0 = state.world
                GameButton(
                    text = if (state.rolling) "LOOKING..." else if (w0 != null && state.photo == null) "🎲 RANDOM AGAIN" else "🎲 RANDOM WORLD",
                    onClick = {
                        scope.launch {
                            state.rolling = true
                            state.error = ""
                            try {
                                val styles = ArenaStyle.values().filter { com.arsniper.data.PlayerProfile.isUnlocked(it) && AiWorld.serverStyle(it) != null }.shuffled()
                                var found: SavedWorld? = null
                                var lastMsg = ""
                                for (st in styles) {
                                    val server = AiWorld.serverStyle(st) ?: continue
                                    val result = withContext(Dispatchers.IO) {
                                        CommunityWorlds.downloadRandom(context, server, CommunityState.seen(context))
                                    }
                                    if (result is CommunityWorlds.Result.Ok) {
                                        val (file, id) = result.value
                                        CommunityState.setOrigin(context, file.nameWithoutExtension, id)
                                        CommunityState.addSeen(context, id)
                                        val key = com.arsniper.ml.AnalysisCache.keyFor(file)
                                        val world = AiWorld.cachedWorldFile(context, key, server)
                                        if (world != null) { found = SavedWorld(file, world, st, key); break }
                                    } else if (result is CommunityWorlds.Result.Error && result.message != "none") {
                                        lastMsg = result.message
                                        break
                                    }
                                }
                                if (found != null) state.pickWorld(found)
                                else state.error = if (lastMsg.isEmpty()) "No community worlds yet" else "Could not load a world ($lastMsg)"
                            } finally {
                                state.rolling = false
                            }
                        }
                    },
                    enabled = enabled && !state.rolling,
                    style = GameButtonStyle.Success,
                    height = 44.dp,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                val w = state.world
                if (w != null && state.photo == null) {
                    val bmp = remember(w.world.path) { PhotoLibrary.thumbnail(w.world, 320)?.asImageBitmap() }
                    if (bmp != null) {
                        Image(bmp, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 240.dp, height = 150.dp).clip(RoundedCornerShape(10.dp)).border(3.dp, GameColors.Yellow, RoundedCornerShape(10.dp)))
                        OutlinedText("${w.style.title} world", fontSize = 13.sp, color = Color.White, outline = null)
                        val originId = remember(w.key) { CommunityState.originId(context, w.key) }
                        if (originId != null) {
                            OutlinedText("REPORT & BLOCK CREATOR", fontSize = 11.sp, color = Color(0xFFFFB4B4), outline = null,
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) { CommunityWorlds.report(context, originId, block = true) }
                                        if (ok) { state.world = null; state.error = "Reported and blocked. Roll another world" } else state.error = "Could not report, try again"
                                    }
                                }.padding(6.dp))
                        }
                    }
                }
            }
            else -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    if (library.isEmpty()) {
                        OutlinedText("No scanned photos yet. Play a photo first", fontSize = 12.sp, color = muted, outline = null)
                    }
                    for (file in library) {
                        val bmp = remember(file.name) { PhotoLibrary.thumbnail(file, 200)?.asImageBitmap() }
                        val selected = state.photo?.name == file.name
                        Box(
                            modifier = Modifier
                                .size(width = 96.dp, height = 64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(if (selected) 3.dp else 1.dp, if (selected) GameColors.Yellow else Color(0xFF3D4C7A), RoundedCornerShape(8.dp))
                                .clickable { state.pickPhoto(file) }
                        ) {
                            if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GameButton(text = "CAMERA", onClick = { onNewPhoto(true) }, style = GameButtonStyle.Success, height = 40.dp, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    GameButton(text = "GALLERY", onClick = onGallery, style = GameButtonStyle.Success, height = 40.dp, fontSize = 14.sp, modifier = Modifier.weight(1f))
                }
                OutlinedText("MY PHOTOS - CLASSIC: classic arena, no AI world", fontSize = 11.sp, color = muted, outline = null)
            }
        }
        OutlinedText(
            when {
                state.world != null -> "Selected: ${state.world!!.style.title} world"
                state.photo != null -> "Selected: classic photo"
                else -> "Pick a world or a photo"
            },
            fontSize = 12.sp, color = GameColors.Yellow, outline = null
        )
    }
}
