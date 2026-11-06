package com.arsniper.ui.arena

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PhotoLibrary
import com.arsniper.data.PickImport
import com.arsniper.data.PlayerProfile
import com.arsniper.game.progress.ArenaStyle
import com.arsniper.ml.AiWorld
import com.arsniper.ml.AiWorldWorker
import com.arsniper.ml.CommunityWorlds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.arsniper.ui.components.CurrencyBar
import com.arsniper.ui.components.GameIcon
import com.arsniper.ui.components.GameIconButton
import com.arsniper.ui.components.IconGlyph
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.rememberGalleryPicker
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import java.io.File

/** עולם AI שמור בארנה: תמונת המקור שלו, ותמונת העולם */
private class SavedWorld(val source: File, val world: File, val style: ArenaStyle, val key: String)

/** מעקב אחרי עולמות קהילה: מה שיתפתי (מפתח -> מזהה), מה הורדתי (מפתח -> מזהה), ומה כבר ראיתי ב-RANDOM */
private object CommunityState {
    private fun prefs(context: android.content.Context) = context.getSharedPreferences("community_worlds", android.content.Context.MODE_PRIVATE)
    fun sharedId(context: android.content.Context, key: String): String? = prefs(context).getString("shared_$key", null)
    fun setShared(context: android.content.Context, key: String, id: String?) { prefs(context).edit().apply { if (id == null) remove("shared_$key") else putString("shared_$key", id) }.apply() }
    fun originId(context: android.content.Context, key: String): String? = prefs(context).getString("origin_$key", null)
    fun setOrigin(context: android.content.Context, key: String, id: String) { prefs(context).edit().putString("origin_$key", id).apply() }
    fun seen(context: android.content.Context): Set<String> = prefs(context).getStringSet("seen", emptySet()) ?: emptySet()
    fun addSeen(context: android.content.Context, id: String) {
        val set = seen(context).toMutableSet().apply { add(id) }
        prefs(context).edit().putStringSet("seen", set.toList().takeLast(200).toSet()).apply()
    }
}

/** מועדפים: מפתחות התמונות שסומנו בכוכב */
private object WorldFavorites {
    private fun prefs(context: android.content.Context) = context.getSharedPreferences("world_favorites", android.content.Context.MODE_PRIVATE)
    fun all(context: android.content.Context): Set<String> = prefs(context).getStringSet("keys", emptySet()) ?: emptySet()
    fun toggle(context: android.content.Context, key: String) {
        val set = all(context).toMutableSet()
        if (!set.add(key)) set.remove(key)
        prefs(context).edit().putStringSet("keys", set).apply()
    }
}

/** כל העולמות השמורים בכל הארנות (החדש ראשון). רק כאלה שאפשר להפעיל, כלומר שתמונת המקור שלהם עדיין קיימת */
private fun allSavedWorlds(context: android.content.Context): List<SavedWorld> {
    val result = ArrayList<SavedWorld>()
    for (style in ArenaStyle.values()) result += savedWorlds(context, style)
    return result.sortedByDescending { it.world.lastModified() }
}

/** העולמות השמורים של ארנה אחת */
private fun savedWorlds(context: android.content.Context, style: ArenaStyle): List<SavedWorld> {
    val serverStyle = AiWorld.serverStyle(style) ?: return emptyList()
    val dir = File(context.filesDir, "ai_world")
    // כל העולמות של משפחת הארנה (גם כאלה שנוצרו בסגנון ישן), אחד לכל תמונה; המועדף: הסגנון הנוכחי
    val family = serverStyle.substringBefore("_")
    val pattern = Regex("^(.+)_" + Regex.escape(family) + "(_[a-z]+)?\\.jpg$")
    val byKey = (dir.listFiles() ?: emptyArray())
        .filter { !it.name.startsWith("src_") && pattern.matches(it.name) }
        .groupBy { pattern.matchEntire(it.name)!!.groupValues[1] }
    return byKey.map { (key, list) ->
        val world = list.firstOrNull { it.name == "${key}_$serverStyle.jpg" } ?: list.maxByOrNull { it.lastModified() }!!
        key to world
    }
        .sortedByDescending { it.second.lastModified() }
        .mapNotNull { (key, world) ->
            val lib = File(File(context.filesDir, "library"), "$key.jpg")
            val src = if (lib.exists()) lib else AiWorldWorker.sourceFile(context, key).takeIf { it.exists() }
            src?.let { SavedWorld(it, world, style, key) }
        }
}

/**
 * מסך בחירת אופן המשחק בארנה שנבחרה. המבנה קבוע, הרקע והצבעים לפי מוטיב הארנה.
 * כרטיסים: MY WORLDS (רק אם יש עולמות שמורים), USE LAST PHOTO (רק אם יש תמונה קודמת), NEW PHOTO (מצלמה / גלריה).
 * RANDOM (עולמות קהילה) יתווסף כשיהיו עולמות קהילה בשרת.
 */
@Composable
fun ArenaModeScreen(
    style: ArenaStyle,
    onBack: () -> Unit,
    onShop: () -> Unit,
    onUseLast: (File) -> Unit,
    onPlayWorld: (File, ArenaStyle) -> Unit,
    onCamera: () -> Unit,
    onGalleryImported: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerProfile.revision.hashCode()
    val context = LocalContext.current
    val lastFile = remember { PhotoLibrary.list(context).firstOrNull { PhotoLibrary.isReviewed(it) } }
    val lastThumb = remember(lastFile) { lastFile?.let { PhotoLibrary.thumbnail(it, 480) } }
    val worlds = remember(style) { allSavedWorlds(context) }
    val thisArenaWorlds = remember(worlds) { worlds.filter { it.style == style } }
    var favorites by remember { mutableStateOf(WorldFavorites.all(context)) }
    var filter by remember { mutableStateOf<Any>(style) } // ArenaStyle, או "fav", או "all"
    val worldThumbs = remember(worlds) { worlds.take(4).map { PhotoLibrary.thumbnail(it.world, 240) } }
    var message by remember { mutableStateOf<String?>(null) }
    var showWorlds by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var aiLeft by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(Unit) { aiLeft = withContext(Dispatchers.IO) { AiWorld.quotaLeft(context) } }
    var confirmShare by remember { mutableStateOf<SavedWorld?>(null) }
    var communityRevision by remember { mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val picker = rememberGalleryPicker { uri -> PickImport.start(context, uri) }
    val importState = PickImport.state
    LaunchedEffect(importState) {
        when (importState) {
            PickImport.OK -> { PickImport.consume(); onGalleryImported() }
            PickImport.FAILED -> { PickImport.consume(); message = "Could not open that photo" }
            else -> Unit
        }
    }
    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(2500); message = null }
    }

    val tint = Color(style.tint)
    val top = lerp(tint, Color(0xFF0B1B3A), 0.55f)
    val bottom = lerp(tint, Color(0xFF050B1C), 0.8f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .drawBehind {
                // קישוט עדין לפי המוטיב: אור מלמעלה וכתמי הילה
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.minDimension * 0.9f, center = Offset(size.width * 0.5f, -size.height * 0.1f))
                drawCircle(tint.copy(alpha = 0.18f), radius = size.minDimension * 0.5f, center = Offset(size.width * 0.1f, size.height * 0.9f))
                drawCircle(tint.copy(alpha = 0.18f), radius = size.minDimension * 0.5f, center = Offset(size.width * 0.9f, size.height * 0.9f))
            }
    ) {
        // שורה עליונה: חזרה, כותרת, יתרות וחנות
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            GameIconButton(
                icon = GameIcon.Back, onClick = onBack, style = GameButtonStyle.Primary, size = 36.dp,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp)) {
                OutlinedText(style.title, fontSize = 20.sp, color = Color.White, outlineWidth = 10f)
                OutlinedText(
                    aiLeft?.let { "Choose how to play  ·  AI worlds left today: ${it.first}/${it.second}" } ?: "Choose how to play",
                    fontSize = 13.sp, color = GameColors.Ice, outline = null
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                CurrencyBar(PlayerProfile.coins, PlayerProfile.diamonds)
                GameIconButton(icon = GameIcon.Cart, onClick = onShop, style = GameButtonStyle.Secondary, size = 36.dp)
            }
        }

        // הכרטיסים
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.66f)
                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
        ) {
            ModeCard(
                title = "RANDOM",
                color = Color(0xFFFF8A2B),
                caption = if (busy) "Looking for a world..." else "Play a community world",
                modifier = Modifier.weight(1f),
                onClick = {
                    val serverStyle = AiWorld.serverStyle(style)
                    if (busy) return@ModeCard
                    if (serverStyle == null) { message = "No community worlds for this arena"; return@ModeCard }
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            CommunityWorlds.downloadRandom(context, serverStyle, CommunityState.seen(context))
                        }
                        busy = false
                        when (result) {
                            is CommunityWorlds.Result.Ok -> {
                                val (file, id) = result.value
                                CommunityState.setOrigin(context, file.nameWithoutExtension, id)
                                CommunityState.addSeen(context, id)
                                onPlayWorld(file, style)
                            }
                            is CommunityWorlds.Result.Error ->
                                message = if (result.message == "none") "No community worlds here yet. Share one!" else "Could not load a world (${result.message})"
                        }
                    }
                }
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OutlinedText("?", fontSize = 64.sp, color = Color.White, outlineWidth = 12f)
                }
            }
            if (worlds.isNotEmpty()) {
                ModeCard(
                    title = "MY WORLDS",
                    color = Color(0xFF8E4DFF),
                    caption = "PLAY YOUR SAVED AI WORLDS",
                    modifier = Modifier.weight(1f),
                    onClick = { showWorlds = true }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
                        for (rowItems in worldThumbs.chunked(2)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                                for (bmp in rowItems) {
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Color(0x55000000))) {
                                        if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                }
                                if (rowItems.size == 1) Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    OutlinedText("${worlds.size}", fontSize = 13.sp, color = Color.White, outline = null,
                        modifier = Modifier.align(Alignment.TopEnd).background(Color(0xFF6A2BD9), RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 1.dp))
                }
            }
            if (lastFile != null) {
                ModeCard(
                    title = "USE LAST PHOTO",
                    color = Color(0xFF2A8CFF),
                    caption = "Play ${style.title.lowercase().replaceFirstChar { it.uppercase() }} on your last photo",
                    modifier = Modifier.weight(1f),
                    onClick = { onUseLast(lastFile) }
                ) {
                    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color(0x55000000))) {
                        if (lastThumb != null) Image(lastThumb.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            ModeCard(
                title = "NEW PHOTO",
                color = Color(0xFF2FCB5B),
                caption = "Take a new photo or pick one",
                modifier = Modifier.weight(1f),
                onClick = null
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    SourceButton("CAMERA", GameIcon.Photo, Modifier.weight(1f).fillMaxWidth()) { onCamera() }
                    SourceButton("GALLERY", GameIcon.Gallery, Modifier.weight(1f).fillMaxWidth()) { picker.launch() }
                }
            }
        }

        if (showWorlds) {
            // גלריית העולמות: סינון לפי ארנה או מועדפים, כוכב לכל עולם, ולחיצה מפעילה אותו
            val shown = when (val f = filter) {
                "fav" -> worlds.filter { it.key in favorites }
                "all" -> worlds
                is ArenaStyle -> worlds.filter { it.style == f }
                else -> worlds
            }
            val arenasWithWorlds = ArenaStyle.values().filter { st -> worlds.any { it.style == st } }
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xF0050B1C)).clickable { showWorlds = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.clickable(enabled = false) {}
                ) {
                    OutlinedText("MY WORLDS", fontSize = 24.sp, color = Color.White, outlineWidth = 10f)
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp)
                    ) {
                        item { FilterChip("ALL (${worlds.size})", filter == "all") { filter = "all" } }
                        item { FilterChip("★ (${worlds.count { it.key in favorites }})", filter == "fav") { filter = "fav" } }
                        items(arenasWithWorlds.size) { i ->
                            val st = arenasWithWorlds[i]
                            FilterChip("${st.title} (${worlds.count { it.style == st }})", filter == st) { filter = st }
                        }
                    }
                    if (shown.isEmpty()) {
                        OutlinedText(
                            if (filter == "fav") "No favorites yet. Tap the star on a world." else "No worlds here yet.",
                            fontSize = 14.sp, color = GameColors.Ice, outline = null,
                            modifier = Modifier.padding(vertical = 40.dp)
                        )
                    } else {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp)
                        ) {
                            items(shown.size) { i ->
                                val w = shown[i]
                                val bmp = remember(w) { PhotoLibrary.thumbnail(w.world, 480) }
                                val fav = w.key in favorites
                                Box(
                                    modifier = Modifier
                                        .size(width = 230.dp, height = 140.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(3.dp, Color(0xFF8E4DFF), RoundedCornerShape(16.dp))
                                        .background(Color(0x55000000))
                                        .clickable { onPlayWorld(w.source, w.style) }
                                ) {
                                    if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    OutlinedText(w.style.title, fontSize = 11.sp, color = Color.White, outline = null,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                                            .background(Color(0xAA000000), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 1.dp))
                                    OutlinedText(if (fav) "★" else "☆", fontSize = 22.sp, color = if (fav) GameColors.Yellow else Color.White, outline = null,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                            .background(Color(0xAA000000), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp)
                                            .clickable {
                                                WorldFavorites.toggle(context, w.key)
                                                favorites = WorldFavorites.all(context)
                                            })
                                    val origin = remember(w, communityRevision) { CommunityState.originId(context, w.key) }
                                    val sharedId = remember(w, communityRevision) { CommunityState.sharedId(context, w.key) }
                                    val canShare = w.world.name == "${w.key}_${AiWorld.serverStyle(w.style)}.jpg"
                                    val label = when {
                                        origin != null -> "REPORT"
                                        sharedId != null -> "UNSHARE"
                                        canShare -> "SHARE"
                                        else -> null
                                    }
                                    if (label != null) {
                                        OutlinedText(label, fontSize = 11.sp, color = Color.White, outline = null,
                                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                                                .background(Color(0xCC1A2550), RoundedCornerShape(10.dp))
                                                .clickable {
                                                    when {
                                                        origin != null -> scope.launch {
                                                            val ok = withContext(Dispatchers.IO) { CommunityWorlds.report(context, origin) }
                                                            message = if (ok) "Thanks, the world was reported" else "Could not report, try again"
                                                        }
                                                        sharedId != null -> scope.launch {
                                                            val ok = withContext(Dispatchers.IO) { CommunityWorlds.unshare(context, sharedId) }
                                                            if (ok) { CommunityState.setShared(context, w.key, null); communityRevision++ }
                                                            message = if (ok) "World removed from the community" else "Could not remove, try again"
                                                        }
                                                        else -> confirmShare = w
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 3.dp))
                                    }
                                    OutlinedText("PLAY", fontSize = 14.sp, color = Color.White, outline = null,
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                            .background(Color(0xFF1E9E45), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                    OutlinedText("Tap outside to close", fontSize = 12.sp, color = GameColors.Ice, outline = null)
                }
            }
        }
        confirmShare?.let { w ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmShare = null },
                title = { androidx.compose.material3.Text("Share this world?") },
                text = { androidx.compose.material3.Text("The AI world image and its scene layout (objects and depth) will be uploaded so other players can play it. Your original photo is not uploaded. You can remove it later with UNSHARE.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        confirmShare = null
                        val serverStyle = AiWorld.serverStyle(w.style)
                        if (serverStyle != null) scope.launch {
                            val result = withContext(Dispatchers.IO) { CommunityWorlds.share(context, w.key, serverStyle, w.world) }
                            when (result) {
                                is CommunityWorlds.Result.Ok -> { CommunityState.setShared(context, w.key, result.value); communityRevision++; message = "Shared with the community" }
                                is CommunityWorlds.Result.Error -> message = "Could not share (${result.message})"
                            }
                        }
                    }) { androidx.compose.material3.Text("SHARE") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmShare = null }) { androidx.compose.material3.Text("CANCEL") } }
            )
        }
        message?.let {
            OutlinedText(
                it, fontSize = 14.sp, outline = null, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                    .background(Color(0xE6081028), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f
)

@Composable
private fun ModeCard(
    title: String,
    color: Color,
    caption: String,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.95f), lerp(color, Color.Black, 0.55f))))
            .border(3.dp, color.copy(alpha = 0.9f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(10.dp)
    ) {
        OutlinedText(title, fontSize = 18.sp, color = Color.White, outlineWidth = 8f)
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), content = content)
        OutlinedText(caption, fontSize = 12.sp, color = Color.White, outline = null, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SourceButton(text: String, icon: GameIcon, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF5BE67F), Color(0xFF1E9E45))))
            .border(2.dp, Color(0xFFBFFFD0), shape)
            .clickable(onClick = onClick)
    ) {
        IconGlyph(icon, size = 28.dp)
        OutlinedText(text, fontSize = 14.sp, color = Color.White, outline = null)
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    OutlinedText(
        text, fontSize = 13.sp, color = Color.White, outline = null,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Color(0xFF6A2BD9) else Color(0xFF1A2550))
            .border(2.dp, if (selected) Color(0xFFC9A7FF) else Color(0xFF3A4A8A), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}
