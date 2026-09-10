package com.lagradost.cloudstream3.utils
//Reference: https://stackoverflow.com/a/29055283
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.core.content.withStyledAttributes
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError

/**
 * A custom [AppCompatImageView] that allows precise control over the visible crop area
 * of an image by adjusting its horizontal and vertical center offset percentages.
 * Also supports a smooth bottom alpha dissolve (enableBottomFade) to seamlessly blend
 * the sharp hero image into an extended blurred ambient backdrop without container boundaries.
 */
class PercentageCropImageView : androidx.appcompat.widget.AppCompatImageView {
    private var mCropYCenterOffsetPct: Float? = null
    private var mCropXCenterOffsetPct: Float? = null

    private var fadeBottom: Boolean = false
    private val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private var fadeShader: LinearGradient? = null
    private var lastHeight: Int = 0

    var enableBottomFade: Boolean
        get() = fadeBottom
        set(value) {
            fadeBottom = value
            invalidate()
        }

    constructor(context: Context?) : super(context!!)

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        initAttrs(context, attrs)
    }

    constructor(
        context: Context?, attrs: AttributeSet?,
        defStyle: Int
    ) : super(context!!, attrs, defStyle) {
        initAttrs(context, attrs)
    }

    var cropYCenterOffsetPct: Float
        get() = mCropYCenterOffsetPct!!
        set(cropYCenterOffsetPct) {
            require(cropYCenterOffsetPct <= 1.0) { "Value too large: Must be <= 1.0" }
            mCropYCenterOffsetPct = cropYCenterOffsetPct
        }
    var cropXCenterOffsetPct: Float
        get() = mCropXCenterOffsetPct!!
        set(cropXCenterOffsetPct) {
            require(cropXCenterOffsetPct <= 1.0) { "Value too large: Must be <= 1.0" }
            mCropXCenterOffsetPct = cropXCenterOffsetPct
        }

    private fun myConfigureBounds() {
        if (this.scaleType == ScaleType.MATRIX) {

            val d = this.drawable
            if (d != null) {
                val dWidth = d.intrinsicWidth
                val dHeight = d.intrinsicHeight
                val m = Matrix()
                val vWidth = width - this.paddingLeft - this.paddingRight
                val vHeight = height - this.paddingTop - this.paddingBottom
                val scale: Float
                var dx = 0f
                var dy = 0f
                if (dWidth * vHeight > vWidth * dHeight) {
                    val cropXCenterOffsetPct =
                        if (mCropXCenterOffsetPct != null) mCropXCenterOffsetPct!! else 0.5f
                    scale = vHeight.toFloat() / dHeight.toFloat()
                    dx = (vWidth - dWidth * scale) * cropXCenterOffsetPct
                } else {
                    val cropYCenterOffsetPct =
                        if (mCropYCenterOffsetPct != null) mCropYCenterOffsetPct!! else 0f
                    scale = vWidth.toFloat() / dWidth.toFloat()
                    dy = (vHeight - dHeight * scale) * cropYCenterOffsetPct
                }
                m.setScale(scale, scale)
                m.postTranslate((dx + 0.5f).toInt().toFloat(), (dy + 0.5f).toInt().toFloat())
                this.imageMatrix = m
            }
        }
    }

    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
        val changed = super.setFrame(l, t, r, b)
        myConfigureBounds()
        return changed
    }

    override fun setImageDrawable(d: Drawable?) {
        super.setImageDrawable(d)
        myConfigureBounds()
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        myConfigureBounds()
    }

    override fun onDraw(canvas: Canvas) {
        if (fadeBottom && height > 0 && width > 0) {
            val checkpoint = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            super.onDraw(canvas)
            if (fadeShader == null || lastHeight != height) {
                lastHeight = height
                val fadeStart = height * 0.52f
                fadeShader = LinearGradient(
                    0f, fadeStart, 0f, height.toFloat(),
                    Color.BLACK, Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                fadePaint.shader = fadeShader
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)
            canvas.restoreToCount(checkpoint)
        } else {
            super.onDraw(canvas)
        }
    }

    fun redraw() {
        val d = this.drawable
        if (d != null) {
            setImageDrawable(null)
            setImageDrawable(d)
        }
    }

    private fun initAttrs(context: Context, attrs: AttributeSet?) {
        attrs ?: return
        context.withStyledAttributes(attrs, R.styleable.PercentageCropImageView) {
            try {
                if (hasValue(R.styleable.PercentageCropImageView_cropYCenterOffsetPct)) {
                    mCropYCenterOffsetPct = getFloat(
                        R.styleable.PercentageCropImageView_cropYCenterOffsetPct,
                        0.5f
                    )
                }
                if (hasValue(R.styleable.PercentageCropImageView_cropXCenterOffsetPct)) {
                    mCropXCenterOffsetPct = getFloat(
                        R.styleable.PercentageCropImageView_cropXCenterOffsetPct,
                        0.5f
                    )
                }
                if (hasValue(R.styleable.PercentageCropImageView_enableBottomFade)) {
                    fadeBottom = getBoolean(
                        R.styleable.PercentageCropImageView_enableBottomFade,
                        false
                    )
                }
            } catch (e: Exception) {
                logError(e)
            }
        }
    }
}