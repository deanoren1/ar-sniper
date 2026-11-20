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
fun BossSuctionOverlay(progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0f) return
    Canvas(modifier.fillMaxSize()) {
        val p = progress.coerceIn(0f, 1f)
        val c = Offset(size.width * 0.5f, size.height * 0.5f)
        val maxR = hypot(size.width, size.height) * 0.56f
        val coreR = maxR * (0.04f + 0.88f * p * p)

        // Vortex glow surrounding the growing dark core.
        val glowR = coreR + size.height * (0.08f + 0.05f * (1f - p))
        drawCircle(
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
                    color = if (i % 2 == 0) Color(0xFF55D8FF) else Color(0xFFB45CFF),
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
        if (blackout > 0f) {
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

half4 main(float2 fragCoord) {
    float2 res = max(uResolution, float2(1.0, 1.0));
    float2 uv = fragCoord / res;
    float2 centered = uv - float2(0.5, 0.5);
    float aspect = res.x / res.y;
    float2 p = centered * float2(aspect, 1.0);
    float r = length(p);
    float angle = atan(p.y, p.x);

    float t = clamp(uProgress, 0.0, 1.0);
    float ease = t * t * (3.0 - 2.0 * t);

    // Stronger twist near the singularity, with a radial pull toward the center.
    float influence = 1.0 - smoothstep(0.08, 0.95, r);
    angle += ease * 7.2 * influence * (0.35 + 0.65 * (1.0 - r));
    float pull = ease * 0.48 * pow(max(0.0, 1.0 - r / 0.92), 2.0);
    float sampleR = r + pull;

    float2 warped = float2(cos(angle), sin(angle)) * sampleR;
    float2 sampleUv = warped / float2(aspect, 1.0) + float2(0.5, 0.5);

    // eval מקבל קואורדינטות בפיקסלים (לא 0..1)
    half4 base = uContent.eval(clamp(sampleUv, float2(0.0), float2(1.0)) * res);

    // Expanding black event horizon.
    float holeRadius = ease * 0.98;
    float keep = smoothstep(holeRadius - 0.025, holeRadius + 0.035, r);
    base.rgb *= half(keep);

    float ring = exp(-abs(r - (0.12 + 0.20 * ease)) * (24.0 - 10.0 * ease)) * ease;
    half3 energy = half3(0.12, 0.42, 1.0) * half(ring * 0.55)
                 + half3(0.48, 0.08, 0.95) * half(ring * 0.42);
    base.rgb += energy * half(1.0 - smoothstep(0.72, 1.0, t));

    // Final frame goes fully dark before the portal scene appears.
    base.rgb *= half(1.0 - smoothstep(0.94, 1.0, t));
    return base;
}
"""
