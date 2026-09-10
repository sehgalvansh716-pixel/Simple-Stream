package com.lagradost.cloudstream3.ui.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.DialogInterface
import android.view.inputmethod.InputMethodManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.graphics.Outline
import android.view.ViewOutlineProvider
import android.os.Build
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.content.ContextCompat
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKeys
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.FragmentSearchBinding
import com.lagradost.cloudstream3.databinding.HomeSelectMainpageBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.BaseAdapter
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.home.HomeFragment
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.bindChips
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.currentSpan
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.loadHomepageList
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.updateChips
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.AppContextUtils.filterSearchResultByFilmQuality
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiProviderLangSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.ownHide
import com.lagradost.cloudstream3.utils.AppContextUtils.ownShow
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.ui.utils.TvAmbientVideoHelper
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock

class SearchFragment : BaseFragment<FragmentSearchBinding>(
    BaseFragment.BindingCreator.Bind(FragmentSearchBinding::bind)
) {
    companion object {
        fun List<SearchResponse>.filterSearchResponse(): List<SearchResponse> {
            return this.filter { response ->
                if (response is AnimeSearchResponse) {
                    val status = response.dubStatus
                    (status.isNullOrEmpty()) || (status.any {
                        APIRepository.dubStatusActive.contains(it)
                    })
                } else {
                    true
                }
            }
        }

        const val SEARCH_QUERY = "search_query"

        fun newInstance(query: String): Bundle {
            return Bundle().apply {
                if (query.isNotBlank()) putString(SEARCH_QUERY, query)
            }
        }
    }

    private val searchViewModel: SearchViewModel by activityViewModels()
    private val homeViewModel: HomeViewModel by activityViewModels()
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var tvAmbientVideoHelper: TvAmbientVideoHelper? = null
    private var isSearchCardsFocused = false

    private fun setSearchCardsFocusedState(focused: Boolean) {
        isSearchCardsFocused = focused
    }

    private fun initTvAmbientVideo(rootView: View) {
        val textureView = rootView.findViewById<TextureView>(R.id.tv_search_video) ?: return
        tvAmbientVideoHelper?.release()
        tvAmbientVideoHelper = TvAmbientVideoHelper(rootView.context).apply {
            attach(textureView, R.raw.tv_search_bg, autoPlay = true)
        }
    }

    private fun loadDefaultTrendingItems() {
        val rootView = view ?: return
        val sectionTitle = rootView.findViewById<TextView>(R.id.tv_search_section_title)
        val searchEmpty = rootView.findViewById<TextView>(R.id.tv_search_empty)

        sectionTitle?.setText(R.string.tv_trending_today)
        searchEmpty?.isVisible = false

        val pageData = (homeViewModel.page.value as? Resource.Success)?.value
        val defaultItems = pageData?.values?.firstOrNull()?.list?.list
        if (!defaultItems.isNullOrEmpty()) {
            (binding?.searchAutofitResults?.adapter as? SearchAdapter)?.submitList(defaultItems)
        } else {
            val api = (homeViewModel.apiName.value?.let { APIHolder.getApiFromNameNull(it) })
                ?: allProviders.firstOrNull { it.hasMainPage }
            if (api != null && api.hasMainPage) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val repo = APIRepository(api)
                        val res = repo.getMainPage(1, null)
                        if (res is Resource.Success) {
                            val firstList = res.value.firstOrNull()?.items?.firstOrNull()?.list
                            if (!firstList.isNullOrEmpty()) {
                                withContext(Dispatchers.Main) {
                                    (binding?.searchAutofitResults?.adapter as? SearchAdapter)?.submitList(firstList)
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    private val speechRecognizerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    binding?.mainSearch?.setQuery(recognizedText, true)
                }
            }
        }

    override fun pickLayout(): Int? =
        if (isLayout(TV or EMULATOR)) R.layout.fragment_search_tv else R.layout.fragment_search

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        bottomSheetDialog?.ownShow()
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onDestroyView() {
        hideKeyboard()
        bottomSheetDialog?.ownHide()
        tvAmbientVideoHelper?.release()
        tvAmbientVideoHelper = null
        activity?.detachBackPressedCallback("SearchFragment")
        activity?.detachBackPressedCallback("SearchFragmentTv")
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        afterPluginsLoadedEvent += ::reloadRepos
        tvAmbientVideoHelper?.play()
    }

    override fun onPause() {
        tvAmbientVideoHelper?.pause()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        afterPluginsLoadedEvent -= ::reloadRepos
    }

    var selectedSearchTypes = mutableListOf<TvType>()
    var selectedApis = mutableSetOf<String>()

    /**
     * Will filter all providers by preferred media and selectedSearchTypes.
     * If that results in no available providers then only filter
     * providers by preferred media
     **/
    fun search(query: String?) {
        if (query == null) return
        // don't resume state from prev search
        (binding?.searchMasterRecycler?.adapter as? BaseAdapter<*, *>)?.clearState()
        context?.let { ctx ->
            val default = enumValues<TvType>().sorted().filter { it != TvType.NSFW }
                .map { it.ordinal.toString() }.toSet()
            val preferredTypes = (PreferenceManager.getDefaultSharedPreferences(ctx)
                .getStringSet(this.getString(R.string.prefer_media_type_key), default)
                ?.ifEmpty { default } ?: default)
                .mapNotNull { it.toIntOrNull() ?: return@mapNotNull null }

            val settings = ctx.getApiSettings()

            val notFilteredBySelectedTypes = selectedApis.filter { name ->
                settings.contains(name)
            }.map { name ->
                name to getApiFromNameNull(name)?.supportedTypes
            }.filter { (_, types) ->
                types?.any { preferredTypes.contains(it.ordinal) } == true
            }

            searchViewModel.searchAndCancel(
                query = query,
                providersActive = notFilteredBySelectedTypes.filter { (_, types) ->
                    types?.any { selectedSearchTypes.contains(it) } == true
                }.ifEmpty { notFilteredBySelectedTypes }.map { it.first }.toSet()
            )
        }
    }

    // Null if defined as a variable
    // This needs to be run after view created

    private fun reloadRepos(success: Boolean = false) = main {
        searchViewModel.reloadRepos()
        context?.filterProviderByPreferredMedia()?.let { validAPIs ->
            bindChips(
                binding?.tvtypesChipsScroll?.tvtypesChips,
                selectedSearchTypes,
                validAPIs.flatMap { api -> api.supportedTypes }.distinct()
            ) { list ->
                if (selectedSearchTypes.toSet() != list.toSet()) {
                    DataStoreHelper.searchPreferenceTags = list
                    selectedSearchTypes.clear()
                    selectedSearchTypes.addAll(list)
                    search(binding?.mainSearch?.query?.toString())
                }
            }
        }
    }

    override fun fixLayout(view: View) {
        val isTv = isLayout(TV or EMULATOR)
        if (!isTv) {
            fixSystemBarsPadding(
                view,
                padBottom = isLandscape(),
                padLeft = false
            )
        }

        // Fix grid: 5 columns on TV for reference mockup parity, phone/tablet uses getSpanCount()
        currentSpan = if (isTv) 5 else view.context.getSpanCount()
        binding?.searchAutofitResults?.spanCount = currentSpan
        HomeFragment.configEvent.invoke()
    }

    override fun onBindingCreated(
        binding: FragmentSearchBinding,
        savedInstanceState: Bundle?
    ) {
        val isTv = isLayout(TV or EMULATOR)
        reloadRepos()
        initTvAmbientVideo(binding.root)
        if (isTv) {
            binding.root.findViewById<View>(R.id.tv_search_back)?.apply {
                setOnClickListener {
                    activity?.onBackPressedDispatcher?.onBackPressed()
                }
                setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> {
                                val searchInput = binding.searchRoot.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
                                if (searchInput?.requestFocus() != true) {
                                    binding.mainSearch.requestFocus()
                                }
                                return@setOnKeyListener true
                            }
                        }
                    }
                    false
                }
            }
        } else {
            try {
                val windowBackground = activity?.window?.decorView?.background
                val blurAlgorithm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    eightbitlab.com.blurview.RenderEffectBlur()
                } else {
                    eightbitlab.com.blurview.RenderScriptBlur(requireContext())
                }

                binding.root.findViewById<eightbitlab.com.blurview.BlurView>(R.id.search_bar_blur)?.let { blurView ->
                    blurView.outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, 22.toPx.toFloat())
                        }
                    }
                    blurView.clipToOutline = true
                    blurView.setupWith(binding.root, blurAlgorithm)
                        .setFrameClearDrawable(windowBackground)
                        .setBlurRadius(16f)
                        .setOverlayColor(Color.TRANSPARENT)
                        .setBlurAutoUpdate(true)
                }

                val searchHeaderGroup = binding.root.findViewById<View>(R.id.search_header_group)
                if (!isTv && searchHeaderGroup != null) {
                    ViewCompat.setOnApplyWindowInsetsListener(searchHeaderGroup) { v, windowInsets ->
                        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                        v.updatePadding(top = insets.top + 16.toPx, bottom = 18.toPx)
                        windowInsets
                    }
                }
            } catch (t: Throwable) {
                logError(t)
            }
        }

        binding.apply {
            val adapter =
                SearchAdapter(
                    searchAutofitResults,
                ) { callback ->
                    if (isTv && callback.action == SEARCH_ACTION_FOCUSED) {
                        setSearchCardsFocusedState(true)
                    } else if (isTv && callback.action == SEARCH_ACTION_DPAD_UP_TOP_ROW) {
                        setSearchCardsFocusedState(false)
                        val historyCount = searchHistoryRecycler.adapter?.itemCount ?: 0
                        if (historyCount > 0 && searchHistoryRecycler.isVisible) {
                            searchHistoryRecycler.requestFocus()
                        } else {
                            val searchInput = searchRoot.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
                            searchInput?.requestFocus() ?: mainSearch.requestFocus()
                        }
                    } else {
                        SearchHelper.handleSearchClickCallback(callback)
                    }
                }

            searchRoot.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)?.let { searchInput ->
                if (!isTv) {
                    searchInput.tag = "tv_no_focus_tag"
                } else {
                    searchInput.isFocusable = true
                    searchInput.isFocusableInTouchMode = true
                    searchInput.nextFocusUpId = R.id.tv_nav_search
                    searchHistoryRecycler.nextFocusUpId = androidx.appcompat.R.id.search_src_text
                    val tvSearchBar = searchRoot.findViewById<View>(R.id.tv_search_bar)
                    searchInput.setOnFocusChangeListener { _, hasFocus ->
                        tvSearchBar?.isActivated = hasFocus
                    }
                    searchInput.setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    val historyCount = searchHistoryRecycler.adapter?.itemCount ?: 0
                                    if (historyCount > 0 && searchHistoryRecycler.isVisible) {
                                        searchHistoryRecycler.requestFocus()
                                        return@setOnKeyListener true
                                    } else if (searchAutofitResults.isVisible && (searchAutofitResults.adapter?.itemCount ?: 0) > 0) {
                                        val firstCard = searchAutofitResults.findViewHolderForAdapterPosition(0)?.itemView
                                        if (firstCard != null) {
                                            firstCard.requestFocus()
                                        } else {
                                            searchAutofitResults.scrollToPosition(0)
                                            searchAutofitResults.post {
                                                searchAutofitResults.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                                                    ?: searchAutofitResults.requestFocus()
                                            }
                                        }
                                        return@setOnKeyListener true
                                    }
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                    imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)
                                    val navSearch = activity?.findViewById<View>(R.id.tv_nav_search)
                                    if (navSearch != null) {
                                        navSearch.requestFocus()
                                        return@setOnKeyListener true
                                    }
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    val backBtn = searchRoot.findViewById<View>(R.id.tv_search_back)
                                    if (backBtn != null && (searchInput.selectionStart == 0 || searchInput.text.isNullOrEmpty())) {
                                        backBtn.requestFocus()
                                        return@setOnKeyListener true
                                    }
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    val textLen = searchInput.text?.length ?: 0
                                    if (searchInput.selectionEnd == textLen || searchInput.text.isNullOrEmpty()) {
                                        val exitIcon = binding.mainSearch.findViewById<View>(androidx.appcompat.R.id.search_close_btn)
                                        if (exitIcon != null && exitIcon.isVisible) {
                                            exitIcon.requestFocus()
                                            return@setOnKeyListener true
                                        } else {
                                            binding.voiceSearch.requestFocus()
                                            return@setOnKeyListener true
                                        }
                                    }
                                }
                            }
                        }
                        false
                    }
                }
            }
            if (isTv) {
                searchAutofitResults.spanCount = 4
                searchAutofitResults.layoutManager = androidx.recyclerview.widget.GridLayoutManager(context, 4)
            }
            searchAutofitResults.setHasFixedSize(true)
            searchAutofitResults.setItemViewCacheSize(10)
            searchAutofitResults.setRecycledViewPool(SearchAdapter.sharedPool)
            searchAutofitResults.adapter = adapter
            searchLoadingBar.alpha = 0f
        }

        val searchExitIcon =
            binding.mainSearch.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)

        if (isTv) {
            val tvSearchBar = binding.searchRoot.findViewById<View>(R.id.tv_search_bar)

            if (searchExitIcon != null) {
                searchExitIcon.apply {
                    background = ContextCompat.getDrawable(context, R.drawable.bg_tv_search_icon_btn)
                    imageTintList = ContextCompat.getColorStateList(context, R.color.color_tv_search_icon_tint)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(5.toPx, 5.toPx, 5.toPx, 5.toPx)
                    val lp = layoutParams
                    if (lp != null) {
                        lp.width = 28.toPx
                        lp.height = 28.toPx
                        if (lp is LinearLayout.LayoutParams) {
                            lp.gravity = Gravity.CENTER_VERTICAL
                            lp.setMargins(0, 0, 4.toPx, 0)
                        }
                        layoutParams = lp
                    }
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnFocusChangeListener { _, hasFocus ->
                        tvSearchBar?.isActivated = hasFocus
                        if (hasFocus) {
                            setSearchCardsFocusedState(false)
                        }
                    }
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    val searchInput = binding.searchRoot.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
                                    if (searchInput != null) {
                                        searchInput.requestFocus()
                                        return@setOnKeyListener true
                                    }
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    binding.voiceSearch.requestFocus()
                                    return@setOnKeyListener true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    val navSearch = activity?.findViewById<View>(R.id.tv_nav_search)
                                    if (navSearch != null) {
                                        navSearch.requestFocus()
                                        return@setOnKeyListener true
                                    }
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    val historyCount = binding.searchHistoryRecycler.adapter?.itemCount ?: 0
                                    if (historyCount > 0 && binding.searchHistoryRecycler.isVisible) {
                                        binding.searchHistoryRecycler.requestFocus()
                                        return@setOnKeyListener true
                                    } else if (binding.searchAutofitResults.isVisible && (binding.searchAutofitResults.adapter?.itemCount ?: 0) > 0) {
                                        setSearchCardsFocusedState(true)
                                        binding.searchAutofitResults.requestFocus()
                                        return@setOnKeyListener true
                                    }
                                }
                            }
                        }
                        false
                    }
                    setOnClickListener {
                        binding.mainSearch.setQuery("", false)
                        loadDefaultTrendingItems()
                        val searchInput = binding.searchRoot.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
                        searchInput?.requestFocus()
                    }
                }
            }

            binding.voiceSearch.setOnFocusChangeListener { _, hasFocus ->
                tvSearchBar?.isActivated = hasFocus
                if (hasFocus) setSearchCardsFocusedState(false)
            }
            binding.searchFilter.setOnFocusChangeListener { _, hasFocus ->
                tvSearchBar?.isActivated = hasFocus
                if (hasFocus) setSearchCardsFocusedState(false)
            }
            binding.voiceSearch.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            val navSearch = activity?.findViewById<View>(R.id.tv_nav_search)
                            if (navSearch != null) {
                                navSearch.requestFocus()
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (searchExitIcon?.isVisible == true) {
                                searchExitIcon.requestFocus()
                                return@setOnKeyListener true
                            } else {
                                val searchInput = binding.searchRoot.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
                                searchInput?.requestFocus() ?: binding.mainSearch.requestFocus()
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            val historyCount = binding.searchHistoryRecycler.adapter?.itemCount ?: 0
                            if (historyCount > 0 && binding.searchHistoryRecycler.isVisible) {
                                binding.searchHistoryRecycler.requestFocus()
                                return@setOnKeyListener true
                            } else if (binding.searchAutofitResults.isVisible && (binding.searchAutofitResults.adapter?.itemCount ?: 0) > 0) {
                                binding.searchAutofitResults.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                                    ?: binding.searchAutofitResults.requestFocus()
                                return@setOnKeyListener true
                            }
                        }
                    }
                }
                false
            }
            binding.searchFilter.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            val navSearch = activity?.findViewById<View>(R.id.tv_nav_search)
                            if (navSearch != null) {
                                navSearch.requestFocus()
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            val historyCount = binding.searchHistoryRecycler.adapter?.itemCount ?: 0
                            if (historyCount > 0 && binding.searchHistoryRecycler.isVisible) {
                                binding.searchHistoryRecycler.requestFocus()
                                return@setOnKeyListener true
                            } else if (binding.searchAutofitResults.isVisible && (binding.searchAutofitResults.adapter?.itemCount ?: 0) > 0) {
                                binding.searchAutofitResults.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                                    ?: binding.searchAutofitResults.requestFocus()
                                return@setOnKeyListener true
                            }
                        }
                    }
                }
                false
            }

            val backBtn = binding.searchRoot.findViewById<View>(R.id.tv_search_back)
            backBtn?.setOnClickListener {
                activity?.onBackPressedDispatcher?.onBackPressed()
            }
            backBtn?.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            val navSearch = activity?.findViewById<View>(R.id.tv_nav_search)
                            if (navSearch != null) {
                                navSearch.requestFocus()
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            val searchInput = binding.searchRoot.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
                            searchInput?.requestFocus() ?: binding.mainSearch.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                }
                false
            }

            val searchInputView = binding.searchRoot.findViewById<View>(androidx.appcompat.R.id.search_src_text)
            listOf(
                R.id.tv_nav_home,
                R.id.tv_nav_search,
                R.id.tv_nav_library,
                R.id.tv_nav_downloads,
                R.id.tv_nav_settings
            ).mapNotNull { activity?.findViewById<View>(it) }.forEach { tab ->
                tab.nextFocusDownId = searchInputView?.id ?: R.id.main_search
            }
        }

        binding.voiceSearch.setOnClickListener { searchView ->
            searchView?.context?.let { ctx ->
                try {
                    if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                        showToast(R.string.speech_recognition_unavailable)
                    } else {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(
                                RecognizerIntent.EXTRA_PROMPT,
                                ctx.getString(R.string.begin_speaking)
                            )
                        }
                        speechRecognizerLauncher.launch(intent)
                    }
                } catch (_: Throwable) {
                    // launch may throw
                    showToast(R.string.speech_recognition_unavailable)
                }
            }
        }

        selectedApis = DataStoreHelper.searchPreferenceProviders.toMutableSet()

        binding.searchFilter.setOnClickListener { searchView ->
            searchView?.context?.let { ctx ->
                val validAPIs = ctx.filterProviderByPreferredMedia(hasHomePageIsRequired = false)
                var currentValidApis = listOf<MainAPI>()
                val currentSelectedApis = if (selectedApis.isEmpty()) validAPIs.map { it.name }
                    .toMutableSet() else selectedApis

                val builder =
                    BottomSheetDialog(ctx)

                builder.behavior.state = BottomSheetBehavior.STATE_EXPANDED
                builder.behavior.skipCollapsed = true

                val selectMainpageBinding: HomeSelectMainpageBinding =
                    HomeSelectMainpageBinding.inflate(
                        builder.layoutInflater,
                        null,
                        false
                    )
                builder.setContentView(selectMainpageBinding.root)
                builder.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                builder.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
                builder.window?.setDimAmount(0.45f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    builder.window?.attributes?.blurBehindRadius = 32
                }
                selectMainpageBinding.selectMainpageTitle.text = ctx.getString(R.string.search) + " Filters"
                selectMainpageBinding.selectMainpageClose.setOnClickListener {
                    builder.dismissSafe()
                }
                selectMainpageBinding.applyBttHolder.isVisible = true
                builder.show()
                builder.let { dialog ->
                    val previousSelectedApis = selectedApis.toSet()
                    val previousSelectedSearchTypes = selectedSearchTypes.toSet()

                    val isMultiLang = ctx.getApiProviderLangSettings().let { set ->
                        set.size > 1 || set.contains(AllLanguagesName)
                    }

                    val cancelBtt = dialog.findViewById<MaterialButton>(R.id.cancel_btt)
                    val applyBtt = dialog.findViewById<MaterialButton>(R.id.apply_btt)

                    val listView = dialog.findViewById<ListView>(R.id.listview1)
                    val arrayAdapter = ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
                    listView?.adapter = arrayAdapter
                    listView?.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE

                    listView?.setOnItemClickListener { _, _, i, _ ->
                        if (currentValidApis.isNotEmpty()) {
                            val api = currentValidApis[i].name
                            if (currentSelectedApis.contains(api)) {
                                listView.setItemChecked(i, false)
                                currentSelectedApis -= api
                            } else {
                                listView.setItemChecked(i, true)
                                currentSelectedApis += api
                            }
                        }
                    }

                    fun updateList(types: List<TvType>) {
                        DataStoreHelper.searchPreferenceTags = types

                        arrayAdapter.clear()
                        currentValidApis = validAPIs.filter { api ->
                            api.supportedTypes.any {
                                types.contains(it)
                            }
                        }.sortedBy { it.name.lowercase() }

                        val names = currentValidApis.map {
                            if (isMultiLang) "${
                                SubtitleHelper.getFlagFromIso(
                                    it.lang
                                )?.plus(" ") ?: ""
                            }${it.name}" else it.name
                        }
                        for ((index, api) in currentValidApis.map { it.name }.withIndex()) {
                            listView?.setItemChecked(index, currentSelectedApis.contains(api))
                        }

                        //arrayAdapter.notifyDataSetChanged()
                        arrayAdapter.addAll(names)
                        arrayAdapter.notifyDataSetChanged()
                    }

                    bindChips(
                        selectMainpageBinding.tvtypesChipsScroll.tvtypesChips,
                        selectedSearchTypes,
                        validAPIs.flatMap { api -> api.supportedTypes }.distinct()
                    ) { list ->
                        updateList(list)

                        // refresh selected chips in main chips
                        if (selectedSearchTypes.toSet() != list.toSet()) {
                            selectedSearchTypes.clear()
                            selectedSearchTypes.addAll(list)
                            updateChips(
                                binding.tvtypesChipsScroll.tvtypesChips,
                                selectedSearchTypes
                            )

                        }
                    }


                    cancelBtt?.setOnClickListener {
                        dialog.dismissSafe()
                    }

                    applyBtt?.setOnClickListener {
                        //if (currentApiName != selectedApiName) {
                        //    currentApiName?.let(callback)
                        //}
                        dialog.dismissSafe()
                    }

                    dialog.setOnDismissListener {
                        DataStoreHelper.searchPreferenceProviders = currentSelectedApis.toList()
                        selectedApis = currentSelectedApis

                        // run search when dialog is close
                        if (previousSelectedApis != selectedApis.toSet() || previousSelectedSearchTypes != selectedSearchTypes.toSet()) {
                            search(binding.mainSearch.query.toString())
                        }
                    }
                    updateList(selectedSearchTypes.toList())
                }
            }
        }

        val settingsManager = context?.let { PreferenceManager.getDefaultSharedPreferences(it) }
        val isAdvancedSearch = settingsManager?.getBoolean("advanced_search", true) ?: true
        val isSearchSuggestionsEnabled = settingsManager?.getBoolean("search_suggestions_enabled", true) ?: true

        selectedSearchTypes = DataStoreHelper.searchPreferenceTags.toMutableList()

        if (!isLayout(PHONE)) {
            binding.searchFilter.isFocusable = true
            binding.searchFilter.isFocusableInTouchMode = true
        }

        // Hide suggestions when search view loses focus (phone only)
        if (isLayout(PHONE)) {
            binding.mainSearch.setOnQueryTextFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    searchViewModel.clearSuggestions()
                }
            }
        }


        binding.mainSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                search(query)
                searchViewModel.clearSuggestions()

                binding.mainSearch.let {
                    hideKeyboard(it)
                }

                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                //searchViewModel.quickSearch(newText)
                val showHistory = newText.isBlank()
                val isTv = isLayout(TV or EMULATOR)
                if (showHistory) {
                    searchViewModel.clearSearch()
                    searchViewModel.updateHistory()
                    searchViewModel.clearSuggestions()
                    loadDefaultTrendingItems()
                } else {
                    // Fetch suggestions when user is typing (if enabled)
                    if (isSearchSuggestionsEnabled && !isTv) {
                        searchViewModel.fetchSuggestions(newText)
                    }
                }
                binding.apply {
                    val sectionTitle = root.findViewById<TextView>(R.id.tv_search_section_title)
                    val emptyView = root.findViewById<TextView>(R.id.tv_search_empty)
                    if (showHistory) {
                        sectionTitle?.setText(R.string.tv_trending_today)
                        emptyView?.isVisible = false
                    } else {
                        sectionTitle?.text = root.context.getString(R.string.search_results)
                    }
                    searchHistoryRecycler.isVisible = showHistory && ((searchHistoryRecycler.adapter?.itemCount ?: 0) > 0)
                    searchMasterRecycler.isVisible = false
                    searchAutofitResults.isVisible = true
                    searchSuggestionsRecycler.isVisible = false
                }

                return true
            }
        })

        observe(searchViewModel.searchResponse) {
            when (it) {
                is Resource.Success -> {
                    it.value.let { data ->
                        val list = data.list
                        val emptyView = view?.findViewById<TextView>(R.id.tv_search_empty)
                        if (list.isNotEmpty()) {
                            (binding.searchAutofitResults.adapter as? SearchAdapter)?.submitList(
                                list
                            )
                            emptyView?.isVisible = false
                        } else {
                            val query = binding.mainSearch.query?.toString()
                            emptyView?.isVisible = !query.isNullOrBlank()
                        }
                    }
                    searchExitIcon?.alpha = 1f
                    binding.searchLoadingBar.alpha = 0f
                }

                is Resource.Failure -> {
                    // Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                    val emptyView = view?.findViewById<TextView>(R.id.tv_search_empty)
                    val query = binding.mainSearch.query?.toString()
                    emptyView?.isVisible = !query.isNullOrBlank()
                    searchExitIcon?.alpha = 1f
                    binding.searchLoadingBar.alpha = 0f
                }

                is Resource.Loading -> {
                    val emptyView = view?.findViewById<TextView>(R.id.tv_search_empty)
                    emptyView?.isVisible = false
                    searchExitIcon?.alpha = 0f
                    binding.searchLoadingBar.alpha = 1f
                }
            }
        }

        val listLock = ReentrantLock()
        observe(searchViewModel.currentSearch) { list ->
            try {
                // https://stackoverflow.com/questions/6866238/concurrent-modification-exception-adding-to-an-arraylist
                listLock.lock()

                val pinnedOrder = DataStoreHelper.pinnedProviders.reversedArray()

                val sortedList = list.toList().sortedWith(compareBy { (providerName, _) ->
                    val index = pinnedOrder.indexOf(providerName)
                    if (index == -1) Int.MAX_VALUE else index
                })

                (binding.searchMasterRecycler.adapter as? ParentItemAdapter)?.apply {
                    val newItems = sortedList.map { (providerName, providerData) ->
                        val dataList = providerData.list
                        val dataListFiltered =
                            context?.filterSearchResultByFilmQuality(dataList) ?: dataList

                        val homePageList = HomePageList(
                            providerName,
                            dataListFiltered
                        )

                        HomeViewModel.ExpandableHomepageList(
                            homePageList,
                            providerData.currentPage,
                            providerData.hasNext
                        )
                    }

                    submitList(newItems)
                    //notifyDataSetChanged()
                }
            } catch (e: Exception) {
                logError(e)
            } finally {
                listLock.unlock()
            }
        }


        /*main_search.setOnQueryTextFocusChangeListener { _, b ->
            if (b) {
                // https://stackoverflow.com/questions/12022715/unable-to-show-keyboard-automatically-in-the-searchview
                showInputMethod(view.findFocus())
            }
        }*/
        //main_search.onActionViewExpanded()*/

        val masterAdapter =
            ParentItemAdapter(id = "masterAdapter".hashCode(), { callback ->
                SearchHelper.handleSearchClickCallback(callback)
            }, { item ->
                bottomSheetDialog = activity?.loadHomepageList(item, dismissCallback = {
                    bottomSheetDialog = null
                }, expandCallback = { name -> searchViewModel.expandAndReturn(name) })
            }, expandCallback = { name ->
                ioSafe {
                    searchViewModel.expandAndReturn(name)
                }
            })

        val historyAdapter = SearchHistoryAdaptor { click ->
            val searchItem = click.item
            when (click.clickAction) {
                SEARCH_HISTORY_OPEN -> {
                    if (searchItem == null) return@SearchHistoryAdaptor
                    searchViewModel.clearSearch()
                    if (searchItem.type.isNotEmpty())
                        updateChips(
                            binding.tvtypesChipsScroll.tvtypesChips,
                            searchItem.type.toMutableList()
                        )
                    binding.mainSearch.setQuery(searchItem.searchText, true)
                }

                SEARCH_HISTORY_REMOVE -> {
                    if (searchItem == null) return@SearchHistoryAdaptor
                    removeKey("$currentAccount/$SEARCH_HISTORY_KEY", searchItem.key)
                    searchViewModel.updateHistory()
                }

                SEARCH_HISTORY_CLEAR -> {
                    // Show confirmation dialog (from footer button)
                    val ctx = activity ?: return@SearchHistoryAdaptor
                    try {
                        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_tv_clear_history, null)
                        val dialog = AlertDialog.Builder(ctx, R.style.AlertDialogCustomTransparent)
                            .setView(dialogView)
                            .create()

                        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                        dialog.window?.setDimAmount(0.45f)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                            dialog.window?.attributes?.blurBehindRadius = 32
                        }

                        val cancelBtn = dialogView.findViewById<View>(R.id.btn_cancel)
                        val clearBtn = dialogView.findViewById<View>(R.id.btn_clear)

                        cancelBtn?.setOnClickListener {
                            dialog.dismissSafe()
                        }

                        clearBtn?.setOnClickListener {
                            removeKeys("$currentAccount/$SEARCH_HISTORY_KEY")
                            searchViewModel.updateHistory()
                            dialog.dismissSafe()
                        }

                        dialog.show()
                        cancelBtn?.requestFocus()
                    } catch (e: Exception) {
                        logError(e)
                    }
                }

                else -> {
                    // wth are you doing???
                }
            }
        }

        val suggestionAdapter = SearchSuggestionAdapter { callback ->
            when (callback.clickAction) {
                SEARCH_SUGGESTION_CLICK -> {
                    // Search directly
                    binding.mainSearch.setQuery(callback.suggestion, true)
                    searchViewModel.clearSuggestions()
                }
                SEARCH_SUGGESTION_FILL -> {
                    // Fill the search box without searching
                    binding.mainSearch.setQuery(callback.suggestion, false)
                }
                SEARCH_SUGGESTION_CLEAR -> {
                    // Clear suggestions (from footer button)
                    searchViewModel.clearSuggestions()
                }
            }
        }

        binding.apply {
            searchHistoryRecycler.adapter = historyAdapter
            searchHistoryRecycler.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            //searchHistoryRecycler.layoutManager = GridLayoutManager(context, 1)

            // Setup suggestions RecyclerView
            searchSuggestionsRecycler.adapter = suggestionAdapter
            searchSuggestionsRecycler.layoutManager = LinearLayoutManager(context)

            searchMasterRecycler.setRecycledViewPool(ParentItemAdapter.sharedPool)
            searchMasterRecycler.adapter = masterAdapter
            //searchMasterRecycler.setLinearListLayout(isHorizontal = false, nextRight = FOCUS_SELF)

            searchMasterRecycler.layoutManager = GridLayoutManager(context, 1)

            // Automatically search the specified query, this allows the app search to launch from intent
            var sq =
                arguments?.getString(SEARCH_QUERY) ?: savedInstanceState?.getString(SEARCH_QUERY)
            if (sq.isNullOrBlank()) {
                sq = MainActivity.nextSearchQuery
            }

            sq?.let { query ->
                if (query.isBlank()) return@let

                // Queries are dropped if you are submitted before layout finishes
                mainSearch.doOnLayout {
                    mainSearch.setQuery(query, true)
                }
                // Clear the query as to not make it request the same query every time the page is opened
                arguments?.remove(SEARCH_QUERY)
                savedInstanceState?.remove(SEARCH_QUERY)
                MainActivity.nextSearchQuery = null
            }
        }

        observe(searchViewModel.currentHistory) { list ->
            (binding.searchHistoryRecycler.adapter as? SearchHistoryAdaptor?)?.submitList(list)
            val hasQuery = !binding.mainSearch.query.isNullOrBlank()
            binding.searchHistoryRecycler.isVisible = !hasQuery && list.isNotEmpty()
             // Scroll to top to show newest items (list is sorted by newest first)
            if (list.isNotEmpty()) {
                binding.searchHistoryRecycler.scrollToPosition(0)
            }
        }

        // Observe search suggestions
        observe(searchViewModel.searchSuggestions) { suggestions ->
            val hasSuggestions = suggestions.isNotEmpty()
            binding.searchSuggestionsRecycler.isVisible = hasSuggestions
            (binding.searchSuggestionsRecycler.adapter as? SearchSuggestionAdapter?)?.submitList(suggestions)

            // On non-phone layouts, redirect focus and handle back button
            if (!isLayout(PHONE)) {
                if (hasSuggestions) {
                    binding.tvtypesChipsScroll.tvtypesChips.root.nextFocusDownId = R.id.search_suggestions_recycler
                    // Attach back button callback to clear suggestions
                    activity?.attachBackPressedCallback("SearchFragment") {
                        searchViewModel.clearSuggestions()
                    }
                } else {
                    // Reset to default focus target (history)
                    binding.tvtypesChipsScroll.tvtypesChips.root.nextFocusDownId = R.id.search_history_recycler
                    // Detach back button callback when no suggestions
                    activity?.detachBackPressedCallback("SearchFragment")
                }
            }
        }

        searchViewModel.updateHistory()

        if (isTv) {
            activity?.attachBackPressedCallback("SearchFragmentTv") {
                if (isSearchCardsFocused) {
                    setSearchCardsFocusedState(false)
                    val searchInput = binding.searchRoot.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
                    searchInput?.requestFocus() ?: binding.mainSearch.requestFocus()
                } else if (!binding.mainSearch.query.isNullOrBlank()) {
                    binding.mainSearch.setQuery("", false)
                    loadDefaultTrendingItems()
                } else {
                    activity?.detachBackPressedCallback("SearchFragmentTv")
                    activity?.onBackPressedDispatcher?.onBackPressed()
                }
            }
        }

        loadDefaultTrendingItems()
        observe(homeViewModel.page) { res ->
            if (binding.mainSearch.query.isNullOrBlank()) {
                val pageData = (res as? Resource.Success)?.value
                val defaultItems = pageData?.values?.firstOrNull()?.list?.list
                if (!defaultItems.isNullOrEmpty()) {
                    (binding.searchAutofitResults.adapter as? SearchAdapter)?.submitList(defaultItems)
                }
            }
        }
    }
}
