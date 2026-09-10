package com.lagradost.cloudstream3.ui.utils

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError

/**
 * Native Hardware-Accelerated Theme Background Manager.
 *
 * Migrated from Chromium WebGL WebView to native Android AGSL (RuntimeShader on API 33+)
 * and OpenGL ES 2.0 fallback (API < 33).
 *
 * Provides:
 * 1. 100% native 60fps procedural wave dynamics with 0% WebView overhead.
 * 2. Zero Chromium process RAM usage and zero JavaScript timer freezes.
 * 3. Exact visual and mathematical fidelity with theme color palettes.
 * 4. Automatic lifecycle pausing (0% CPU/GPU overhead when off-screen).
 */
class TvThemeBackgroundHelper(private val context: Context) {

    private var shaderView: ThemeShaderView? = null
    private var scrimOverlayView: View? = null
    private var attachedView: View? = null
    private var currentLoadedTheme: String? = null

    companion object {
        const val DEFAULT_THEME = ThemeShaderCode.DEFAULT_THEME

        @Deprecated("Migrated to native AGSL/GLES shaders; kept for binary compatibility")
        fun getThemeAsset(themeKey: String?): String {
            return when (themeKey) {
                "MidnightMonochrome" -> "themes/midnight_monochrome.html"
                "WarmMinimal" -> "themes/warm_minimal.html"
                "SageCream" -> "themes/sage_and_cream.html"
                "ArcticGlass" -> "themes/arctic_glass.html"
                "SoftLavender" -> "themes/soft_lavender.html"
                "ObsidianElectric" -> "themes/obsidian_electric.html"
                "PureMono" -> "themes/pure_mono.html"
                else -> "themes/midnight_monochrome.html"
            }
        }
    }

    /**
     * Replaces or overlays a TextureView or existing background View with the native theme shader.
     */
    fun attach(targetView: View, onReady: (() -> Unit)? = null) {
        release()
        attachedView = targetView

        val parent = targetView.parent as? ViewGroup ?: return
        val context = targetView.context

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val currentTheme = prefs.getString(context.getString(R.string.app_theme_key), DEFAULT_THEME) ?: DEFAULT_THEME

        val sv = ThemeShaderView(context, initialTheme = currentTheme).apply {
            this.onReady = onReady
            isFocusable = false
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val index = parent.indexOfChild(targetView)

        val layoutParams = if (parent is ConstraintLayout) {
            ConstraintLayout.LayoutParams(0, 0).apply {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
        } else {
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        sv.layoutParams = layoutParams

        // Hide original view so only the hardware-accelerated native shader renders
        targetView.visibility = View.INVISIBLE

        parent.addView(sv, index, layoutParams)
        currentLoadedTheme = currentTheme
        shaderView = sv

        // Trigger onReady immediately once laid out
        sv.post {
            onReady?.invoke()
        }
    }

    /**
     * Attaches directly to any container ViewGroup (e.g. settings_top_root) at a specific child index.
     */
    fun attachToContainer(
        container: ViewGroup,
        insertAtIndex: Int = 0,
        addScrim: Boolean = true,
        onReady: (() -> Unit)? = null
    ) {
        release()

        val context = container.context
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val currentTheme = prefs.getString(context.getString(R.string.app_theme_key), DEFAULT_THEME) ?: DEFAULT_THEME

        val sv = ThemeShaderView(context, initialTheme = currentTheme).apply {
            this.onReady = onReady
            isFocusable = false
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val lp = if (container is ConstraintLayout) {
            ConstraintLayout.LayoutParams(0, 0).apply {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
        } else {
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val safeIndex = insertAtIndex.coerceIn(0, container.childCount)
        container.addView(sv, safeIndex, lp)
        currentLoadedTheme = currentTheme
        shaderView = sv

        if (addScrim) {
            val scrim = View(context).apply {
                background = ContextCompat.getDrawable(context, R.drawable.bg_tv_search_video_overlay)
                isFocusable = false
                isClickable = false
            }
            val scrimLp = if (container is ConstraintLayout) {
                ConstraintLayout.LayoutParams(0, 0).apply {
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                }
            } else {
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            val scrimIndex = (safeIndex + 1).coerceIn(0, container.childCount)
            container.addView(scrim, scrimIndex, scrimLp)
            scrimOverlayView = scrim
        }

        sv.post {
            onReady?.invoke()
        }
    }

    fun resume() {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val currentTheme = prefs.getString(context.getString(R.string.app_theme_key), DEFAULT_THEME) ?: DEFAULT_THEME
            if (currentLoadedTheme != null && currentLoadedTheme != currentTheme) {
                currentLoadedTheme = currentTheme
                shaderView?.setTheme(currentTheme)
            }
            shaderView?.resumeAnimation()
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun pause() {
        try {
            shaderView?.pauseAnimation()
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun release() {
        try {
            attachedView?.visibility = View.VISIBLE
            attachedView = null

            scrimOverlayView?.apply {
                (parent as? ViewGroup)?.removeView(this)
            }
            scrimOverlayView = null

            shaderView?.apply {
                release()
                (parent as? ViewGroup)?.removeView(this)
            }
            shaderView = null
        } catch (e: Exception) {
            logError(e)
        }
    }
}
