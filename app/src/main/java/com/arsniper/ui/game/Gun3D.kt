package com.arsniper.ui.game

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.arsniper.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/** מתמטיקה של הרובה התלת-ממדי: מטריצות (עמודות) והטלה זהה לזו שנבדקה ב-harness */
object Gun3DMath {
    const val RX = 0.95f; const val RY = 0.24f; const val RZ = 0f
    const val MX = -0.95f; const val MY = 0.22f; const val MZ = 0f
    const val D = 4.5f

    fun ident() = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }
    fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (c in 0 until 4) for (row in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += a[k * 4 + row] * b[c * 4 + k]
            r[c * 4 + row] = s
        }
        return r
    }
    fun rotX(a: Float) = ident().also { val c = cos(a); val s = sin(a); it[5] = c; it[9] = -s; it[6] = s; it[10] = c }
    fun rotY(a: Float) = ident().also { val c = cos(a); val s = sin(a); it[0] = c; it[8] = s; it[2] = -s; it[10] = c }
    fun rotZ(a: Float) = ident().also { val c = cos(a); val s = sin(a); it[0] = c; it[4] = -s; it[1] = s; it[5] = c }
    fun trans(x: Float, y: Float, z: Float) = ident().also { it[12] = x; it[13] = y; it[14] = z }

    /** מודל: סיבוב (roll,pitch,yaw) סביב נקודת האחיזה R, כשהקנה פונה למעלה במסך */
    fun model(yaw: Float, pitch: Float, roll: Float = 0f): FloatArray =
        mul(rotZ(roll), mul(rotX(pitch), mul(rotY(yaw), mul(rotY((-Math.PI / 2).toFloat()), trans(-RX, -RY, -RZ)))))

    /** נקודת מודל -> פיקסל מסך, כשהמרכז האופטי (נקודת האחיזה) ב-(cx,cy) והמוקד f */
    fun proj(m: FloatArray, x: Float, y: Float, z: Float, cx: Float, cy: Float, f: Float, out: FloatArray) {
        val vx = m[0] * x + m[4] * y + m[8] * z + m[12]
        val vy = m[1] * x + m[5] * y + m[9] * z + m[13]
        val vz = m[2] * x + m[6] * y + m[10] * z + m[14] - D
        out[0] = cx + f * vx / (-vz); out[1] = cy - f * vy / (-vz)
    }

    /** כיוון הקנה במסך במעלות מהאנך (0 = ישר למעלה, שלילי = שמאלה) */
    fun dirOf(yaw: Float, pitch: Float, roll: Float, cx: Float, cy: Float, f: Float): Float {
        val m = model(yaw, pitch, roll)
        val a = FloatArray(2); val b = FloatArray(2)
        proj(m, RX, RY, RZ, cx, cy, f, a); proj(m, MX, MY, MZ, cx, cy, f, b)
        return Math.toDegrees(Math.atan2((b[0] - a[0]).toDouble(), -(b[1] - a[1]).toDouble())).toFloat()
    }

    /** הסיבוב (yaw) שבו הקנה מכוון בדיוק לזווית theta (מעלות) */
    fun solveYaw(theta: Float, pitch: Float, roll: Float, cx: Float, cy: Float, f: Float): Float {
        var lo = -1.45f; var hi = 1.45f
        repeat(26) {
            val mid = (lo + hi) / 2f
            if (dirOf(mid, pitch, roll, cx, cy, f) > theta) lo = mid else hi = mid
        }
        return (lo + hi) / 2f
    }

    fun perspective(w: Float, h: Float, cx: Float, cy: Float, f: Float): FloatArray {
        val n = 0.1f; val fr = 20f
        val p = FloatArray(16)
        p[0] = 2 * f / w; p[8] = 1 - 2 * cx / w; p[5] = 2 * f / h; p[9] = 2 * cy / h - 1
        p[10] = (fr + n) / (n - fr); p[14] = 2 * fr * n / (n - fr); p[11] = -1f
        return p
    }
}

/** מצב שנכתב מה-Compose ונקרא בחוט ה-GL: [0..15] = מטריצת מודל-מצלמה, [16..31] = הטלה */
class Gun3DState {
    @Volatile var matrices: FloatArray? = null
    @Volatile var visible: Boolean = true
}

private class Gun3DRenderer(private val context: Context, private val state: Gun3DState) : Thread("Gun3D") {
    @Volatile var surface: SurfaceTexture? = null
    @Volatile var width = 1
    @Volatile var height = 1
    @Volatile var running = true

    override fun run() {
        val st = surface ?: return
        val dpy: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(dpy, ver, 0, ver, 1)) return
        var config: EGLConfig? = null
        for (samples in intArrayOf(4, 0)) {
            val attrs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, 0x40 /* ES3 */, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_SAMPLE_BUFFERS, if (samples > 0) 1 else 0, EGL14.EGL_SAMPLES, samples, EGL14.EGL_NONE
            )
            val cfgs = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
            if (EGL14.eglChooseConfig(dpy, attrs, 0, cfgs, 0, 1, n, 0) && n[0] > 0) { config = cfgs[0]; break }
        }
        if (config == null) return
        val ctx: EGLContext = EGL14.eglCreateContext(dpy, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        val surf: EGLSurface = EGL14.eglCreateWindowSurface(dpy, config, Surface(st), intArrayOf(EGL14.EGL_NONE), 0)
        if (!EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) return
        try {
            val prog = buildProgram() ?: return
            GLES30.glUseProgram(prog)
            // רשת
            val buf = context.resources.openRawResource(R.raw.gun3d).use { it.readBytes() }
            val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
            val nV = bb.getInt(4); val nI = bb.getInt(8)
            fun f(o: Int) = bb.getFloat(o)
            var o = 52
            fun slice(len: Int): ByteBuffer {
                val d = ByteBuffer.allocateDirect(len).order(ByteOrder.nativeOrder())
                d.put(buf, o, len); d.position(0); o += len; return d
            }
            val posB = slice(nV * 6); val norB = slice(nV * 4); val uvB = slice(nV * 4); val idxB = slice(nI * 4)
            val bufs = IntArray(4); GLES30.glGenBuffers(4, bufs, 0)
            fun attrib(i: Int, name: String, data: ByteBuffer, size: Int, type: Int) {
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufs[i])
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.capacity(), data, GLES30.GL_STATIC_DRAW)
                val l = GLES30.glGetAttribLocation(prog, name)
                GLES30.glEnableVertexAttribArray(l)
                GLES30.glVertexAttribPointer(l, size, type, true, 0, 0)
            }
            attrib(0, "p", posB, 3, GLES30.GL_UNSIGNED_SHORT)
            attrib(1, "n", norB, 4, GLES30.GL_BYTE)
            attrib(2, "t", uvB, 2, GLES30.GL_UNSIGNED_SHORT)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, bufs[3])
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idxB.capacity(), idxB, GLES30.GL_STATIC_DRAW)
            GLES30.glUniform3f(GLES30.glGetUniformLocation(prog, "bmin"), f(12), f(16), f(20))
            GLES30.glUniform3f(GLES30.glGetUniformLocation(prog, "bsz"), f(24), f(28), f(32))
            GLES30.glUniform2f(GLES30.glGetUniformLocation(prog, "umin"), f(36), f(40))
            GLES30.glUniform2f(GLES30.glGetUniformLocation(prog, "usz"), f(44), f(48))
            // מרקם
            val tex = IntArray(1); GLES30.glGenTextures(1, tex, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
            val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.gun3d_tex, BitmapFactory.Options().apply { inScaled = false })
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0); bmp.recycle()
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            val uM = GLES30.glGetUniformLocation(prog, "M"); val uP = GLES30.glGetUniformLocation(prog, "P")
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            while (running) {
                val t0 = System.nanoTime()
                val m = state.matrices
                GLES30.glViewport(0, 0, width, height)
                GLES30.glClearColor(0f, 0f, 0f, 0f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
                if (m != null && state.visible) {
                    GLES30.glUniformMatrix4fv(uM, 1, false, m, 0)
                    GLES30.glUniformMatrix4fv(uP, 1, false, m, 16)
                    GLES30.glDrawElements(GLES30.GL_TRIANGLES, nI, GLES30.GL_UNSIGNED_INT, 0)
                }
                EGL14.eglSwapBuffers(dpy, surf)
                val spent = (System.nanoTime() - t0) / 1_000_000
                if (spent < 12) try { sleep(12 - spent) } catch (_: InterruptedException) { }
            }
        } finally {
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(dpy, surf); EGL14.eglDestroyContext(dpy, ctx); EGL14.eglTerminate(dpy)
        }
    }

    private fun buildProgram(): Int? {
        val vs = """#version 300 es
            in vec3 p; in vec4 n; in vec2 t; uniform mat4 M; uniform mat4 P; uniform vec3 bmin; uniform vec3 bsz; uniform vec2 umin; uniform vec2 usz;
            out vec3 N; out vec2 T;
            void main(){ vec3 q=bmin+p*bsz; N=mat3(M)*n.xyz; T=umin+t*usz; gl_Position=P*(M*vec4(q,1.0)); }"""
        val fs = """#version 300 es
            precision highp float; in vec3 N; in vec2 T; uniform sampler2D tex; out vec4 o;
            void main(){ vec3 n=normalize(N); vec3 col=texture(tex,T).rgb; vec3 L1=normalize(vec3(-.4,.8,.6)); vec3 L2=normalize(vec3(.7,.2,.5));
              float d=.55+.45*max(dot(n,L1),0.)+.25*max(dot(n,L2),0.); o=vec4(col*d,1.0); }"""
        fun sh(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type); GLES30.glShaderSource(s, src); GLES30.glCompileShader(s); return s
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, sh(GLES30.GL_VERTEX_SHADER, vs)); GLES30.glAttachShader(p, sh(GLES30.GL_FRAGMENT_SHADER, fs))
        GLES30.glLinkProgram(p)
        val ok = IntArray(1); GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        return if (ok[0] == 0) null else p
    }
}

/** שכבת GL שקופה שמציירת את הרובה. אין מגע. */
@Composable
fun Gun3DView(state: Gun3DState, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                isClickable = false
                isFocusable = false
                var renderer: Gun3DRenderer? = null
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        renderer = Gun3DRenderer(ctx.applicationContext, state).also {
                            it.surface = st; it.width = w; it.height = h; it.start()
                        }
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        renderer?.width = w; renderer?.height = h
                    }
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        renderer?.running = false
                        try { renderer?.join(500) } catch (_: InterruptedException) { }
                        renderer = null
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        }
    )
}
