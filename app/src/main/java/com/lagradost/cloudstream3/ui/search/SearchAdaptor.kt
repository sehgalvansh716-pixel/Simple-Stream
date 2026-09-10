package com.lagradost.cloudstream3.ui.search

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.SearchResultGridBinding
import com.lagradost.cloudstream3.databinding.SearchResultGridExpandedBinding
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.newSharedPool
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.UIHelper.isBottomLayout
import kotlin.math.roundToInt

/** Click */
const val SEARCH_ACTION_LOAD = 0

/** Long press */
const val SEARCH_ACTION_SHOW_METADATA = 1
const val SEARCH_ACTION_PLAY_FILE = 2
const val SEARCH_ACTION_FOCUSED = 4
const val SEARCH_ACTION_DPAD_UP_TOP_ROW = 5

class SearchClickCallback(
    val action: Int,
    val view: View,
    val position: Int,
    val card: SearchResponse
)

class SearchAdapter(
    private val resView: AutofitRecyclerView,
    private val isHorizontal:Boolean = false,
    private val clickCallback: (SearchClickCallback) -> Unit,
) : NoStateAdapter<SearchResponse>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
    if (a.id != null || b.id != null) {
        a.id == b.id
    } else {
        a.name == b.name
    }
})) {
    companion object {
        val sharedPool =
            newSharedPool { setMaxRecycledViews(CONTENT, 10) }
    }

    var hasNext: Boolean = false

    private val coverRatio = if (isHorizontal) 1.8 else 0.68

    private val coverHeight: Int
        get() {
            val isTv = isLayout(TV or EMULATOR)
            if (isTv) {
                val dm = resView.resources.displayMetrics
                val totalHorizontalPadding = (72 * dm.density).toInt() + (4 * (16 * dm.density).toInt())
                val colWidth = (dm.widthPixels - totalHorizontalPadding) / 4
                return (colWidth / coverRatio).roundToInt()
            }
            return (resView.itemWidth / coverRatio).roundToInt()
        }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val inflater = LayoutInflater.from(parent.context)
        val isTv = isLayout(TV or EMULATOR)

        val layout =
            if (!isTv) SearchResultGridExpandedBinding.inflate(
                inflater,
                parent,
                false
            ) else SearchResultGridBinding.inflate(
                inflater,
                parent,
                false
            )
        return ViewHolderState(layout)
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        clearImage(
            when (val binding = holder.view) {
                is SearchResultGridExpandedBinding -> binding.imageView
                is SearchResultGridBinding -> binding.imageView
                else -> null
            }
        )
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: SearchResponse, position: Int) {
        val isTv = isLayout(TV or EMULATOR)
        val imageView = when (val binding = holder.view) {
            is SearchResultGridExpandedBinding -> binding.imageView
            is SearchResultGridBinding -> binding.imageView
            else -> null
        }

        if (isTv && imageView != null) {
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                coverHeight
            )
            if (imageView.layoutParams.width != params.width || imageView.layoutParams.height != params.height) {
                imageView.layoutParams = params
            }
        }
        SearchResultBuilder.bind(clickCallback, item, position, holder.view.root)
        if (isLayout(TV or EMULATOR)) {
            val span = 4
            holder.view.root.setOnKeyListener { v, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val totalCount = itemCount
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            val targetPos = position + span
                            if (targetPos < totalCount) {
                                val targetHolder = resView.findViewHolderForAdapterPosition(targetPos)
                                if (targetHolder != null && targetHolder.itemView.isAttachedToWindow) {
                                    targetHolder.itemView.requestFocus()
                                } else {
                                    resView.smoothScrollToPosition(targetPos)
                                    resView.postDelayed({
                                        val h = resView.findViewHolderForAdapterPosition(targetPos)
                                        if (h != null && h.itemView.isAttachedToWindow) {
                                            h.itemView.requestFocus()
                                        } else {
                                            resView.postDelayed({
                                                resView.findViewHolderForAdapterPosition(targetPos)?.itemView?.requestFocus()
                                            }, 80)
                                        }
                                    }, 220)
                                }
                                return@setOnKeyListener true
                            } else if (position / span < (totalCount - 1) / span) {
                                val lastPos = totalCount - 1
                                val targetHolder = resView.findViewHolderForAdapterPosition(lastPos)
                                if (targetHolder != null && targetHolder.itemView.isAttachedToWindow) {
                                    targetHolder.itemView.requestFocus()
                                } else {
                                    resView.smoothScrollToPosition(lastPos)
                                    resView.postDelayed({
                                        val h = resView.findViewHolderForAdapterPosition(lastPos)
                                        if (h != null && h.itemView.isAttachedToWindow) {
                                            h.itemView.requestFocus()
                                        } else {
                                            resView.postDelayed({
                                                resView.findViewHolderForAdapterPosition(lastPos)?.itemView?.requestFocus()
                                            }, 80)
                                        }
                                    }, 220)
                                }
                                return@setOnKeyListener true
                            } else {
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (position in 0 until span) {
                                resView.smoothScrollToPosition(0)
                                clickCallback(SearchClickCallback(SEARCH_ACTION_DPAD_UP_TOP_ROW, v, position, item))
                                return@setOnKeyListener true
                            } else {
                                val targetPos = position - span
                                val targetHolder = resView.findViewHolderForAdapterPosition(targetPos)
                                if (targetHolder != null && targetHolder.itemView.isAttachedToWindow) {
                                    targetHolder.itemView.requestFocus()
                                } else {
                                    resView.scrollToPosition(targetPos)
                                    resView.post {
                                        val h = resView.findViewHolderForAdapterPosition(targetPos)
                                        if (h != null && h.itemView.isAttachedToWindow) {
                                            h.itemView.requestFocus()
                                        } else {
                                            resView.postDelayed({
                                                resView.findViewHolderForAdapterPosition(targetPos)?.itemView?.requestFocus()
                                            }, 30)
                                        }
                                    }
                                }
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (position % span == 0) {
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (position % span == span - 1 || position == totalCount - 1) {
                                return@setOnKeyListener true
                            }
                        }
                    }
                }
                false
            }
        }
    }
}