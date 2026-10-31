package com.arsniper.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.arsniper.R

/**
 * מסגרת ה-HUD של הטונדרה: חמישה חלקים נפרדים, כל אחד דבוק לפינה או לאמצע שלו ומעוטף סביב רכיב ה-HUD שלו.
 * החלקים לא נמתחים (קנה מידה אחיד לפי רוחב המסך), והרכיבים מוקטנים כך שיכנסו בחריץ של החלק.
 * הקואורדינטות (px) הן של תמונת המקור 2340x1080 וביחס לפינת החלק.
 */
@Composable
fun BoxScope.TundraHudFrame(
    currency: @Composable () -> Unit,
    hearts: @Composable () -> Unit,
    zoom: @Composable () -> Unit,
    joystick: @Composable () -> Unit,
    fire: @Composable () -> Unit,
    ammo: @Composable () -> Unit
) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val d = (widthDp / 2340f * 1.2f).coerceIn(0.25f, 0.62f)
    @Composable
    fun Piece(res: Int, align: Alignment, w: Int, h: Int, content: @Composable BoxScope.() -> Unit) {
        Box(Modifier.align(align).size((w * d).dp, (h * d).dp)) {
            Image(painterResource(res), null, Modifier.size((w * d).dp, (h * d).dp), contentScale = ContentScale.FillBounds)
            content()
        }
    }
    @Composable
    fun Slot(x: Int, y: Int, w: Int, h: Int, content: @Composable () -> Unit) {
        Box(Modifier.offset((x * d).dp, (y * d).dp).size((w * d).dp, (h * d).dp)) {
            FitSlot(content)
        }
    }
    Piece(R.drawable.tundra_hud_tl, Alignment.TopStart, 560, 200) { Slot(102, 53, 356, 63, currency) }
    Piece(R.drawable.tundra_hud_tc, Alignment.TopCenter, 780, 200) { Slot(145, 38, 487, 81, hearts) }
    Piece(R.drawable.tundra_hud_tr, Alignment.TopEnd, 560, 200) { Slot(101, 53, 355, 68, zoom) }
    Piece(R.drawable.tundra_hud_bl, Alignment.BottomStart, 700, 520) { Slot(100, 206, 233, 233, joystick) }
    Piece(R.drawable.tundra_hud_br, Alignment.BottomEnd, 700, 520) {
        Slot(356, 206, 230, 230, fire)
        Slot(600, 155, 60, 205, ammo)
    }
}

/** מקטין את התוכן (בלי לעוות) כך שיכנס בדיוק בתיבה שמקיפה אותו; התוכן ממורכז. מגע ממשיך לעבוד כרגיל */
@Composable
private fun FitSlot(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val cw = (placeables.maxOfOrNull { it.width } ?: 1).coerceAtLeast(1)
        val ch = (placeables.maxOfOrNull { it.height } ?: 1).coerceAtLeast(1)
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val s = minOf(w / cw.toFloat(), h / ch.toFloat())
        layout(w, h) {
            placeables.forEach {
                it.placeRelativeWithLayer((w - it.width) / 2, (h - it.height) / 2) {
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin.Center
                }
            }
        }
    }
}
