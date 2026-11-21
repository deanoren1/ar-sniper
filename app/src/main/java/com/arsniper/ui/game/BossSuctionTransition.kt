package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Screen-space energy, layered over the world while the world content itself is warped by AGSL.
 * progress is 0..1. Deterministic particle positions avoid allocations/randomness per frame.
 */
@Composable
fun BossSuctionOverlay(progress: Float, modifier: Modifier = Modifier, warped: Boolean = false) {
    if (progress <= 0f) return
    Canvas(modifier.fillMaxSize()) {
        val p = progress.coerceIn(0f, 1f)
        // אותו מרכז כמו השיידר (uCenter)
        val c = Offset(size.width * 0.5f, size.height * (0.62f - 0.14f * p))
        val maxR = hypot(size.width, size.height) * 0.56f
        val coreR = maxR * (0.04f + 0.88f * p * p)

        // Vortex glow surrounding the growing dark core.
        val glowR = coreR + size.height * (0.08f + 0.05f * (1f - p))
        // עם השיידר החור האמיתי נוצר מהעיוות עצמו; הליבה הכהה המצוירת רק בטלפונים בלי AGSL
        if (!warped) drawCircle(
            Brush.radialGradient(
                0f to Color(0xFF050313).copy(alpha = 0.98f),
                (coreR / maxR).coerceIn(0.02f, 0.95f) to Color(0xFF130B35).copy(alpha = 0.92f),
                ((glowR / maxR).coerceIn(0.05f, 1f)) to Color(0xFF287DFF).copy(alpha = 0.22f * (1f - p * 0.45f)),
                1f to Color.Transparent,
                center = c,
                radius = maxR
            ),
            radius = maxR,
            center = c
        )

        // Rotating energy rings.
        for (i in 0..3) {
            val fraction = 0.25f + i * 0.19f
            val radius = maxR * (fraction * (1f - 0.55f * p) + 0.08f * p)
            rotate(degrees = (p * 900f * (if (i % 2 == 0) 1f else -1f)) + i * 31f, pivot = c) {
                drawArc(
                    color = (if (i % 2 == 0) Color(0xFF55D8FF) else Color(0xFFB45CFF)).copy(alpha = if (warped) 0.55f else 1f),
                    startAngle = 12f + i * 37f,
                    sweepAngle = 205f + 45f * p,
                    useCenter = false,
                    topLeft = Offset(c.x - radius, c.y - radius * 0.72f),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 1.44f),
                    style = Stroke(width = size.height * (0.0035f + 0.002f * (1f - p)))
                )
            }
        }

        // Bright particles spiralling toward the singularity.
        for (i in 0 until 42) {
            val seed = i * 2.3999632f
            val startRadius = maxR * (0.22f + ((i * 37) % 71) / 100f)
            val radius = startRadius * (1f - 0.78f * p)
            val angle = seed + p * (5.5f + (i % 5) * 0.55f)
            val x = c.x + cos(angle) * radius
            val y = c.y + sin(angle) * radius * 0.62f
            val particleR = size.height * (0.0018f + (i % 4) * 0.0008f) * (1f - 0.25f * p)
            drawCircle(
                color = if (i % 3 == 0) Color(0xFFB66BFF) else Color(0xFF7CEBFF),
                radius = particleR,
                center = Offset(x, y),
                alpha = (0.45f + 0.5f * p).coerceIn(0f, 1f)
            )
        }

        // Final dark-out: lets the boss portal take over cleanly.
        val blackout = ((p - 0.78f) / 0.22f).coerceIn(0f, 1f)
        if (blackout > 0f && !warped) {
            drawRect(Color.Black.copy(alpha = blackout))
        }
    }
}

/** אפקט השאיבה על שכבה שלמה (Android 13+), או null. progress 0..1 */
fun bossSuctionEffect(shader: Any?, progress: Float, w: Float, h: Float): androidx.compose.ui.graphics.RenderEffect? {
    if (progress <= 0f || shader == null || android.os.Build.VERSION.SDK_INT < 33) return null
    val s = shader as android.graphics.RuntimeShader
    s.setFloatUniform("uResolution", w.coerceAtLeast(1f), h.coerceAtLeast(1f))
    s.setFloatUniform("uProgress", progress)
    // מרכז השאיבה זז במהלך האנימציה: מהרצפה מול השחקן אל מרכז המסך
    s.setFloatUniform("uCenter", 0.5f, 0.62f - 0.14f * progress)
    return android.graphics.RenderEffect.createRuntimeShaderEffect(s, "uContent").asComposeRenderEffect()
}

/** השיידר (Android 13+), או null */
fun createBossSuctionShader(): Any? =
    if (android.os.Build.VERSION.SDK_INT >= 33) runCatching { android.graphics.RuntimeShader(BOSS_SUCTION_AGSL) }.getOrNull() else null

/** AGSL fragment shader. It samples the already-rendered arena layer, twisting and pulling it inward. */
const val BOSS_SUCTION_AGSL: String = """
uniform shader uContent;
uniform float2 uResolution;
uniform float uProgress;
uniform float2 uCenter;

half4 main(float2 fragCoord) {
    float2 res = max(uResolution, float2(1.0, 1.0));
    float2 c = uCenter * res;
    // יחידות של גובה המסך, ביחס למרכז השאיבה
    float2 p = (fragCoord - c) / res.y;
    float r = length(p);
    float t = clamp(uProgress, 0.0, 1.0);
    float e = t * t * (3.0 - 2.0 * t);

    // משיכה כבידתית: כל נקודה מציגה תוכן שנמצא רחוק יותר מהמרכז, וחזק יותר ככל שקרובים לחור.
    // כך הקרקע, ההרים והאובייקטים נמתחים ונדחסים פנימה (לא רק מסתובבים)
    float pull = e * (0.10 + 0.22 / (r + 0.10));
    float sampleR = r + pull * (1.0 + 2.0 * e);
    // סיבוב שמתחזק לכיוון המרכז (דיסקת ספיחה)
    float swirl = e * (1.2 + 3.2 / (r * 3.0 + 0.5));
    float a = atan(p.y, p.x) + swirl;
    float2 sp = c + float2(cos(a), sin(a)) * sampleR * res.y;

    half4 base = uContent.eval(clamp(sp, float2(0.0), res));
    // מחוץ לתמונה: שקוף/כהה במקום מריחת קצוות
    float inside = step(0.0, sp.x) * step(sp.x, res.x) * step(0.0, sp.y) * step(sp.y, res.y);
    base *= half(inside);

    // אופק אירועים: חור שחור שגדל עד שמכסה את כל המסך
    float hole = pow(e, 1.6) * 1.35;
    float keep = smoothstep(hole - 0.02, hole + 0.06, r);
    // טבעת זוהרת בשפת החור
    float ring = exp(-abs(r - hole - 0.035) * 38.0) * (1.0 - smoothstep(0.85, 1.0, t)) * step(0.001, e);
    base *= half(keep);
    half3 glow = half3(0.25, 0.55, 1.0) * half(ring * 0.9) + half3(0.55, 0.15, 1.0) * half(ring * 0.35);
    base.rgb += glow * base.a;
    return base;
}
"""
