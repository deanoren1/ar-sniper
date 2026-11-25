package com.arsniper.ui.arena

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.R
import com.arsniper.data.PlayerProfile
import com.arsniper.game.progress.ArenaStyle
import com.arsniper.game.progress.Journey
import com.arsniper.game.progress.JourneyWorld
import com.arsniper.ui.components.AddCurrencyButton
import com.arsniper.ui.components.CurrencyItem
import com.arsniper.ui.components.GameIcon
import com.arsniper.ui.components.GameIconButton
import com.arsniper.ui.components.IconGlyph
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import kotlinx.coroutines.flow.first

/** תמונות המפה לפי הסדר, משמאל לימין. כל אחת חמישית מרוחב המפה */
private val MAP_TILES = listOf(
    R.drawable.journey_map_1,
    R.drawable.journey_map_2,
    R.drawable.journey_map_3,
    R.drawable.journey_map_4,
    R.drawable.journey_map_5
)

/**
 * WORLD JOURNEY: מפה ארוכה שגוללים לרוחב, 18 עולמות בזיגזג עם גשרים ביניהם.
 * על כל רחבה: שלט עם שם וכוכבים. עולם נעול מעומעם עם מנעול,
 * עולם שעוד לא קיים במשחק מסומן SOON. שערי פרקים על הגשרים נפתחים לפי סך הכוכבים.
 * למעלה: חזרה, מונה כוכבים, מטבעות ויהלומים ו-+. רמת הקושי נבחרת במסך הבא.
 * בכניסה המפה נגללת לבד לעולם הנוכחי. (onMyPhotos, onArmory, onMultiplayer נשארו לתאימות ולא מוצגים)
 */
@Composable
fun ArenaSelectScreen(
    onPick: (ArenaStyle) -> Unit,
    onBack: () -> Unit,
    onMyPhotos: (() -> Unit)? = null,
    onShop: (() -> Unit)? = null,
    onArmory: (() -> Unit)? = null,
    onMultiplayer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    PlayerProfile.revision.hashCode()
    val total = PlayerProfile.totalStars
    val stars: (ArenaStyle) -> Int = { PlayerProfile.starsOf(it) }
    val current = Journey.currentIndex(stars, total)

    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hint) {
        if (hint != null) {
            kotlinx.coroutines.delay(2500)
            hint = null
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(GameColors.Navy900)) {
        val screenW = maxWidth
        val mapH = maxHeight
        val mapW = mapH * (Journey.MAP_W / Journey.MAP_H)
        val scroll = rememberScrollState()
        val density = LocalDensity.current

        // בכניסה: גוללים כך שהעולם הנוכחי באמצע המסך
        LaunchedEffect(Unit) {
            val max = snapshotFlow { scroll.maxValue }.first { it > 0 && it < Int.MAX_VALUE }
            val target = with(density) { (mapW * Journey.worlds[current].x - screenW / 2).toPx() }
            scroll.scrollTo(target.toInt().coerceIn(0, max))
        }

        Box(modifier = Modifier.fillMaxSize().horizontalScroll(scroll)) {
            Box(modifier = Modifier.width(mapW).fillMaxHeight()) {
                // התמונות חופפות בפיקסל אחד, כדי שלא יופיע קו דק בחיבור ביניהן
                MAP_TILES.forEachIndexed { i, tile ->
                    Image(
                        painter = painterResource(tile),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .offset(x = mapW / Journey.TILES * i)
                            .width(mapW / Journey.TILES + 1.dp)
                            .fillMaxHeight()
                    )
                }

                // לחיצה על העולם: על כל האי או על השלט שלו
                val openWorld: (Int) -> Unit = { i ->
                    val world = Journey.worlds[i]
                    val unlocked = Journey.isUnlocked(i, stars, total)
                    val style = world.style
                    when {
                        style == null -> hint = "${world.title}: coming soon"
                        unlocked -> onPick(style)
                        total < Journey.gateStarsFor(i) && i >= Journey.gates.first().firstIndex ->
                            hint = "Collect ${Journey.gateStarsFor(i)} stars in total to open ${world.title} (you have $total)"
                        else -> {
                            val prev = (i - 1 downTo 0).firstOrNull { Journey.worlds[it].style != null }
                            hint = if (prev != null) "Win ${Journey.worlds[prev].title} with at least 1 star to open ${world.title}"
                            else "${world.title} is locked"
                        }
                    }
                }

                // אזור לחיצה שקוף על כל אי: האי נמצא סביב הרחבה ומעליה
                Journey.worlds.forEachIndexed { i, world ->
                    Box(
                        modifier = Modifier
                            .centerAt(mapW * world.x, mapH * world.y - mapH * (if (world.y < 0.5f) ISLAND_RISE_HIGH else ISLAND_RISE_LOW))
                            .size(mapH * ISLAND_W, mapH * ISLAND_H)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { openWorld(i) }
                    )
                }

                // שערי פרקים: מוצגים רק כל עוד הם סגורים
                for (gate in Journey.gates) {
                    if (total < gate.stars) {
                        GateBadge(
                            stars = gate.stars,
                            unit = mapH,
                            modifier = Modifier.centerAt(mapW * gate.x, mapH * gate.y),
                            onClick = { hint = "Collect ${gate.stars} stars in total to open the next chapter (you have $total)" }
                        )
                    }
                }

                // איים בערפל: עולמות שיגיעו בהמשך
                for ((fx, fy) in Journey.fogIslands) {
                    SoonBadge(
                        unit = mapH,
                        modifier = Modifier.centerAt(mapW * fx, mapH * fy + mapH * 0.075f),
                        onClick = { hint = "New worlds are coming soon" }
                    )
                }

                Journey.worlds.forEachIndexed { i, world ->
                    val unlocked = Journey.isUnlocked(i, stars, total)
                    WorldNode(
                        index = i,
                        world = world,
                        unlocked = unlocked,
                        stars = world.style?.let(stars) ?: 0,
                        unit = mapH,
                        // השלט ממורכז על העולם, והקצה העליון שלו בחלק העליון של העיגול
                        modifier = Modifier.centerAt(mapW * world.x, mapH * (world.y + LABEL_DROP)),
                        onClick = { openWorld(i) }
                    )
                }
            }
        }

        // פס כהה עדין למעלה, כדי שהכפתורים יבלטו על השמיים
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(mapH * 0.2f)
                .background(Brush.verticalGradient(listOf(Color(0x99081028), Color.Transparent)))
        )

        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val dir = LocalLayoutDirection.current
        val safeL = insets.calculateLeftPadding(dir)
        val safeR = insets.calculateRightPadding(dir)
        // אותה יחידה כמו במסך הראשי ובמסך CHOOSE YOUR GAME (קנבס 2560x1180)
        val unit = maxOf(screenW, mapH * (2560f / 1180f)) / 2560f
        val margin = maxOf(unit * 28f, 10.dp)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(unit * 30f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = maxOf(margin, safeL), top = maxOf(margin, insets.calculateTopPadding()))
        ) {
            GameIconButton(GameIcon.Back, onClick = onBack, style = GameButtonStyle.Secondary, size = maxOf(unit * 110f, 40.dp))
            StarCounter(total = total, max = Journey.maxStars, unit = unit)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(unit * 24f),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = maxOf(unit * 28f, insets.calculateTopPadding()), end = maxOf(unit * 28f, safeR))
        ) {
            CurrencyItem(R.drawable.ui_icon_coin, PlayerProfile.coins, "Coins", unit)
            CurrencyItem(R.drawable.ui_icon_gem, PlayerProfile.diamonds, "Gems", unit)
            if (onShop != null) AddCurrencyButton(onClick = onShop, unit = unit)
        }

        hint?.let {
            OutlinedText(
                text = it,
                fontSize = 14.sp,
                outline = null,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE6081028))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

/** אזור הלחיצה של אי, ביחידות של גובה המפה: רוחב, גובה, וכמה מרכז האי מעל הרחבה (באיים העליונים הרחבה בתחתית האי) */
private const val ISLAND_W = 0.5f
private const val ISLAND_H = 0.5f
private const val ISLAND_RISE_HIGH = 0.14f
private const val ISLAND_RISE_LOW = 0.02f

/** כמה מרכז השלט מתחת למרכז העיגול (ביחידות של גובה המפה): חצי גובה שלט פחות חצי גובה העיגול */
private const val LABEL_DROP = 0.03f

/** ממקם את הרכיב כך שהמרכז שלו בנקודה (x, y) בתוך המפה */
private fun Modifier.centerAt(x: Dp, y: Dp): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) {
        p.place(x.roundToPx() - p.width / 2, y.roundToPx() - p.height / 2)
    }
}

/** מיקום כל שלט בגיליונות השלטים (x, y, רוחב, גובה), לפי סדר העולמות במסע */
private val LABEL_RECTS = intArrayOf(
    0, 0, 357, 165, 357, 0, 357, 165, 714, 0, 357, 165, 1071, 0, 357, 165, 1428, 0, 357, 165, 1785, 0, 357, 165,
    1, 211, 356, 168, 357, 211, 357, 168, 714, 211, 357, 168, 1071, 211, 357, 168, 1428, 211, 357, 168, 1785, 210, 357, 169,
    1, 426, 356, 167, 357, 426, 357, 167, 714, 426, 357, 167, 1071, 426, 357, 169, 1428, 426, 357, 169, 1785, 425, 357, 168
)

/** בשלט: איפה מתחיל פס הכוכבים (חלק מהגובה), ואיפה נגמרים הכוכב הראשון והשני (חלק מהרוחב) */
private const val STAR_BAR_TOP = 0.55f
private val STAR_CUTS = floatArrayOf(0f, 0.44f, 0.605f, 1f)

/** אפור לעולמות שעוד לא קיימים במשחק */
private val GRAY = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
    androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
)

/**
 * שלט עולם על הרחבה העגולה של העולם, מהאמנות המצוירת: השלט עם הכוכבים הריקים, ומעליו הכוכבים המלאים לפי מה שהושג.
 * נעול: מעומעם עם מנעול. עולם שעוד לא קיים במשחק: אפור עם SOON.
 */
@Composable
private fun WorldNode(
    index: Int,
    world: JourneyWorld,
    unlocked: Boolean,
    stars: Int,
    unit: Dp,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val exists = world.style != null
    val full = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.journey_labels_full)
    val empty = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.journey_labels_empty)
    val rx = LABEL_RECTS[index * 4]
    val ry = LABEL_RECTS[index * 4 + 1]
    val rw = LABEL_RECTS[index * 4 + 2]
    val rh = LABEL_RECTS[index * 4 + 3]
    val w = unit * 0.34f
    val h = w * (rh.toFloat() / rw)
    Box(
        modifier = modifier
            .size(w, h)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().alpha(if (unlocked) 1f else 0.8f)) {
            val dst = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
            val src = androidx.compose.ui.unit.IntOffset(rx, ry)
            val srcSize = androidx.compose.ui.unit.IntSize(rw, rh)
            drawImage(empty, srcOffset = src, srcSize = srcSize, dstSize = dst, colorFilter = if (exists) null else GRAY)
            val k = stars.coerceIn(0, 3)
            if (k > 0) {
                clipRect(left = 0f, top = size.height * STAR_BAR_TOP, right = size.width * STAR_CUTS[k], bottom = size.height) {
                    drawImage(full, srcOffset = src, srcSize = srcSize, dstSize = dst)
                }
            }
        }
        if (!unlocked) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = w * 0.06f, bottom = h * 0.04f)
                    .size(h * 0.42f)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xE6081028))
                    .border(unit * 0.004f, GameColors.Ice, RoundedCornerShape(50))
            ) {
                IconGlyph(GameIcon.Lock, size = h * 0.26f, tint = Color.White)
            }
        }
        if (!exists) {
            OutlinedText(
                "SOON", fontSize = (unit.value * 0.026f).sp, color = Color.White, outline = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = w * 0.03f, bottom = h * 0.06f)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xE6081028))
                    .padding(horizontal = unit * 0.012f)
            )
        }
    }
}

/** שער פרק סגור על הגשר: מנעול וכמה כוכבים צריך בסך הכול */
@Composable
private fun GateBadge(stars: Int, unit: Dp, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(unit * 0.03f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .background(Color(0xF0101A3A))
            .border(unit * 0.006f, GameColors.Ice, shape)
            .padding(horizontal = unit * 0.02f, vertical = unit * 0.01f)
    ) {
        IconGlyph(GameIcon.Lock, size = unit * 0.045f, tint = Color.White)
        Spacer(Modifier.width(unit * 0.01f))
        IconGlyph(GameIcon.Star, size = unit * 0.042f, tint = GameColors.Yellow)
        Spacer(Modifier.width(unit * 0.006f))
        OutlinedText("$stars", fontSize = (unit.value * 0.04f).sp, color = Color.White)
    }
}

/** אי בערפל: עולם עתידי */
@Composable
private fun SoonBadge(unit: Dp, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(unit * 0.03f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .background(Color(0x99202A44))
            .padding(horizontal = unit * 0.02f, vertical = unit * 0.008f)
    ) {
        IconGlyph(GameIcon.Lock, size = unit * 0.035f, tint = Color.White)
        Spacer(Modifier.width(unit * 0.008f))
        OutlinedText("COMING SOON", fontSize = (unit.value * 0.03f).sp, outline = null, color = Color.White)
    }
}

/** מונה הכוכבים הכולל, ליד כפתור החזרה */
@Composable
private fun StarCounter(total: Int, max: Int, unit: Dp) {
    val h = maxOf(unit * 90f, 32.dp)
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(h)
            .clip(shape)
            .background(Color(0xCC0B1B52))
            .border(2.dp, GameColors.Yellow.copy(alpha = 0.7f), shape)
            .padding(horizontal = h * 0.3f)
    ) {
        IconGlyph(GameIcon.Star, size = h * 0.7f, tint = GameColors.Yellow)
        Spacer(Modifier.width(h * 0.15f))
        OutlinedText(
            "$total/$max",
            fontSize = (h.value * 0.5f).sp,
            color = Color.White,
            outline = Color(0xFF0B1B52),
            outlineWidth = 6f
        )
    }
}
