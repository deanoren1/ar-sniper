package com.arsniper.ui.game

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.arsniper.game.engine.Accessories
import com.arsniper.game.engine.AccessoryTarget
import com.arsniper.game.engine.Occlusion
import com.arsniper.game.engine.SceneContext
import com.arsniper.game.engine.WINDOW_HIDE_OFFSET
import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.model.EnemyState
import com.arsniper.game.model.Vec2
import com.arsniper.game.spots.HidingSpot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** משך אנימציית הפגיעה (שניות): המפלצת נופלת עם כוכבים, ואז נעלמת בענן */
private const val DEATH_SECONDS = 0.7f

/** גובה דמות אדם ביחידות רדיוס (ראש עד רגליים), כמו במנוע */
private const val FIGURE_HEIGHT_RADII = 2.2f

/** הגדלה כללית של המפלצות ביחס לגודל "אדם" באותו מקום */
private const val MONSTER_SCALE = 1.1f

/** הרוחב המקסימלי של מפלצת (הפריים הרחב ביותר שלה) ביחידות רדיוס */
private const val MAX_WIDTH_RADII = 3.0f

/** כמה פריימי הליכה מתחלפים בכל מחזור של phase (2π) */
private const val FRAMES_PER_CYCLE = 6f

private class Dying(val since: Double, val enemy: Enemy)

private class DrawItem(val enemy: Enemy, val dead: Boolean, val progress: Float)

/**
 * ציור האויבים: מפלצות מצוירות (ספרייטים), ואם הקבצים לא קיימים בפרויקט: דמות אדם פשוטה.
 * מציץ: החלק שנמצא "בתוך" העצם נחתך, כך שנראה שהוא מאחורי העצם.
 * דמות הולכת: עצם שקרוב יותר למצלמה (נוגע ברצפה נמוך מהרגליים שלה) מכסה אותה.
 * חלון: הדמות נחתכת באדן החלון.
 * פגיעה: המפלצת מוצגת בתנוחת פגיעה עם כוכבים ונעלמת בענן.
 */
@Composable
fun EnemyLayer(
    enemies: List<Enemy>,
    scene: SceneContext?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spritesAvailable = remember { MonsterSprites.isAvailable(context) }
    val spritesState = produceState<MonsterSprites?>(null, context) {
        if (spritesAvailable) {
            value = withContext(Dispatchers.Default) { MonsterSprites.load(context) }
        }
    }
    val sprites = spritesState.value

    val maskCache = remember(scene) { HashMap<Int, ImageBitmap>() }
    // אויבים שנפגעו: זמן הפגיעה והמצב שלהם, כדי שאנימציית הפגיעה תסתיים גם אם הם כבר הוסרו מהרשימה
    val dying = remember(scene) { HashMap<Int, Dying>() }
    val finished = remember(scene) { HashSet<Int>() }

    Canvas(modifier = modifier.fillMaxSize()) {
        // האביזרים נרשמים מחדש בכל ציור, כדי שהפגיעה תתאים למה שרואים עכשיו
        AccessoryRegistry.clear()

        // הספרייטים עוד נטענים: לא מציירים כלום (כדי לא להבהב דמות אדם לרגע)
        if (spritesAvailable && sprites == null) return@Canvas

        val nowS = System.nanoTime() / 1e9
        val anim = (nowS % 1000.0).toFloat()

        for (e in enemies) {
            if (!e.isAlive && !finished.contains(e.id) && !dying.containsKey(e.id)) {
                dying[e.id] = Dying(nowS, e)
            }
        }
        val iterator = dying.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowS - entry.value.since > DEATH_SECONDS) {
                finished.add(entry.key)
                iterator.remove()
            }
        }

        val items = ArrayList<DrawItem>()
        for (e in enemies) {
            if (e.isAlive) items.add(DrawItem(e, false, 0f))
        }
        if (sprites != null) {
            for ((_, d) in dying) {
                items.add(DrawItem(d.enemy, true, ((nowS - d.since) / DEATH_SECONDS).toFloat().coerceIn(0f, 1f)))
            }
        }
        // מהרחוק לקרוב: הדמות שרגליה נמוכות יותר במסך (קרובה יותר) מצוירת אחרונה, מעל האחרות
        items.sortBy { depthKey(it.enemy) }

        for (item in items) {
            val enemy = item.enemy
            val isPeeker = enemy.kind == EnemyKind.Peek || enemy.kind == EnemyKind.Window
            if (!item.dead) {
                if (isPeeker && enemy.exposure < 0.02f) continue
                // דמות הולכת שמחכה מוסתרת מאחורי עצם: לא מציירים כלל
                if (enemy.kind == EnemyKind.Open && enemy.spot != null && enemy.state == EnemyState.Idle) continue
            }

            val center = Offset(size.width * enemy.position.x, size.height * enemy.position.y)
            val radius = size.height * enemy.radius
            val spot = enemy.spot

            // התאמה לתאורה בתמונה: באזור כהה האויב כהה יותר, באזור בהיר בהיר יותר
            val shade = if (scene != null) {
                (0.55f + 0.6f * scene.lumaAt(enemy.position.x, enemy.position.y)).coerceIn(0.55f, 1.1f)
            } else {
                1f
            }
            val gait = Gait.of(enemy)

            // הפונקציה שמציירת את הדמות עצמה (מפלצת או דמות פשוטה), עם או בלי צל על הקרקע
            fun figure(withShadow: Boolean): DrawScope.() -> Unit = {
                if (sprites != null) {
                    drawMonster(
                        sheet = sprites.forEnemy(enemy.id),
                        enemy = enemy,
                        dead = item.dead,
                        progress = item.progress,
                        center = center,
                        r = radius,
                        shade = shade,
                        shadow = withShadow,
                        anim = anim
                    )
                } else {
                    drawPerson(center, radius, shade, withShadow, gait)
                }
            }

            if (enemy.kind == EnemyKind.Window) {
                // יוצא מחלון: הדמות נחתכת באדן החלון, כך שנראה שהיא עומדת מאחוריו
                val sillY = (enemy.hidePos.y - WINDOW_HIDE_OFFSET * enemy.radius) * size.height
                val draw = figure(false)
                clipRect(0f, 0f, size.width, sillY) {
                    draw()
                }
            } else if (isPeeker && spot != null && scene != null) {
                val mask = maskCache.getOrPut(spot.id) { buildMask(spot) }
                // אדם שעולה מאחורי עצם נמוך (תנועה אנכית): נחתך בקצה התחתון של העצם
                val rising = isPeeker && enemy.hidePos.y != enemy.peekPos.y
                val clipBottom = if (rising) {
                    spot.columnBottom(scene.mapper.photoX(enemy.position.x))
                        ?.let { scene.mapper.worldY(it) * size.height }
                } else {
                    null
                }
                drawFigureBehind(figure(!rising), spot, mask, scene, clipBottom)
            } else {
                // עצמים שקרובים יותר למצלמה מכסים דמות הולכת שנמצאת מאחוריהם (לפי עומק הרגליים)
                val blockers = if (scene != null && !item.dead) Occlusion.nearerSpots(scene, enemy) else emptyList()
                if (scene != null && blockers.isNotEmpty()) {
                    val masks = blockers.map { maskCache.getOrPut(it.id) { buildMask(it) } }
                    drawFigureBehindAll(figure(true), blockers, masks, scene)
                } else {
                    val draw = figure(true)
                    draw()
                }
                // נקודת תורפה שמוסתרת מאחורי עצם לא מצוירת ולא ניתנת לפגיעה
                fun covered(px: Float, py: Float): Boolean =
                    scene != null && blockers.isNotEmpty() &&
                        Occlusion.covered(scene, blockers, px / size.width, py / size.height)
                // נקודת תורפה: בלון, פצצה או יהלום ליד המפלצת
                if (!item.dead && sprites != null && Accessories.isActive(enemy)) {
                    val box = monsterBox(sprites.forEnemy(enemy.id), center, radius)
                    val kind = Accessories.kindOf(enemy)
                    val accessory = accessorySpot(kind, box, size.height)
                    val accessoryCovered = covered(accessory.x, accessory.y)
                    if (!accessoryCovered) drawAccessory(kind, enemy, box, accessory, anim)
                    if (!accessoryCovered) AccessoryRegistry.add(
                        AccessoryTarget(
                            enemy = enemy,
                            kind = kind,
                            anchor = Vec2(accessory.x / size.width, accessory.y / size.height),
                            hitRadius = accessory.radius * ACCESSORY_HIT_SCALE / size.height,
                            bodyHeight = box.height / size.height
                        )
                    )
                }
            }
        }
    }
}

/**
 * מפתח עומק לסדר הציור: גובה הרגליים במסך. מציץ/חלון: לפי מקום ההסתתרות (לא לפי מצב ההצצה הנוכחי),
 * כדי שהסדר לא יקפוץ תוך כדי עלייה.
 */
private fun depthKey(enemy: Enemy): Float =
    if (enemy.kind == EnemyKind.Peek || enemy.kind == EnemyKind.Window) {
        enemy.hidePos.y + enemy.radius
    } else {
        enemy.position.y + enemy.radius
    }

/** תנועת הליכה: phase = שלב המחזור, swing = כמה חזק מנופפים (0 = עומד, 1 = הליכה, 1.6 = ריצה) */
private class Gait(val phase: Float, val swing: Float) {
    companion object {
        val STILL = Gait(0f, 0f)

        fun of(enemy: Enemy): Gait {
            if (enemy.kind == EnemyKind.Open) {
                if (enemy.state == EnemyState.Walking) return Gait(enemy.phase, 1f)
                if (enemy.state == EnemyState.Running) return Gait(enemy.phase, 1.6f)
            }
            return STILL
        }
    }
}

/**
 * קנה המידה של הספרייט בפיקסלים (לכל פיקסל של הפריים): לפי הגובה הרצוי, ומוגבל גם לפי הרוחב.
 * מפלצות רחבות מאדם, ולכן בלי ההגבלה הן יוצאות ענקיות וחצי מהן מחוץ למסך.
 */
private fun spriteScale(sheet: MonsterSheet, r: Float): Float {
    val widest = max(
        max(sheet.idle.width, sheet.hit.width),
        sheet.walk.maxOf { it.width }
    )
    val kByHeight = r * FIGURE_HEIGHT_RADII * MONSTER_SCALE * sheet.sizeScale / sheet.idle.height
    val kByWidth = r * MAX_WIDTH_RADII / widest
    return min(kByHeight, kByWidth)
}

/** התיבה שהמפלצת תופסת במסך (בלי המסגרת הלבנה), לפי אותו חישוב גודל שמשמש לציור */
private fun monsterBox(sheet: MonsterSheet, center: Offset, r: Float): MonsterBox {
    val k = spriteScale(sheet, r)
    val feetY = center.y + r
    return MonsterBox(
        cx = center.x,
        headY = feetY - sheet.idle.height * k,
        feetY = feetY,
        halfWidth = sheet.idle.width * k / 2f
    )
}

/**
 * מציירת מפלצת: עומדת (פריים עמידה עם נשימה קלה), הולכת (4 פריימים, מתהפכת לפי כיוון ההליכה),
 * או נפגעת (תנוחת פגיעה עם כוכבים, נעלמת בענן). הרגליים ב-center.y + r, כמו הדמות הפשוטה.
 */
private fun DrawScope.drawMonster(
    sheet: MonsterSheet,
    enemy: Enemy,
    dead: Boolean,
    progress: Float,
    center: Offset,
    r: Float,
    shade: Float,
    shadow: Boolean,
    anim: Float
) {
    val gait = Gait.of(enemy)
    val walking = !dead && gait.swing > 0f
    val sprite: MonsterSprite = if (dead) {
        sheet.hit
    } else if (walking) {
        val raw = (gait.phase / (2f * PI.toFloat()) * FRAMES_PER_CYCLE).toInt()
        val count = sheet.walk.size
        sheet.walk[((raw % count) + count) % count]
    } else {
        sheet.idle
    }

    val cx = center.x
    val feetY = center.y + r
    // גובה הדמות בפיקסלים: הפריים עצמו (בלי מסגרת) מוקטן/מוגדל בהתאם לפריים העמידה של המפלצת
    val k = spriteScale(sheet, r)
    val targetHeight = sheet.idle.height * k

    // נשימה קלה כשעומדת
    var sx = 1f
    var sy = 1f
    if (!dead && !walking) {
        val breath = sin(anim * 3.2f + enemy.id)
        sy = 1f + 0.025f * breath
        sx = 1f - 0.015f * breath
    }
    val alpha = if (dead) {
        if (progress < 0.5f) 1f else (1f - (progress - 0.5f) * 2f).coerceIn(0f, 1f)
    } else {
        1f
    }

    // צל על הקרקע מתחת לרגליים
    if (shadow) {
        val shadowWidth = sheet.idle.width * k * 0.75f
        drawOval(
            color = Color.Black.copy(alpha = 0.38f * alpha),
            topLeft = Offset(cx - shadowWidth / 2f, feetY - r * 0.14f),
            size = Size(shadowWidth, r * 0.3f)
        )
    }

    val gray = (0.3f + 0.7f * shade).coerceIn(0.4f, 1f)
    val tint = ColorFilter.tint(Color(gray, gray, gray, 1f), BlendMode.Modulate)
    val width = sprite.image.width * k * sx
    val height = sprite.image.height * k * sy
    val padPx = sprite.pad * k * sy
    val left = cx - width / 2f
    val top = feetY + padPx - height
    val flip = (walking || dead) && enemy.direction < 0f

    val drawSprite: DrawScope.() -> Unit = {
        drawImage(
            image = sprite.image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(sprite.image.width, sprite.image.height),
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
            alpha = alpha,
            colorFilter = tint
        )
    }
    if (flip) {
        scale(-1f, 1f, Offset(cx, feetY)) { drawSprite() }
    } else {
        drawSprite()
    }

    // ענן קטן בזמן הפגיעה
    if (dead && progress < 0.6f) {
        val puff = progress / 0.6f
        val puffAlpha = (1f - puff) * 0.7f
        val bodyCenter = Offset(cx, feetY - targetHeight * 0.45f)
        for (i in 0 until 6) {
            val angle = (2.0 * PI * i / 6.0).toFloat() + 0.5f
            val distance = targetHeight * (0.15f + 0.45f * puff)
            drawCircle(
                color = Color.White.copy(alpha = puffAlpha),
                radius = targetHeight * (0.10f + 0.10f * puff),
                center = Offset(bodyCenter.x + cos(angle) * distance, bodyCenter.y + sin(angle) * distance)
            )
        }
    }
}

/** כמה פיקסלים (ברזולוציית הציור) מצמצמים את מסכת העצם: ערך גבוה = הדמות קרובה יותר לעצם */
private const val MASK_SHRINK_PX = 2

/** שחיקה (min) של מסכה, אופקית ואז אנכית. מחוץ למסכה נחשב שקוף. */
private fun erode(alpha: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
    if (radius <= 0) return alpha
    val temp = ByteArray(alpha.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var m = 255
            for (d in -radius..radius) {
                val xx = x + d
                val v = if (xx in 0 until width) alpha[y * width + xx].toInt() and 0xFF else 0
                if (v < m) m = v
            }
            temp[y * width + x] = m.toByte()
        }
    }
    val out = ByteArray(alpha.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var m = 255
            for (d in -radius..radius) {
                val yy = y + d
                val v = if (yy in 0 until height) temp[yy * width + x].toInt() and 0xFF else 0
                if (v < m) m = v
            }
            out[y * width + x] = m.toByte()
        }
    }
    return out
}

/** מסכת הציור של המחבוא: לבן עם אלפא רכה (אחרי ליטוש הקצוות) */
private fun buildMask(spot: HidingSpot): ImageBitmap {
    // מצמצמים מעט את המסכה, כדי שלא יישאר פס רקע בין העצם לדמות
    val alpha = erode(spot.drawAlpha(), spot.drawWidth, spot.drawHeight, MASK_SHRINK_PX)
    val pixels = IntArray(alpha.size)
    for (i in pixels.indices) {
        pixels[i] = ((alpha[i].toInt() and 0xFF) shl 24) or 0x00FFFFFF
    }
    return Bitmap.createBitmap(pixels, spot.drawWidth, spot.drawHeight, Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

/** מצייר דמות מאחורי עצם: הדמות נמחקת בכל מקום שבו העצם (המסכה המלוטשת) נמצא */
private fun DrawScope.drawFigureBehind(
    drawFigure: DrawScope.() -> Unit,
    spot: HidingSpot,
    mask: ImageBitmap,
    scene: SceneContext,
    clipBottom: Float?
) {
    val mapper = scene.mapper
    val left = mapper.worldX(spot.drawMinX) * size.width
    val top = mapper.worldY(spot.drawMinY) * size.height
    val right = mapper.worldX(spot.drawMaxX) * size.width
    val bottom = mapper.worldY(spot.drawMaxY) * size.height

    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
        if (clipBottom != null) {
            clipRect(0f, 0f, size.width, clipBottom) {
                drawFigure()
            }
        } else {
            drawFigure()
        }
        drawImage(
            image = mask,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(mask.width, mask.height),
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(
                (right - left).roundToInt().coerceAtLeast(1),
                (bottom - top).roundToInt().coerceAtLeast(1)
            ),
            blendMode = BlendMode.DstOut
        )
        canvas.restore()
    }
}

/** כמו drawFigureBehind, אבל לכמה עצמים בבת אחת: הדמות נמחקת בכל מקום שבו אחד מהעצמים הקרובים נמצא */
private fun DrawScope.drawFigureBehindAll(
    drawFigure: DrawScope.() -> Unit,
    spots: List<HidingSpot>,
    masks: List<ImageBitmap>,
    scene: SceneContext
) {
    val mapper = scene.mapper
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
        drawFigure()
        for (i in spots.indices) {
            val spot = spots[i]
            val mask = masks[i]
            val left = mapper.worldX(spot.drawMinX) * size.width
            val top = mapper.worldY(spot.drawMinY) * size.height
            val right = mapper.worldX(spot.drawMaxX) * size.width
            val bottom = mapper.worldY(spot.drawMaxY) * size.height
            drawImage(
                image = mask,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(mask.width, mask.height),
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(
                    (right - left).roundToInt().coerceAtLeast(1),
                    (bottom - top).roundToInt().coerceAtLeast(1)
                ),
                blendMode = BlendMode.DstOut
            )
        }
        canvas.restore()
    }
}

/** צובע בגוון כהה/בהיר יותר, לפי התאורה בתמונה */
private fun Color.shaded(factor: Float): Color = Color(
    red = (red * factor).coerceIn(0f, 1f),
    green = (green * factor).coerceIn(0f, 1f),
    blue = (blue * factor).coerceIn(0f, 1f),
    alpha = alpha
)

/**
 * דמות אדם פשוטה (גיבוי כשאין ספרייטים). center = מרכז ההיט; הרגליים ב-center.y + r, והראש ב-center.y - 1.2r.
 * הצבעים מוכהים או מובהרים לפי התאורה בתמונה (shade), ויש צל מתחת לרגליים.
 * הליכה: הרגליים והידיים מתנדנדות בניגוד זו לזו, והגוף עולה ויורד קלות עם כל צעד.
 */
private fun DrawScope.drawPerson(center: Offset, r: Float, shade: Float, shadow: Boolean, gait: Gait) {
    val cx = center.x
    val feetY = center.y + r

    // צל על הקרקע, בדיוק מתחת לרגליים (נשאר על הקרקע גם כשהגוף עולה)
    if (shadow) {
        drawOval(
            color = Color.Black.copy(alpha = 0.4f),
            topLeft = Offset(cx - r * 0.8f, feetY - r * 0.14f),
            size = Size(r * 1.6f, r * 0.3f)
        )
    }

    val swingSin = sin(gait.phase)
    // קפיצה קלה בכל צעד (פעמיים במחזור), רק בזמן הליכה
    val bob = abs(swingSin) * 0.05f * r * gait.swing
    val cy = center.y - bob
    val legAngle = 26f * gait.swing * swingSin
    val armAngle = 22f * gait.swing * swingSin

    val cloth = Color(0xFF4A5D68).shaded(shade)
    val clothDark = Color(0xFF263238).shaded(shade)
    val skin = Color(0xFFD2A67F).shaded(shade)
    val line = Color(0xFF10181C)
    val stroke = Stroke(width = r * 0.05f)

    // רגליים: מסתובבות סביב הירך, אחת קדימה ואחת אחורה
    for (side in listOf(-1f, 1f)) {
        val topLeft = Offset(if (side < 0) cx - r * 0.36f else cx + r * 0.06f, cy + r * 0.15f)
        val legSize = Size(r * 0.3f, r * 0.85f)
        val hip = Offset(topLeft.x + legSize.width / 2f, topLeft.y)
        rotate(degrees = legAngle * side, pivot = hip) {
            drawRoundRect(clothDark, topLeft, legSize, CornerRadius(r * 0.08f))
            drawRoundRect(line, topLeft, legSize, CornerRadius(r * 0.08f), stroke)
        }
    }

    // ידיים: מתנדנדות בניגוד לרגל שבאותו צד, סביב הכתף
    for (side in listOf(-1f, 1f)) {
        val topLeft = Offset(if (side < 0) cx - r * 0.6f else cx + r * 0.42f, cy - r * 0.5f)
        val armSize = Size(r * 0.18f, r * 0.68f)
        val shoulder = Offset(topLeft.x + armSize.width / 2f, topLeft.y)
        rotate(degrees = -armAngle * side, pivot = shoulder) {
            drawRoundRect(cloth, topLeft, armSize, CornerRadius(r * 0.09f))
            drawRoundRect(line, topLeft, armSize, CornerRadius(r * 0.09f), stroke)
            drawCircle(skin, r * 0.1f, Offset(topLeft.x + r * 0.09f, cy + r * 0.22f))
        }
    }

    // גוף
    val torsoTopLeft = Offset(cx - r * 0.44f, cy - r * 0.55f)
    val torsoSize = Size(r * 0.88f, r * 0.8f)
    drawRoundRect(cloth, torsoTopLeft, torsoSize, CornerRadius(r * 0.22f))
    drawRoundRect(line, torsoTopLeft, torsoSize, CornerRadius(r * 0.22f), stroke)

    // ראש וכובע
    val headCenter = Offset(cx, cy - r * 0.9f)
    drawCircle(skin, r * 0.3f, headCenter)
    drawCircle(line, r * 0.3f, headCenter, style = stroke)
    val capTopLeft = Offset(cx - r * 0.33f, cy - r * 1.22f)
    val capSize = Size(r * 0.66f, r * 0.24f)
    drawRoundRect(clothDark, capTopLeft, capSize, CornerRadius(r * 0.12f))
    drawRoundRect(line, capTopLeft, capSize, CornerRadius(r * 0.12f), stroke)
}