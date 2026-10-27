package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.arsniper.game.engine.AccessoryKind
import com.arsniper.game.engine.AccessoryTarget
import com.arsniper.game.model.Enemy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** משך אפקט הפיצוץ (שניות) */
const val BLAST_SECONDS = 0.9f

/**
 * אפקט פיצוץ אחד. x ו-y בקואורדינטות עולם (0..1), radius ביחידות גובה מסך,
 * bornNanos = זמן הפריים שבו הפגיעה קרתה.
 */
class Blast(
    val x: Float,
    val y: Float,
    val kind: AccessoryKind,
    val label: String,
    val radius: Float,
    val bornNanos: Long
)

/** רדיוס הפגיעה ביחס לרדיוס הציור של האביזר (קצת נדיב, כי האביזר קטן) */
const val ACCESSORY_HIT_SCALE = 1.15f

/**
 * התיבה של מפלצת מצוירת במסך (בפיקסלים): מרכז אופקי, קצה הראש, הרגליים וחצי הרוחב.
 * האביזרים ממוקמים לפיה, כדי שיהיו צמודים למפלצת בכל גודל.
 */
class MonsterBox(val cx: Float, val headY: Float, val feetY: Float, val halfWidth: Float) {
    val height: Float get() = feetY - headY
}

/** מקום וגודל של אביזר במסך (פיקסלים): מרכז ורדיוס */
class AccessorySpot(val x: Float, val y: Float, val radius: Float)

/**
 * האביזרים שצוירו בפריים האחרון, במקום האמיתי שלהם. GameScreen בודק בהם פגיעה,
 * כך שמה שרואים הוא בדיוק מה שאפשר לפגוע בו. נכתב ונקרא רק מה-UI thread.
 */
object AccessoryRegistry {
    private val current = ArrayList<AccessoryTarget>()

    fun clear() {
        current.clear()
    }

    fun add(target: AccessoryTarget) {
        current.add(target)
    }

    fun snapshot(): List<AccessoryTarget> = ArrayList(current)
}

/**
 * איפה האביזר ביחס למפלצת: בלון מעל הראש על חוט, יהלום צף מעל הראש, פצצה בצד הגוף.
 * הגודל יחסי לגובה המפלצת (עם מינימום, כדי שגם מפלצת רחוקה תהיה בת-פגיעה בתקריב).
 */
fun accessorySpot(kind: AccessoryKind, box: MonsterBox, screenHeight: Float): AccessorySpot {
    val ar = max(box.height * 0.15f, screenHeight * 0.014f)
    return when (kind) {
        AccessoryKind.Balloon -> AccessorySpot(
            x = box.cx + box.halfWidth * 0.25f,
            y = box.headY - box.height * 0.28f - ar,
            radius = ar
        )
        AccessoryKind.Bomb -> AccessorySpot(
            x = box.cx + box.halfWidth + ar * 0.6f,
            y = box.feetY - box.height * 0.3f,
            radius = ar
        )
        AccessoryKind.Gem -> AccessorySpot(
            x = box.cx,
            y = box.headY - box.height * 0.1f - ar * 1.3f,
            radius = ar
        )
    }
}

private val STICKER_WHITE = Color.White

/**
 * מצייר את האביזר של מפלצת: בלון על חוט, פצצה עם פתיל בוער, או יהלום זוהר.
 * box = התיבה של המפלצת המצוירת, spot = המקום והגודל מ-accessorySpot.
 */
fun DrawScope.drawAccessory(
    kind: AccessoryKind,
    enemy: Enemy,
    box: MonsterBox,
    spot: AccessorySpot,
    anim: Float
) {
    val ar = spot.radius
    val ax = spot.x
    val ay = spot.y
    val outline = max(2f, ar * 0.16f)

    when (kind) {
        AccessoryKind.Balloon -> {
            val sway = sin(anim * 2.2f + enemy.id) * 0.1f * ar
            val bx = ax + sway
            // חוט מהראש אל הבלון
            drawLine(
                color = Color(0xFF37474F),
                start = Offset(box.cx + box.halfWidth * 0.2f, box.headY + box.height * 0.12f),
                end = Offset(bx, ay + ar * 1.05f),
                strokeWidth = max(1.5f, ar * 0.07f)
            )
            // מסגרת לבנה (סטיקר) ואז הבלון
            drawOval(
                color = STICKER_WHITE,
                topLeft = Offset(bx - ar * 0.85f - outline, ay - ar - outline),
                size = Size(ar * 1.7f + 2 * outline, ar * 2f + 2 * outline)
            )
            drawOval(
                color = Color(0xFFE53935),
                topLeft = Offset(bx - ar * 0.85f, ay - ar),
                size = Size(ar * 1.7f, ar * 2f)
            )
            drawOval(
                color = Color.White.copy(alpha = 0.5f),
                topLeft = Offset(bx - ar * 0.5f, ay - ar * 0.75f),
                size = Size(ar * 0.35f, ar * 0.55f)
            )
            val knot = Path().apply {
                moveTo(bx, ay + ar * 0.95f)
                lineTo(bx - ar * 0.2f, ay + ar * 1.2f)
                lineTo(bx + ar * 0.2f, ay + ar * 1.2f)
                close()
            }
            drawPath(knot, Color(0xFFB71C1C))
        }

        AccessoryKind.Bomb -> {
            drawCircle(STICKER_WHITE, ar + outline, Offset(ax, ay))
            drawCircle(Color(0xFF263238), ar, Offset(ax, ay))
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = ar * 0.25f,
                center = Offset(ax - ar * 0.38f, ay - ar * 0.4f)
            )
            // פתיל ונצנוץ
            val fuseStart = Offset(ax + ar * 0.3f, ay - ar * 0.85f)
            val fuseEnd = Offset(ax + ar * 0.75f, ay - ar * 1.45f)
            drawLine(
                color = Color(0xFF8D6E63),
                start = fuseStart,
                end = fuseEnd,
                strokeWidth = max(2f, ar * 0.14f)
            )
            val flicker = 0.22f + 0.1f * sin(anim * 25f + enemy.id)
            drawCircle(Color(0xFFFF6F00), ar * (flicker + 0.12f), fuseEnd)
            drawCircle(Color(0xFFFFEB3B), ar * flicker, fuseEnd)
        }

        AccessoryKind.Gem -> {
            val pulse = 1f + 0.1f * sin(anim * 4f + enemy.id)
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.14f + 0.07f * sin(anim * 4f + enemy.id)),
                radius = ar * 1.5f * pulse,
                center = Offset(ax, ay)
            )
            val gem = Path().apply {
                moveTo(ax - ar * 0.6f, ay - ar * 0.65f)
                lineTo(ax + ar * 0.6f, ay - ar * 0.65f)
                lineTo(ax + ar, ay - ar * 0.1f)
                lineTo(ax, ay + ar)
                lineTo(ax - ar, ay - ar * 0.1f)
                close()
            }
            drawPath(gem, STICKER_WHITE, style = Stroke(width = outline * 2f))
            drawPath(gem, Color(0xFF18FFFF))
            // פאות
            val facet = Color.White.copy(alpha = 0.55f)
            val facetWidth = max(1.5f, ar * 0.06f)
            drawLine(facet, Offset(ax - ar * 0.6f, ay - ar * 0.65f), Offset(ax - ar * 0.3f, ay - ar * 0.1f), facetWidth)
            drawLine(facet, Offset(ax + ar * 0.6f, ay - ar * 0.65f), Offset(ax + ar * 0.3f, ay - ar * 0.1f), facetWidth)
            drawLine(facet, Offset(ax - ar, ay - ar * 0.1f), Offset(ax + ar, ay - ar * 0.1f), facetWidth)
            drawLine(facet, Offset(ax - ar * 0.3f, ay - ar * 0.1f), Offset(ax, ay + ar), facetWidth)
            drawLine(facet, Offset(ax + ar * 0.3f, ay - ar * 0.1f), Offset(ax, ay + ar), facetWidth)
            // נצנוץ
            val twinkle = ar * (0.12f + 0.1f * (0.5f + 0.5f * sin(anim * 6f + enemy.id)))
            drawCircle(Color.White, twinkle, Offset(ax - ar * 0.3f, ay - ar * 0.4f))
        }
    }
}

private fun easeOut(p: Float): Float {
    val q = 1f - p
    return 1f - q * q * q
}

/**
 * שכבת האפקטים: פיצוצים. נמצאת בתוך שכבת העולם, ולכן עוברת זום ותזוזה יחד עם התמונה.
 * nowNanos = זמן הפריים הנוכחי (מהלולאה של המשחק), שמפעיל את האנימציה.
 */
@Composable
fun EffectsLayer(blasts: List<Blast>, nowNanos: Long, modifier: Modifier = Modifier) {
    val labelImages = LabelArt.load()
    Canvas(modifier = modifier.fillMaxSize()) {
        for (blast in blasts) {
            val age = (nowNanos - blast.bornNanos) / 1e9f
            if (age < 0f || age > BLAST_SECONDS) continue
            val p = age / BLAST_SECONDS
            val e = easeOut(p)
            val cx = blast.x * size.width
            val cy = blast.y * size.height
            val maxR = max(blast.radius * size.height, size.height * 0.06f)

            val main: Color
            val second: Color
            when (blast.kind) {
                AccessoryKind.Bomb -> {
                    main = Color(0xFFFF5722)
                    second = Color(0xFFFFC107)
                }
                AccessoryKind.Balloon -> {
                    main = Color(0xFFE53935)
                    second = Color(0xFFFFCDD2)
                }
                AccessoryKind.Gem -> {
                    main = Color(0xFF18FFFF)
                    second = Color(0xFFFFFFFF)
                }
            }

            // כדור אש (פצצה) או הילה (בלון, יהלום)
            if (blast.kind == AccessoryKind.Bomb) {
                drawCircle(main.copy(alpha = (1f - p) * 0.75f), maxR * 0.75f * e, Offset(cx, cy))
                drawCircle(second.copy(alpha = (1f - p) * 0.9f), maxR * 0.45f * e, Offset(cx, cy))
            } else {
                drawCircle(second.copy(alpha = (1f - p) * 0.5f), maxR * 0.5f * e, Offset(cx, cy))
            }

            // טבעת הלם
            drawCircle(
                color = main.copy(alpha = (1f - p) * 0.9f),
                radius = maxR * e,
                center = Offset(cx, cy),
                style = Stroke(width = maxR * 0.12f * (1f - p) + 2f)
            )

            // ניצוצות וחלקיקים
            val count = 14
            for (i in 0 until count) {
                val angle = (2.0 * PI * i / count).toFloat() + 0.3f * (i % 3)
                val variation = 0.7f + 0.3f * ((i * 37) % 10) / 10f
                val distance = maxR * (0.3f + 0.9f * e) * variation
                val particleSize = maxR * 0.07f * (1f - p) + 2f
                drawCircle(
                    color = (if (i % 2 == 0) main else second).copy(alpha = 1f - p),
                    radius = particleSize,
                    center = Offset(cx + cos(angle) * distance, cy + sin(angle) * distance)
                )
            }

            // כיתוב עולה למעלה
            val textSize = size.height * 0.07f * (0.8f + 0.4f * min(1f, p * 6f))
            val textY = cy - maxR * 0.3f - p * size.height * 0.12f
            val alpha = (255f * (1f - p * p)).toInt().coerceIn(0, 255)
            val art = labelImages[blast.label]
            if (blast.label != "BLOCK!") continue
            if (art != null) {
                val w = size.height * LabelArt.widthFor(blast.label) * (0.85f + 0.3f * min(1f, p * 6f))
                val h = w * art.height / art.width
                drawImage(
                    art,
                    dstOffset = androidx.compose.ui.unit.IntOffset((cx - w / 2f).toInt(), (textY - h / 2f).toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()),
                    alpha = (1f - p * p).coerceIn(0f, 1f)
                )
                continue
            }
            drawIntoCanvas { canvas ->
                val fill = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    this.textSize = textSize
                    color = android.graphics.Color.WHITE
                    this.alpha = alpha
                }
                val stroke = android.graphics.Paint(fill).apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = textSize * 0.16f
                    color = android.graphics.Color.BLACK
                    this.alpha = alpha
                }
                canvas.nativeCanvas.drawText(blast.label, cx, textY, stroke)
                canvas.nativeCanvas.drawText(blast.label, cx, textY, fill)
            }
        }
    }
}
/**
 * נקודות התורפה שצוירו בפריים האחרון (ראו AccessoryRegistry): GameScreen בודק בהן פגיעה,
 * כך שמה שרואים הוא בדיוק מה שאפשר לפגוע בו. נכתב ונקרא רק מה-UI thread.
 */
object ZoneRegistry {
    private val current = ArrayList<com.arsniper.game.engine.ZoneTarget>()

    fun clear() {
        current.clear()
    }

    fun add(target: com.arsniper.game.engine.ZoneTarget) {
        current.add(target)
    }

    fun snapshot(): List<com.arsniper.game.engine.ZoneTarget> = ArrayList(current)
}
