package com.lagradost.cloudstream3.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import androidx.core.view.marginEnd
import com.lagradost.cloudstream3.R
import kotlin.math.max

class FlowLayout : ViewGroup {
    var itemSpacing: Int = 0

    constructor(context: Context?) : super(context)

    //@JvmOverloads
    //constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    @SuppressLint("CustomViewStyleable")
    internal constructor(c: Context, attrs: AttributeSet?) : super(c, attrs) {
        c.withStyledAttributes(attrs, R.styleable.FlowLayout_Layout) {
            itemSpacing = getDimensionPixelSize(R.styleable.FlowLayout_Layout_itemSpacing, 0)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val realWidth = MeasureSpec.getSize(widthMeasureSpec)
        var currentWidth = 0
        var currentY = 0
        var currentX = 0

        val rowChildren = mutableListOf<View>()
        var rowMaxHeight = 0

        fun finalizeRow() {
            if (rowChildren.isEmpty()) return
            for (child in rowChildren) {
                val lp = child.layoutParams as LayoutParams
                val childHeight = child.measuredHeight
                lp.y = currentY + (rowMaxHeight - childHeight) / 2
            }
            currentWidth = max(currentWidth, currentX - if (currentX > 0) itemSpacing else 0)
            currentY += rowMaxHeight + itemSpacing
            rowChildren.clear()
            rowMaxHeight = 0
            currentX = 0
        }

        val childCount = this.childCount
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (!child.isVisible) {
                continue
            }
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            // check if child can be placed in current row, else wrap to new line
            if (currentX > 0 && currentX + childWidth - child.marginEnd - child.paddingEnd > realWidth) {
                finalizeRow()
            }

            val lp = child.layoutParams as LayoutParams
            lp.x = currentX
            rowChildren.add(child)
            rowMaxHeight = max(rowMaxHeight, childHeight)
            currentX += childWidth + itemSpacing
        }
        finalizeRow()

        val finalHeight = if (currentY > 0) currentY - itemSpacing else 0

        setMeasuredDimension(
            resolveSize(currentWidth, widthMeasureSpec),
            resolveSize(finalHeight, heightMeasureSpec)
        )
    }

    override fun onLayout(b: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        //call layout on children
        val childCount = this.childCount
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams
            child.layout(lp.x, lp.y, lp.x + child.measuredWidth, lp.y + child.measuredHeight)
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams {
        return LayoutParams(context, attrs)
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams): LayoutParams {
        return LayoutParams(p)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams): Boolean {
        return p is LayoutParams
    }

    class LayoutParams : MarginLayoutParams {
        var spacing = -1
        var x = 0
        var y = 0

        @SuppressLint("CustomViewStyleable")
        internal constructor(c: Context, attrs: AttributeSet?) : super(c, attrs) {
            c.withStyledAttributes(attrs, R.styleable.FlowLayout_Layout) {
                spacing = 0
            }
        }

        internal constructor(width: Int, height: Int) : super(width, height) {
            spacing = 0
        }

        constructor(source: MarginLayoutParams?) : super(source)
        internal constructor(source: ViewGroup.LayoutParams?) : super(source)
    }
}