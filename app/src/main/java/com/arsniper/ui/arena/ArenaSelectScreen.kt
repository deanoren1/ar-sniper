package com.arsniper.ui.arena

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.arsniper.ui.components.StarRow
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import kotlinx.coroutines.flow.first

/** תמונות המפה לפי הסדר, משמאל לימין. כל אחת רבע מרוחב המפה */
private val MAP_TILES = listOf(
    R.drawable.journey_map_1,
    R.drawable.journey_map_2,
    R.drawable.journey_map_3,
    R.drawable.journey_map_4
)

/**
 * WORLD JOURNEY: מפה ארוכה שגוללים לרוחב, 18 עולמות בזיגזג עם גשרים ביניהם.
 * על כל רחבה: שם וכוכבים. העולם הנוכחי מסומן בסמן קופץ ובטבעת זוהרת, עולם נעול מעומעם עם מנעול,
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
                Row {
                    for (tile in MAP_TILES) {
                        Image(
                            painter = painterResource(tile),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.width(mapW / Journey.TILES).fillMaxHeight()
                        )
                    }
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
                    val cx = mapW * world.x
                    val cy = mapH * world.y
                    if (i == current) {
                        CurrentMarker(unit = mapH, plazaX = cx, plazaY = cy)
                    }
                    WorldNode(
                        world = world,
                        unlocked = unlocked,
                        stars = world.style?.let(stars) ?: 0,
                        unit = mapH,
                        modifier = Modifier.centerAt(cx, cy + mapH * 0.075f),
                        onClick = {
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

/** ממקם את הרכיב כך שהמרכז שלו בנקודה (x, y) בתוך המפה */
private fun Modifier.centerAt(x: Dp, y: Dp): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) {
        p.place(x.roundToPx() - p.width / 2, y.roundToPx() - p.height / 2)
    }
}

/** שלט עולם מתחת לרחבה: שם וכוכבים, מנעול כשנעול, SOON כשעוד לא קיים */
@Composable
private fun WorldNode(
    world: JourneyWorld,
    unlocked: Boolean,
    stars: Int,
    unit: Dp,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val exists = world.style != null
    val shape = RoundedCornerShape(unit * 0.03f)
    val font = (unit.value * 0.034f).sp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .alpha(if (unlocked) 1f else 0.82f)
            .background(
                when {
                    unlocked -> Color(0xE6102050)
                    exists -> Color(0xD90A1230)
                    else -> Color(0xCC1A1F33)
                }
            )
            .border(unit * 0.005f, if (unlocked) GameColors.Yellow else GameColors.Outline, shape)
            .padding(horizontal = unit * 0.022f, vertical = unit * 0.006f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!unlocked) {
                IconGlyph(GameIcon.Lock, size = unit * 0.04f, tint = GameColors.Ice)
                Spacer(Modifier.width(unit * 0.008f))
            }
            OutlinedText(world.title, fontSize = font, outline = null, color = if (unlocked) Color.White else GameColors.Ice)
        }
        if (exists) {
            StarRow(stars = stars, size = unit * 0.036f)
        } else {
            OutlinedText("SOON", fontSize = font * 0.8f, outline = null, color = GameColors.Slate)
        }
    }
}

/** העולם הנוכחי: טבעת זוהרת פועמת סביב הרחבה וסמן צהוב קופץ מעליה */
@Composable
private fun CurrentMarker(unit: Dp, plazaX: Dp, plazaY: Dp) {
    val pulse = rememberInfiniteTransition(label = "current")
    val t by pulse.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse"
    )
    val ringW = unit * 0.2f
    val ringH = unit * 0.09f
    Canvas(modifier = Modifier.centerAt(plazaX, plazaY).size(ringW, ringH)) {
        val grow = 1f + t * 0.12f
        val w = size.width * grow
        val h = size.height * grow
        val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
        drawOval(Color(0xFFFFE066).copy(alpha = 0.25f + 0.35f * t), topLeft = tl, size = androidx.compose.ui.geometry.Size(w, h))
        drawOval(
            Color(0xFFFFF3A0).copy(alpha = 0.9f), topLeft = tl, size = androidx.compose.ui.geometry.Size(w, h),
            style = Stroke(width = size.height * 0.08f)
        )
    }
    // סמן: עיגול צהוב עם כוכב, וחץ שמצביע על הרחבה. קופץ למעלה ולמטה
    val pinSize = unit * 0.075f
    val bob = unit * 0.02f * t
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.centerAt(plazaX, plazaY - unit * 0.1f - bob)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(pinSize)
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(Color(0xFFFFE066), Color(0xFFFFA81C))))
                .border(unit * 0.005f, Color.White, RoundedCornerShape(50))
        ) {
            IconGlyph(GameIcon.Play, size = pinSize * 0.5f, tint = Color(0xFF2A1A00))
        }
        Canvas(modifier = Modifier.size(pinSize * 0.45f, pinSize * 0.3f)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close()
            }
            drawPath(path, Color(0xFFFFA81C))
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
