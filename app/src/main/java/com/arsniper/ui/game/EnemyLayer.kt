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
import com.arsniper.game.engine.AccessoryKind
import com.arsniper.game.engine.AccessoryTarget
import com.arsniper.game.engine.ArenaDirector
import com.arsniper.game.engine.Hover
import com.arsniper.game.engine.WeakZones
import com.arsniper.game.engine.ZoneTarget
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

/** מוות הבוס ארוך יותר: נופל, שוכב ומתפוצץ */
private const val BOSS_DEATH_SECONDS = 2.2f

private fun deathSeconds(enemy: Enemy): Float = if (enemy.id == ArenaDirector.BOSS_ID) BOSS_DEATH_SECONDS else DEATH_SECONDS

/** משך הבהוב האדום והרעידה אחרי פגיעה שלא הורגת (שניות) */
private const val HURT_SECONDS = 0.35f

/** מאיזה חלק גלוי של מציץ מתחילים להציג לו מד חיים (0..1) */
private const val PEEK_BAR_EXPOSURE = 0.15f

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
 * מפלצת שיוצאת מאחורי עצם וממשיכה ללכת: מצוירת שלמה (בלי חיתוך), כדי שתמיד תהיה גלויה במלואה.
 * חלון: הדמות נחתכת באדן החלון.
 * פגיעה: המפלצת מוצגת בתנוחת פגיעה עם כוכבים ונעלמת בענן.
 */
@Composable
fun EnemyLayer(
    enemies: List<Enemy>,
    scene: SceneContext?,
    modifier: Modifier = Modifier,
    /** מפתח גיליון מפלצות של הארנה (למשל "tundra"); null או גיליון חסר = הגיליון הרגיל */
    monsterSet: String? = null
) {
    val context = LocalContext.current
    val spritesAvailable = remember { MonsterSprites.isAvailable(context) }
    val spritesState = produceState<MonsterSprites?>(null, context, monsterSet) {
        if (spritesAvailable) {
            value = withContext(Dispatchers.Default) { MonsterSprites.load(context, monsterSet) }
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
        ZoneRegistry.clear()

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
            if (nowS - entry.value.since > deathSeconds(entry.value.enemy)) {
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
                items.add(DrawItem(d.enemy, true, ((nowS - d.since) / deathSeconds(d.enemy)).toFloat().coerceIn(0f, 1f)))
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

            // אחרי פגיעה שלא הורגת: הבהוב אדום ורעידה קלה, שדועכים
            val sinceHurt = (nowS - enemy.hurtAt).toFloat()
            val hurtLevel = if (!item.dead && sinceHurt >= 0f && sinceHurt <= HURT_SECONDS) {
                1f - sinceHurt / HURT_SECONDS
            } else {
                0f
            }
            val shakePx = if (!isPeeker && hurtLevel > 0f) sin(sinceHurt * 70f) * radius * 0.12f * hurtLevel else 0f
            // מפלצת הבלונים מרחפת באוויר; כשהיא נופלת (מתה) היא צונחת אל הקרקע
            var liftPx = Hover.lift(enemy, nowS) * size.height
            if (item.dead) {
                val fall = (item.progress * 2.5f).coerceIn(0f, 1f)
                liftPx *= 1f - fall * fall
            }
            val drawCenter = Offset(center.x + shakePx, center.y - liftPx)

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
                        center = drawCenter,
                        r = radius,
                        shade = shade,
                        shadow = withShadow,
                        anim = anim,
                        hurt = hurtLevel,
                        groundLift = liftPx
                    )
                } else {
                    drawPerson(drawCenter, radius, shade, withShadow, gait)
                }
            }

            if (enemy.kind == EnemyKind.Window) {
                // יוצא מחלון: הדמות נחתכת באדן החלון, כך שנראה שהיא עומדת מאחוריו
                val sillY = (enemy.hidePos.y - WINDOW_HIDE_OFFSET * enemy.radius) * size.height
                val draw = figure(false)
                clipRect(0f, 0f, size.width, sillY) {
                    draw()
                }
                // מד חיים מעל הראש: הרגליים מוסתרות, אז לא מתחתן
                if (!item.dead && sprites != null && enemy.maxHp > 1 && enemy.exposure >= PEEK_BAR_EXPOSURE) {
                    drawHealthBar(monsterBox(sprites.forEnemy(enemy.id), drawCenter, radius), enemy, radius, true)
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
                // מציץ: מד החיים מעל הראש, כי הרגליים מוסתרות מאחורי העצם
                if (!item.dead && sprites != null && enemy.maxHp > 1 && enemy.exposure >= PEEK_BAR_EXPOSURE) {
                    drawHealthBar(monsterBox(sprites.forEnemy(enemy.id), drawCenter, radius), enemy, radius, true)
                }
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
                // נקודות תורפה: מפלצת הבלונים (הבלונים), ולשאר לפי "חידת הירי" שלהן (WeakZones).
                // רק כשהמפלצת חופשית ומוצגת מלפנים (עומדת, מתכוננת לירות, או מתקרבת אל המצלמה)
                val free = (enemy.kind == EnemyKind.Open && enemy.spot == null) || enemy.kind == EnemyKind.Static
                if (!item.dead && sprites != null && enemy.isAlive && free) {
                    val box = monsterBox(sprites.forEnemy(enemy.id), drawCenter, radius)
                    val telegraphing = AttackPose.ids.contains(enemy.id)
                    if (Hover.isBalloonMonster(enemy)) {
                        // פחות בלונים: אזור הפגיעה קטן יותר
                        val share = if (enemy.maxHp > 0) kotlin.math.ceil(enemy.hp * 3f / enemy.maxHp).toInt().coerceIn(1, 3) else 3
                        val px = box.cx
                        val py = box.headY + box.height * 0.2f
                        val rr = box.halfWidth * (0.45f + 0.15f * share)
                        // הבלונים מרחפים מעל הכול: עצמים בסצנה לא מסתירים אותם
                        run {
                            ZoneRegistry.add(
                                ZoneTarget(enemy, WeakZones.BALLOONS, Vec2(px / size.width, py / size.height), rr * 1.15f / size.height)
                            )
                        }
                    } else if (gait.swing <= 0f || enemy.dirY > 0.5f) {
                        for (zone in WeakZones.zonesOf(WeakZones.foeByRow(enemy.id))) {
                            if (zone.onlyTelegraph && !telegraphing) continue
                            val px = box.cx + zone.x * box.halfWidth
                            val py = box.headY + zone.y * box.height
                            val rr = max(zone.radius * box.height, size.height * 0.035f)
                            if (covered(px, py)) continue
                            drawZoneHint(px, py, rr, zone.onlyTelegraph, anim)
                            ZoneRegistry.add(ZoneTarget(enemy, zone, Vec2(px / size.width, py / size.height), rr / size.height))
                        }
                    }
                }
                // מד חיים מתחת לרגליים, למפלצת עם יותר מפגיעה אחת (לא כשעצם מכסה אותה)
                if (!item.dead && sprites != null && enemy.maxHp > 1 && blockers.isEmpty()) {
                    drawHealthBar(monsterBox(sprites.forEnemy(enemy.id), drawCenter, radius), enemy, radius, false)
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
 * הפריים של הבוס לפי מה שהוא עושה: מסוחרר (כוכבים), מתכווץ מפגיעה, מתכונן (הפה נפתח ונדלק לפי התקדמות ההכנה),
 * יורה (פה פתוח ואז התאוששות), או עומד (כנפיים ונשימה בלולאה הלוך-חזור).
 */
private fun bossFrame(a: BossAnim, enemy: Enemy, anim: Float, hurt: Float): MonsterSprite {
    if (AttackPose.bossStunned) return a.hit[3]
    if (hurt > 0.5f) return a.hit[2]
    val progress = AttackPose.progress[enemy.id]
    if (progress != null) return a.prep[(progress * 4f).toInt().coerceIn(0, 3)]
    val since = AttackPose.bossSinceFire
    if (since < 0.18f) return a.fire[0]
    if (since < 0.38f) return a.fire[1]
    if (since < 0.6f) return a.prep[4]
    // הלוך-חזור על פריימי העמידה: 0,1,2,3,4,5,4,3,2,1
    val n = a.idle.size
    val step = (anim * (if (AttackPose.bossEnraged) 10f else 6f)).toInt()
    val cycle = 2 * (n - 1)
    val i = ((step % cycle) + cycle) % cycle
    return a.idle[if (i < n) i else cycle - i]
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
    anim: Float,
    hurt: Float = 0f,
    groundLift: Float = 0f
) {
    val gait = Gait.of(enemy)
    val walking = !dead && gait.swing > 0f
    val sprite: MonsterSprite = if (dead && sheet.bossAnim != null) {
        sheet.bossAnim.death[(progress * 6f).toInt().coerceIn(0, 5)]
    } else if (dead && sheet.rig != null) {
        rigDeath(sheet.rig, enemy, progress)
    } else if (dead) {
        sheet.hit
    } else if (walking) {
        // המבט נקבע לפי כיוון ההליכה: אל המצלמה = מבט קדמי, מתרחקת = גב, לרוחב = צד.
        // התנועה עצמה נוצרת בקפיצות, הטיות ולחיצות של אותו ציור (החלפת תנוחות בהליכה נראתה קופצנית)
        val moves = movesFor(sheet, enemy)
        if (moves != null) {
            // פריימי הליכה אמיתיים: פריים לפי שלב המחזור (אותו קצב כמו התנועה, כך שהרגליים לא "מחליקות")
            val list = if (enemy.dirY > 0.5f) {
                moves.front
            } else if (enemy.dirY < -0.5f) {
                moves.back
            } else if (gait.swing > 1.2f) {
                moves.run
            } else {
                moves.side
            }
            val raw = (gait.phase / (2f * PI.toFloat()) * list.size).toInt()
            list[((raw % list.size) + list.size) % list.size]
        } else if (enemy.dirY > 0.5f) sheet.idle else if (enemy.dirY < -0.5f) sheet.walk[1] else sheet.walk[0]
    } else if (sheet.rig != null) {
        rigFrame(sheet.rig, enemy, hurt)
    } else if (sheet.frontPeek && (enemy.kind == EnemyKind.Peek || enemy.kind == EnemyKind.Window)) {
        // מציץ מאחורי עצם או חלון: רק מבט קדמי (נושם בלולאת העמידה של הגיליון הקדמי)
        movesFor(sheet, enemy)?.front?.firstOrNull() ?: sheet.idle
    } else if (sheet.bossAnim != null) {
        bossFrame(sheet.bossAnim, enemy, anim, hurt)
    } else if (AttackPose.ids.contains(enemy.id)) {
        // מפלצת בלונים שאיבדה בלון: גם בהכנה לירייה נשארת עם מספר הבלונים שנשאר (תמונת ההכנה המקורית היא עם שלושה)
        sheet.fewer[balloonsLeft(sheet, enemy)]?.front?.get(0) ?: sheet.peek
    } else {
        // מפלצת בלונים שאיבדה בלון: עומדת בציור של מספר הבלונים שנשאר
        sheet.fewer[balloonsLeft(sheet, enemy)]?.front?.get(0) ?: sheet.idle
    }

    val cx = center.x
    val feetY = center.y + r
    // גובה הדמות בפיקסלים: הפריים עצמו (בלי מסגרת) מוקטן/מוגדל בהתאם לפריים העמידה של המפלצת
    val k = spriteScale(sheet, r)
    val targetHeight = sheet.idle.height * k

    // תנועה: נשימה כשעומדת; קפיצה, לחיצה והטיה קדימה בהליכה; התנופפות קלה לבלונים; התכווצות לפני ירייה
    var sx = 1f
    var sy = 1f
    var tilt = 0f
    var hop = 0f
    var shakeX = 0f
    if (!dead && walking) {
        val swing = gait.swing
        val bounce = abs(sin(gait.phase))
        // עם פריימי הליכה אמיתיים התנועה כבר בציור: רק קפיצה וטלטול קלים
        val real = if (sheet.moves != null) 0.3f else 1f
        hop = bounce * targetHeight * 0.05f * swing * real
        // לחוץ בנחיתה, מתוח באוויר
        sy = 1f + 0.05f * (bounce - 0.5f) * swing * real
        sx = 1f - 0.035f * (bounce - 0.5f) * swing * real
        // הטיה קדימה בכיוון ההליכה, וטלטול לצדדים עם כל צעד
        val forward = if (enemy.direction < 0f) -1f else 1f
        val sideways = abs(enemy.dirY) <= 0.5f
        tilt = (if (sideways) forward * 3.5f * swing else 0f) + sin(gait.phase) * 3f * swing
    } else if (!dead) {
        val breath = sin(anim * (if (enemy.id == ArenaDirector.BOSS_ID && AttackPose.bossEnraged) 6f else 3.2f) + enemy.id)
        sy = 1f + 0.025f * breath
        sx = 1f - 0.015f * breath
        tilt = sin(anim * 1.3f + enemy.id) * 1f
        if (Hover.isBalloonMonster(enemy)) {
            // בלון הליום: מתנדנדת בעדינות מצד לצד
            tilt = sin(anim * 1.8f + enemy.id) * 4f
        }
        if (sheet.bossAnim == null && AttackPose.ids.contains(enemy.id)) {
            // לפני ירייה: מתנפחת קצת ורועדת
            val pulse = 1.05f + 0.025f * sin(anim * 28f)
            sx *= pulse
            sy *= pulse
            shakeX = sin(anim * 55f) * r * 0.04f
        }
    }
    val alpha = if (dead && sheet.bossAnim != null) {
        if (progress < 0.9f) 1f else (1f - (progress - 0.9f) * 10f).coerceIn(0f, 1f)
    } else if (dead && sheet.rig != null) {
        // פריימי המוות כבר מתפוגגים בציור עצמו: דוהים רק בסוף
        if (progress < 0.92f) 1f else (1f - (progress - 0.92f) * 12f).coerceIn(0f, 1f)
    } else if (dead) {
        if (progress < 0.5f) 1f else (1f - (progress - 0.5f) * 2f).coerceIn(0f, 1f)
    } else {
        1f
    }

    // צל על הקרקע מתחת לרגליים
    if (shadow) {
        val shadowWidth = sheet.idle.width * k * 0.75f
        drawOval(
            color = Color.Black.copy(alpha = 0.38f * alpha),
            topLeft = Offset(cx - shadowWidth / 2f, feetY + groundLift - r * 0.14f),
            size = Size(shadowWidth, r * 0.3f)
        )
    }

    val gray = (0.3f + 0.7f * shade).coerceIn(0.4f, 1f)
    val raging = enemy.id == ArenaDirector.BOSS_ID && AttackPose.bossEnraged && !dead
    val tint = if (raging && hurt <= 0f) {
        // בוס כועס: גוון אדום פועם
        ColorFilter.tint(Color(1f, 0.25f, 0.2f, 0.28f + 0.14f * sin(anim * 9f)), BlendMode.SrcAtop)
    } else if (hurt > 0f) {
        // פגיעה שלא הורגת: הבהוב אדום שדועך
        ColorFilter.tint(Color(1f, 0.3f, 0.3f, 0.65f * hurt), BlendMode.SrcAtop)
    } else {
        ColorFilter.tint(Color(gray, gray, gray, 1f), BlendMode.Modulate)
    }
    val width = sprite.image.width * k * sx
    val height = sprite.image.height * k * sy
    val padPx = sprite.pad * k * sy
    val left = cx - width / 2f + shakeX
    val top = feetY + padPx - height - hop
    val flip = sheet.bossAnim == null && (walking || dead) && enemy.direction < 0f && (dead || abs(enemy.dirY) <= 0.5f)

    // מפלצת בלונים שנפגעה למוות: הבלונים התפוצצו, אז מציירים רק את הגוף (בלי החלק העליון עם הבלונים)
    val cropTop = if (dead && sheet.bossAnim == null && Hover.isBalloonMonster(enemy)) 0.4f else 0f
    val srcCut = (sprite.image.height * cropTop).toInt()
    val dstCut = height * cropTop
    val drawSprite: DrawScope.() -> Unit = {
        drawImage(
            image = sprite.image,
            srcOffset = IntOffset(0, srcCut),
            srcSize = IntSize(sprite.image.width, sprite.image.height - srcCut),
            dstOffset = IntOffset(left.roundToInt(), (top + dstCut).roundToInt()),
            dstSize = IntSize(width.roundToInt().coerceAtLeast(1), (height - dstCut).roundToInt().coerceAtLeast(1)),
            alpha = alpha,
            colorFilter = tint
        )
    }
    rotate(tilt, Offset(cx, feetY)) {
        if (flip) {
            scale(-1f, 1f, Offset(cx, feetY)) { drawSprite() }
        } else {
            drawSprite()
        }
    }

    // בוס מת: פיצוצי אש סביב הגוף שגדלים ודועכים
    if (dead && sheet.bossAnim != null) {
        val body = Offset(cx, feetY - targetHeight * 0.3f)
        for (i in 0 until 9) {
            val local = (progress * 1.6f - i * 0.07f).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue
            val angle = i * 2.4f
            val reach = targetHeight * (0.15f + 0.4f * (((i * 37) % 10) / 10f))
            val center = Offset(body.x + cos(angle) * reach * 1.4f, body.y + sin(angle) * reach * 0.6f)
            val radius = targetHeight * (0.06f + 0.16f * local)
            drawCircle(Color(0xFFFFC107).copy(alpha = (1f - local) * 0.85f), radius, center)
            drawCircle(Color(0xFFFF5722).copy(alpha = (1f - local) * 0.6f), radius * 1.5f, center)
        }
    }

    // ענן קטן בזמן הפגיעה
    if (dead && sheet.bossAnim == null && progress < 0.6f) {
        val puff = progress / 0.6f
        val puffAlpha = (1f - puff) * 0.7f
        // מפלצת בלונים: הבלונים מתפוצצים בחתיכות אדומות מעל הראש
        val balloons = Hover.isBalloonMonster(enemy)
        val bodyCenter = Offset(cx, feetY - targetHeight * (if (balloons) 0.8f else 0.45f))
        val puffColor = if (balloons) Color(0xFFFF1744) else Color.White
        for (i in 0 until 6) {
            val angle = (2.0 * PI * i / 6.0).toFloat() + 0.5f
            val distance = targetHeight * (0.15f + 0.45f * puff)
            drawCircle(
                color = puffColor.copy(alpha = puffAlpha),
                radius = targetHeight * (0.10f + 0.10f * puff),
                center = Offset(bodyCenter.x + cos(angle) * distance, bodyCenter.y + sin(angle) * distance)
            )
        }
    }
}

/** רמז לנקודת תורפה: טבעת דקה שפועמת; אדומה ובולטת כשהיא נחשפת רק בזמן ההכנה לירייה */
private fun DrawScope.drawZoneHint(x: Float, y: Float, radius: Float, hot: Boolean, anim: Float) {
    val pulse = 0.5f + 0.5f * sin(anim * 6f)
    val color = if (hot) Color(1f, 0.2f, 0.2f, 0.55f + 0.4f * pulse) else Color(1f, 1f, 1f, 0.16f + 0.14f * pulse)
    drawCircle(
        color = color,
        radius = radius * (0.92f + 0.08f * pulse),
        center = Offset(x, y),
        style = Stroke(width = if (hot) 4f else 2.5f)
    )
}

/** כמה בלונים נשארו למפלצת הבלונים (1..2 = פחות מהמלא), או 0 אם היא מלאה או לא מפלצת בלונים */
private fun balloonsLeft(sheet: MonsterSheet, enemy: Enemy): Int {
    if (!Hover.isBalloonMonster(enemy) || enemy.maxHp <= 0 || sheet.fewer.isEmpty()) return 0
    val left = kotlin.math.ceil(enemy.hp * 3f / enemy.maxHp).toInt()
    return if (left in 1..2) left else 0
}

/** לולאות ההליכה של המפלצת: של מספר הבלונים שנשאר אם יש ציור כזה, אחרת הרגילות */
/** מפלצת שהולכת או רצה לרוחב מוצגת מהצד; כל השאר (עומדת, מתכוננת, מציצה, מתקרבת) מלפנים */
private fun facesSide(enemy: Enemy): Boolean =
    enemy.kind == EnemyKind.Open &&
        (enemy.state == EnemyState.Walking || enemy.state == EnemyState.Running) &&
        abs(enemy.dirY) <= 0.5f

private fun rigDeath(rig: RigAnim, enemy: Enemy, progress: Float): MonsterSprite {
    val frames = if (facesSide(enemy)) rig.deathSide else rig.deathFront
    return frames[(progress * frames.size).toInt().coerceIn(0, frames.size - 1)]
}

/** מפלצת rig2 שלא הולכת ולא מתה: הכנה לירי לפי ההתקדמות, פגיעה לפי עוצמת ההבהוב, אחרת עמידה מלפנים */
private fun rigFrame(rig: RigAnim, enemy: Enemy, hurt: Float): MonsterSprite {
    if (AttackPose.ids.contains(enemy.id)) {
        val p = AttackPose.progress[enemy.id] ?: 1f
        return rig.chargeFront[(p * rig.chargeFront.size).toInt().coerceIn(0, rig.chargeFront.size - 1)]
    }
    if (hurt > 0.55f) return rig.hitFront[1]
    if (hurt > 0.1f) return rig.hitFront[0]
    return rig.idleFront
}

private fun movesFor(sheet: MonsterSheet, enemy: Enemy): WalkSet? =
    sheet.fewer[balloonsLeft(sheet, enemy)] ?: sheet.moves

/**
 * מד חיים: פס עם תא לכל נקודת חיים. ירוק, ואז צהוב ואדום כשנשאר מעט.
 * above = false: מתחת לרגליים (מפלצת שהולכת). above = true: מעל הראש (מציץ או חלון, כשהרגליים מוסתרות).
 */
private fun DrawScope.drawHealthBar(box: MonsterBox, enemy: Enemy, r: Float, above: Boolean) {
    // לבוס יש פס חיים גדול למעלה; לא מציירים לו פס נוסף על הגוף
    if (enemy.id == ArenaDirector.BOSS_ID) return
    val total = enemy.maxHp
    val segmentWidth = max(r * 0.28f, 6f)
    val gap = max(r * 0.04f, 1.5f)
    val barHeight = max(r * 0.12f, 4f)
    val width = total * segmentWidth + (total - 1) * gap
    val left = box.cx - width / 2f
    val top = if (above) box.headY - barHeight - r * 0.25f else box.feetY + r * 0.2f

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(left - gap * 1.5f, top - gap * 1.5f),
        size = Size(width + gap * 3f, barHeight + gap * 3f),
        cornerRadius = CornerRadius(barHeight * 0.7f)
    )
    val fraction = enemy.hp.toFloat() / total
    val fill = if (fraction > 0.6f) {
        Color(0xFF4CAF50)
    } else if (fraction > 0.34f) {
        Color(0xFFFFC107)
    } else {
        Color(0xFFE53935)
    }
    for (i in 0 until total) {
        drawRoundRect(
            color = if (i < enemy.hp) fill else Color(0xFF3A0E0E).copy(alpha = 0.6f),
            topLeft = Offset(left + i * (segmentWidth + gap), top),
            size = Size(segmentWidth, barHeight),
            cornerRadius = CornerRadius(barHeight * 0.5f)
        )
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