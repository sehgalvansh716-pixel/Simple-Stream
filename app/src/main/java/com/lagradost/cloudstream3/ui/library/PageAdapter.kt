package com.lagradost.cloudstream3.ui.library

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.google.android.material.tabs.TabLayout
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.SearchResultGridBinding
import com.lagradost.cloudstream3.databinding.SearchResultGridExpandedBinding
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import kotlin.math.roundToInt

class PageAdapter(
    private val resView: AutofitRecyclerView,
    val clickCallback: (SearchClickCallback) -> Unit
) :
    NoStateAdapter<SyncAPI.LibraryItem>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
        if (a.id != null || b.id != null) {
            a.id == b.id
        } else {
            a.name == b.name && a.url == b.url
        }
    })) {
    private val coverHeight: Int get() = (resView.itemWidth / 0.68).roundToInt()

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val isTv = isLayout(TV or EMULATOR)
        val binding = if (isTv) {
            SearchResultGridBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        } else {
            SearchResultGridExpandedBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        }
        return ViewHolderState(binding)
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        when (val binding = holder.view) {
            is SearchResultGridBinding -> clearImage(binding.imageView)
            is SearchResultGridExpandedBinding -> clearImage(binding.imageView)
        }
    }

    override fun onBindContent(
        holder: ViewHolderState<Any>,
        item: SyncAPI.LibraryItem,
        position: Int
    ) {
        val (imageView, watchProgress, imageText) = when (val binding = holder.view) {
            is SearchResultGridBinding -> Triple(binding.imageView, binding.watchProgress, binding.imageText)
            is SearchResultGridExpandedBinding -> Triple(binding.imageView, binding.watchProgress, binding.imageText)
            else -> return
        }

        /** https://stackoverflow.com/questions/8817522/how-to-get-color-code-of-image-view */
        SearchResultBuilder.bind(
            this@PageAdapter.clickCallback,
            item,
            position,
            holder.itemView,
        )

        // Fix cover height
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            coverHeight
        )
        if (params.height != imageView.layoutParams.height || params.width != imageView.layoutParams.width) {
            imageView.layoutParams = params
        }

        val showProgress = item.episodesCompleted?.let { it > 0 } == true && item.episodesTotal != null
        watchProgress.isVisible = showProgress
        if (showProgress) {
            watchProgress.max = item.episodesTotal ?: 100
            watchProgress.progress = item.episodesCompleted ?: 0
        }

        imageText.text = item.name

        if (isLayout(TV or EMULATOR)) {
            holder.itemView.setOnKeyListener { v, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && position in 0..3) {
                        val tabLayout = v.rootView?.findViewById<TabLayout>(R.id.library_tab_layout)
                        val selectedTab = tabLayout?.getTabAt(tabLayout.selectedTabPosition)?.view
                        selectedTab?.requestFocus() ?: tabLayout?.requestFocus()
                        return@setOnKeyListener true
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && position % 4 == 0) {
                        val optionsItem = v.rootView?.findViewById<View>(R.id.tv_provider_pill)
                            ?: v.rootView?.findViewById<View>(R.id.tv_library_search_capsule)
                        optionsItem?.requestFocus()
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }
    }
}