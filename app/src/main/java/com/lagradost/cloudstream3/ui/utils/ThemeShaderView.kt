package com.lagradost.cloudstream3.ui.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RuntimeShader
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.mvvm.logError
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Hardware-accelerated native theme shader background view.
 *
 * Uses:
 * - Android 13+ (API 33+): [AgslCanvasView] powered by Skia [RuntimeShader] on Canvas.
 * - Android < 33 (or fallback): [ThemeGlesView] powered by OpenGL ES 2.0 on GLSurfaceView.
 *
 * Lifecycle-aware: automatically pauses animation on pause or when detached from window,
 * consuming 0% CPU and 0% GPU when off-screen.
 */
class ThemeShaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    initialTheme: String = ThemeShaderCode.DEFAULT_THEME
) : FrameLayout(context, attrs, defStyleAttr) {

    private interface IShaderChild {
        fun setTheme(themeKey: String)
        fun pauseAnimation()
        fun resumeAnimation()
        fun release()
        var onFirstFrameRendered: (() -> Unit)?
    }

    private var activeChild: IShaderChild? = null
    var onReady: (() -> Unit)? = null

    init {
        var initialized = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val agsl = AgslCanvasView(context, initialTheme).apply {
                    onFirstFrameRendered = {
                        this@ThemeShaderView.onReady?.invoke()
                    }
                }
                addView(agsl, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                activeChild = agsl
                initialized = true
            } catch (t: Throwable) {
                logError(t)
            }
        }

        if (!initialized) {
            try {
                val gles = ThemeGlesView(context, initialTheme).apply {
                    onFirstFrameRendered = {
                        this@ThemeShaderView.onReady?.invoke()
                    }
                }
                addView(gles, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                activeChild = gles
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }

    fun setTheme(themeKey: String) {
        activeChild?.setTheme(themeKey)
    }

    fun pauseAnimation() {
        activeChild?.pauseAnimation()
    }

    fun resumeAnimation() {
        activeChild?.resumeAnimation()
    }

    fun release() {
        activeChild?.release()
        activeChild = null
        removeAllViews()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resumeAnimation()
    }

    override fun onDetachedFromWindow() {
        pauseAnimation()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) {
            resumeAnimation()
        } else {
            pauseAnimation()
        }
    }

    override fun draw(canvas: Canvas) {
        if (!canvas.isHardwareAccelerated) {
            canvas.drawColor(0xFF0B0E14.toInt())
            return
        }
        super.draw(canvas)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!canvas.isHardwareAccelerated) {
            canvas.drawColor(0xFF0B0E14.toInt())
            return
        }
        super.dispatchDraw(canvas)
    }

    // =========================================================================
    // API 33+ AGSL SkSL Canvas View
    // =========================================================================
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private class AgslCanvasView(
        context: Context,
        initialTheme: String
    ) : View(context), IShaderChild {

        override var onFirstFrameRendered: (() -> Unit)? = null

        private var currentThemeKey: String = ThemeShaderCode.normalizeThemeKey(initialTheme)
        private var runtimeShader: RuntimeShader? = null
        private val paint = Paint()

        private var startTime: Long = SystemClock.uptimeMillis()
        private var pausedAccumulatedTime: Long = 0L
        private var isPlaying: Boolean = true
        private var firstFrameNotified: Boolean = false

        init {
            loadShader(currentThemeKey)
        }

        private fun loadShader(themeKey: String) {
            try {
                val agslSource = ThemeShaderCode.getAgslShader(themeKey)
                val shader = RuntimeShader(agslSource)
                if (width > 0 && height > 0) {
                    shader.setFloatUniform("u_res", width.toFloat(), height.toFloat())
                }
                runtimeShader = shader
                paint.shader = shader
            } catch (t: Throwable) {
                logError(t)
            }
        }

        override fun setTheme(themeKey: String) {
            val normalized = ThemeShaderCode.normalizeThemeKey(themeKey)
            if (currentThemeKey == normalized) return
            currentThemeKey = normalized
            loadShader(normalized)
            if (isPlaying) {
                postInvalidateOnAnimation()
            }
        }

        override fun pauseAnimation() {
            if (!isPlaying) return
            isPlaying = false
            pausedAccumulatedTime = SystemClock.uptimeMillis() - startTime
        }

        override fun resumeAnimation() {
            if (isPlaying) return
            isPlaying = true
            startTime = SystemClock.uptimeMillis() - pausedAccumulatedTime
            postInvalidateOnAnimation()
        }

        override fun release() {
            isPlaying = false
            runtimeShader = null
            paint.shader = null
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) {
                runtimeShader?.setFloatUniform("u_res", w.toFloat(), h.toFloat())
            }
        }

        override fun draw(canvas: Canvas) {
            if (!canvas.isHardwareAccelerated) {
                canvas.drawColor(0xFF0B0E14.toInt())
                return
            }
            super.draw(canvas)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!canvas.isHardwareAccelerated) {
                canvas.drawColor(0xFF0B0E14.toInt())
                return
            }
            val shader = runtimeShader ?: run {
                canvas.drawColor(0xFF0B0E14.toInt())
                return
            }
            val w = width
            val h = height
            if (w <= 0 || h <= 0) return

            val now = SystemClock.uptimeMillis()
            val elapsedSeconds = (now - startTime) / 1000f

            try {
                shader.setFloatUniform("u_res", w.toFloat(), h.toFloat())
                shader.setFloatUniform("u_time", elapsedSeconds)
                canvas.drawPaint(paint)
            } catch (t: Throwable) {
                canvas.drawColor(0xFF0B0E14.toInt())
                return
            }

            if (!firstFrameNotified) {
                firstFrameNotified = true
                post { onFirstFrameRendered?.invoke() }
            }

            if (isPlaying) {
                postInvalidateOnAnimation()
            }
        }
    }

    // =========================================================================
    // API < 33 OpenGL ES 2.0 GLSurfaceView
    // =========================================================================
    private class ThemeGlesView(
        context: Context,
        initialTheme: String
    ) : GLSurfaceView(context), IShaderChild {

        override var onFirstFrameRendered: (() -> Unit)? = null

        private val renderer: ThemeGlesRenderer

        init {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            setZOrderMediaOverlay(false)

            renderer = ThemeGlesRenderer(initialTheme) {
                post { onFirstFrameRendered?.invoke() }
            }
            setRenderer(renderer)
            renderMode = RENDERMODE_CONTINUOUSLY
        }

        override fun setTheme(themeKey: String) {
            renderer.setTheme(themeKey)
        }

        override fun pauseAnimation() {
            renderer.pause()
            onPause()
        }

        override fun resumeAnimation() {
            renderer.resume()
            onResume()
        }

        override fun release() {
            renderer.release()
            onPause()
        }
    }

    // =========================================================================
    // GLES 2.0 Renderer Implementation
    // =========================================================================
    private class ThemeGlesRenderer(
        initialTheme: String,
        private val onFirstFrame: () -> Unit
    ) : GLSurfaceView.Renderer {

        private var currentThemeKey = ThemeShaderCode.normalizeThemeKey(initialTheme)
        private var pendingThemeKey: String? = null

        private var programId: Int = 0
        private var pPositionHandle: Int = -1
        private var uResHandle: Int = -1
        private var uTimeHandle: Int = -1

        private var width: Int = 0
        private var height: Int = 0
        private var startTime: Long = SystemClock.uptimeMillis()
        private var pausedAccumulatedTime: Long = 0L
        private var isPlaying: Boolean = true
        private var firstFrameReported: Boolean = false

        private val quadBuffer: FloatBuffer

        init {
            val quadCoords = floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                -1f,  1f,
                 1f, -1f,
                 1f,  1f
            )
            val bb = ByteBuffer.allocateDirect(quadCoords.size * 4)
            bb.order(ByteOrder.nativeOrder())
            quadBuffer = bb.asFloatBuffer()
            quadBuffer.put(quadCoords)
            quadBuffer.position(0)
        }

        fun setTheme(themeKey: String) {
            val normalized = ThemeShaderCode.normalizeThemeKey(themeKey)
            if (currentThemeKey == normalized && pendingThemeKey == null) return
            pendingThemeKey = normalized
        }

        fun pause() {
            if (!isPlaying) return
            isPlaying = false
            pausedAccumulatedTime = SystemClock.uptimeMillis() - startTime
        }

        fun resume() {
            if (isPlaying) return
            isPlaying = true
            startTime = SystemClock.uptimeMillis() - pausedAccumulatedTime
        }

        fun release() {
            isPlaying = false
            deleteProgram()
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            compileAndLink(currentThemeKey)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            this.width = width
            this.height = height
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            pendingThemeKey?.let { newTheme ->
                pendingThemeKey = null
                currentThemeKey = newTheme
                deleteProgram()
                compileAndLink(newTheme)
            }

            if (programId == 0) return

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(programId)

            val now = SystemClock.uptimeMillis()
            val elapsed = if (isPlaying) {
                (now - startTime) / 1000f
            } else {
                pausedAccumulatedTime / 1000f
            }

            if (uResHandle >= 0) {
                GLES20.glUniform2f(uResHandle, width.toFloat(), height.toFloat())
            }
            if (uTimeHandle >= 0) {
                GLES20.glUniform1f(uTimeHandle, elapsed)
            }

            if (pPositionHandle >= 0) {
                quadBuffer.position(0)
                GLES20.glVertexAttribPointer(pPositionHandle, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)
                GLES20.glEnableVertexAttribArray(pPositionHandle)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
                GLES20.glDisableVertexAttribArray(pPositionHandle)
            }

            if (!firstFrameReported) {
                firstFrameReported = true
                onFirstFrame.invoke()
            }
        }

        private fun compileAndLink(themeKey: String) {
            try {
                val vsSource = ThemeShaderCode.GLSL_VERTEX_SHADER
                val fsSource = ThemeShaderCode.getGlslFragmentShader(themeKey)

                val vs = loadShader(GLES20.GL_VERTEX_SHADER, vsSource)
                val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fsSource)
                if (vs == 0 || fs == 0) return

                val prog = GLES20.glCreateProgram()
                if (prog == 0) return

                GLES20.glAttachShader(prog, vs)
                GLES20.glAttachShader(prog, fs)
                GLES20.glLinkProgram(prog)

                val linkStatus = IntArray(1)
                GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
                if (linkStatus[0] != GLES20.GL_TRUE) {
                    val info = GLES20.glGetProgramInfoLog(prog)
                    android.util.Log.e("ThemeShaderView", "GL Link Error: $info")
                    GLES20.glDeleteProgram(prog)
                    return
                }

                programId = prog
                pPositionHandle = GLES20.glGetAttribLocation(prog, "p")
                uResHandle = GLES20.glGetUniformLocation(prog, "u_res")
                uTimeHandle = GLES20.glGetUniformLocation(prog, "u_time")
            } catch (t: Throwable) {
                logError(t)
            }
        }

        private fun deleteProgram() {
            if (programId != 0) {
                GLES20.glDeleteProgram(programId)
                programId = 0
            }
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            if (shader != 0) {
                GLES20.glShaderSource(shader, shaderCode)
                GLES20.glCompileShader(shader)
                val compiled = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
                if (compiled[0] == 0) {
                    val info = GLES20.glGetShaderInfoLog(shader)
                    android.util.Log.e("ThemeShaderView", "GL Compile Error ($type): $info")
                    GLES20.glDeleteShader(shader)
                    return 0
                }
            }
            return shader
        }
    }
}
