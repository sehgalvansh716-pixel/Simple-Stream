package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.lagradost.cloudstream3.ui.utils.TvAmbientVideoHelper
import com.discord.panels.OverlappingPanelsLayout
import com.discord.panels.PanelState
import com.discord.panels.PanelsChildGestureRegionObserver
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.databinding.FragmentResultBinding
import com.lagradost.cloudstream3.databinding.FragmentResultSwipeBinding
import com.lagradost.cloudstream3.databinding.ResultRecommendationsBinding
import com.lagradost.cloudstream3.databinding.ResultSyncBinding
import com.lagradost.cloudstream3.databinding.TrailerCustomLayoutBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.services.SubscriptionWorkManager
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.APP_STRING_SHARE
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_LONG_CLICK
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup
import com.lagradost.cloudstream3.ui.home.HomeChildItemAdapter
import com.lagradost.cloudstream3.ui.player.CS3IPlayer
import com.lagradost.cloudstream3.ui.player.CSPlayerEvent
import com.lagradost.cloudstream3.ui.player.IPlayer
import com.lagradost.cloudstream3.ui.player.PlayerView
import com.lagradost.cloudstream3.ui.player.source_priority.QualityProfileDialog
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.result.ResultFragment.bindLogo
import com.lagradost.cloudstream3.ui.result.ResultFragment.getStoredData
import com.lagradost.cloudstream3.ui.result.ResultFragment.updateUIEvent
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.SettingsGeneral.Companion.pickDownloadPath
import com.lagradost.cloudstream3.ui.settings.utils.getChooseFolderLauncher
import com.lagradost.cloudstream3.utils.AppContextUtils.getNameFull
import com.lagradost.cloudstream3.utils.AppContextUtils.isCastApiAvailable
import com.lagradost.cloudstream3.utils.AppContextUtils.loadCache
import com.lagradost.cloudstream3.utils.AppContextUtils.openBrowser
import com.lagradost.cloudstream3.utils.AppContextUtils.updateHasTrailers
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.BatteryOptimizationChecker.openBatteryOptimizationSettings
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogInstant
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.UIHelper.clipboardHelper
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.populateChips
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import com.lagradost.cloudstream3.utils.UIHelper.setListViewHeightBasedOnItems
import com.lagradost.cloudstream3.utils.UIHelper.setNavigationBarColorCompat
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getBasePath
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import com.lagradost.cloudstream3.utils.getImageFromDrawable
import com.lagradost.cloudstream3.utils.setText
import com.lagradost.cloudstream3.utils.setTextHtml
import com.lagradost.cloudstream3.utils.txt
import java.net.URLEncoder
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.roundToInt

open class ResultFragmentPhone : BaseFragment<FragmentResultSwipeBinding>(
    BindingCreator.Inflate(FragmentResultSwipeBinding::inflate)
), PlayerView.Callbacks {
    private val gestureRegionsListener =
        object : PanelsChildGestureRegionObserver.GestureRegionsListener {
            override fun onGestureRegionsUpdate(gestureRegions: List<Rect>) {
                binding?.resultOverlappingPanels?.setChildGestureRegions(gestureRegions)
            }
        }

    /** Queue of pending actions that is deferred to after a custom path is set */
    private val pendingPathActions = ConcurrentLinkedDeque<Pair<Int, ResultEpisode>>()

    /**
     * Appends all actions to a queue, and asks for a user to enter the download folder if not already set up.
     *
     * Then processes the queue in the given order, only after the user has selected a folder.
     * This is to defer the download to after a file path is set, due to perms.
     * */
    private fun requirePathForActions(list: Collection<Pair<Int, ResultEpisode>>) {
        pendingPathActions.addAll(list)
        val (_, path) = context?.getBasePath() ?: return
        if (path == null) {
            /** If we have not set any download path, then ask the user for it before we download it */
            try {
                /** Give the user some info of what we are doing and why, even if it may be missed */
                showToast(R.string.download_path_pref)
                pathPicker.launch(Uri.EMPTY)
            } catch (t: Throwable) {
                logError(t)
                /** Something went wrong, TV Device?
                 * Use the fallback behavior of just downloading it even if no path is selected,
                 * and hope it works */
                processPendingActions()
            }
        } else {
            /**
             * Otherwise dispatch everything, as we already have a valid download path
             * Even if this is "wrong", we do not care as the user has entered something
             * */
            processPendingActions()
        }
    }

    /** Clear all the items in the queue and dispatch them to the viewmodel in order */
    private fun processPendingActions() = viewModel.viewModelScope.launchSafe {
        while (!pendingPathActions.isEmpty()) {
            try {
                val (action, data) = pendingPathActions.pop()
                viewModel.handleAction(
                    EpisodeClickEvent(
                        action,
                        data
                    )
                )
            } catch (_: NoSuchElementException) {
                /** In case of a race */
            }
        }
    }

    private val pathPicker = getChooseFolderLauncher { uri, path ->
        if (uri == null) {
            /** No path selected, clear the list without acting on it, canceling */
            if (!pendingPathActions.isEmpty()) {
                /** Only show on non-empty, just in case */
                showToast(R.string.download_canceled)
                pendingPathActions.clear()
            }
        } else {
            /** Select the folder, and dispatch everything */
            pickDownloadPath(uri, path)
            processPendingActions()
        }
    }

    protected lateinit var viewModel: ResultViewModel2
    protected lateinit var syncModel: SyncViewModel

    protected var resultBinding: FragmentResultBinding? = null
    protected var recommendationBinding: ResultRecommendationsBinding? = null
    protected var syncBinding: ResultSyncBinding? = null

    var player: IPlayer = CS3IPlayer()
    protected open var hasPipModeSupport: Boolean = false
    protected open var isFullScreenPlayer: Boolean = true
    protected open var lockRotation: Boolean = true
    protected var playerBinding: TrailerCustomLayoutBinding? = null
    protected var isShowing: Boolean = false

    protected var playerHostView: PlayerView? = null

    open fun updateUIVisibility() {}

    protected fun uiReset() {
        isShowing = false
        updateUIVisibility()
    }

    open fun showMirrorsDialogue() {}
    open fun showTracksDialogue() {}
    open fun openOnlineSubPicker(
        context: android.content.Context,
        loadResponse: LoadResponse?,
        dismissCallback: () -> Unit
    ) {}

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(view, padTop = false, padBottom = false)
        view.findViewById<View>(R.id.result_top_bar)?.let { topBar ->
            fixSystemBarsPadding(topBar, padTop = true, padBottom = false, padLeft = false, padRight = false, overlayCutout = false)
        }
    }

    private var isBackdropImageAActive = true
    private var lastBackdropUrl: String? = null
    private var trailerVideoHelper: TvAmbientVideoHelper? = null
    private var trailerJob: Job? = null
    private var isPosterMode = false
    private var isAmbientPausedByScroll = false

    private fun initAtmosphericBackdrop(rootView: View) {
        val imageA = rootView.findViewById<ImageView>(R.id.result_backdrop_image_a) ?: return
        val imageB = rootView.findViewById<ImageView>(R.id.result_backdrop_image_b) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurEffect = RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP)
            imageA.setRenderEffect(blurEffect)
            imageB.setRenderEffect(blurEffect)
        }
    }

    private fun updateAtmosphericBackdrop(posterUrl: String?, headers: Map<String, String>? = null) {
        val b = binding ?: return
        if (posterUrl.isNullOrBlank() || posterUrl == lastBackdropUrl) return
        val isFirstLoad = (lastBackdropUrl == null)
        lastBackdropUrl = posterUrl

        val imageA = b.resultBackdropImageA
        val imageB = b.resultBackdropImageB

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurEffect = RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
            imageA.setRenderEffect(blurEffect)
            imageB.setRenderEffect(blurEffect)
        }

        if (isFirstLoad) {
            imageA.alpha = 0f
            imageB.alpha = 0f
            imageA.loadImage(posterUrl, headers) {
                size(320, 180)
            }
            imageA.animate().alpha(1.0f).setDuration(800).start()
            isBackdropImageAActive = true
            return
        }

        val incomingView = if (isBackdropImageAActive) imageB else imageA
        val outgoingView = if (isBackdropImageAActive) imageA else imageB

        incomingView.alpha = 0f
        incomingView.loadImage(posterUrl, headers) {
            size(320, 180)
        }
        incomingView.animate()
            .alpha(1.0f)
            .setDuration(600)
            .withEndAction {
                outgoingView.alpha = 0f
                isBackdropImageAActive = !isBackdropImageAActive
            }
            .start()
        outgoingView.animate()
            .alpha(0.0f)
            .setDuration(600)
            .start()
    }

    private fun playAmbientVideo(url: String, headers: Map<String, String>? = null) {
        trailerJob?.cancel()
        trailerJob = viewLifecycleOwner.lifecycleScope.launch {
            if (!isActive) return@launch
            val ctx = context ?: return@launch
            val rBinding = resultBinding ?: return@launch
            try {
                if (trailerVideoHelper == null) {
                    trailerVideoHelper = TvAmbientVideoHelper(ctx)
                }
                trailerVideoHelper?.attachUri(
                    textureView = rBinding.resultTrailerVideoView,
                    uri = Uri.parse(url),
                    headers = headers,
                    autoPlay = !isPosterMode && !isAmbientPausedByScroll,
                    onReady = {
                        if (!isPosterMode) {
                            rBinding.resultTrailerVideoView.animate()
                                .alpha(1.0f)
                                .setDuration(700)
                                .start()
                        }
                        rBinding.resultTrailerToggle.apply {
                            isVisible = true
                            text = if (isPosterMode) "Trailer" else "Poster"
                            setIconResource(if (isPosterMode) R.drawable.ic_baseline_play_arrow_24 else R.drawable.ic_baseline_image_24)
                        }
                    }
                )
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PanelsChildGestureRegionObserver.Provider.get().apply {
            resultBinding?.resultCastItems?.let { register(it) }
            resultBinding?.resultEpisodes?.let { register(it) }
        }
    }

    var currentTrailers: List<Pair<ExtractorLink, String>> = emptyList()
    var currentTrailerIndex = 0

    override fun nextMirror() {
        currentTrailerIndex++
        loadTrailer()
    }

    override fun hasNextMirror(): Boolean {
        return currentTrailerIndex + 1 < currentTrailers.size
    }

    override fun playerError(exception: Throwable) {
        if (player.getIsPlaying()) { // because we don't want random toasts in player
            playerHostView?.playerError(exception)
        } else {
            nextMirror()
        }
    }

    fun loadTrailer(index: Int? = null) {
        val target = currentTrailers.getOrNull(index ?: currentTrailerIndex)?.first
        if (target != null && !target.url.isNullOrBlank()) {
            playAmbientVideo(target.url, target.headers)
        }
    }

    private fun setTrailers(trailers: List<Pair<ExtractorLink, String>>?) {
        context?.updateHasTrailers()
        if (!LoadResponse.isTrailersEnabled) return
        val rawResp = viewModel.getCurrentResponse()
        val ambientTrailerUrl = rawResp?.trailers?.firstOrNull { it.raw }?.extractorUrl

        currentTrailers = if (ambientTrailerUrl != null) {
            val ambient = trailers?.filter { it.second == ambientTrailerUrl } ?: emptyList()
            val others = trailers?.filter { it.second != ambientTrailerUrl } ?: emptyList()
            ambient + others.sortedBy { -it.first.quality }
        } else {
            trailers?.sortedBy { -it.first.quality } ?: emptyList()
        }

        if (trailerVideoHelper?.isPlaying() != true) {
            val firstTrailer = currentTrailers.firstOrNull()?.first
            if (firstTrailer != null && !firstTrailer.url.isNullOrBlank()) {
                playAmbientVideo(firstTrailer.url, firstTrailer.headers)
            }
        }
    }

    override fun onDestroyView() {
        trailerJob?.cancel()
        trailerJob = null
        trailerVideoHelper?.release()
        trailerVideoHelper = null
        lastBackdropUrl = null
        isPosterMode = false
        isAmbientPausedByScroll = false

        PanelsChildGestureRegionObserver.Provider.get().let { obs ->
            resultBinding?.resultCastItems?.let {
                obs.unregister(it)
            }
            resultBinding?.resultEpisodes?.let {
                obs.unregister(it)
            }
            obs.removeGestureRegionsUpdateListener(gestureRegionsListener)
        }

        updateUIEvent -= ::updateUI
        playerHostView?.release()
        playerBinding = null
        resultBinding?.resultScroll?.setOnClickListener(null)
        resultBinding = null
        syncBinding = null
        recommendationBinding = null
        lastBackdropUrl = null
        activity?.detachBackPressedCallback(this@ResultFragmentPhone.toString())
        super.onDestroyView()
    }

    var loadingDialog: Dialog? = null
    var popupDialog: Dialog? = null

    /**
     * Sets next focus to allow navigation up and down between 2 views
     * if either of them is null nothing happens.
     **/
    private fun setFocusUpAndDown(upper: View?, down: View?) {
        if (upper == null || down == null) return
        upper.nextFocusDownId = down.id
        down.nextFocusUpId = upper.id
    }

    var selectSeason: String? = null
    var selectEpisodeRange: String? = null

    private fun setUrl(url: String?) {
        if (url == null) {
            binding?.resultOpenInBrowser?.isVisible = false
            return
        }

        val valid = url.startsWith("http")

        binding?.resultOpenInBrowser?.apply {
            isVisible = valid
            setOnClickListener {
                context?.openBrowser(url)
            }
        }

        resultBinding?.resultReloadConnectionOpenInBrowser?.setOnClickListener {
            view?.context?.openBrowser(url)
        }

        resultBinding?.resultMetaSite?.setOnClickListener {
            view?.context?.openBrowser(url)
        }
    }

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
        afterPluginsLoadedEvent += ::reloadViewModel
        activity?.setNavigationBarColorCompat(R.attr.primaryBlackBackground)
        context?.let { ctx ->
            playerHostView?.onResume(ctx)
            playerHostView?.setupKeyEventListener()
        }
        if (!isPosterMode && !isAmbientPausedByScroll) {
            trailerVideoHelper?.play()
        }
        super.onResume()
        PanelsChildGestureRegionObserver.Provider.get()
            .addGestureRegionsUpdateListener(gestureRegionsListener)
    }

    override fun onPause() {
        trailerJob?.cancel()
        trailerVideoHelper?.pause()
        playerHostView?.releaseKeyEventListener()
        super.onPause()
    }

    override fun onStop() {
        afterPluginsLoadedEvent -= ::reloadViewModel
        playerHostView?.onStop()
        super.onStop()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun updateUI(id: Int?) {
        syncModel.updateUserData()
        viewModel.reloadEpisodes()
    }

    override fun onBindingCreated(binding: FragmentResultSwipeBinding, savedInstanceState: Bundle?) {
        // Set up sub-binding references
        viewModel = ViewModelProvider(this)[ResultViewModel2::class.java]
        syncModel = ViewModelProvider(this)[SyncViewModel::class.java]
        updateUIEvent += ::updateUI

        resultBinding = binding.fragmentResult
        recommendationBinding = binding.resultRecommendations
        syncBinding = binding.resultSync

        initAtmosphericBackdrop(binding.root)

        // Set up trailer player
        val ctx = context ?: return
        playerHostView = PlayerView(ctx)
        playerHostView?.player = player
        playerHostView?.hasPipModeSupport = hasPipModeSupport
        playerHostView?.callbacks = this
        playerHostView?.bindViews(binding.root)
        playerBinding = binding.root.findViewById<View?>(R.id.player_holder)?.let {
            TrailerCustomLayoutBinding.bind(it)
        }
        playerHostView?.initialize()

        // ===== setup =====
        val storedData = getStoredData() ?: return
        activity?.window?.decorView?.clearFocus()
        activity?.loadCache()
        context?.updateHasTrailers()
        hideKeyboard(binding.root)
        if (storedData.restart || !viewModel.hasLoaded())
            viewModel.load(
                activity,
                storedData.url,
                storedData.apiName,
                storedData.showFillers,
                storedData.dubStatus,
                storedData.start
            )

        setUrl(storedData.url)
        syncModel.addFromUrl(storedData.url)
        val api = APIHolder.getApiFromNameNull(storedData.apiName)

        // This may not be 100% reliable, and may delay for small period
        // before resultCastItems will be scrollable again, but this does work
        // most of the time.
        binding.resultOverlappingPanels.registerEndPanelStateListeners(
            object : OverlappingPanelsLayout.PanelStateListener {
                override fun onPanelStateChange(panelState: PanelState) {
                    PanelsChildGestureRegionObserver.Provider.get().apply {
                        resultBinding?.resultCastItems?.let { register(it) }
                        resultBinding?.resultEpisodes?.let { register(it) }
                        resultBinding?.resultOnpageRecommendations?.let { register(it) }
                    }
                }
            }
        )

        // ===== ===== =====

        binding.resultSearch.isGone = storedData.name.isBlank()
        binding.resultSearch.setOnClickListener {
            QuickSearchFragment.pushSearch(activity, storedData.name)
        }

        resultBinding?.apply {
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

            resultCastItems.setLinearListLayout(
                isHorizontal = true,
                nextLeft = FOCUS_SELF,
                nextRight = FOCUS_SELF
            )
            /*resultCastItems.layoutManager = object : LinearListLayout(view.context) {
                override fun onRequestChildFocus(
                    parent: RecyclerView,
                    state: RecyclerView.State,
                    child: View,
                    focused: View?
                ): Boolean {
                    // Make the cast always focus the first visible item when focused
                    // from somewhere else. Otherwise, it jumps to the last item.
                    return if (parent.focusedChild == null) {
                        scrollToPosition(this.findFirstCompletelyVisibleItemPosition())
                        true
                    } else {
                        super.onRequestChildFocus(parent, state, child, focused)
                    }
                }
            }.apply {
                this.orientation = RecyclerView.HORIZONTAL
            }*/
            resultCastItems.apply {
                setHasFixedSize(true)
                setRecycledViewPool(ActorAdaptor.sharedPool)
                adapter = ActorAdaptor()
            }
            resultEpisodes.apply {
                setHasFixedSize(true)
                setItemViewCacheSize(20)
                setRecycledViewPool(EpisodeAdapter.sharedPool)
                adapter =
                    EpisodeAdapter(
                        api?.hasDownloadSupport == true,
                        { episodeClick ->
                            when (episodeClick.action) {
                                ACTION_DOWNLOAD_EPISODE, ACTION_DOWNLOAD_MIRROR -> {
                                    requirePathForActions(listOf(episodeClick.action to episodeClick.data))
                                }

                                else -> viewModel.handleAction(episodeClick)
                            }
                        },
                        { downloadClickEvent ->
                            DownloadButtonSetup.handleDownloadClick(downloadClickEvent)
                        }

                    )
            }

            resultOnpageRecommendations.apply {
                setHasFixedSize(true)
                setItemViewCacheSize(20)
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                    context,
                    androidx.recyclerview.widget.RecyclerView.HORIZONTAL,
                    false
                )
                setRecycledViewPool(HomeChildItemAdapter.sharedPool)
                adapter = HomeChildItemAdapter(
                    id = 0,
                    clickCallback = { callback ->
                        SearchHelper.handleSearchClickCallback(callback)
                    }
                )
            }

            observeNullable(viewModel.selectedSorting) {
                resultSortButton.setText(it)
            }

            observe(viewModel.sortSelections) { sort ->
                resultBinding?.resultSortButton?.setOnClickListener { view ->
                    view?.context?.let { ctx ->
                        val names = sort
                            .mapNotNull { (text, r) ->
                                r to (text.asStringNull(ctx) ?: return@mapNotNull null)
                            }

                        activity?.showBottomDialog(
                            names.map { it.second },
                            viewModel.selectedSortingIndex.value ?: -1,
                            ctx.getString(R.string.sort_by),
                            false,
                            {}) { itemId ->
                            viewModel.setSort(names[itemId].first)
                        }
                    }
                }
            }

            resultTrailerToggle.setOnClickListener {
                isPosterMode = !isPosterMode
                if (isPosterMode) {
                    resultTrailerVideoView.animate().alpha(0.0f).setDuration(400).start()
                    trailerVideoHelper?.pause()
                    resultTrailerToggle.apply {
                        text = "Trailer"
                        setIconResource(R.drawable.ic_baseline_play_arrow_24)
                    }
                } else {
                    trailerVideoHelper?.play()
                    resultTrailerVideoView.animate().alpha(1.0f).setDuration(400).start()
                    resultTrailerToggle.apply {
                        text = "Poster"
                        setIconResource(R.drawable.ic_baseline_image_24)
                    }
                }
            }

            resultScroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val dy = scrollY - oldScrollY
                if (dy > 0) { //check for scroll down
                    binding.resultBookmarkFab.shrink()
                } else if (dy < -5) {
                    binding.resultBookmarkFab.extend()
                }
                if (!isFullScreenPlayer && player.getIsPlaying()) {
                    if (scrollY > (resultBinding?.fragmentTrailer?.playerBackground?.height
                            ?: scrollY)
                    ) {
                        player.handleEvent(CSPlayerEvent.Pause)
                    }
                }

                val isScrolledAway = scrollY > 250
                if (isScrolledAway != isAmbientPausedByScroll) {
                    isAmbientPausedByScroll = isScrolledAway
                    if (isScrolledAway) {
                        trailerVideoHelper?.pause()
                    } else if (!isPosterMode) {
                        trailerVideoHelper?.play()
                    }
                }
            })
        }

        binding.apply {
            resultOverlappingPanels.setStartPanelLockState(OverlappingPanelsLayout.LockState.CLOSE)
            resultOverlappingPanels.setEndPanelLockState(OverlappingPanelsLayout.LockState.CLOSE)
            resultBack.setOnClickListener {
                activity?.popCurrentPage()
            }

            activity?.attachBackPressedCallback(this@ResultFragmentPhone.toString()) {
                if (resultOverlappingPanels.getSelectedPanel().ordinal == 1) {
                    runDefault()
                } else resultOverlappingPanels.closePanels()
            }

            resultMiniSync.setOnClickListener {
                if (resultOverlappingPanels.getSelectedPanel().ordinal == 1) {
                    resultOverlappingPanels.openStartPanel()
                } else resultOverlappingPanels.closePanels()
            }

            /*
            resultMiniSync.setRecycledViewPool(ImageAdapter.sharedPool)
            resultMiniSync.adapter = ImageAdapter(
                nextFocusDown = R.id.result_sync_set_score,
                clickCallback = { action ->
                    if (action == IMAGE_CLICK || action == IMAGE_LONG_CLICK) {
                        if (resultOverlappingPanels.getSelectedPanel().ordinal == 1) {
                            resultOverlappingPanels.openStartPanel()
                        } else resultOverlappingPanels.closePanels()
                    }
                })
            */
            resultSubscribe.setOnClickListener {
                viewModel.toggleSubscriptionStatus(context) { newStatus: Boolean? ->
                    if (newStatus == null) return@toggleSubscriptionStatus

                    val message = if (newStatus) {
                        // Kinda icky to have this here, but it works.
                        SubscriptionWorkManager.enqueuePeriodicWork(context)
                        R.string.subscription_new
                    } else {
                        R.string.subscription_deleted
                    }

                    val name = (viewModel.page.value as? Resource.Success)?.value?.title
                        ?: com.lagradost.cloudstream3.utils.txt(R.string.no_data)
                            .asStringNull(context) ?: ""
                    showToast(
                        com.lagradost.cloudstream3.utils.txt(message, name),
                        Toast.LENGTH_SHORT
                    )
                }
                context?.let { openBatteryOptimizationSettings(it) }
            }
            resultFavorite.setOnClickListener {
                viewModel.toggleFavoriteStatus(context) { newStatus: Boolean? ->
                    if (newStatus == null) return@toggleFavoriteStatus

                    val message = if (newStatus) {
                        R.string.favorite_added
                    } else {
                        R.string.favorite_removed
                    }

                    val name = (viewModel.page.value as? Resource.Success)?.value?.title
                        ?: com.lagradost.cloudstream3.utils.txt(R.string.no_data)
                            .asStringNull(context) ?: ""
                    showToast(
                        com.lagradost.cloudstream3.utils.txt(message, name),
                        Toast.LENGTH_SHORT
                    )
                }
            }
            mediaRouteButton.apply {
                val chromecastSupport = api?.hasChromecastSupport == true
                alpha = if (chromecastSupport) 1f else 0.3f
                if (!chromecastSupport) {
                    setOnClickListener {
                        showToast(
                            R.string.no_chromecast_support_toast,
                            Toast.LENGTH_LONG
                        )
                    }
                }
                activity?.let { act ->
                    if (act.isCastApiAvailable()) {
                        try {
                            CastButtonFactory.setUpMediaRouteButton(act, this)
                            CastContext.getSharedInstance(act.applicationContext) {
                                it.run()
                            }.addOnCompleteListener {
                                isGone = !it.isSuccessful
                            }
                            // this shit leaks for some reason
                            //castContext.addCastStateListener { state ->
                            //    media_route_button?.isGone = state == CastState.NO_DEVICES_AVAILABLE
                            //}
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }
                }
            }
        }

        playerBinding?.apply {
            playerOpenSource.setOnClickListener {
                currentTrailers.getOrNull(currentTrailerIndex)?.let { (_, ogTrailerLink) ->
                    context?.openBrowser(ogTrailerLink)
                }
            }
        }

        recommendationBinding?.apply {
            resultRecommendationsList.apply {
                spanCount = 3
                setRecycledViewPool(SearchAdapter.sharedPool)
                adapter =
                    SearchAdapter(
                        this,
                    ) { callback ->
                        SearchHelper.handleSearchClickCallback(callback)
                    }
            }
        }


        /*
        result_bookmark_button?.setOnClickListener {
            it.popupMenuNoIcons(
                items = WatchType.values()
                    .map { watchType -> Pair(watchType.internalId, watchType.stringRes) },
                //.map { watchType -> Triple(watchType.internalId, watchType.iconRes, watchType.stringRes) },
            ) {
                viewModel.updateWatchStatus(WatchType.fromInternalId(this.itemId))
            }
        }*/

        observeNullable(viewModel.resumeWatching) { resume ->
            resultBinding?.apply {
                if (resume == null) {
                    resultResumeParent.isVisible = false
                    resultPlayParent.isVisible = true
                    resultResumeProgressHolder.isVisible = false
                    return@observeNullable
                }
                resultResumeParent.isVisible = true
                resume.progress?.let { progress ->
                    resultNextSeriesButton.isVisible = false
                    val thumbUrl = resume.result.poster ?: (viewModel.page.value as? Resource.Success)?.value?.posterImage
                    if (!thumbUrl.isNullOrBlank()) {
                        resultResumeThumbnail.loadImage(thumbUrl)
                        resultResumeThumbnailCard.isVisible = true
                    } else {
                        resultResumeThumbnailCard.isVisible = false
                    }
                    resultResumeSeriesTitle.apply {
                        isVisible = !resume.isMovie
                        text =
                            if (resume.isMovie) null else context?.getNameFull(
                                resume.result.name,
                                resume.result.episode,
                                resume.result.season
                            )
                    }
                    if (resume.isMovie) {
                        resultPlayParent.isGone = true
                        resultResumeSeriesProgressText.isVisible = true
                        resultResumeSeriesProgressText.setText(progress.progressLeft)
                    }
                    resultResumeSeriesProgress.apply {
                        isVisible = true
                        this.max = progress.maxProgress
                        this.progress = progress.progress
                    }
                    resultResumeProgressHolder.isVisible = true
                } ?: run {
                    resultResumeProgressHolder.isVisible = false
                    if (!resume.isMovie) {
                        resultNextSeriesButton.isVisible = true
                        resultNextSeriesButton.text = "Play"
                    }
                    resultResumeSeriesProgress.isVisible = false
                    resultResumeSeriesTitle.isVisible = false
                    resultResumeSeriesProgressText.isVisible = false
                }

                resultResumeSeriesButton.setOnClickListener {
                    resumeAction(storedData, resume)
                }
                resultNextSeriesButton.setOnClickListener {
                    resumeAction(storedData, resume)
                }
            }
        }

        observeNullable(viewModel.subscribeStatus) { isSubscribed ->
            binding.resultSubscribe.isVisible = isSubscribed != null
            if (isSubscribed == null) return@observeNullable

            val drawable = if (isSubscribed) {
                R.drawable.ic_baseline_notifications_active_24
            } else {
                R.drawable.baseline_notifications_none_24
            }

            binding.resultSubscribe.setImageResource(drawable)
        }

        observeNullable(viewModel.favoriteStatus) { isFavorite ->
            binding.resultFavorite.isVisible = isFavorite != null
            if (isFavorite == null) return@observeNullable

            val drawable = if (isFavorite) {
                R.drawable.ic_baseline_favorite_24
            } else {
                R.drawable.ic_baseline_favorite_border_24
            }

            binding.resultFavorite.setImageResource(drawable)
        }

        observeNullable(viewModel.episodes) { episodes ->
            resultBinding?.apply {
                // no failure?
                resultEpisodeLoading.isVisible = episodes is Resource.Loading
                resultEpisodes.isVisible = episodes is Resource.Success
                resultEpisodesHeaderRow.isVisible =
                    episodes is Resource.Success && episodes.value.isNotEmpty()
                resultBatchDownloadButton.isVisible =
                    episodes is Resource.Success && episodes.value.isNotEmpty()

                if (episodes is Resource.Success) {
                    (resultEpisodes.adapter as? EpisodeAdapter)?.submitList(episodes.value)

                    // Show quality dialog with all sources
                    resultBatchDownloadButton.setOnLongClickListener {
                        ioSafe {
                            val defaultSources = QualityProfileDialog.getAllDefaultSources()
                            val activity = activity ?: return@ioSafe
                            activity.runOnUiThread {
                                QualityProfileDialog(
                                    activity,
                                    R.style.DialogFullscreenPlayer,
                                    defaultSources,
                                ).show()
                            }
                        }

                        true
                    }

                    resultBatchDownloadButton.setOnClickListener { view ->
                        val episodeStart =
                            episodes.value.firstOrNull()?.episode ?: return@setOnClickListener
                        val episodeEnd =
                            episodes.value.lastOrNull()?.episode ?: return@setOnClickListener

                        val episodeRange = if (episodeStart == episodeEnd) {
                            episodeStart.toString()
                        } else {
                            txt(
                                R.string.episodes_range,
                                episodeStart,
                                episodeEnd
                            ).asString(view.context)
                        }

                        val rangeMessage = txt(
                            R.string.download_episode_range,
                            episodeRange
                        ).asString(view.context)

                        AlertDialog.Builder(view.context, R.style.AlertDialogCustom)
                            .setTitle(R.string.download_all)
                            .setMessage(rangeMessage)
                            .setPositiveButton(R.string.yes) { _, _ ->
                                requirePathForActions(episodes.value.map { ACTION_DOWNLOAD_EPISODE to it })
                            }
                            .setNegativeButton(R.string.cancel) { _, _ -> }.show()
                    }
                }
            }
        }

        observeNullable(viewModel.movie) { data ->
            resultBinding?.apply {
                resultPlayMovie.isVisible = data is Resource.Success
                downloadButton.isVisible =
                    data is Resource.Success && viewModel.currentRepo?.api?.hasDownloadSupport == true

                (data as? Resource.Success)?.value?.let { (text, ep) ->
                    resultPlayMovie.text = "Play"
                    resultPlayMovie.setOnClickListener {
                        viewModel.handleAction(
                            EpisodeClickEvent(ACTION_CLICK_DEFAULT, ep)
                        )
                    }
                    resultPlayMovie.setOnLongClickListener {
                        viewModel.handleAction(
                            EpisodeClickEvent(ACTION_SHOW_OPTIONS, ep)
                        )
                        return@setOnLongClickListener true
                    }
                    resultResumeSeriesButton.setOnLongClickListener {
                        viewModel.handleAction(
                            EpisodeClickEvent(ACTION_SHOW_OPTIONS, ep)
                        )
                        return@setOnLongClickListener true
                    }

                    val status = VideoDownloadManager.downloadStatus[ep.id]
                    downloadButton.setStatus(status)
                    downloadButton.setDefaultClickListener(
                        DownloadObjects.DownloadEpisodeCached(
                            name = ep.name,
                            poster = ep.poster,
                            episode = 0,
                            season = null,
                            id = ep.id,
                            parentId = ep.id,
                            score = ep.score,
                            description = ep.description,
                            cacheTime = System.currentTimeMillis(),
                        ),
                        null
                    ) { click ->
                        context?.let { openBatteryOptimizationSettings(it) }

                        when (click.action) {
                            DOWNLOAD_ACTION_DOWNLOAD -> {
                                requirePathForActions(listOf(ACTION_DOWNLOAD_EPISODE to ep))
                            }

                            DOWNLOAD_ACTION_LONG_CLICK -> {
                                requirePathForActions(listOf(ACTION_DOWNLOAD_MIRROR to ep))
                            }

                            else -> DownloadButtonSetup.handleDownloadClick(click)
                        }
                    }
                }
            }
        }

        observe(viewModel.page) { data ->
            if (data == null) return@observe
            resultBinding?.apply {
                PanelsChildGestureRegionObserver.Provider.get().apply {
                    register(resultCastItems)
                    register(resultEpisodes)
                    register(resultOnpageRecommendations)
                }
                (data as? Resource.Success)?.value?.let { d ->
                    // Atmospheric blurred backdrop transition
                    val backdropUrl = d.posterBackgroundImage ?: d.posterImage
                    updateAtmosphericBackdrop(backdropUrl, d.posterHeaders)

                    // Ambient Hero Video Loop: Check for direct raw MP4 trailer
                    val rawResp = viewModel.getCurrentResponse()
                    val ambientTrailer = rawResp?.trailers?.firstOrNull { it.raw }
                    if (ambientTrailer != null && !ambientTrailer.extractorUrl.isNullOrBlank()) {
                        playAmbientVideo(ambientTrailer.extractorUrl, ambientTrailer.headers)
                    }

                    // Launch TMDB Enrichment Coroutine
                    viewLifecycleOwner.lifecycleScope.launch {
                        val resp = viewModel.getCurrentResponse()
                        val enriched = if (resp != null) {
                            TvTmdbEnricher.enrich(resp)
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
                            if (d.logoUrl.isNullOrBlank() && !enriched.logoUrl.isNullOrBlank()) {
                                bindLogo(
                                    url = enriched.logoUrl,
                                    headers = null,
                                    titleView = resultTitle,
                                    logoView = backgroundPosterWatermarkBadge
                                )
                            }

                            if (!enriched.yearSpan.isNullOrBlank()) {
                                resultMetaYear.text = enriched.yearSpan
                                resultMetaYear.isVisible = true
                            }
                            if (!enriched.contentRating.isNullOrBlank()) {
                                resultMetaContentRating.text = enriched.contentRating
                                resultMetaContentRating.isVisible = true
                            }
                            if (!enriched.tmdbRating.isNullOrBlank()) {
                                resultMetaRating.text = enriched.tmdbRating
                                resultMetaRating.isVisible = true
                            }

                            val directTrailer = resp?.trailers?.firstOrNull { it.raw }
                            if (directTrailer == null && !enriched.trailerStreamUrl.isNullOrBlank() && trailerVideoHelper?.isPlaying() != true) {
                                playAmbientVideo(enriched.trailerStreamUrl, enriched.trailerHeaders)
                            }
                        }
                    }
                    resultVpn.setText(d.vpnText)
                    resultInfo.setText(d.metaText)
                    resultNoEpisodes.setText(d.noEpisodesFoundText)
                    resultTitle.setText(d.titleText)
                    resultMetaSite.setText(d.apiName)
                    resultMetaType.setText(d.typeText)
                    val cleanYear = d.yearText?.asStringNull(context)?.trim()
                    resultMetaYear.text = cleanYear
                    resultMetaYear.isVisible = !cleanYear.isNullOrBlank()
                    resultMetaDuration.setText(d.durationText)
                    val cleanRating = d.ratingText?.asStringNull(context)?.replace("★", "")?.replace("Rated:", "")?.trim()
                    resultMetaRating.text = cleanRating
                    resultMetaRating.isVisible = !cleanRating.isNullOrBlank()
                    resultMetaStatus.setText(d.onGoingText)
                    val cleanContentRating = d.contentRatingText?.asStringNull(context)?.trim()
                    resultMetaContentRating.text = cleanContentRating
                    resultMetaContentRating.isVisible = !cleanContentRating.isNullOrBlank()
                    resultCastText.setText(d.actorsText)
                    resultNextAiring.setText(d.nextAiringEpisode)
                    resultNextAiringTime.setText(d.nextAiringDate)
                    resultPoster.loadImage(d.posterImage, headers = d.posterHeaders) {
                        error {
                            getImageFromDrawable(
                                context ?: return@error null,
                                R.drawable.default_cover
                            )
                        }
                    }
                    resultPosterBackground.loadImage(
                        d.posterBackgroundImage,
                        headers = d.posterHeaders
                    ) {
                        error {
                            getImageFromDrawable(
                                context ?: return@error null,
                                R.drawable.default_cover
                            )
                        }
                    }

                    bindLogo(
                        url = d.logoUrl,
                        headers = d.posterHeaders,
                        titleView = resultTitle,
                        logoView = backgroundPosterWatermarkBadge
                    )

                    var isExpanded = false
                    resultDescription.apply {
                        setTextHtml(d.plotText)
                        setOnClickListener {
                            isExpanded = !isExpanded
                            maxLines = if (isExpanded) {
                                Integer.MAX_VALUE
                            } else 10
                        }
                    }

                    populateChips(resultTag, d.tags)

                    resultComingSoon.isVisible = d.comingSoon
                    resultDataHolder.isGone = d.comingSoon

                    val prefs =
                        androidx.preference.PreferenceManager.getDefaultSharedPreferences(root.context)
                    val showCast = prefs.getBoolean(
                        root.context.getString(R.string.show_cast_in_details_key),
                        true
                    )

                    resultCastItems.isGone = !showCast || d.actors.isNullOrEmpty()
                    (resultCastItems.adapter as? ActorAdaptor)?.submitList(if (showCast) d.actors else emptyList())

                    if (d.contentRatingText == null) {
                        // If there is no rating to display, we don't want an empty gap
                        resultMetaContentRating.width = 0
                    }

                    if (syncModel.addSyncs(d.syncData)) {
                        syncModel.updateMetaAndUser()
                        syncModel.updateSynced()
                    } else {
                        syncModel.addFromUrl(d.url)
                    }

                    binding.apply {
                        resultSearch.isGone = d.title.isBlank()
                        resultSearch.setOnClickListener {
                            QuickSearchFragment.pushSearch(activity, d.title)
                        }

                        resultShare.setOnClickListener {
                            try {
                                val i = Intent(Intent.ACTION_SEND)
                                val nameBase64 =
                                    base64Encode(d.apiName.toString().toByteArray(Charsets.UTF_8))
                                val urlBase64 = base64Encode(d.url.toByteArray(Charsets.UTF_8))
                                val encodedUri = URLEncoder.encode(
                                    "$APP_STRING_SHARE:$nameBase64?$urlBase64",
                                    "UTF-8"
                                )
                                val redirectUrl =
                                    "https://recloudstream.github.io/csredirect?redirectto=$encodedUri"
                                i.type = "text/plain"
                                i.putExtra(Intent.EXTRA_SUBJECT, d.title)
                                i.putExtra(Intent.EXTRA_TEXT, redirectUrl)
                                startActivity(Intent.createChooser(i, d.title))
                            } catch (e: Exception) {
                                logError(e)
                            }
                        }
                        setUrl(d.url)
                        resultBookmarkFab.isVisible = false
                    }
                }

                (data as? Resource.Failure)?.let { data ->
                    @SuppressLint("SetTextI18n")
                    resultErrorText.text = storedData.url.plus("\n") + data.errorString
                }

                binding.resultBookmarkFab.isVisible = false
                resultFinishLoading.isVisible = data is Resource.Success

                resultLoading.isVisible = data is Resource.Loading

                resultLoadingError.isVisible = data is Resource.Failure
                resultErrorText.isVisible = data is Resource.Failure
                resultReloadConnectionOpenInBrowser.isVisible = data is Resource.Failure

                resultTitle.setOnLongClickListener {
                    clipboardHelper(
                        com.lagradost.cloudstream3.utils.txt(R.string.title),
                        resultTitle.text
                    )
                    true
                }
            }
        }

        observeNullable(viewModel.episodesCountText) { count ->
            resultBinding?.resultEpisodesText.setText(count)
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

        observe(viewModel.trailers) { trailers ->
            setTrailers(trailers.flatMap { it.mirros }) // I don't care about subtitles yet!
        }

        observe(syncModel.synced) { list ->
            syncBinding?.resultSyncNames?.text =
                list.filter { it.isSynced && it.hasAccount }.joinToString { it.name }

            val newList = list.filter { it.isSynced && it.hasAccount }

            binding.resultMiniSync.isVisible = newList.isNotEmpty()
        }


        var currentSyncProgress = 0
        fun setSyncMaxEpisodes(totalEpisodes: Int?) {
            syncBinding?.resultSyncEpisodes?.max = (totalEpisodes ?: 0) * 1000

            safe {
                val ctx = syncBinding?.resultSyncEpisodes?.context
                syncBinding?.resultSyncMaxEpisodes?.text =
                    totalEpisodes?.let { episodes ->
                        ctx?.getString(R.string.sync_total_episodes_some)?.format(episodes)
                    } ?: run {
                        ctx?.getString(R.string.sync_total_episodes_none)
                    }
            }
        }
        observe(syncModel.metadata) { meta ->
            when (meta) {
                is Resource.Success -> {
                    val d = meta.value
                    syncBinding?.resultSyncEpisodes?.progress = currentSyncProgress * 1000
                    setSyncMaxEpisodes(d.totalEpisodes)

                    viewModel.setMeta(d, syncModel.getSyncs())
                }

                is Resource.Loading -> {
                    syncBinding?.resultSyncMaxEpisodes?.text =
                        syncBinding?.resultSyncMaxEpisodes?.context?.getString(R.string.sync_total_episodes_none)
                }

                else -> {}
            }
        }


        observe(syncModel.userData) { status ->
            var closed = false
            syncBinding?.apply {
                when (status) {
                    is Resource.Failure -> {
                        resultSyncLoadingShimmer.stopShimmer()
                        resultSyncLoadingShimmer.isVisible = false
                        resultSyncHolder.isVisible = false
                        closed = true
                    }

                    is Resource.Loading -> {
                        resultSyncLoadingShimmer.startShimmer()
                        resultSyncLoadingShimmer.isVisible = true
                        resultSyncHolder.isVisible = false
                    }

                    is Resource.Success -> {
                        resultSyncLoadingShimmer.stopShimmer()
                        resultSyncLoadingShimmer.isVisible = false
                        resultSyncHolder.isVisible = true

                        val d = status.value
                        val desiredScore = d.score?.toFloat(1) ?: 0.0f
                        val totalSteps = (resultSyncRating.valueTo / resultSyncRating.stepSize)
                        val desiredStep = (totalSteps * desiredScore).roundToInt()
                        resultSyncRating.value = desiredStep * resultSyncRating.stepSize

                        resultSyncCheck.setItemChecked(d.status.internalId + 1, true)
                        val watchedEpisodes = d.watchedEpisodes ?: 0
                        currentSyncProgress = watchedEpisodes

                        d.maxEpisodes?.let {
                            // don't directly call it because we don't want to override metadata observe
                            setSyncMaxEpisodes(it)
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            resultSyncEpisodes.setProgress(watchedEpisodes * 1000, true)
                        } else {
                            resultSyncEpisodes.progress = watchedEpisodes * 1000
                        }
                        resultSyncCurrentEpisodes.text =
                            Editable.Factory.getInstance()?.newEditable(watchedEpisodes.toString())
                        safe { // format might fail
                            val text = d.score?.toFloat(10)?.roundToInt()?.let {
                                context?.getString(R.string.sync_score_format)?.format(it)
                            } ?: "?"
                            resultSyncScoreText.text = text
                        }
                    }

                    null -> {
                        closed = false
                    }
                }
            }
            binding.resultOverlappingPanels.setStartPanelLockState(if (closed) OverlappingPanelsLayout.LockState.CLOSE else OverlappingPanelsLayout.LockState.UNLOCKED)
        }
        observe(viewModel.recommendations) { recommendations ->
            setRecommendations(recommendations, null)
        }
        context?.let { ctx ->
            val arrayAdapter = ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
            /*
            -1 -> None
            0 -> Watching
            1 -> Completed
            2 -> OnHold
            3 -> Dropped
            4 -> PlanToWatch
            5 -> ReWatching
            */
            val items = listOf(
                R.string.none,
                R.string.type_watching,
                R.string.type_completed,
                R.string.type_on_hold,
                R.string.type_dropped,
                R.string.type_plan_to_watch,
                R.string.type_re_watching
            ).map { ctx.getString(it) }
            arrayAdapter.addAll(items)
            syncBinding?.apply {
                resultSyncCheck.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                resultSyncCheck.adapter = arrayAdapter
                setListViewHeightBasedOnItems(resultSyncCheck)

                resultSyncCheck.setOnItemClickListener { _, _, which, _ ->
                    syncModel.setStatus(which - 1)
                }

                resultSyncRating.addOnChangeListener { it, value, fromUser ->
                    if (fromUser) syncModel.setScore(Score.from(value, it.valueTo.roundToInt()))
                }

                resultSyncAddEpisode.setOnClickListener {
                    syncModel.setEpisodesDelta(1)
                }

                resultSyncSubEpisode.setOnClickListener {
                    syncModel.setEpisodesDelta(-1)
                }

                resultSyncCurrentEpisodes.doOnTextChanged { text, _, before, count ->
                    if (count == before) return@doOnTextChanged
                    text?.toString()?.toIntOrNull()?.let { ep ->
                        syncModel.setEpisodes(ep)
                    }
                }
            }
        }

        syncBinding?.resultSyncSetScore?.setOnClickListener {
            syncModel.publishUserData()
        }

        observe(viewModel.watchStatus) { watchType ->
            resultBinding?.resultBookmarkButton?.apply {
                val isSaved = watchType != WatchType.NONE
                text = if (isSaved) "Saved" else "Save"
                val iconRes = if (isSaved) R.drawable.ic_baseline_bookmark_24 else R.drawable.ic_baseline_bookmark_border_24
                setIconResource(iconRes)
                val tintColor = if (isSaved) {
                    context.colorFromAttribute(R.attr.colorPrimary)
                } else {
                    0xFFE9EAEE.toInt()
                }
                val colorState = ColorStateList.valueOf(tintColor)
                iconTint = colorState
                setTextColor(colorState)

                setOnClickListener { btn ->
                    activity?.showBottomDialog(
                        WatchType.entries.map { btn.context.getString(it.stringRes) }.toList(),
                        watchType.ordinal,
                        btn.context.getString(R.string.action_add_to_bookmarks),
                        showApply = false,
                        {}) {
                        viewModel.updateWatchStatus(WatchType.entries[it], context)
                    }
                }
            }

            binding.resultBookmarkFab.apply {
                setText(watchType.stringRes)
                if (watchType == WatchType.NONE) {
                    context?.colorFromAttribute(R.attr.white)
                } else {
                    context?.colorFromAttribute(R.attr.colorPrimary)
                }?.let {
                    val colorState = ColorStateList.valueOf(it)
                    iconTint = colorState
                    setTextColor(colorState)
                }

                setOnClickListener { fab ->
                    activity?.showBottomDialog(
                        WatchType.entries.map { fab.context.getString(it.stringRes) }.toList(),
                        watchType.ordinal,
                        fab.context.getString(R.string.action_add_to_bookmarks),
                        showApply = false,
                        {}) {
                        viewModel.updateWatchStatus(WatchType.entries[it], context)
                    }
                }
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
                    @SuppressLint("SetTextI18n")
                    text = "${context.getString(R.string.skip_loading)} (${load.linksLoaded})"
                }
            }
        }

        observeNullable(viewModel.selectedSeason) { text ->
            resultBinding?.apply {
                resultSeasonButton.setText(text)

                selectSeason =
                    text?.asStringNull(resultSeasonButton.context)
                // If the season button is visible the result season button will be next focus down
                if (resultSeasonButton.isVisible && resultResumeParent.isVisible) {
                    setFocusUpAndDown(resultResumeSeriesButton, resultSeasonButton)
                }
            }
        }

        observeNullable(viewModel.selectedDubStatus) { status ->
            resultBinding?.apply {
                resultDubSelect.setText(status)

                if (resultDubSelect.isVisible && !resultSeasonButton.isVisible && !resultEpisodeSelect.isVisible && resultResumeParent.isVisible) {
                    setFocusUpAndDown(resultResumeSeriesButton, resultDubSelect)
                }
            }
        }
        observeNullable(viewModel.selectedRange) { range ->
            resultBinding?.apply {
                resultEpisodeSelect.setText(range)

                selectEpisodeRange = range?.asStringNull(resultEpisodeSelect.context)
                // If Season button is invisible then the bookmark button next focus is episode select
                if (resultEpisodeSelect.isVisible && !resultSeasonButton.isVisible && resultResumeParent.isVisible) {
                    setFocusUpAndDown(resultResumeSeriesButton, resultEpisodeSelect)
                }
            }
        }

//        val preferDub = context?.getApiDubstatusSettings()?.all { it == DubStatus.Dubbed } == true

        observe(viewModel.dubSubSelections) { range ->
            resultBinding?.resultDubSelect?.setOnClickListener { view ->
                view?.context?.let { ctx ->
                    val names = range.mapNotNull { (text, status) ->
                        status to (text?.asStringNull(ctx) ?: return@mapNotNull null)
                    }
                    val currentDub = viewModel.selectedDubStatus.value?.asStringNull(ctx)
                    activity?.showBottomDialog(
                        names.map { it.second },
                        names.indexOfFirst { it.second == currentDub }.coerceAtLeast(0),
                        "Audio / Dub",
                        false,
                        {}) { itemId ->
                        viewModel.changeDubStatus(names[itemId].first)
                    }
                }
            }
        }

        observe(viewModel.rangeSelections) { range ->
            resultBinding?.resultEpisodeSelect?.setOnClickListener { view ->
                view?.context?.let { ctx ->
                    val names = range
                        .mapNotNull { (text, r) ->
                            r to (text?.asStringNull(ctx) ?: return@mapNotNull null)
                        }

                    activity?.showBottomDialog(
                        names.map { it.second },
                        names.indexOfFirst { it.second == selectEpisodeRange }.coerceAtLeast(0),
                        ctx.getString(R.string.episodes),
                        false,
                        {}) { itemId ->
                        viewModel.changeRange(names[itemId].first)
                    }
                }
            }
        }

        observe(viewModel.seasonSelections) { seasonList ->
            resultBinding?.resultSeasonButton?.setOnClickListener { view ->

                view?.context?.let { ctx ->
                    val names = seasonList
                        .mapNotNull { (text, r) ->
                            r to (text?.asStringNull(ctx) ?: return@mapNotNull null)
                        }

                    activity?.showBottomDialog(
                        names.map { it.second },
                        names.indexOfFirst { it.second == selectSeason }.coerceAtLeast(0),
                        ctx.getString(R.string.season),
                        false,
                        {}) { itemId ->
                        viewModel.changeSeason(names[itemId].first)
                    }
                }
            }
        }
    }

    private fun resumeAction(
        storedData: ResultFragment.StoredData,
        resume: ResumeWatchingStatus
    ) {
        viewModel.handleAction(
            EpisodeClickEvent(
                storedData.playerAction, //?: ACTION_PLAY_EPISODE_IN_PLAYER,
                resume.result
            )
        )
    }

    private fun setRecommendations(rec: List<SearchResponse>?, validApiName: String?) {
        val isInvalid = rec.isNullOrEmpty()
        val matchAgainst = validApiName ?: rec?.firstOrNull()?.apiName

        recommendationBinding?.apply {
            root.isGone = isInvalid
            root.post {
                rec?.let { list ->
                    (resultRecommendationsList.adapter as? SearchAdapter)?.submitList(list.filter { it.apiName == matchAgainst })
                }
            }
        }

        resultBinding?.apply {
            resultOnpageRecommendationsHolder.isGone = isInvalid
            val filtered = if (matchAgainst != null) rec?.filter { it.apiName == matchAgainst } else rec
            (resultOnpageRecommendations.adapter as? HomeChildItemAdapter)?.submitList(filtered ?: emptyList())
        }


        binding?.apply {
            // Recommendation pill button is removed; on-page slider handles this.
            resultRecommendationsBtt.isGone = true
            // Always keep the end swipe panel locked closed
            resultOverlappingPanels.setEndPanelLockState(OverlappingPanelsLayout.LockState.CLOSE)
        }
    }
}
