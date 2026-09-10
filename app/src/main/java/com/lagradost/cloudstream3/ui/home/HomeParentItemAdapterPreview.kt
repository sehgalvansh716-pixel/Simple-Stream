package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.children
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationBarItemView
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.ui.APIRepository.Companion.noneApi
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.selectHomepage
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.FragmentHomeHeadBinding
import com.lagradost.cloudstream3.databinding.FragmentHomeHeadTvBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.debugException
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.account.AccountHelper.showAccountEditDialog
import com.lagradost.cloudstream3.ui.account.AccountHelper.showAccountSelectLinear
import com.lagradost.cloudstream3.ui.account.AccountViewModel
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.ResultFragment.bindLogo
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.cloudstream3.ui.result.getId
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_SHOW_METADATA
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showOptionSelectStringRes
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbarMargin
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbarView
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.populateChips
import androidx.core.graphics.toColorInt
import com.lagradost.cloudstream3.ui.setRecycledViewPool

class HomeParentItemAdapterPreview(
    private val viewModel: HomeViewModel,
    private val accountViewModel: AccountViewModel
) : ParentItemAdapter(
    id = "HomeParentItemAdapterPreview".hashCode(),
    clickCallback = {
        viewModel.click(it)
    }, moreInfoClickCallback = {
        viewModel.popup(it)
    }, expandCallback = {
        viewModel.expand(it)
    }) {
    override val headers = 1
    private var currentHeaderHolder: HeaderViewHolder? = null
    var onPluginSearchClick: (() -> Unit)? = null
    var onCarouselItemChanged: ((LoadResponse) -> Unit)? = null

    fun updateApiName(apiName: String?) {
        currentHeaderHolder?.updateApiName(apiName)
    }

    override fun onCreateHeader(parent: ViewGroup): ViewHolderState<Bundle> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (isLayout(TV or EMULATOR)) FragmentHomeHeadTvBinding.inflate(
            inflater,
            parent,
            false
        ) else FragmentHomeHeadBinding.inflate(inflater, parent, false)

        if (binding is FragmentHomeHeadBinding) {
            val displayMetrics = parent.context.resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val density = displayMetrics.density
            val bottomReservedPx = (118 * density).toInt()
            val targetHeight = (screenHeight - bottomReservedPx).coerceAtLeast((650 * density).toInt())
            binding.homeHeroContainer.layoutParams.height = targetHeight
        }

        if (binding is FragmentHomeHeadTvBinding && isLayout(EMULATOR)) {
            binding.homeBookmarkParentItemMoreInfo.isVisible = true

            val marginInDp = 50
            val density = binding.horizontalScrollChips.context.resources.displayMetrics.density
            val marginInPixels = (marginInDp * density).toInt()

            val params = binding.horizontalScrollChips.layoutParams as ViewGroup.MarginLayoutParams
            params.marginEnd = marginInPixels
            binding.horizontalScrollChips.layoutParams = params
            binding.homeWatchParentItemTitle.setCompoundDrawablesWithIntrinsicBounds(
                null,
                null,
                ContextCompat.getDrawable(
                    parent.context,
                    R.drawable.ic_baseline_arrow_forward_24
                ),
                null
            )
        }

        val holder = HeaderViewHolder(
            binding,
            viewModel,
            accountViewModel,
            onPluginSearchClick = { onPluginSearchClick?.invoke() },
            onCarouselItemChanged = { item -> onCarouselItemChanged?.invoke(item) }
        )
        currentHeaderHolder = holder
        return holder
    }

    override fun onBindHeader(holder: ViewHolderState<Bundle>) {
        currentHeaderHolder = holder as? HeaderViewHolder
        (holder as? HeaderViewHolder)?.bind()
    }

    override fun onViewDetachedFromWindow(holder: ViewHolderState<Bundle>) {
        when (holder) {
            is HeaderViewHolder -> {
                holder.onViewDetachedFromWindow()
            }
        }
    }

    override fun onViewAttachedToWindow(holder: ViewHolderState<Bundle>) {
        when (holder) {
            is HeaderViewHolder -> {
                holder.onViewAttachedToWindow()
            }
        }
    }

    private class HeaderViewHolder(
        val binding: ViewBinding,
        val viewModel: HomeViewModel,
        accountViewModel: AccountViewModel,
        val onPluginSearchClick: (() -> Unit)? = null,
        val onCarouselItemChanged: ((LoadResponse) -> Unit)? = null,
    ) :
        ViewHolderState<Bundle>(binding) {

        override fun save(): Bundle =
            Bundle().apply {
                putParcelable(
                    "resumeRecyclerView",
                    resumeRecyclerView.layoutManager?.onSaveInstanceState()
                )
                putParcelable(
                    "bookmarkRecyclerView",
                    bookmarkRecyclerView.layoutManager?.onSaveInstanceState()
                )
                //putInt("previewViewpager", previewViewpager.currentItem)
            }

        override fun restore(state: Bundle) {
            state.getSafeParcelable<Parcelable>("resumeRecyclerView")?.let { recycle ->
                resumeRecyclerView.layoutManager?.onRestoreInstanceState(recycle)
            }
            state.getSafeParcelable<Parcelable>("bookmarkRecyclerView")?.let { recycle ->
                bookmarkRecyclerView.layoutManager?.onRestoreInstanceState(recycle)
            }
        }

        val previewAdapter = HomeScrollAdapter { view, position, item ->
            viewModel.click(
                LoadClickCallback(0, view, position, item)
            )
        }

        private val resumeAdapter = ResumeItemAdapter(
            nextFocusUp = itemView.nextFocusUpId,
            nextFocusDown = itemView.nextFocusDownId,
            removeCallback = { v ->
                try {
                    val context = v.context ?: return@ResumeItemAdapter
                    val builder: AlertDialog.Builder =
                        AlertDialog.Builder(context)
                    // Copy pasted from https://github.com/recloudstream/cloudstream/pull/1658/files
                    builder.apply {
                        setTitle(R.string.clear_history)
                        setMessage(
                            context.getString(R.string.delete_message).format(
                                context.getString(
                                    R.string.continue_watching
                                )
                            )
                        )
                        setNegativeButton(R.string.cancel) { _, _ -> /*NO-OP*/ }
                        setPositiveButton(R.string.delete) { _, _ ->
                            DataStoreHelper.deleteAllResumeStateIds()
                            viewModel.reloadStored()
                        }
                        show().setDefaultFocus()
                    }
                } catch (t: Throwable) {
                    // This may throw a formatting error
                    logError(t)
                }
            },
            clickCallback = { callback ->
                if (callback.action != SEARCH_ACTION_SHOW_METADATA) {
                    viewModel.click(callback)
                    return@ResumeItemAdapter
                }
                callback.view.context?.getActivity()?.showOptionSelectStringRes(
                    callback.view,
                    callback.card.posterUrl,
                    listOf(
                        R.string.action_open_watching,
                        R.string.action_remove_watching
                    ),
                    listOf(
                        R.string.action_open_play,
                        R.string.action_open_watching,
                        R.string.action_remove_watching
                    )
                ) { (isTv, actionId) ->
                    when (actionId + if (isTv) 0 else 1) {
                        // play
                        0 -> {
                            viewModel.click(
                                SearchClickCallback(
                                    START_ACTION_RESUME_LATEST,
                                    callback.view,
                                    -1,
                                    callback.card
                                )
                            )
                        }
                        //info
                        1 -> {
                            viewModel.click(
                                SearchClickCallback(
                                    SEARCH_ACTION_LOAD,
                                    callback.view,
                                    -1,
                                    callback.card
                                )
                            )
                        }
                        // remove
                        2 -> {
                            val card = callback.card
                            if (card is DataStoreHelper.ResumeWatchingResult) {
                                DataStoreHelper.removeLastWatched(card.parentId)
                                viewModel.reloadStored()
                            }
                        }
                    }
                }
            })
        private val bookmarkAdapter = HomeChildItemAdapter(
            id = "bookmarkAdapter".hashCode(),
            nextFocusUp = itemView.nextFocusUpId,
            nextFocusDown = itemView.nextFocusDownId
        ) { callback ->
            if (callback.action != SEARCH_ACTION_SHOW_METADATA) {
                viewModel.click(callback)
                return@HomeChildItemAdapter
            }

            (callback.view.context?.getActivity() as? MainActivity)?.loadPopup(
                callback.card,
                load = false
            )
            /*
            callback.view.context?.getActivity()?.showOptionSelectStringRes(
                callback.view,
                callback.card.posterUrl,
                listOf(
                    R.string.action_open_watching,
                    R.string.action_remove_from_bookmarks,
                ),
                listOf(
                    R.string.action_open_play,
                    R.string.action_open_watching,
                    R.string.action_remove_from_bookmarks
                )
            ) { (isTv, actionId) ->
                when (actionId + if (isTv) 0 else 1) { // play
                    0 -> {
                        viewModel.click(
                            SearchClickCallback(
                                START_ACTION_RESUME_LATEST,
                                callback.view,
                                -1,
                                callback.card
                            )
                        )
                    }

                    1 -> { // info
                        viewModel.click(
                            SearchClickCallback(
                                SEARCH_ACTION_LOAD,
                                callback.view,
                                -1,
                                callback.card
                            )
                        )
                    }

                    2 -> { // remove
                        DataStoreHelper.setResultWatchState(
                            callback.card.id,
                            WatchType.NONE.internalId
                        )
                        viewModel.reloadStored()
                    }
                }
            }
            */
        }

        private val previewViewpager: ViewPager2 =
            itemView.findViewById(R.id.home_preview_viewpager)

        private val previewViewpagerText: ViewGroup =
            itemView.findViewById(R.id.home_preview_viewpager_text)

        // private val previewHeader: FrameLayout = itemView.findViewById(R.id.home_preview)
        private val resumeHolder: View = itemView.findViewById(R.id.home_watch_holder)
        private val resumeRecyclerView: RecyclerView =
            itemView.findViewById(R.id.home_watch_child_recyclerview)
        private val bookmarkHolder: View = itemView.findViewById(R.id.home_bookmarked_holder)
        private val bookmarkRecyclerView: RecyclerView =
            itemView.findViewById(R.id.home_bookmarked_child_recyclerview)

        private val headProfilePic: ImageView? = itemView.findViewById(R.id.home_head_profile_pic)
        private val headProfilePicCard: View? =
            itemView.findViewById(R.id.home_head_profile_padding)

        private val alternateHeadProfilePic: ImageView? =
            itemView.findViewById(R.id.alternate_home_head_profile_pic)
        private val alternateHeadProfilePicCard: View? =
            itemView.findViewById(R.id.alternate_home_head_profile_padding)

        private val topPadding: View? = itemView.findViewById(R.id.home_padding)

        private val alternativeAccountPadding: View? =
            itemView.findViewById(R.id.alternative_account_padding)

        private val homeNonePadding: View = itemView.findViewById(R.id.home_none_padding)

        private val autoLoopHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private val autoLoopRunnable = object : Runnable {
            override fun run() {
                val count = previewAdapter.itemCount
                if (count > 1 && previewViewpager.isAttachedToWindow) {
                    val nextPos = (previewViewpager.currentItem + 1) % count
                    previewViewpager.setCurrentItem(nextPos, true)
                }
                autoLoopHandler.postDelayed(this, 10000L)
            }
        }

        fun startAutoLoop() {
            stopAutoLoop()
            autoLoopHandler.postDelayed(autoLoopRunnable, 10000L)
        }

        fun stopAutoLoop() {
            autoLoopHandler.removeCallbacks(autoLoopRunnable)
        }

        fun resetAutoLoop() {
            stopAutoLoop()
            startAutoLoop()
        }

        private fun updateCarouselIndicator(currentPos: Int, totalCount: Int) {
            if (binding is FragmentHomeHeadBinding) {
                binding.homeCarouselIndicator.isVisible = false
                return
            }
            val container = (binding as? FragmentHomeHeadTvBinding)?.homeCarouselIndicator
                ?: return

            val displayCount = totalCount.coerceAtMost(10)
            if (displayCount <= 1) {
                container.removeAllViews()
                container.isVisible = false
                return
            }
            container.isVisible = true

            val context = container.context
            val density = context.resources.displayMetrics.density
            val dotSize = (6 * density).toInt()
            val activeWidth = (20 * density).toInt()
            val margin = (4 * density).toInt()

            val activeIndex = currentPos % displayCount
            if (container.childCount != displayCount) {
                container.removeAllViews()
                for (i in 0 until displayCount) {
                    val view = View(context)
                    val params = android.widget.LinearLayout.LayoutParams(
                        if (i == activeIndex) activeWidth else dotSize,
                        dotSize
                    )
                    params.setMargins(margin, 0, margin, 0)
                    view.layoutParams = params
                    view.setBackgroundResource(
                        if (i == activeIndex) R.drawable.bg_carousel_dot_active
                        else R.drawable.bg_carousel_dot_inactive
                    )
                    container.addView(view)
                }
            } else {
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i)
                    val isActive = i == activeIndex
                    val targetWidth = if (isActive) activeWidth else dotSize
                    if (child.layoutParams.width != targetWidth) {
                        child.layoutParams.width = targetWidth
                        child.requestLayout()
                    }
                    child.setBackgroundResource(
                        if (isActive) R.drawable.bg_carousel_dot_active
                        else R.drawable.bg_carousel_dot_inactive
                    )
                }
            }
        }

        fun onSelect(item: LoadResponse, position: Int) {
            onCarouselItemChanged?.invoke(item)
            (binding as? FragmentHomeHeadTvBinding)?.apply {
                homePreviewDescription.isGone = item.plot.isNullOrBlank()
                homePreviewDescription.text = item.plot?.html() ?: ""

                val scoreText = item.score?.toStringNull(0.1, 10, 1, false)
                if (scoreText != null) {
                    val cleanScore = scoreText.replace("★", "").trim()
                    homePreviewScore.text = "$cleanScore/10"
                    homePreviewScore.isVisible = true
                } else {
                    homePreviewScore.isVisible = false
                }

                item.year?.let { year ->
                    homePreviewYear.text = year.toString()
                }
                homePreviewYear.isVisible = item.year != null

                val itemTags = item.tags
                val genreText = if (!itemTags.isNullOrEmpty()) {
                    itemTags.take(3).joinToString(", ")
                } else item.duration?.let { min ->
                    homePreviewDuration.context.getString(R.string.duration_format, min)
                }
                homePreviewDuration.text = genreText
                homePreviewDuration.isVisible = !genreText.isNullOrBlank()

                homeMetaDot1.isVisible = homePreviewScore.isVisible && (homePreviewYear.isVisible || homePreviewDuration.isVisible)
                homeMetaDot2.isVisible = homePreviewYear.isVisible && homePreviewDuration.isVisible

                val castText = item.actors?.take(3)?.joinToString(", ") { it.actor.name }
                if (!castText.isNullOrBlank()) {
                    homePreviewCast.text =
                        homePreviewCast.context.getString(R.string.cast_format, castText)
                    homePreviewCast.isVisible = true
                } else {
                    homePreviewCast.isVisible = false
                }

                homePreviewText.text = item.name.html()

                bindLogo(
                    url = item.logoUrl,
                    headers = item.posterHeaders,
                    titleView = homePreviewText,
                    logoView = homeBackgroundPosterWatermarkBadgeHolder
                )

                homePreviewTags.isGone = true

                // Primary Action: Solid white pill [ ▶ Play ]
                homePreviewPlay.setOnClickListener { view ->
                    viewModel.click(
                        LoadClickCallback(
                            START_ACTION_RESUME_LATEST,
                            view,
                            position,
                            item
                        )
                    )
                }

                // Secondary Action: Frosted glass split pill [ + ] Watchlist toggle / dialog
                val id = item.getId()
                val watchState = DataStoreHelper.getResultWatchState(id)
                homePreviewBookmark.setImageResource(
                    if (watchState != WatchType.NONE) R.drawable.ic_baseline_check_24
                    else R.drawable.ic_baseline_add_24
                )

                homePreviewBookmark.setOnClickListener { fab ->
                    fab.context.getActivity()?.showBottomDialog(
                        WatchType.entries
                            .map { fab.context.getString(it.stringRes) }
                            .toList(),
                        DataStoreHelper.getResultWatchState(id).ordinal,
                        fab.context.getString(R.string.action_add_to_bookmarks),
                        showApply = false,
                        {}) { selectedIndex ->
                        val newValue = WatchType.entries[selectedIndex]
                        ResultViewModel2().updateWatchStatus(
                            newValue,
                            fab.context,
                            item
                        ) { statusChanged: Boolean ->
                            if (!statusChanged) return@updateWatchStatus
                            homePreviewBookmark.setImageResource(
                                if (newValue != WatchType.NONE) R.drawable.ic_baseline_check_24
                                else R.drawable.ic_baseline_add_24
                            )
                        }
                    }
                }

                // Secondary Action: Frosted glass split pill [ ⓘ ] Info / Details
                homePreviewInfo.setOnClickListener { view ->
                    viewModel.click(
                        LoadClickCallback(0, view, position, item)
                    )
                }

                homePreviewInfoBtt.setOnClickListener { view ->
                    viewModel.click(
                        LoadClickCallback(0, view, position, item)
                    )
                }

                updateCarouselIndicator(position, previewAdapter.itemCount)
            }
            (binding as? FragmentHomeHeadBinding)?.apply {
                homePreviewDescription.isGone = item.plot.isNullOrBlank()
                homePreviewDescription.text = item.plot?.html() ?: ""

                val scoreText = item.score?.toStringNull(0.1, 10, 1, false)
                if (scoreText != null) {
                    val cleanScore = scoreText.replace("★", "").trim()
                    homePreviewScore.text = "$cleanScore/10"
                    homePreviewScore.isVisible = true
                } else {
                    homePreviewScore.isVisible = false
                }

                item.year?.let { year ->
                    homePreviewYear.text = year.toString()
                }
                homePreviewYear.isVisible = item.year != null

                val itemTags = item.tags
                val genreText = if (!itemTags.isNullOrEmpty()) {
                    itemTags.take(3).joinToString(", ")
                } else item.duration?.let { min ->
                    homePreviewDuration.context.getString(R.string.duration_format, min)
                }
                homePreviewDuration.text = genreText
                homePreviewDuration.isVisible = !genreText.isNullOrBlank()

                homeMetaDot1.isVisible = homePreviewScore.isVisible && (homePreviewYear.isVisible || homePreviewDuration.isVisible)
                homeMetaDot2.isVisible = homePreviewYear.isVisible && homePreviewDuration.isVisible

                homePreviewText.text = item.name.html()

                bindLogo(
                    url = item.logoUrl,
                    headers = item.posterHeaders,
                    titleView = homePreviewText,
                    logoView = homeBackgroundPosterWatermarkBadgeHolder
                )

                try {
                    val decorView = itemView.rootView as? ViewGroup ?: itemView as ViewGroup
                    val blurAlgorithm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        eightbitlab.com.blurview.RenderEffectBlur()
                    } else {
                        eightbitlab.com.blurview.RenderScriptBlur(itemView.context)
                    }
                    homePreviewActionPillBlur.setupWith(decorView, blurAlgorithm)
                        .setBlurRadius(16f)
                        .setOverlayColor(Color.TRANSPARENT)
                    homePreviewActionPillBlur.outlineProvider = ViewOutlineProvider.BACKGROUND
                    homePreviewActionPillBlur.clipToOutline = true
                } catch (e: Throwable) {
                    logError(e)
                }

                homePreviewPlay.setOnClickListener { view ->
                    viewModel.click(
                        LoadClickCallback(
                            START_ACTION_RESUME_LATEST,
                            view,
                            position,
                            item
                        )
                    )
                }

                homePreviewInfo.setOnClickListener { view ->
                    viewModel.click(
                        LoadClickCallback(0, view, position, item)
                    )
                }

                val id = item.getId()
                val watchState = DataStoreHelper.getResultWatchState(id)
                homePreviewBookmark.setImageResource(
                    if (watchState != WatchType.NONE) R.drawable.ic_baseline_check_24
                    else R.drawable.ic_baseline_add_24
                )

                homePreviewBookmark.setOnClickListener { fab ->
                    fab.context.getActivity()?.showBottomDialog(
                        WatchType.entries
                            .map { fab.context.getString(it.stringRes) }
                            .toList(),
                        DataStoreHelper.getResultWatchState(id).ordinal,
                        fab.context.getString(R.string.action_add_to_bookmarks),
                        showApply = false,
                        {}) { selectedIndex ->
                        val newValue = WatchType.entries[selectedIndex]
                        ResultViewModel2().updateWatchStatus(
                            newValue,
                            fab.context,
                            item
                        ) { statusChanged: Boolean ->
                            if (!statusChanged) return@updateWatchStatus
                            homePreviewBookmark.setImageResource(
                                if (newValue != WatchType.NONE) R.drawable.ic_baseline_check_24
                                else R.drawable.ic_baseline_add_24
                            )
                        }
                    }
                }

                updateCarouselIndicator(position, previewAdapter.itemCount)
            }
        }

        private val previewCallback: ViewPager2.OnPageChangeCallback =
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    previewAdapter.apply {
                        if (position >= itemCount - 1 && hasMoreItems) {
                            hasMoreItems = false // don't make two requests
                            viewModel.loadMoreHomeScrollResponses()
                        }
                    }
                    val item = previewAdapter.getItemOrNull(position) ?: return
                    onSelect(item, position)
                    resetAutoLoop()
                }

                override fun onPageScrollStateChanged(state: Int) {
                    if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                        resetAutoLoop()
                    }
                }
            }

        fun onViewDetachedFromWindow() {
            previewViewpager.unregisterOnPageChangeCallback(previewCallback)
            stopAutoLoop()
        }

        private val toggleList = listOf<Pair<Chip, WatchType>>(
            Pair(itemView.findViewById(R.id.home_type_watching_btt), WatchType.WATCHING),
            Pair(itemView.findViewById(R.id.home_type_completed_btt), WatchType.COMPLETED),
            Pair(itemView.findViewById(R.id.home_type_dropped_btt), WatchType.DROPPED),
            Pair(itemView.findViewById(R.id.home_type_on_hold_btt), WatchType.ONHOLD),
            Pair(itemView.findViewById(R.id.home_plan_to_watch_btt), WatchType.PLANTOWATCH),
        )

        private val toggleListHolder: ChipGroup? = itemView.findViewById(R.id.home_type_holder)

        fun updateApiName(apiName: String?) {
            val display = itemView.context?.let { ctx ->
                HomeFragment.Companion.run { ctx.getDisplayName(apiName) }
            } ?: apiName ?: "Provider"
            (binding as? FragmentHomeHeadTvBinding)?.homeChangeApi?.text = "$display ▾"
            (binding as? FragmentHomeHeadBinding)?.homeChangeApi?.text = "$display ▾"
        }

        fun bind() {
            updateApiName(viewModel.apiName.value)
        }

        init {
            if (binding is FragmentHomeHeadBinding) {
                val displayMetrics = itemView.context.resources.displayMetrics
                val screenHeight = displayMetrics.heightPixels
                val density = displayMetrics.density
                val bottomReservedPx = (118 * density).toInt()
                val targetHeight = (screenHeight - bottomReservedPx).coerceAtLeast((650 * density).toInt())
                if (binding.homeHeroContainer.layoutParams.height != targetHeight) {
                    binding.homeHeroContainer.layoutParams.height = targetHeight
                    binding.homeHeroContainer.requestLayout()
                }
                binding.homeCarouselIndicator.isVisible = false
            }

            previewViewpager.setPageTransformer(HomeScrollTransformer())
            previewViewpager.offscreenPageLimit = 2

            previewViewpager.adapter = previewAdapter
            resumeRecyclerView.adapter = resumeAdapter
            bookmarkRecyclerView.setRecycledViewPool(HomeChildItemAdapter.sharedPool)
            bookmarkRecyclerView.adapter = bookmarkAdapter

            resumeRecyclerView.setLinearListLayout(
                nextLeft = R.id.nav_rail_view,
                nextRight = FOCUS_SELF
            )

            bookmarkRecyclerView.setLinearListLayout(
                nextLeft = R.id.nav_rail_view,
                nextRight = FOCUS_SELF
            )

            fixPaddingStatusbarMargin(topPadding)

            for ((chip, watch) in toggleList) {
                chip.isChecked = false
                chip.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        viewModel.loadStoredData(setOf(watch))
                    }
                    // Else if all are unchecked -> Do not load data
                    else if (toggleList.all { !it.first.isChecked }) {
                        viewModel.loadStoredData(emptySet())
                    }
                }
            }

            headProfilePicCard?.isGone = isLayout(TV or EMULATOR)
            alternateHeadProfilePicCard?.isGone = isLayout(TV or EMULATOR)

            (headProfilePic ?: alternateHeadProfilePic)?.observe(viewModel.currentAccount) { currentAccount ->
                headProfilePic?.loadImage(currentAccount?.image)
                alternateHeadProfilePic?.loadImage(currentAccount?.image)
            }

            headProfilePicCard?.setOnClickListener {
                activity?.showAccountSelectLinear()
            }

            fun showAccountEditBox(context: Context): Boolean {
                val currentAccount = DataStoreHelper.getCurrentAccount()
                return if (currentAccount != null) {
                    showAccountEditDialog(
                        context = context,
                        account = currentAccount,
                        isNewAccount = false,
                        accountEditCallback = { accountViewModel.handleAccountUpdate(it, context) },
                        accountDeleteCallback = {
                            accountViewModel.handleAccountDelete(
                                it,
                                context
                            )
                        }
                    )
                    true
                } else false
            }

            alternateHeadProfilePicCard?.setOnLongClickListener {
                showAccountEditBox(it.context)
            }
            headProfilePicCard?.setOnLongClickListener {
                showAccountEditBox(it.context)
            }

            alternateHeadProfilePicCard?.setOnClickListener {
                activity?.showAccountSelectLinear()
            }

            (binding as? FragmentHomeHeadTvBinding)?.apply {
                updateApiName(viewModel.apiName.value)

                homeChangeApi.setOnClickListener { view ->
                    view.context.selectHomepage(viewModel.apiName.value) { api ->
                        updateApiName(api)
                        viewModel.loadAndCancel(api, forceReload = true, fromUI = true)
                    }
                }

                fun scrollToHeaderTop() {
                    val master = itemView.parent as? RecyclerView
                        ?: itemView.rootView?.findViewById(R.id.home_master_recycler)
                    (master?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
                        ?.scrollToPositionWithOffset(0, 0)
                }

                homeChangeApi.setOnFocusChangeListener { v, hasFocus ->
                    val scale = if (hasFocus) 1.06f else 1.0f
                    v.animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) 8f else 0f).setDuration(150).start()
                    if (hasFocus) scrollToHeaderTop()
                }

                homePreviewPlay.setOnFocusChangeListener { v, hasFocus ->
                    val scale = if (hasFocus) 1.08f else 1.0f
                    v.animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) 8f else 0f).setDuration(150).start()
                    if (hasFocus) scrollToHeaderTop()
                }

                homePreviewBookmark.setOnFocusChangeListener { v, hasFocus ->
                    val scale = if (hasFocus) 1.08f else 1.0f
                    v.animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) 8f else 0f).setDuration(150).start()
                    if (hasFocus) scrollToHeaderTop()
                }

                homePreviewInfo.setOnFocusChangeListener { v, hasFocus ->
                    val scale = if (hasFocus) 1.08f else 1.0f
                    v.animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) 8f else 0f).setDuration(150).start()
                    if (hasFocus) scrollToHeaderTop()
                }

                homeChangeApi.setOnKeyListener { _, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            homePreviewPlay.requestFocus()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            homePluginSearchBtn.requestFocus()
                            true
                        }
                        else -> false
                    }
                }

                homePluginSearchBtn.setOnFocusChangeListener { v, hasFocus ->
                    val scale = if (hasFocus) 1.08f else 1.0f
                    v.animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) 8f else 0f).setDuration(150).start()
                    if (hasFocus) scrollToHeaderTop()
                }

                homePluginSearchBtn.setOnClickListener {
                    onPluginSearchClick?.invoke()
                }

                homePluginSearchBtn.setOnKeyListener { _, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            homeChangeApi.requestFocus()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            homePreviewPlay.requestFocus()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val nav = activity?.findViewById<View>(R.id.tv_nav_home)
                            if (nav != null) {
                                nav.requestFocus()
                                true
                            } else false
                        }
                        else -> false
                    }
                }

                fun handleDpadDown(): Boolean {
                    if (resumeHolder.isVisible && (resumeRecyclerView.adapter?.itemCount ?: 0) > 0) {
                        return resumeRecyclerView.requestFocus()
                    }
                    if (bookmarkHolder.isVisible && (bookmarkRecyclerView.adapter?.itemCount ?: 0) > 0) {
                        return (toggleListHolder?.children?.firstOrNull { it.isVisible && it.isFocusable }
                            ?: bookmarkRecyclerView).requestFocus()
                    }
                    val master = itemView.parent as? RecyclerView
                        ?: itemView.rootView?.findViewById(R.id.home_master_recycler)
                    if (master != null) {
                        val firstRowHolder = master.findViewHolderForAdapterPosition(1)
                        val childRecycler = firstRowHolder?.itemView?.findViewById<RecyclerView>(R.id.home_child_recyclerview)
                        val firstCard = childRecycler?.getChildAt(0)
                        if (firstCard != null && firstCard.isFocusable && firstCard.requestFocus()) {
                            return true
                        }

                        // Row 1 is not yet in viewport or laid out; scroll master down to row 1
                        master.smoothScrollToPosition(1)

                        fun tryFocusChild(): Boolean {
                            val vh = master.findViewHolderForAdapterPosition(1)
                            val cr = vh?.itemView?.findViewById<RecyclerView>(R.id.home_child_recyclerview)
                            val card = cr?.getChildAt(0)
                            return if (card != null && card.isFocusable) {
                                card.requestFocus()
                            } else if (cr != null && cr.isFocusable) {
                                cr.requestFocus()
                            } else false
                        }

                        master.post {
                            if (!tryFocusChild()) {
                                master.postDelayed({
                                    if (!tryFocusChild()) {
                                        master.postDelayed({
                                            tryFocusChild()
                                        }, 100)
                                    }
                                }, 60)
                            }
                        }
                        return true
                    }
                    return false
                }

                fun handleDpadUp(): Boolean {
                    val master = itemView.parent as? RecyclerView
                        ?: itemView.rootView?.findViewById(R.id.home_master_recycler)
                    (master?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
                        ?.scrollToPositionWithOffset(0, 0)
                    homeChangeApi.post {
                        homeChangeApi.requestFocus()
                    }
                    return true
                }

                homePreviewPlay.setOnKeyListener { _, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            val totalItems = previewAdapter.itemCount
                            if (totalItems > 1) {
                                val prevPos = if (previewViewpager.currentItem > 0) previewViewpager.currentItem - 1 else totalItems - 1
                                previewViewpager.setCurrentItem(prevPos, true)
                                resetAutoLoop()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> handleDpadUp()
                        KeyEvent.KEYCODE_DPAD_DOWN -> handleDpadDown()
                        else -> false
                    }
                }

                homePreviewBookmark.setOnKeyListener { _, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> handleDpadUp()
                        KeyEvent.KEYCODE_DPAD_DOWN -> handleDpadDown()
                        else -> false
                    }
                }

                homePreviewInfo.setOnKeyListener { _, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val totalItems = previewAdapter.itemCount
                            if (totalItems > 1) {
                                val nextPos = (previewViewpager.currentItem + 1) % totalItems
                                previewViewpager.setCurrentItem(nextPos, true)
                                resetAutoLoop()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> handleDpadUp()
                        KeyEvent.KEYCODE_DPAD_DOWN -> handleDpadDown()
                        else -> false
                    }
                }

                homePreviewInfoBtt.setOnKeyListener { _, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> handleDpadUp()
                        KeyEvent.KEYCODE_DPAD_DOWN -> handleDpadDown()
                        else -> false
                    }
                }
            }

            (binding as? FragmentHomeHeadBinding)?.apply {
                updateApiName(viewModel.apiName.value)

                homeChangeApi.setOnClickListener { view ->
                    view.context.selectHomepage(viewModel.apiName.value) { api ->
                        updateApiName(api)
                        viewModel.loadAndCancel(api, forceReload = true, fromUI = true)
                    }
                }

                homePluginSearchBtn.setOnClickListener {
                    onPluginSearchClick?.invoke()
                }

                homeSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        viewModel.queryTextSubmit(query)
                        return true
                    }

                    override fun onQueryTextChange(newText: String): Boolean {
                        viewModel.queryTextChange(newText)
                        return true
                    }
                })
            }
        }

        private fun updatePreview(preview: Resource<Pair<Boolean, List<LoadResponse>>>) {
            homeNonePadding.apply {
                val params = layoutParams
                params.height = 0
                layoutParams = params
            }

            when (preview) {
                is Resource.Success -> {
                    preview.value.second.forEach { loadResp ->
                        com.lagradost.cloudstream3.utils.CardMetadataManager.cacheFromLoadResponse(loadResp)
                    }
                    previewAdapter.submitList(preview.value.second)
                    previewAdapter.hasMoreItems = preview.value.first
                    /*if (!.setItems(
                            preview.value.second,
                            preview.value.first
                        )
                    ) {
                        // this might seam weird and useless, however this prevents a very weird andrid bug were the viewpager is not rendered properly
                        // I have no idea why that happens, but this is my ducktape solution
                        previewViewpager.setCurrentItem(0, false)
                        previewViewpager.beginFakeDrag()
                        previewViewpager.fakeDragBy(1f)
                        previewViewpager.endFakeDrag()
                        previewCallback.onPageSelected(0)
                        //previewHeader.isVisible = true
                    }*/

                    previewViewpager.isVisible = true
                    previewViewpagerText.isVisible = true
                    alternativeAccountPadding?.isVisible = false
                    (binding as? FragmentHomeHeadTvBinding)?.apply {
                        homePreviewInfoBtt.isVisible = true
                    }
                    // Explicitly bind the current item to ensure instant loading
                    val currentPos = previewViewpager.currentItem
                    val item = preview.value.second.getOrNull(currentPos)
                    if (item != null) {
                        onSelect(item, currentPos)
                    }
                    updateCarouselIndicator(currentPos, preview.value.second.size)
                    resetAutoLoop()
                }

                else -> {
                    previewAdapter.submitList(listOf())
                    previewViewpager.setCurrentItem(0, false)
                    previewViewpager.isVisible = false
                    previewViewpagerText.isVisible = false
                    alternativeAccountPadding?.isVisible = false
                    (binding as? FragmentHomeHeadTvBinding)?.apply {
                        homePreviewInfoBtt.isVisible = false
                    }
                    stopAutoLoop()
                    //previewHeader.isVisible = false
                }
            }
        }

        private fun updatePreviewNextFocusDown() {
            (binding as? FragmentHomeHeadTvBinding)?.apply {
                val targetId = when {
                    resumeHolder.isVisible -> R.id.home_watch_child_recyclerview
                    bookmarkHolder.isVisible -> R.id.home_bookmarked_child_recyclerview
                    else -> View.NO_ID
                }
                homePreviewPlay.nextFocusDownId = targetId
                homePreviewBookmark.nextFocusDownId = targetId
                homePreviewInfo.nextFocusDownId = targetId
                homePreviewInfoBtt.nextFocusDownId = targetId
            }
        }

        private fun updateResume(resumeWatching: List<SearchResponse>) {
            resumeHolder.isVisible = resumeWatching.isNotEmpty()
            resumeAdapter.submitList(resumeWatching)
            updatePreviewNextFocusDown()

            if (
                binding is FragmentHomeHeadBinding ||
                binding is FragmentHomeHeadTvBinding &&
                isLayout(EMULATOR)
            ) {
                val title = (binding as? FragmentHomeHeadBinding)?.homeWatchParentItemTitle
                    ?: (binding as? FragmentHomeHeadTvBinding)?.homeWatchParentItemTitle

                title?.setOnClickListener {
                    viewModel.popup(
                        HomeViewModel.ExpandableHomepageList(
                            HomePageList(
                                title.text.toString(),
                                resumeWatching,
                                false
                            ), 1, false
                        ),
                        deleteCallback = {
                            viewModel.deleteResumeWatching()
                        }
                    )
                }
            }
        }

        private fun updateBookmarks(data: Pair<Boolean, List<SearchResponse>>) {
            val (visible, list) = data
            bookmarkHolder.isVisible = visible
            bookmarkAdapter.submitList(list)
            updatePreviewNextFocusDown()

            if (
                binding is FragmentHomeHeadBinding ||
                binding is FragmentHomeHeadTvBinding &&
                isLayout(EMULATOR)
            ) {
                val title = (binding as? FragmentHomeHeadBinding)?.homeBookmarkParentItemTitle
                    ?: (binding as? FragmentHomeHeadTvBinding)?.homeBookmarkParentItemTitle

                title?.setOnClickListener {
                    val items = toggleList.map { it.first }.filter { it.isChecked }
                    if (items.isEmpty()) return@setOnClickListener // we don't want to show an empty dialog
                    val textSum = items
                        .mapNotNull { it.text }.joinToString()

                    viewModel.popup(
                        HomeViewModel.ExpandableHomepageList(
                            HomePageList(
                                textSum,
                                list,
                                false
                            ), 1, false
                        ), deleteCallback = {
                            viewModel.deleteBookmarks(list)
                        }
                    )
                }
            }
        }

        fun onViewAttachedToWindow() {
            previewViewpager.registerOnPageChangeCallback(previewCallback)
            startAutoLoop()

            previewViewpager.apply {
                observe(viewModel.preview) {
                    updatePreview(it)
                }
                observe(viewModel.resumeWatching) {
                    updateResume(it)
                }
                observe(viewModel.bookmarks) {
                    updateBookmarks(it)
                }
                observe(viewModel.availableWatchStatusTypes) { (checked, visible) ->
                    for ((chip, watch) in toggleList) {
                        chip.apply {
                            isVisible = visible.contains(watch)
                            isChecked = checked.contains(watch)
                        }
                    }
                    toggleListHolder?.isGone = visible.isEmpty()
                }
            }
        }
    }
}
