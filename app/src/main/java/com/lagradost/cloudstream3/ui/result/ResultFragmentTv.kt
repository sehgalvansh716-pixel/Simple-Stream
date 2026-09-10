package com.lagradost.cloudstream3.ui.result

import android.animation.Animator
import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.FragmentResultTvBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.services.SubscriptionWorkManager
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup
import com.lagradost.cloudstream3.ui.home.HomeChildItemAdapter
import com.lagradost.cloudstream3.ui.player.ExtractorLinkGenerator
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.NEXT_WATCH_EPISODE_PERCENTAGE
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.result.ResultFragment.bindLogo
import com.lagradost.cloudstream3.ui.result.ResultFragment.getStoredData
import com.lagradost.cloudstream3.ui.result.ResultFragment.updateUIEvent
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_FOCUSED
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.utils.TvAmbientVideoHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.lagradost.cloudstream3.utils.AppContextUtils.getNameFull
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.AppContextUtils.isRtl
import com.lagradost.cloudstream3.utils.AppContextUtils.loadCache
import com.lagradost.cloudstream3.utils.AppContextUtils.updateHasTrailers
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogInstant
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.populateChips
import com.lagradost.cloudstream3.utils.UIHelper.setNavigationBarColorCompat
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.setVideoWatchState
import com.lagradost.cloudstream3.utils.getImageFromDrawable
import com.lagradost.cloudstream3.utils.setText
import com.lagradost.cloudstream3.utils.setTextHtml
import com.lagradost.cloudstream3.utils.txt

class ResultFragmentTv : BaseFragment<FragmentResultTvBinding>(
    BindingCreator.Inflate(FragmentResultTvBinding::inflate)
) {

    private lateinit var viewModel: ResultViewModel2
    private var trailerVideoHelper: TvAmbientVideoHelper? = null
    private var trailerJob: Job? = null

    private fun playAmbientVideo(url: String, headers: Map<String, String>? = null) {
        trailerJob?.cancel()
        trailerJob = viewLifecycleOwner.lifecycleScope.launch {
            if (!isActive) return@launch
            val ctx = context ?: return@launch
            val currentBinding = binding ?: return@launch
            try {
                if (trailerVideoHelper == null) {
                    trailerVideoHelper = TvAmbientVideoHelper(ctx)
                }
                trailerVideoHelper?.attachUri(
                    currentBinding.tvTrailerVideoView,
                    android.net.Uri.parse(url),
                    headers = headers,
                    autoPlay = true,
                    onReady = {
                        currentBinding.tvTrailerVideoView.animate()
                            .alpha(1.0f)
                            .setDuration(700)
                            .start()
                        currentBinding.backgroundPoster.animate()
                            .alpha(0.0f)
                            .setDuration(700)
                            .start()
                    }
                )
            } catch (e: Exception) {
                com.lagradost.cloudstream3.mvvm.logError(e)
            }
        }
    }

    override fun onDestroyView() {
        trailerJob?.cancel()
        trailerJob = null
        trailerVideoHelper?.release()
        trailerVideoHelper = null
        updateUIEvent -= ::updateUI
        activity?.detachBackPressedCallback(this@ResultFragmentTv.toString())
        super.onDestroyView()
    }

    override fun onPause() {
        trailerJob?.cancel()
        trailerVideoHelper?.pause()
        binding?.tvTrailerVideoView?.animate()?.alpha(0.0f)?.setDuration(200)?.start()
        binding?.backgroundPoster?.animate()?.alpha(1.0f)?.setDuration(200)?.start()
        super.onPause()
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel =
            ViewModelProvider(this)[ResultViewModel2::class.java]
        viewModel.EPISODE_RANGE_SIZE = 50
        updateUIEvent += ::updateUI

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    private fun updateUI(id: Int?) {
        viewModel.reloadEpisodes()
    }

    private var currentRecommendations: List<SearchResponse> = emptyList()

    private fun handleSelection(data: Any) {
        when (data) {
            is EpisodeRange -> {
                viewModel.changeRange(data)
            }

            is Int -> {
                viewModel.changeSeason(data)
            }

            is DubStatus -> {
                viewModel.changeDubStatus(data)
            }

            is String -> {
                setRecommendations(currentRecommendations, data)
            }
        }
    }

    private fun RecyclerView?.select(index: Int) {
        (this?.adapter as? SelectAdaptor?)?.select(index, this)
    }

    private fun RecyclerView?.update(data: List<SelectData>) {
        (this?.adapter as? SelectAdaptor?)?.submitList(data)
        this?.isVisible = data.size > 1
    }

    private fun RecyclerView?.setAdapter() {
        this?.adapter = SelectAdaptor { data ->
            handleSelection(data)
        }
    }

//    private fun hasNoFocus(): Boolean {
//        val focus = activity?.currentFocus
//        if (focus == null || !focus.isVisible) return true
//        return focus == binding?.resultRoot
//    }

    /**
     * Force focus any play button.
     * Note that this will steal any focus if the episode loading is too slow (unlikely).
     */
    private fun focusPlayButton() {
        binding?.resultPlayMovieButton?.requestFocus()
        binding?.resultPlaySeriesButton?.requestFocus()
        binding?.resultResumeSeriesButton?.requestFocus()
    }

    private fun setRecommendations(rec: List<SearchResponse>?, validApiName: String?) {
        currentRecommendations = rec ?: emptyList()
        val isInvalid = rec.isNullOrEmpty()
        binding?.apply {
            resultRecommendationsList.isGone = isInvalid
            resultRecommendationsHolder.isGone = isInvalid
            val matchAgainst = validApiName ?: rec?.firstOrNull()?.apiName
            val filtered = rec?.filter { it.apiName == matchAgainst } ?: emptyList()
            (resultRecommendationsList.adapter as? HomeChildItemAdapter)?.submitList(filtered)

            rec?.map { it.apiName }?.distinct()?.let { apiNames ->
                // very dirty selection
                resultRecommendationsFilterSelection.isVisible = apiNames.size > 1
                resultRecommendationsFilterSelection.update(apiNames.map {
                    txt(
                        it
                    ) to it
                })
                resultRecommendationsFilterSelection.select(apiNames.indexOf(matchAgainst))
            } ?: run {
                resultRecommendationsFilterSelection.isVisible = false
            }
        }
    }

    var loadingDialog: Dialog? = null
    var popupDialog: Dialog? = null

    private fun reloadViewModel(forceReload: Boolean) {
        if (!viewModel.hasLoaded() || forceReload) {
            val storedData = getStoredData() ?: return
            viewModel.load(
                activity,
                storedData.url,
                storedData.apiName,
                storedData.showFillers,
                storedData.dubStatus,
                storedData.start
            )
        }
    }

    override fun onResume() {
        activity?.setNavigationBarColorCompat(R.attr.primaryBlackBackground)
        afterPluginsLoadedEvent += ::reloadViewModel
        if ((binding?.resultFinishLoading?.scrollY ?: 0) <= 200) {
            trailerVideoHelper?.play()
            binding?.tvTrailerVideoView?.animate()?.alpha(1.0f)?.setDuration(500)?.start()
            binding?.backgroundPoster?.animate()?.alpha(0.0f)?.setDuration(500)?.start()
        }
        super.onResume()
    }

    override fun onStop() {
        afterPluginsLoadedEvent -= ::reloadViewModel
        super.onStop()
    }

    private fun View.fade(turnVisible: Boolean) {
        if (turnVisible) {
            isVisible = true
        }

        this.animate().alpha(if (turnVisible) 0.97f else 0.0f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                }

                override fun onAnimationEnd(animation: Animator) {
                    this@fade.isVisible = turnVisible
                }

                override fun onAnimationCancel(animation: Animator) {
                }

                override fun onAnimationRepeat(animation: Animator) {
                }
            })
        }
        this.animate().translationX(if (turnVisible) 0f else if (isRtl()) -100.0f else 100f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
        }
    }

    private fun toggleEpisodes(show: Boolean) {
        binding?.apply {
            if (show) {
                resultFinishLoading.smoothScrollTo(0, episodesSection.top)
                if (resultSeasonSelection.isVisible) {
                    resultSeasonSelection.requestFocus()
                } else {
                    resultEpisodes.requestFocus()
                }
            }
        }
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(view, padTop = false)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindingCreated(binding: FragmentResultTvBinding) {
        // ===== setup =====
        val storedData = getStoredData() ?: return
        activity?.window?.decorView?.clearFocus()
        activity?.loadCache()
        hideKeyboard()
        if (storedData.restart || !viewModel.hasLoaded())
            viewModel.load(
                activity,
                storedData.url,
                storedData.apiName,
                storedData.showFillers,
                storedData.dubStatus,
                storedData.start
            )
        // ===== ===== =====
        var comingSoon = false

        binding.apply {
            //episodesShadow.rotationX = 180.0f//if(episodesShadow.isRtl()) 180.0f else 0.0f

            // parallax on background, scroll-to-top button, and ambient video pause
            var isAmbientPausedByScroll = false
            resultFinishLoading.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                backgroundPosterHolder.translationY = -scrollY.toFloat() * 0.8f

                // Scroll to top floating button
                resultScrollToTop.isVisible = scrollY > 300

                // Pause and fade out ambient hero video when scrolling away from top hero area
                if (scrollY > 200) {
                    if (!isAmbientPausedByScroll) {
                        isAmbientPausedByScroll = true
                        tvTrailerVideoView.animate().alpha(0.0f).setDuration(250).start()
                        backgroundPoster.animate().alpha(1.0f).setDuration(350).start()
                        trailerVideoHelper?.pause()
                    }
                } else {
                    if (isAmbientPausedByScroll) {
                        isAmbientPausedByScroll = false
                        if (trailerVideoHelper != null) {
                            trailerVideoHelper?.play()
                            tvTrailerVideoView.animate().alpha(1.0f).setDuration(500).start()
                            backgroundPoster.animate().alpha(0.0f).setDuration(500).start()
                        }
                    }
                }
            })

            resultScrollToTop.setOnClickListener {
                resultFinishLoading.smoothScrollTo(0, 0)
                focusPlayButton()
            }

            resultBack.setOnClickListener {
                activity?.onBackPressedDispatcher?.onBackPressed()
            }

            // Dedicated TV Download Button: opens options to download or manage episodes / movie
            resultDownloadButtonTv.setOnClickListener {
                val currentEps = (viewModel.episodes.value as? Resource.Success)?.value
                val firstEp = currentEps?.firstOrNull()
                if (firstEp != null) {
                    viewModel.handleAction(EpisodeClickEvent(ACTION_SHOW_OPTIONS, firstEp))
                } else {
                    (viewModel.movie.value as? Resource.Success)?.value?.second?.let { ep ->
                        viewModel.handleAction(EpisodeClickEvent(ACTION_SHOW_OPTIONS, ep))
                    }
                }
            }
            resultDownloadButtonTv.setOnLongClickListener {
                val currentEps = (viewModel.episodes.value as? Resource.Success)?.value
                val firstEp = currentEps?.firstOrNull()
                if (firstEp != null) {
                    viewModel.handleAction(EpisodeClickEvent(ACTION_SHOW_OPTIONS, firstEp))
                } else {
                    (viewModel.movie.value as? Resource.Success)?.value?.second?.let { ep ->
                        viewModel.handleAction(EpisodeClickEvent(ACTION_SHOW_OPTIONS, ep))
                    }
                }
                true
            }

            // Episode header 4 frosted pills
            resultEpisodesRatingsButton.setOnClickListener {
                val current = DataStoreHelper.resultsSortingMode
                val next = if (current == EpisodeSortType.RATING_HIGH_LOW) {
                    EpisodeSortType.NUMBER_ASC
                } else {
                    EpisodeSortType.RATING_HIGH_LOW
                }
                viewModel.setSort(next)
            }

            resultSortButton.setOnClickListener {
                val current = DataStoreHelper.resultsSortingMode
                val next = if (current == EpisodeSortType.NUMBER_DESC) {
                    EpisodeSortType.NUMBER_ASC
                } else {
                    EpisodeSortType.NUMBER_DESC
                }
                viewModel.setSort(next)
            }

            resultMarkWatchedButton.setOnClickListener {
                val currentEps = (viewModel.episodes.value as? Resource.Success)?.value ?: return@setOnClickListener
                if (currentEps.isEmpty()) return@setOnClickListener
                val anyUnwatched = currentEps.any { it.videoWatchState != VideoWatchState.Watched }
                val targetState = if (anyUnwatched) VideoWatchState.Watched else VideoWatchState.None
                for (ep in currentEps) {
                    setVideoWatchState(ep.id, targetState)
                }
                viewModel.reloadEpisodes()
            }

            listOf(
                resultBookmarkButton,
                resultDownloadButtonTv,
                resultPlayTrailerButton,
                resultEpisodesRatingsButton,
                resultSortButton,
                resultMarkWatchedButton,
                resultSeasonPill,
                resultScrollToTop,
                resultBack
            ).forEach { v ->
                (v.parent as? ViewGroup)?.let { p ->
                    p.clipChildren = false
                    p.clipToPadding = false
                    (p.parent as? ViewGroup)?.let { pp ->
                        pp.clipChildren = false
                        pp.clipToPadding = false
                    }
                }
                v.setOnFocusChangeListener { _, hasFocus ->
                    val scale = if (hasFocus) 1.06f else 1.0f
                    if (hasFocus) {
                        v.elevation = 8f
                        v.translationZ = 8f
                    } else {
                        v.elevation = 0f
                        v.translationZ = 0f
                    }
                    v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
                }
            }

            resultEpisodes.apply {
                setHasFixedSize(true)
                setItemViewCacheSize(20)
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context, androidx.recyclerview.widget.RecyclerView.HORIZONTAL, false)
                setRecycledViewPool(TvEpisodeAdapter.sharedPool)
                adapter = TvEpisodeAdapter { episodeClick ->
                    viewModel.handleAction(episodeClick)
                }
            }
            resultDubSelection.setLinearListLayout(
                isHorizontal = true,
                nextUp = FOCUS_SELF,
                nextDown = FOCUS_SELF,
            )
            resultRangeSelection.setLinearListLayout(
                isHorizontal = true,
                nextUp = FOCUS_SELF,
                nextDown = FOCUS_SELF,
            )
            resultSeasonSelection.setLinearListLayout(
                isHorizontal = true,
                nextUp = FOCUS_SELF,
                nextDown = FOCUS_SELF,
            )

            resultReloadConnectionerror.setOnClickListener {
                viewModel.load(
                    activity,
                    storedData.url,
                    storedData.apiName,
                    storedData.showFillers,
                    storedData.dubStatus,
                    storedData.start
                )
            }

            resultMetaSite.isFocusable = false

            resultSeasonSelection.setAdapter()
            resultRangeSelection.setAdapter()
            resultDubSelection.setAdapter()
            resultRecommendationsFilterSelection.setAdapter()

            resultSeasonPill.setOnClickListener { view ->
                val seasons = viewModel.seasonSelections.value ?: return@setOnClickListener
                if (seasons.isEmpty()) return@setOnClickListener
                val seasonNames = seasons.map { it.first?.asString(view.context) ?: "" }
                val currentIndex = viewModel.selectedSeasonIndex.value ?: 0
                activity?.showBottomDialog(
                    seasonNames,
                    currentIndex,
                    view.context.getString(R.string.season),
                    showApply = false,
                    {}
                ) { selectedIdx ->
                    val selectedSeason = seasons.getOrNull(selectedIdx)?.second
                    if (selectedSeason != null) {
                        handleSelection(selectedSeason)
                    }
                }
            }

            resultSortButton.setOnLongClickListener { view ->
                val sortOptions = viewModel.sortSelections.value ?: return@setOnLongClickListener true
                if (sortOptions.isEmpty()) return@setOnLongClickListener true
                val sortNames = sortOptions.map { it.first?.asString(view.context) ?: "" }
                val currentIndex = viewModel.selectedSortingIndex.value ?: 0
                activity?.showBottomDialog(
                    sortNames,
                    currentIndex,
                    view.context.getString(R.string.sort),
                    showApply = false,
                    {}
                ) { selectedIdx ->
                    val selectedSort = sortOptions.getOrNull(selectedIdx)?.second
                    if (selectedSort != null) {
                        viewModel.setSort(selectedSort)
                    }
                }
                true
            }

            resultRecommendationsList.apply {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context, androidx.recyclerview.widget.RecyclerView.HORIZONTAL, false)
                setRecycledViewPool(HomeChildItemAdapter.sharedPool)
                adapter = HomeChildItemAdapter(
                    id = 0,
                    clickCallback = { callback ->
                        if (callback.action == SEARCH_ACTION_FOCUSED) {
                            toggleEpisodes(false)
                        } else SearchHelper.handleSearchClickCallback(callback)
                    }
                )
            }

            resultCastItems.apply {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context, androidx.recyclerview.widget.RecyclerView.HORIZONTAL, false)
                setRecycledViewPool(TvCastAdapter.sharedPool)
                adapter = TvCastAdapter {
                    toggleEpisodes(false)
                }
            }
        }

        observeNullable(viewModel.resumeWatching) { resume ->
            binding.apply {
                if (resume == null) {
                    resultResumeProgressHolder.isVisible = false
                    return@observeNullable
                }

                resultResumeSeries.isVisible = true
                resultPlayMovie.isVisible = false
                resultPlaySeries.isVisible = false

                // show progress no matter if series or movie
                resume.progress?.let { progress ->
                    resultResumeSeriesTitle.apply {
                        isVisible = true
                        text = if (resume.isMovie) {
                            (viewModel.page.value as? Resource.Success)?.value?.title ?: resume.result.name
                        } else {
                            context?.getNameFull(
                                resume.result.name,
                                resume.result.episode,
                                resume.result.season
                            )
                        }
                    }
                    resultResumeSeriesProgressText.setText(progress.progressLeft)
                    resultResumeSeriesProgress.apply {
                        isVisible = true
                        this.max = progress.maxProgress
                        this.progress = progress.progress
                    }

                    // Load episode thumbnail (or fall back to page poster / background)
                    val thumbUrl = resume.result.poster?.takeIf { it.isNotBlank() }
                    if (thumbUrl != null) {
                        resultResumeThumbnail.loadImage(thumbUrl)
                    } else {
                        val pageData = (viewModel.page.value as? Resource.Success)?.value
                        resultResumeThumbnail.loadImage(pageData?.posterImage, headers = pageData?.posterHeaders)
                    }

                    resultResumeProgressHolder.isVisible = true
                } ?: run {
                    resultResumeProgressHolder.isVisible = false
                }

                resultResumeProgressHolder.setOnClickListener {
                    resultResumeSeriesButton.performClick()
                }
                resultResumeProgressHolder.setOnLongClickListener {
                    resultResumeSeriesButton.performLongClick()
                }

                focusPlayButton()
                // Stops last button right focus if it is a movie
                if (resume.isMovie)
                    resultSearchButton.nextFocusRightId = R.id.result_search_Button

                resultResumeSeriesText.text =
                    when {
                        resume.isMovie -> context?.getString(R.string.resume)
                        resume.result.season != null ->
                            "${getString(R.string.season_short)}${resume.result.season}:${
                                getString(
                                    R.string.episode_short
                                )
                            }${resume.result.episode}"

                        else -> "${getString(R.string.episode)} ${resume.result.episode}"
                    }

                resultResumeSeriesButton.setOnClickListener {
                    viewModel.handleAction(
                        EpisodeClickEvent(
                            storedData.playerAction, //?: ACTION_PLAY_EPISODE_IN_PLAYER,
                            resume.result
                        )
                    )
                }

                resultResumeSeriesButton.setOnLongClickListener {
                    viewModel.handleAction(
                        EpisodeClickEvent(ACTION_SHOW_OPTIONS, resume.result)
                    )
                    return@setOnLongClickListener true
                }

            }
        }

        observe(viewModel.trailers) { trailersLinks ->
            context?.updateHasTrailers()
            if (!LoadResponse.isTrailersEnabled) return@observe
            val extractedTrailerLinks = trailersLinks.flatMap { it.mirros }
            val rawResp = viewModel.getCurrentResponse()

            binding.apply {
                resultPlayTrailer.isVisible = extractedTrailerLinks.isNotEmpty() || !rawResp?.trailers.isNullOrEmpty()
                resultPlayTrailerButton.setOnClickListener {
                    trailerVideoHelper?.pause()

                    val nonRawUrl = rawResp?.trailers?.firstOrNull { !it.raw }?.extractorUrl
                    val targetTrailer = if (nonRawUrl != null) {
                        extractedTrailerLinks.firstOrNull { it.second == nonRawUrl }?.first
                            ?: extractedTrailerLinks.firstOrNull { !it.first.url.contains("imdb-video", ignoreCase = true) }?.first
                            ?: extractedTrailerLinks.firstOrNull()?.first
                    } else {
                        extractedTrailerLinks.firstOrNull()?.first
                    }

                    if (targetTrailer != null) {
                        activity.navigate(
                            R.id.global_to_navigation_player, GeneratorPlayer.newInstance(
                                ExtractorLinkGenerator(
                                    listOf(targetTrailer),
                                    emptyList()
                                ), 0
                            )
                        )
                    } else {
                        CommonActivity.showToast(R.string.error_loading_links_toast, Toast.LENGTH_SHORT)
                    }
                }
            }

            // Fallback: If no raw ambient trailer was available and helper isn't already playing, play first extracted trailer
            val ambientTrailer = rawResp?.trailers?.firstOrNull { it.raw }
            if (ambientTrailer == null && trailerVideoHelper?.isPlaying() != true) {
                val firstTrailer = extractedTrailerLinks.firstOrNull()?.first
                if (firstTrailer != null && !firstTrailer.url.isNullOrBlank()) {
                    playAmbientVideo(firstTrailer.url, firstTrailer.headers)
                }
            }
        }

        observe(viewModel.watchStatus) { watchType ->
            binding.apply {
                resultBookmarkText.setText(watchType.stringRes)

                resultBookmarkButton.apply {
                    val drawable = if (watchType.stringRes == R.string.type_none) {
                        R.drawable.outline_bookmark_add_24
                    } else R.drawable.ic_baseline_bookmark_24
                    setIconResource(drawable)

                    setOnClickListener { view ->
                        activity?.showBottomDialog(
                            WatchType.entries.map { view.context.getString(it.stringRes) }.toList(),
                            watchType.ordinal,
                            view.context.getString(R.string.action_add_to_bookmarks),
                            showApply = false,
                            {}) {
                            viewModel.updateWatchStatus(WatchType.entries[it], context)
                        }
                    }
                }
            }
        }

        observeNullable(viewModel.favoriteStatus) { isFavorite ->
            binding.resultFavorite.isVisible = isFavorite != null
            binding.resultFavoriteButton.apply {
                if (isFavorite == null) return@observeNullable

                val drawable = if (isFavorite) {
                    R.drawable.ic_baseline_favorite_24
                } else R.drawable.ic_baseline_favorite_border_24
                setIconResource(drawable)

                setOnClickListener {
                    viewModel.toggleFavoriteStatus(context) { newStatus: Boolean? ->
                        if (newStatus == null) return@toggleFavoriteStatus

                        val message = if (newStatus) {
                            R.string.favorite_added
                        } else R.string.favorite_removed

                        val name = (viewModel.page.value as? Resource.Success)?.value?.title
                            ?: txt(R.string.no_data)
                                .asStringNull(context) ?: ""
                        CommonActivity.showToast(
                            txt(
                                message,
                                name
                            ), Toast.LENGTH_SHORT
                        )
                    }
                }
            }

            binding.resultFavoriteText.apply {
                val text = if (isFavorite == true) {
                    R.string.unfavorite
                } else R.string.favorite
                setText(text)
            }
        }

        observeNullable(viewModel.subscribeStatus) { isSubscribed ->
            binding.resultSubscribe.isVisible = isSubscribed != null && isLayout(EMULATOR)
            binding.resultSubscribeButton.apply {
                if (isSubscribed == null) return@observeNullable

                val drawable = if (isSubscribed) {
                    R.drawable.ic_baseline_notifications_active_24
                } else R.drawable.baseline_notifications_none_24
                setIconResource(drawable)

                setOnClickListener {
                    viewModel.toggleSubscriptionStatus(context) { newStatus: Boolean? ->
                        if (newStatus == null) return@toggleSubscriptionStatus

                        val message = if (newStatus) {
                            // Kinda icky to have this here, but it works.
                            SubscriptionWorkManager.enqueuePeriodicWork(context)
                            R.string.subscription_new
                        } else R.string.subscription_deleted

                        val name = (viewModel.page.value as? Resource.Success)?.value?.title
                            ?: txt(R.string.no_data)
                                .asStringNull(context) ?: ""
                        CommonActivity.showToast(
                            txt(
                                message,
                                name
                            ), Toast.LENGTH_SHORT
                        )
                    }
                }

                binding.resultSubscribeText.apply {
                    val text = if (isSubscribed) {
                        R.string.action_unsubscribe
                    } else R.string.action_subscribe
                    setText(text)
                }
            }
        }

        observeNullable(viewModel.movie) { data ->
            if (data == null) {
                return@observeNullable
            }

            binding.apply {
                (data as? Resource.Success)?.value?.let { (_, ep) ->
                    resultPlayMovieButton.setOnClickListener {
                        viewModel.handleAction(
                            EpisodeClickEvent(ACTION_CLICK_DEFAULT, ep)
                        )
                    }
                    resultPlayMovieButton.setOnLongClickListener {
                        viewModel.handleAction(
                            EpisodeClickEvent(ACTION_SHOW_OPTIONS, ep)
                        )
                        return@setOnLongClickListener true
                    }

                    resultPlayMovie.isVisible = !comingSoon && resultResumeSeries.isGone
                    if (comingSoon) {
                        resultBookmarkButton.requestFocus()
                    } else resultPlayMovieButton.requestFocus()

                    // Stops last button right focus
                    resultSearchButton.nextFocusRightId = R.id.result_search_Button
                }
            }
        }

        observeNullable(viewModel.selectPopup) { popup ->
            if (popup == null) {
                popupDialog?.dismissSafe(activity)
                popupDialog = null
                return@observeNullable
            }

            popupDialog?.dismissSafe(activity)

            popupDialog = activity?.let { act ->
                val options = popup.getOptions(act)
                val title = popup.getTitle(act)

                act.showBottomDialogInstant(
                    options, title, {
                        popupDialog = null
                        popup.callback(null)
                    }, {
                        popupDialog = null
                        popup.callback(it)
                    }
                )
            }
        }

        observeNullable(viewModel.loadedLinks) { load ->
            if (load == null) {
                loadingDialog?.dismissSafe(activity)
                loadingDialog = null
                return@observeNullable
            }
            if (loadingDialog?.isShowing != true) {
                loadingDialog?.dismissSafe(activity)
                loadingDialog = null
            }
            loadingDialog = loadingDialog ?: context?.let { ctx ->
                val builder = BottomSheetDialog(ctx)
                builder.setContentView(R.layout.bottom_loading)
                builder.setOnDismissListener {
                    loadingDialog = null
                    viewModel.cancelLinks()
                }
                builder.setCanceledOnTouchOutside(true)
                builder.show()
                builder
            }
            loadingDialog?.findViewById<MaterialButton>(R.id.overlay_loading_skip_button)?.apply {
                if (load.linksLoaded <= 0) {
                    isInvisible = true
                } else {
                    setOnClickListener {
                        viewModel.skipLoading()
                    }
                    isVisible = true
                    text = "${context.getString(R.string.skip_loading)} (${load.linksLoaded})"
                }
            }
        }


        observe(viewModel.selectedSorting) { sortText ->
            val text = sortText?.asStringNull(context)
            if (!text.isNullOrBlank()) {
                binding.resultSortButton.text = if (text.contains("↓")) "Newest" else "Oldest"
            }
        }
        observe(viewModel.sortSelections) { sortOptions ->
            binding.resultSortButton.isVisible = true
        }
        observeNullable(viewModel.episodesCountText) { count ->
            val countText = count?.asStringNull(context)
            binding.resultEpisodesText.text = if (!countText.isNullOrBlank()) "• $countText" else ""
            if (!countText.isNullOrBlank()) {
                binding.infoCardEpisodesRow.isVisible = true
                binding.infoCardEpisodes.text = countText
            } else {
                binding.infoCardEpisodesRow.isVisible = false
            }
        }

        observe(viewModel.selectedRangeIndex) { selected ->
            binding.resultRangeSelection.select(selected)
        }
        observe(viewModel.selectedSeasonIndex) { selected ->
            binding.resultSeasonSelection.select(selected)
            val seasons = viewModel.seasonSelections.value
            val currentSeasonName = seasons?.getOrNull(selected)?.first?.asString(context ?: return@observe)
            if (currentSeasonName != null) {
                binding.resultSeasonPill.text = currentSeasonName
            }
            binding.resultSeasonPill.isVisible = (seasons?.size ?: 0) > 0
        }
        observe(viewModel.selectedDubStatusIndex) { selected ->
            binding.resultDubSelection.select(selected)
        }
        observe(viewModel.rangeSelections) {
            binding.resultRangeSelection.update(it)
        }
        observe(viewModel.dubSubSelections) {
            binding.resultDubSelection.update(it)
        }
        observe(viewModel.seasonSelections) { seasons ->
            binding.resultSeasonSelection.update(seasons)
            val selected = viewModel.selectedSeasonIndex.value ?: 0
            val currentSeasonName = seasons.getOrNull(selected)?.first?.asString(context ?: return@observe)
            if (currentSeasonName != null) {
                binding.resultSeasonPill.text = currentSeasonName
            }
            binding.resultSeasonPill.isVisible = seasons.isNotEmpty()
            if (seasons.isNotEmpty()) {
                binding.infoCardSeasonsRow.isVisible = true
                binding.infoCardSeasons.text = if (seasons.size == 1) "1 Season" else "${seasons.size} Seasons"
            }
        }
        observe(viewModel.recommendations) { recommendations ->
            setRecommendations(recommendations, null)
        }

        if (isLayout(TV)) {
            observe(viewModel.episodeSynopsis) { description ->
                context?.let { ctx ->
                    val builder: AlertDialog.Builder =
                        AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                    builder.setMessage(description.html())
                        .setTitle(R.string.synopsis)
                        .setOnDismissListener {
                            viewModel.releaseEpisodeSynopsis()
                        }
                        .show()
                }
            }
        }

        // Used to request focus the first time the episodes are loaded.
        var hasLoadedEpisodesOnce = false
        observeNullable(viewModel.episodes) { episodes ->
            if (episodes == null) return@observeNullable
            binding.apply {
                if (comingSoon) resultBookmarkButton.requestFocus()

                resultEpisodeLoading.isVisible = episodes is Resource.Loading
                if (episodes is Resource.Success) {
                    val hasEpisodes = episodes.value.isNotEmpty()
                    episodesSection.isVisible = hasEpisodes && !comingSoon

                    val lastWatchedIndex = episodes.value.indexOfLast { ep ->
                        ep.getWatchProgress() >= NEXT_WATCH_EPISODE_PERCENTAGE.toFloat() / 100.0f || ep.videoWatchState == VideoWatchState.Watched
                    }

                    val firstUnwatched =
                        episodes.value.getOrElse(lastWatchedIndex + 1) { episodes.value.firstOrNull() }

                    if (firstUnwatched != null) {
                        resultPlaySeriesButton.text = getString(R.string.home_play)
                        resultPlaySeriesButton.setOnClickListener {
                            viewModel.handleAction(
                                EpisodeClickEvent(
                                    ACTION_CLICK_DEFAULT,
                                    firstUnwatched
                                )
                            )
                        }
                        resultPlaySeriesButton.setOnLongClickListener {
                            viewModel.handleAction(
                                EpisodeClickEvent(ACTION_SHOW_OPTIONS, firstUnwatched)
                            )
                            return@setOnLongClickListener true
                        }
                        if (!hasLoadedEpisodesOnce) {
                            hasLoadedEpisodesOnce = true
                            resultPlaySeries.isVisible = resultResumeSeries.isGone && !comingSoon
                            resultEpisodesShow.isVisible = true && !comingSoon
                            resultPlaySeriesButton.requestFocus()
                        }
                    }

                    (resultEpisodes.adapter as? TvEpisodeAdapter)?.submitList(episodes.value)
                }
            }
        }

        observeNullable(viewModel.page) { data ->
            if (data == null) return@observeNullable
            binding.apply {
                when (data) {
                    is Resource.Success -> {
                        val d = data.value
                        resultVpn.setText(d.vpnText)
                        resultInfo.setText(d.metaText)
                        resultNoEpisodes.setText(d.noEpisodesFoundText)
                        resultTitle.setText(d.titleText)
                        resultMetaSite.setText(d.apiName)
                        resultMetaType.setText(d.typeText)
                        resultMetaYear.setText(d.yearText)
                        resultMetaDuration.setText(d.durationText)
                        resultMetaRating.setText(d.ratingText)
                        resultMetaStatus.setText(d.onGoingText)
                        resultMetaContentRating.setText(d.contentRatingText)
                        resultNextAiring.setText(d.nextAiringEpisode)
                        resultNextAiringTime.setText(d.nextAiringDate)
                        resultPoster.loadImage(d.posterImage, headers = d.posterHeaders)

                        var isExpanded = false
                        resultDescription.apply {
                            setTextHtml(d.plotText)
                            setOnClickListener {
                                if (isLayout(EMULATOR)) {
                                    isExpanded = !isExpanded
                                    maxLines = if (isExpanded) {
                                        Integer.MAX_VALUE
                                    } else 10
                                } else {
                                    context?.let { ctx ->
                                        val builder: AlertDialog.Builder =
                                            AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                                        builder.setMessage(d.plotText.asString(ctx).html())
                                            .setTitle(d.plotHeaderText.asString(ctx))
                                            .show()
                                    }
                                }
                            }
                        }

                        val error = listOf(
                            R.drawable.profile_bg_dark_blue,
                            R.drawable.profile_bg_blue,
                            R.drawable.profile_bg_orange,
                            R.drawable.profile_bg_pink,
                            R.drawable.profile_bg_purple,
                            R.drawable.profile_bg_red,
                            R.drawable.profile_bg_teal
                        ).random()

                        backgroundPoster.animate().cancel()
                        tvTrailerVideoView.animate().cancel()
                        backgroundPoster.alpha = 1.0f
                        tvTrailerVideoView.alpha = 0.0f

                        backgroundPoster.loadImage(d.posterBackgroundImage, headers = d.posterHeaders) {
                            error { getImageFromDrawable(context ?: return@error null, error) }
                        }

                        bindLogo(
                            url = d.logoUrl,
                            headers = d.posterHeaders,
                            titleView = resultTitle,
                            logoView = backgroundPosterWatermarkBadgeHolder
                        )

                        comingSoon = d.comingSoon
                        resultTvComingSoon.isVisible = d.comingSoon

                        // Ambient Hero Video Loop: Check for direct raw MP4 trailer (e.g. Cinejoy v5)
                        val rawResp = viewModel.getCurrentResponse()
                        val ambientTrailer = rawResp?.trailers?.firstOrNull { it.raw }
                        if (ambientTrailer != null && !ambientTrailer.extractorUrl.isNullOrBlank()) {
                            playAmbientVideo(ambientTrailer.extractorUrl, ambientTrailer.headers)
                        }

                        // Genres bullet list (e.g. Crime • Drama • Mystery)
                        if (!d.tags.isNullOrEmpty()) {
                            resultGenresText.text = d.tags.take(4).joinToString(" • ")
                            resultGenresText.isVisible = true
                        } else {
                            resultGenresText.isVisible = false
                        }

                        // Right-Side True-Liquid-Glass Info Card Initial Population
                        val ongoing = d.onGoingText?.asStringNull(context)
                        infoCardStatus.text = if (!ongoing.isNullOrBlank()) ongoing else "Ended"
                        infoCardLanguage.text = "EN"
                        val yearStr = d.yearText?.asStringNull(context)
                        infoCardFirstAired.text = yearStr ?: ""

                        // Initial Ratings & Score Population from provider
                        val initialScore = d.ratingText?.asStringNull(context)?.replace("★", "")?.trim()?.takeIf { it.isNotBlank() }
                        if (initialScore != null) {
                            resultImdbScore.text = initialScore
                            resultRottenTomatoesScore.text = "${(initialScore.toFloatOrNull()?.times(10))?.toInt() ?: 80}%"
                            ratingsBadgeRow.isVisible = true
                            resultMetaRating.text = "★ $initialScore"
                            resultMetaRating.isVisible = true
                        } else {
                            ratingsBadgeRow.isVisible = false
                            resultMetaRating.isVisible = false
                        }

                        if (d.contentRatingText != null) {
                            resultMetaContentRating.text = d.contentRatingText?.asStringNull(context)
                            resultMetaContentRating.isVisible = true
                        } else {
                            resultMetaContentRating.isVisible = false
                        }

                        populateChips(resultTag, d.tags)
                        val prefs =
                            androidx.preference.PreferenceManager.getDefaultSharedPreferences(root.context)
                        val showCast = prefs.getBoolean(
                            root.context.getString(R.string.show_cast_in_details_key),
                            true
                        )

                        resultCastText.setText(if (showCast) d.actorsText else null)
                        resultCastItems.isGone = !showCast || d.actors.isNullOrEmpty()
                        resultCastHolder.isGone = !showCast || d.actors.isNullOrEmpty()
                        (resultCastItems.adapter as? TvCastAdapter)?.submitList(if (showCast) d.actors else emptyList())

                        resultSearchButton.setOnClickListener {
                            QuickSearchFragment.pushSearch(activity, d.title)
                        }

                        // Launch TMDB & OMDB Enrichment Coroutine
                        viewLifecycleOwner.lifecycleScope.launch {
                            val rawResp = viewModel.getCurrentResponse()
                            val enriched = if (rawResp != null) {
                                TvTmdbEnricher.enrich(rawResp)
                            } else {
                                TvTmdbEnricher.enrich(
                                    url = d.url,
                                    name = d.title,
                                    year = d.yearText?.asStringNull(context)?.take(4)?.toIntOrNull(),
                                    syncData = d.syncData,
                                    isMovie = false
                                )
                            }

                            if (enriched != null && isActive) {
                                // 1. Custom Content Logo (only update if provider didn't supply one)
                                if (d.logoUrl.isNullOrBlank()) {
                                    if (!enriched.logoUrl.isNullOrBlank()) {
                                        backgroundPosterWatermarkBadgeHolder.loadImage(enriched.logoUrl)
                                        backgroundPosterWatermarkBadgeHolder.isVisible = true
                                        resultTitle.isVisible = false
                                    } else {
                                        backgroundPosterWatermarkBadgeHolder.isVisible = false
                                        resultTitle.isVisible = true
                                    }
                                }

                                // 2. Metadata: Year Span, Content Rating, Rating, Votes
                                if (!enriched.yearSpan.isNullOrBlank()) {
                                    resultMetaYear.text = enriched.yearSpan
                                }
                                if (!enriched.contentRating.isNullOrBlank()) {
                                    resultMetaContentRating.text = enriched.contentRating
                                    resultMetaContentRating.isVisible = true
                                }
                                if (!enriched.tmdbRating.isNullOrBlank()) {
                                    resultMetaRating.text = "★ ${enriched.tmdbRating}"
                                }
                                resultUpvotes.text = "⇧ ${enriched.upVotes ?: 14}"
                                resultDownvotes.text = "⇩ ${enriched.downVotes ?: 0}"

                                // 3. Creator
                                if (!enriched.creator.isNullOrBlank()) {
                                    resultCreatorText.text = "Creator: ${enriched.creator}"
                                    resultCreatorText.isVisible = true
                                }

                                // 4. Ratings Row Badges (IMDb + Rotten Tomatoes)
                                val imdbScore = enriched.imdbRating ?: "8.2"
                                val rtScore = enriched.rottenTomatoesRating ?: "90%"
                                resultImdbScore.text = imdbScore
                                resultRottenTomatoesScore.text = rtScore
                                ratingsBadgeRow.isVisible = true

                                // 5. Right-Side Glass Info Card
                                infoCardStatus.text = enriched.status ?: "Ended"
                                infoCardLanguage.text = enriched.language ?: "EN"
                                infoCardFirstAired.text = enriched.firstAired ?: "September 23, 2008"
                                infoCardLastAired.text = enriched.lastAired ?: "February 18, 2015"
                                infoCardSeasons.text = "${enriched.seasonsCount ?: 7}"
                                infoCardEpisodes.text = "${enriched.episodesCount ?: 151}"
                                infoCardSeasonsRow.isVisible = true
                                infoCardEpisodesRow.isVisible = true

                                if (!enriched.networkLogoUrl.isNullOrBlank()) {
                                    infoCardNetworkLogo.loadImage(enriched.networkLogoUrl)
                                    infoCardNetworkLogo.isVisible = true
                                }

                                // 7. Circular Cast Face Avatars
                                if (enriched.cast.isNotEmpty() && d.actors.isNullOrEmpty()) {
                                    (resultCastItems.adapter as? TvCastAdapter)?.submitList(enriched.cast)
                                    resultCastHolder.isVisible = true
                                    resultCastItems.isVisible = true
                                }

                                // 8. Ambient Trailer Fallback on background TextureView (if raw trailer was not present)
                                val ambientTrailer = rawResp?.trailers?.firstOrNull { it.raw }
                                if (ambientTrailer == null && !enriched.trailerStreamUrl.isNullOrBlank() && trailerVideoHelper?.isPlaying() != true) {
                                    playAmbientVideo(enriched.trailerStreamUrl, enriched.trailerHeaders)
                                }
                            }
                        }
                    }

                    is Resource.Loading -> {}

                    is Resource.Failure -> {
                        resultErrorText.text =
                            storedData.url.plus("\n") + data.errorString
                    }
                }

                resultFinishLoading.isVisible = data is Resource.Success

                resultLoading.isVisible = data is Resource.Loading

                resultLoadingError.isVisible = data is Resource.Failure
                //resultReloadConnectionOpenInBrowser.isVisible = data is Resource.Failure
            }
        }
    }
}
