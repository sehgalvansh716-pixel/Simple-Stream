package com.lagradost.cloudstream3.ui.search

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.SearchHistoryFooterBinding
import com.lagradost.cloudstream3.databinding.SearchHistoryFooterTvBinding
import com.lagradost.cloudstream3.databinding.SearchHistoryItemBinding
import com.lagradost.cloudstream3.databinding.SearchHistoryItemTvBinding
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchHistoryItem(
    @JsonProperty("searchedAt") @SerialName("searchedAt") val searchedAt: Long,
    @JsonProperty("searchText") @SerialName("searchText") val searchText: String,
    @JsonProperty("type") @SerialName("type") val type: List<TvType>,
    @JsonProperty("key") @SerialName("key") val key: String,
)

data class SearchHistoryCallback(
    val item: SearchHistoryItem?,
    val clickAction: Int,
)

const val SEARCH_HISTORY_OPEN = 0
const val SEARCH_HISTORY_REMOVE = 1
const val SEARCH_HISTORY_CLEAR = 2

class SearchHistoryAdaptor(
    private val clickCallback: (SearchHistoryCallback) -> Unit,
) : NoStateAdapter<SearchHistoryItem>(diffCallback = BaseDiffCallback(itemSame = { a,b ->
    a.searchedAt == b.searchedAt && a.searchText == b.searchText
})) {
    
    // Add footer for all layouts
    override val footers = 1
    
    override fun submitList(list: Collection<SearchHistoryItem>?, commitCallback: Runnable?) {
        super.submitList(list, commitCallback)
        // Notify footer to rebind when list changes to update visibility
        if (footers > 0) {
            notifyItemChanged(itemCount - 1)
        }
    }
    
    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (isLayout(TV or EMULATOR)) {
            SearchHistoryItemTvBinding.inflate(inflater, parent, false)
        } else {
            SearchHistoryItemBinding.inflate(inflater, parent, false)
        }
        return ViewHolderState(binding)
    }

    override fun onBindContent(
        holder: ViewHolderState<Any>,
        item: SearchHistoryItem,
        position: Int
    ) {
        when (val binding = holder.view) {
            is SearchHistoryItemTvBinding -> {
                binding.homeHistoryTitle.text = item.searchText
                binding.homeHistoryRemove.setOnClickListener {
                    clickCallback.invoke(SearchHistoryCallback(item, SEARCH_HISTORY_REMOVE))
                }
                binding.homeHistoryTab.setOnClickListener {
                    clickCallback.invoke(SearchHistoryCallback(item, SEARCH_HISTORY_OPEN))
                }
                binding.homeHistoryTab.setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        val searchInput = binding.root.rootView?.findViewById<View>(androidx.appcompat.R.id.search_src_text)
                        if (searchInput != null) {
                            searchInput.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                    false
                }
            }
            is SearchHistoryItemBinding -> {
                binding.homeHistoryTitle.text = item.searchText
                binding.homeHistoryRemove.setOnClickListener {
                    clickCallback.invoke(SearchHistoryCallback(item, SEARCH_HISTORY_REMOVE))
                }
                binding.homeHistoryTab.setOnClickListener {
                    clickCallback.invoke(SearchHistoryCallback(item, SEARCH_HISTORY_OPEN))
                }
            }
        }
    }
    
    override fun onCreateFooter(parent: ViewGroup): ViewHolderState<Any> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (isLayout(TV or EMULATOR)) {
            SearchHistoryFooterTvBinding.inflate(inflater, parent, false)
        } else {
            SearchHistoryFooterBinding.inflate(inflater, parent, false)
        }
        return ViewHolderState(binding)
    }
    
    override fun onBindFooter(holder: ViewHolderState<Any>) {
        val button = when (val binding = holder.view) {
            is SearchHistoryFooterTvBinding -> binding.searchClearCallHistory
            is SearchHistoryFooterBinding -> binding.searchClearCallHistory
            else -> null
        } ?: return

        button.apply {
            isGone = immutableCurrentList.isEmpty()
            if (isLayout(TV or EMULATOR)) {
                isFocusable = true
                isFocusableInTouchMode = true
            }
            setOnClickListener {
                clickCallback.invoke(SearchHistoryCallback(null, SEARCH_HISTORY_CLEAR))
            }
        }
    }
}
