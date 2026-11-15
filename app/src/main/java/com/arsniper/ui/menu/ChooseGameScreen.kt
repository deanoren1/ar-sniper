package com.arsniper.ui.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.R
import com.arsniper.data.PlayerProfile
import com.arsniper.ui.components.AddCurrencyButton
import com.arsniper.ui.components.CurrencyItem
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.GameIcon
import com.arsniper.ui.components.GameIconButton
import com.arsniper.ui.components.NavyPanel
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.SpriteButton
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import java.io.File

// יחסי הגובה/רוחב של האמנות (כפי שנחתכה מגיליון הנכסים)
private const val LOGO_RATIO = 708f / 177f
private const val TITLE_RATIO = 738f / 116f
private const val JOURNEY_RATIO = 1400f / 1132f
private const val CARD_RATIO = 1300f / 308f

/**
 * CHOOSE YOUR GAME: נפתח אחרי PLAY במסך הראשי.
 * שמאל: WORLD JOURNEY (הכרטיס הגדול). ימין: CLASSIC, RANDOM, MY WORLDS. מעל: הלוגו והכותרת.
 * כל כרטיס הוא תמונה אחת שלמה ולחיצה על כל השטח שלו (כולל PLAY שבתוך WORLD JOURNEY) פותחת את המצב.
 * המונים ו-+ זהים למסך הראשי. כל המידות יחסיות לגובה המסך, והקבוצה מוקטנת אם המסך צר.
 */
@Composable
fun ChooseGameScreen(
    onBack: () -> Unit,
    onJourney: () -> Unit,
    onClassic: () -> Unit,
    onRandom: () -> Unit,
    onMyWorlds: () -> Unit,
    onShop: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerProfile.revision.hashCode()
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(GameColors.Navy900).clipToBounds()) {
        val w = maxWidth
        val h = maxHeight
        // רקע: גרסה מוכנה של תמונת התעלה: החלק העליון (שמיים ובניינים, הגדלה x1.2) בחצי העליון,
        // והשתקפות שלו כמו במים בחצי התחתון. מטושטש, כהה ובניגודיות גבוהה. ממלא את כל המסך בלי עיוות
        Image(
            painter = painterResource(R.drawable.cg_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val dir = LocalLayoutDirection.current
        val safeL = insets.calculateLeftPadding(dir)
        val safeR = insets.calculateRightPadding(dir)
        // אותה יחידה כמו במסך הראשי (קנבס 2560x1180), כדי שהמונים ו-+ יהיו באותו גודל בדיוק
        val unit = maxOf(w, h * (2560f / 1180f)) / 2560f
        val margin = maxOf(unit * 28f, 10.dp)

        // לוגו וכותרת: נמתחים לרוחב 40% מהמסך בלי לשנות את הגובה. הלוגו יושב על הכותרת מלמעלה
        val logoW = w * 0.40f
        val logoH = h * 0.14f
        val titleW = w * 0.40f
        val titleH = h * 0.115f
        val titleY = logoH * 0.80f

        // אזור הכרטיסים: מתחת לכותרת, עם מרווח כפול מלמטה. הכרטיסים שומרים על הפרופורציות שלהם
        val colGap = h * 0.03f
        val rowGap = h * 0.025f
        val areaTop = titleY + titleH + h * 0.02f
        val bottomGap = h * 0.05f
        val areaH = h - areaTop - bottomGap
        var journeyH = areaH
        var cardH = (areaH - rowGap * 2) / 3
        val naturalW = journeyH * JOURNEY_RATIO + colGap + cardH * CARD_RATIO
        val maxW = w - maxOf(margin, safeL) - maxOf(margin, safeR)
        // מסך צר: כל הקבוצה קטנה באותו יחס
        if (naturalW > maxW) {
            val k = maxW / naturalW
            journeyH *= k
            cardH *= k
        }
        val jW = journeyH * JOURNEY_RATIO
        val cW = cardH * CARD_RATIO
        val left = (w - (jW + colGap + cW)) / 2
        val top = areaTop + (areaH - journeyH) / 2

        val cards = listOf(
            Triple(R.drawable.cg_journey, "World Journey", onJourney),
            Triple(R.drawable.cg_classic, "Classic", onClassic),
            Triple(R.drawable.cg_random, "Random", onRandom),
            Triple(R.drawable.cg_myworlds, "My Worlds", onMyWorlds)
        )
        // מיקום וגודל של כל כרטיס: הראשון הוא WORLD JOURNEY, השאר בעמודה הימנית
        fun rect(i: Int): Pair<Pair<Dp, Dp>, Pair<Dp, Dp>> =
            if (i == 0) (left to top) to (jW to journeyH)
            else ((left + jW + colGap) to (top + (cardH + rowGap) * (i - 1))) to (cW to cardH)

        // זוהר: עותק מטושטש של כל כרטיס מאחוריו, בצבעים של הכרטיס עצמו (Android 12 ומעלה; בגרסאות ישנות אין טשטוש ולכן לא מצויר)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            cards.forEachIndexed { i, (sprite, _, _) ->
                val (pos, size) = rect(i)
                val gw = size.first * 1.03f
                val gh = size.second * 1.06f
                Image(
                    painter = painterResource(sprite), contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset(x = pos.first - (gw - size.first) / 2, y = pos.second - (gh - size.second) / 2)
                        .requiredSize(gw, gh)
                        .blur(size.second * 0.07f, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
                        .alpha(0.95f)
                )
            }
        }
        cards.forEachIndexed { i, (sprite, label, action) ->
            val (pos, size) = rect(i)
            SpriteButton(
                sprite = sprite, contentDescription = label, onClick = action,
                width = size.first, height = size.second,
                modifier = Modifier.offset(x = pos.first, y = pos.second)
            )
        }

        Image(
            painter = painterResource(R.drawable.cg_title), contentDescription = "Choose your game",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.offset(x = (w - titleW) / 2, y = titleY).requiredSize(titleW, titleH)
        )
        Image(
            painter = painterResource(R.drawable.cg_logo), contentDescription = "SnapBoo",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.offset(x = (w - logoW) / 2, y = 0.dp).requiredSize(logoW, logoH)
        )

        // כפתור חזרה בפינה השמאלית העליונה
        GameIconButton(
            GameIcon.Back, onClick = onBack, style = GameButtonStyle.Secondary, size = maxOf(unit * 110f, 40.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(start = maxOf(margin, safeL), top = maxOf(margin, insets.calculateTopPadding()))
        )
        // מטבעות, יהלומים ו-+: כמו במסך הראשי
        Row(
            horizontalArrangement = Arrangement.spacedBy(unit * 24f),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = maxOf(unit * 28f, insets.calculateTopPadding()), end = maxOf(unit * 28f, safeR))
        ) {
            CurrencyItem(R.drawable.ui_icon_coin, PlayerProfile.coins, "Coins", unit)
            CurrencyItem(R.drawable.ui_icon_gem, PlayerProfile.diamonds, "Gems", unit)
            AddCurrencyButton(onClick = onShop, unit = unit)
        }
    }
}

/**
 * RANDOM / MY WORLDS: הבורר המשותף פתוח על הלשונית המתאימה (בלי שורת הלשוניות), ו-PLAY כשנבחר עולם.
 * random = true: הגרלת עולם קהילה ציבורי. false: העולמות השמורים של השחקן.
 */
@Composable
fun WorldPlayScreen(
    random: Boolean,
    onBack: () -> Unit,
    onPlay: (source: File, style: com.arsniper.game.progress.ArenaStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val pick = remember(random) { WorldPickerState(if (random) 0 else 1) }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(GameColors.Navy900).clipToBounds()) {
        Image(
            painter = painterResource(R.drawable.cg_background), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Color(0xCC081028)))
        val screenH = maxHeight
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val dir = LocalLayoutDirection.current
        GameIconButton(
            GameIcon.Back, onClick = onBack, style = GameButtonStyle.Secondary, size = 44.dp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = maxOf(12.dp, insets.calculateLeftPadding(dir)), top = 12.dp)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.align(Alignment.Center).widthIn(max = 520.dp).fillMaxWidth(0.62f)
                .verticalScroll(rememberScrollState()).padding(vertical = 12.dp)
        ) {
            // כותרת: הכרטיס המעוצב של המצב (אותה אמנות כמו ב-CHOOSE YOUR GAME)
            val headerH = screenH * 0.2f
            Image(
                painter = painterResource(if (random) R.drawable.cg_random else R.drawable.cg_myworlds),
                contentDescription = if (random) "Random" else "My Worlds",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.size(headerH * CARD_RATIO, headerH)
            )
            NavyPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    WorldPicker(
                        state = pick,
                        library = emptyList(),
                        onNewPhoto = {},
                        onGallery = {},
                        showTabs = false
                    )
                    if (pick.error.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedText(pick.error, fontSize = 12.sp, color = Color(0xFFFFB4B4), outline = null)
                    }
                }
            }
            val world = pick.world
            GameButton(
                text = "PLAY",
                onClick = { if (world != null) onPlay(world.source, world.style) },
                enabled = world != null,
                icon = GameIcon.Play,
                style = GameButtonStyle.Success,
                height = 52.dp,
                fontSize = 22.sp,
                modifier = Modifier.fillMaxWidth(0.6f)
            )
        }
    }
}
