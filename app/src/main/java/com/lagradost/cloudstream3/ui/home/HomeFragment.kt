package com.lagradost.cloudstream3.ui.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Build
import android.graphics.Outline
import android.view.ViewOutlineProvider
import android.graphics.RenderEffect
import android.graphics.Shader
import com.lagradost.cloudstream3.LoadResponse
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import android.view.KeyEvent
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.databinding.SearchResultGridBinding
import com.lagradost.cloudstream3.databinding.ItemTvPluginSearchCardBinding
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import android.view.TextureView
import android.graphics.SurfaceTexture
import android.view.Surface
import android.graphics.Matrix
import android.media.MediaPlayer
import com.lagradost.cloudstream3.ui.APIRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.FragmentHomeBinding
import com.lagradost.cloudstream3.databinding.HomeEpisodesExpandedBinding
import com.lagradost.cloudstream3.databinding.HomeSelectMainpageBinding
import com.lagradost.cloudstream3.databinding.TvtypesChipsBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.ui.APIRepository.Companion.noneApi
import com.lagradost.cloudstream3.ui.APIRepository.Companion.randomApi
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.account.AccountHelper.showAccountSelectLinear
import com.lagradost.cloudstream3.ui.account.AccountViewModel
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_PLAY_FILE
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper.handleSearchClickCallback
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiProviderLangSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.isNetworkAvailable
import com.lagradost.cloudstream3.utils.AppContextUtils.isRecyclerScrollable
import com.lagradost.cloudstream3.utils.AppContextUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.AppContextUtils.openBrowser
import com.lagradost.cloudstream3.utils.AppContextUtils.ownHide
import com.lagradost.cloudstream3.utils.AppContextUtils.ownShow
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.EmptyEvent
import com.lagradost.cloudstream3.utils.SubtitleHelper.getFlagFromIso
import com.lagradost.cloudstream3.utils.TvChannelUtils
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.ui.utils.TvAmbientVideoHelper

private const val TAG = "HomeFragment"

class HomeFragment : BaseFragment<FragmentHomeBinding>(
    BindingCreator.Bind(FragmentHomeBinding::bind)
) {
    companion object {
        // Used for configuration changed events to fix any popups that are not attached to a fragment
        val configEvent = EmptyEvent()
        var currentSpan = 1
        var lastFocusedItem: java.lang.ref.WeakReference<View>? = null

        private val errorProfilePics = listOf(
            R.drawable.monke_benene,
            R.drawable.monke_burrito,
            R.drawable.monke_coco,
            R.drawable.monke_cookie,
            R.drawable.monke_flusdered,
            R.drawable.monke_funny,
            R.drawable.monke_like,
            R.drawable.monke_party,
            R.drawable.monke_sob,
            R.drawable.monke_drink,
        )

        val errorProfilePic = errorProfilePics.random()

        fun Context.getDisplayName(apiName: String?): String? {
            return when (apiName) {
                noneApi.name -> getString(R.string.none)
                randomApi.name -> getString(R.string.home_random)
                else -> apiName
            }
        }

        //fun Activity.loadHomepageList(
        //    item: HomePageList,
        //    deleteCallback: (() -> Unit)? = null,
        //) {
        //    loadHomepageList(
        //        expand = HomeViewModel.ExpandableHomepageList(item, 1, false),
        //        deleteCallback = deleteCallback,
        //        expandCallback = null
        //    )
        //}

        // returns a BottomSheetDialog that will be hidden with OwnHidden upon hide, and must be saved to be able call ownShow in onCreateView

        fun Activity.loadHomepageList(
            expand: HomeViewModel.ExpandableHomepageList,
            deleteCallback: (() -> Unit)? = null,
            expandCallback: (suspend (String) -> HomeViewModel.ExpandableHomepageList?)? = null,
            dismissCallback: (() -> Unit),
        ): BottomSheetDialog {
            val context = this
            val bottomSheetDialogBuilder = BottomSheetDialog(context)
            val binding: HomeEpisodesExpandedBinding = HomeEpisodesExpandedBinding.inflate(
                bottomSheetDialogBuilder.layoutInflater,
                null,
                false
            )
            bottomSheetDialogBuilder.setContentView(binding.root)
            //val title = bottomSheetDialogBuilder.findViewById<TextView>(R.id.home_expanded_text)!!

            //title.findViewTreeLifecycleOwner().lifecycle.addObserver()

            val item = expand.list
            binding.homeExpandedText.text = item.name
            // val recycle =
            //    bottomSheetDialogBuilder.findViewById<AutofitRecyclerView>(R.id.home_expanded_recycler)!!
            //val titleHolder =
            //    bottomSheetDialogBuilder.findViewById<FrameLayout>(R.id.home_expanded_drag_down)!!

            // main {
            //(bottomSheetDialogBuilder.ownerActivity as androidx.fragment.app.FragmentActivity?)?.supportFragmentManager?.fragments?.lastOrNull()?.viewLifecycleOwner?.apply {
            //    println("GOT LIFE: lifecycle $this")
            //    this.lifecycle.addObserver(object : DefaultLifecycleObserver {
            //        override fun onResume(owner: LifecycleOwner) {
            //            super.onResume(owner)
            //            println("onResume!!!!")
            //            bottomSheetDialogBuilder?.ownShow()
            //        }

            //        override fun onStop(owner: LifecycleOwner) {
            //            super.onStop(owner)
            //            bottomSheetDialogBuilder?.ownHide()
            //        }
            //    })
            //}
            // }
            //val delete = bottomSheetDialogBuilder.home_expanded_delete
            binding.homeExpandedDelete.isGone = deleteCallback == null
            if (deleteCallback != null) {
                binding.homeExpandedDelete.setOnClickListener {
                    try {
                        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
                        val dialogClickListener =
                            DialogInterface.OnClickListener { _, which ->
                                when (which) {
                                    DialogInterface.BUTTON_POSITIVE -> {
                                        deleteCallback.invoke()
                                        bottomSheetDialogBuilder.dismissSafe(this)
                                    }

                                    DialogInterface.BUTTON_NEGATIVE -> {}
                                }
                            }

                        builder.setTitle(R.string.clear_history)
                            .setMessage(
                                context.getString(R.string.delete_message).format(
                                    item.name
                                )
                            )
                            .setPositiveButton(R.string.delete, dialogClickListener)
                            .setNegativeButton(R.string.cancel, dialogClickListener)
                            .show().setDefaultFocus()
                    } catch (e: Exception) {
                        logError(e)
                        // ye you somehow fucked up formatting did you?
                    }
                }
            }
            binding.homeExpandedDragDown.setOnClickListener {
                bottomSheetDialogBuilder.dismissSafe(this)
            }


            // Span settings
            binding.homeExpandedRecycler.spanCount = context.getSpanCount(item.isHorizontalImages)
            binding.homeExpandedRecycler.setRecycledViewPool(SearchAdapter.sharedPool)
            binding.homeExpandedRecycler.adapter =
                SearchAdapter(binding.homeExpandedRecycler,item.isHorizontalImages) { callback ->
                    handleSearchClickCallback(callback)
                    if (callback.action == SEARCH_ACTION_LOAD || callback.action == SEARCH_ACTION_PLAY_FILE) {
                        bottomSheetDialogBuilder.ownHide() // we hide here because we want to resume it later
                        //bottomSheetDialogBuilder.dismissSafe(this)
                    }
                }.apply {
                    submitList(item.list)
                    hasNext = expand.hasNext
                }

            binding.homeExpandedRecycler.addOnScrollListener(object :
                RecyclerView.OnScrollListener() {
                var expandCount = 0
                val name = expand.list.name

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)

                    val adapter = recyclerView.adapter
                    if (adapter !is SearchAdapter) return

                    val count = adapter.itemCount
                    val currentHasNext = adapter.hasNext
                    //!recyclerView.canScrollVertically(1)
                    if (!recyclerView.isRecyclerScrollable() && currentHasNext && expandCount != count) {
                        expandCount = count
                        ioSafe {
                            expandCallback?.invoke(name)?.let { newExpand ->
                                (recyclerView.adapter as? SearchAdapter?)?.apply {
                                    hasNext = newExpand.hasNext
                                    submitList(newExpand.list.list)
                                }
                            }
                        }
                    }
                }
            })

            val spanListener = Runnable {
                binding.homeExpandedRecycler.spanCount = context.getSpanCount(item.isHorizontalImages)
                // We want to rebind everything to update the UI, however we also want to avoid
                // any animations ect, this is the easiest way to do this, and the most correct
                @SuppressLint("NotifyDataSetChanged")
                binding.homeExpandedRecycler.adapter?.notifyDataSetChanged()
            }

            configEvent += spanListener

            bottomSheetDialogBuilder.setOnDismissListener {
                dismissCallback.invoke()
                configEvent -= spanListener
            }

            //(recycle.adapter as SearchAdapter).notifyDataSetChanged()

            bottomSheetDialogBuilder.show()
            return bottomSheetDialogBuilder
        }

        private fun getPairList(
            anime: Chip?,
            cartoons: Chip?,
            tvs: Chip?,
            docs: Chip?,
            movies: Chip?,
            asian: Chip?,
            livestream: Chip?,
            torrent: Chip?,
            nsfw: Chip?,
            others: Chip?,
        ): List<Pair<Chip?, List<TvType>>> {
            // This list should be same order as home screen to aid navigation
            return listOf(
                Pair(movies, listOf(TvType.Movie)),
                Pair(tvs, listOf(TvType.TvSeries)),
                Pair(anime, listOf(TvType.Anime, TvType.OVA, TvType.AnimeMovie)),
                Pair(asian, listOf(TvType.AsianDrama)),
                Pair(cartoons, listOf(TvType.Cartoon)),
                Pair(docs, listOf(TvType.Documentary)),
                Pair(livestream, listOf(TvType.Live)),
                Pair(torrent, listOf(TvType.Torrent)),
                Pair(nsfw, listOf(TvType.NSFW)),
                Pair(others, listOf(TvType.Others)),
            )
        }

        private fun getPairList(header: TvtypesChipsBinding) = getPairList(
            header.homeSelectAnime,
            header.homeSelectCartoons,
            header.homeSelectTvSeries,
            header.homeSelectDocumentaries,
            header.homeSelectMovies,
            header.homeSelectAsian,
            header.homeSelectLivestreams,
            header.homeSelectTorrents,
            header.homeSelectNsfw,
            header.homeSelectOthers
        )

        fun validateChips(header: TvtypesChipsBinding?, validTypes: List<TvType>) {
            if (header == null) return
            val pairList = getPairList(header)
            for ((button, types) in pairList) {
                val isValid = validTypes.any { types.contains(it) }
                button?.isVisible = isValid
            }
        }

        fun updateChips(header: TvtypesChipsBinding?, selectedTypes: List<TvType>) {
            if (header == null) return
            val pairList = getPairList(header)
            for ((button, types) in pairList) {
                button?.isChecked =
                    button.isVisible && selectedTypes.any { types.contains(it) }
            }
        }

        fun bindChips(
            header: TvtypesChipsBinding?,
            selectedTypes: List<TvType>,
            validTypes: List<TvType>,
            callback: (List<TvType>) -> Unit
        ) {
            bindChips(header, selectedTypes, validTypes, callback, null, null)
        }

        fun bindChips(
            header: TvtypesChipsBinding?,
            selectedTypes: List<TvType>,
            validTypes: List<TvType>,
            callback: (List<TvType>) -> Unit,
            nextFocusDown: Int?,
            nextFocusUp: Int?
        ) {
            if (header == null) return
            val pairList = getPairList(header)
            for ((button, types) in pairList) {
                val isValid = validTypes.any { types.contains(it) }
                button?.isVisible = isValid
                button?.isChecked = isValid && selectedTypes.any { types.contains(it) }
                button?.isFocusable = true
                if (isLayout(TV)) {
                    button?.isFocusableInTouchMode = true
                }

                if (nextFocusDown != null)
                    button?.nextFocusDownId = nextFocusDown

                if (nextFocusUp != null)
                    button?.nextFocusUpId = nextFocusUp

                button?.setOnCheckedChangeListener { _, _ ->
                    val list = ArrayList<TvType>()
                    for ((sbutton, vvalidTypes) in pairList) {
                        if (sbutton?.isChecked == true)
                            list.addAll(vvalidTypes)
                    }
                    callback(list)
                }
            }
        }

        fun showTvProviderDialog(context: Context, selectedApiName: String?, callback: (String) -> Unit) {
            val allValidAPIs = context.filterProviderByPreferredMedia().toMutableList()
            val isMultiLang = context.getApiProviderLangSettings().let { set ->
                set.size > 1 || set.contains(AllLanguagesName)
            }
            val validAPIs = mutableListOf<MainAPI>()
            validAPIs.addAll(allValidAPIs)
            validAPIs.add(randomApi)
            validAPIs.add(noneApi)

            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_tv_provider_select, null)
            val dialog = AlertDialog.Builder(context, R.style.AlertDialogCustom)
                .setView(dialogView)
                .create()

            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.show()

            val recycler = dialogView.findViewById<RecyclerView>(R.id.tv_provider_recycler) ?: return

            class ProviderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
                val tick: ImageView = v.findViewById(R.id.tv_provider_item_tick)
                val title: TextView = v.findViewById(R.id.tv_provider_item_title)
            }

            val selectedIndex = validAPIs.indexOfFirst { it.name == selectedApiName }.coerceAtLeast(0)

            recycler.adapter = object : RecyclerView.Adapter<ProviderViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProviderViewHolder {
                    val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tv_provider, parent, false)
                    return ProviderViewHolder(v)
                }

                override fun onBindViewHolder(holder: ProviderViewHolder, position: Int) {
                    val api = validAPIs[position]
                    val displayName = context.getDisplayName(api.name) ?: api.name
                    val flag = if (isMultiLang) getFlagFromIso(api.lang)?.plus(" ") ?: "" else ""
                    holder.title.text = "$flag$displayName"
                    val isSelected = (api.name == selectedApiName)
                    holder.tick.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE

                    holder.itemView.setOnClickListener {
                        if (api.name != selectedApiName) {
                            callback(api.name)
                        }
                        dialog.dismissSafe()
                    }

                    if (position == selectedIndex) {
                        holder.itemView.post {
                            holder.itemView.requestFocus()
                        }
                    }
                }

                override fun getItemCount(): Int = validAPIs.size
            }

            recycler.scrollToPosition(selectedIndex)
        }

        fun Context.selectHomepage(selectedApiName: String?, callback: (String) -> Unit) {
            if (isLayout(TV or EMULATOR)) {
                showTvProviderDialog(this, selectedApiName, callback)
                return
            }
            val validAPIs = filterProviderByPreferredMedia().toMutableList()

            validAPIs.add(0, randomApi)
            validAPIs.add(0, noneApi)
            //val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            //builder.setView(R.layout.home_select_mainpage)
            val builder =
                BottomSheetDialog(this)

            builder.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            builder.behavior.skipCollapsed = true
            val binding: HomeSelectMainpageBinding = HomeSelectMainpageBinding.inflate(
                builder.layoutInflater,
                null,
                false
            )

            builder.setContentView(binding.root)
            builder.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            builder.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
            builder.window?.setDimAmount(0.45f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                builder.window?.attributes?.blurBehindRadius = 32
            }
            binding.root.findViewById<View>(R.id.select_mainpage_close)?.setOnClickListener {
                builder.dismissSafe()
            }
            builder.show()
            builder.let { dialog ->
                val isMultiLang = getApiProviderLangSettings().let { set ->
                    set.size > 1 || set.contains(AllLanguagesName)
                }
                //dialog.window?.setGravity(Gravity.BOTTOM)

                var currentApiName = selectedApiName

                var currentValidApis: MutableList<MainAPI> = mutableListOf()
                val preSelectedTypes = DataStoreHelper.homePreference.toMutableList()

                binding.cancelBtt.setOnClickListener {
                    dialog.dismissSafe()
                }

                binding.applyBtt.setOnClickListener {
                    if (currentApiName != selectedApiName) {
                        currentApiName?.let(callback)
                    }
                    dialog.dismissSafe()
                }

                var pinnedphashset = DataStoreHelper.pinnedProviders.toHashSet()

                val listView = dialog.findViewById<ListView>(R.id.listview1)

                val arrayAdapter = object : ArrayAdapter<String>(
                    this, R.layout.sort_bottom_single_provider_choice,
                    mutableListOf()
                ) {
                    override fun getView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup
                    ): View {
                        val view = convertView ?: LayoutInflater.from(context)
                            .inflate(R.layout.sort_bottom_single_provider_choice, parent, false)
                        val titleText = view.findViewById<TextView>(R.id.text1)
                        val pinIcon = view.findViewById<ImageView>(R.id.pinicon)
                        val settingsIcon = view.findViewById<ImageView>(R.id.action_settings)

                        val name = getItem(position)
                        titleText?.text = name
                        if (position < currentValidApis.size) {
                            val providerApi = currentValidApis[position]
                            val isPinned =
                                pinnedphashset.contains(providerApi.name)
                            pinIcon.visibility = if (isPinned) View.VISIBLE else View.GONE

                            val pluginInstance = providerApi.sourcePlugin?.let { PluginManager.plugins[it] } as? Plugin
                            val isDownloadedPluginWithSettings = pluginInstance?.openSettings != null && !isLayout(TV)

                            settingsIcon.visibility = if (isDownloadedPluginWithSettings) View.VISIBLE else View.GONE
                            if (isDownloadedPluginWithSettings) {
                                settingsIcon.setOnClickListener {
                                    try {
                                        val activityContext = it.context.getActivity() ?: it.context
                                        pluginInstance.openSettings?.invoke(activityContext)
                                    } catch (e: Throwable) {
                                        logError(e)
                                    }
                                }
                            }
                        }

                        view.setOnClickListener {
                            if (currentValidApis.isNotEmpty() && position < currentValidApis.size) {
                                currentApiName = currentValidApis[position].name
                                currentApiName?.let(callback)
                                dialog.dismissSafe()
                            }
                        }

                        return view
                    }
                }
                listView?.adapter = arrayAdapter
                listView?.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                if (isLayout(TV)) {
                    listView?.isFocusable = true
                    listView?.isFocusableInTouchMode = true
                    listView?.itemsCanFocus = false
                }

                listView?.setOnItemClickListener { _, _, i, _ ->
                    if (currentValidApis.isNotEmpty() && i < currentValidApis.size) {
                        currentApiName = currentValidApis[i].name
                        currentApiName?.let(callback)
                        dialog.dismissSafe()
                    }
                }

                fun updateList() {
                    DataStoreHelper.homePreference = preSelectedTypes
                    val pinnedp = DataStoreHelper.pinnedProviders.toList()
                    pinnedphashset = pinnedp.toHashSet()
                    arrayAdapter.clear()
                    val sortedApis = validAPIs
                        .filter {
                            val isPinned = pinnedphashset.contains(it.name)

                            // Hide pinned NSFW when NSFW not selected. NSFW is distracting when not chosen.
                            if (isPinned && !preSelectedTypes.contains(TvType.NSFW)) {
                                if (it.supportedTypes.all { type -> type == TvType.NSFW }) return@filter false
                            }

                            it.hasMainPage && (isPinned || it.supportedTypes.any(
                                preSelectedTypes::contains
                            ))
                        }
                        .sortedBy { it.name.lowercase() }

                    val sortedApiMap = LinkedHashMap<String, MainAPI>().apply {
                        sortedApis.forEach { put(it.name, it) }
                    }

                    val pinnedApis = pinnedp.asReversed().mapNotNull { name ->
                        sortedApiMap[name]
                    }

                    val remainingApis = sortedApis.filterNot { pinnedphashset.contains(it.name) }

                    currentValidApis = mutableListOf<MainAPI>().apply {
                        addAll(validAPIs.take(2))
                        addAll(pinnedApis)
                        addAll(remainingApis)
                    }

                    val names = currentValidApis.map {
                        val displayName = getDisplayName(it.name)
                        if (isMultiLang) "${getFlagFromIso(it.lang)?.plus(" ") ?: ""}$displayName" else displayName
                    }
                    val index = currentValidApis.map { it.name }.indexOf(currentApiName)
                    listView?.setItemChecked(index, true)
                    arrayAdapter.addAll(names)
                    arrayAdapter.notifyDataSetChanged()
                    if (isLayout(TV) && index >= 0) {
                        listView?.setSelection(index)
                        listView?.post {
                            listView?.requestFocus()
                        }
                    }
                }
                // pin provider on hold
                listView?.setOnItemLongClickListener { _, _, i, _ ->
                    if (currentValidApis.isNotEmpty() && i > 1) {
                        val pinnedp = DataStoreHelper.pinnedProviders.toMutableList()
                        val thisapi = currentValidApis[i].name
                        if (pinnedp.contains(thisapi)) {
                            pinnedp.remove(thisapi)
                        } else {
                            pinnedp.add(thisapi)
                        }
                        DataStoreHelper.pinnedProviders = pinnedp.toTypedArray()
                        updateList()
                    }
                    true
                }

                bindChips(
                    binding.tvtypesChipsScroll.tvtypesChips,
                    preSelectedTypes,
                    validAPIs.flatMap { it.supportedTypes }.distinct()
                ) { list ->
                    preSelectedTypes.clear()
                    preSelectedTypes.addAll(list)
                    updateList()
                }
                updateList()
            }
        }
    }

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    fun addMovies(cards: List<SearchResponse>) {
        val ctx = context ?: run {
            Log.e(TAG, "Context is null, aborting addMovies")
            return
        }

        try {
            val existingId = TvChannelUtils.getChannelId(ctx, getString(R.string.app_name))
            if (existingId != null) {
                Log.d(TAG, "Channel ID: $existingId")

                val programCards = cards

                TvChannelUtils.addPrograms(
                    context = ctx,
                    channelId = existingId,
                    items = programCards
                )
            } else {
                Log.d(TAG, "Channel does not exist")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding movies: $e")
        }
    }

    private fun deleteAll() {
        val ctx = context ?: run {
            Log.e(TAG, "Context is null, aborting deleteAll")
            return
        }

        try {
            val existingId = TvChannelUtils.getChannelId(ctx, getString(R.string.app_name))
            if (existingId != null) {
                Log.d(TAG, "Channel ID: $existingId")
                TvChannelUtils.deleteStoredPrograms(ctx)
            } else {
                Log.d(TAG, "Channel does not exist")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting programs: ${e.message}")
        }
    }

    override fun pickLayout(): Int? =
        if (isLayout(PHONE)) R.layout.fragment_home else R.layout.fragment_home_tv

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        bottomSheetDialog?.ownShow()
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        val isSearchOverlayVisible = view?.findViewById<View>(R.id.home_plugin_search_overlay)?.isVisible == true
        if (isSearchOverlayVisible) {
            pluginSearchVideoHelper?.play()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            pluginSearchVideoHelper?.pause()
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        (activity as? ComponentActivity)?.detachBackPressedCallback("HomeFragment_BackPress")
        bottomSheetDialog?.ownHide()
        try {
            pluginSearchVideoHelper?.release()
            pluginSearchVideoHelper = null
            pluginSearchJob?.cancel()
        } catch (_: Exception) {}
        super.onDestroyView()
    }

    private val apiChangeClickListener = View.OnClickListener { view ->
        view.context.selectHomepage(currentApiName) { api ->
            homeViewModel.loadAndCancel(api, forceReload = true, fromUI = true)
        }
        /*val validAPIs = view.context?.filterProviderByPreferredMedia()?.toMutableList() ?: mutableListOf()

        validAPIs.add(0, randomApi)
        validAPIs.add(0, noneApi)
        view.popupMenuNoIconsAndNoStringRes(validAPIs.mapIndexed { index, api -> Pair(index, api.name) }) {
            homeViewModel.loadAndCancel(validAPIs[itemId].name)
        }*/
    }

    private var currentApiName: String? = null
    private var toggleRandomButton = false

    private var bottomSheetDialog: BottomSheetDialog? = null
    private var homeMasterAdapter: HomeParentItemAdapterPreview? = null
    private var pluginSearchJob: Job? = null
    private var pluginSearchAdapter: TvPluginSearchAdapter? = null
    private var pluginSearchVideoHelper: TvAmbientVideoHelper? = null
    private var isPluginSearchCardsFocused = false

    var lastSavedHomepage: String? = null

    fun saveHomepageToTV(page: Map<String, HomeViewModel.ExpandableHomepageList>) {
        // No need to update for phone
        if (isLayout(PHONE)) {
            return
        }
        val (name, data) = page.entries.firstOrNull() ?: return
        // Modifying homepage is an expensive operation, and therefore we avoid it at all cost
        if (name == lastSavedHomepage) {
            return
        }
        Log.i(TAG, "Adding programs $name to TV")
        lastSavedHomepage = name
        ioSafe {
            // empty the channel
            deleteAll()
            // insert the program from first array
            addMovies(data.list.list)
        }
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padTop = false,
            padBottom = isLandscape(),
            padLeft = isLayout(TV or EMULATOR)
        )

        // Fix grid
        configEvent.invoke()
    }

    @SuppressLint("SetTextI18n")
    override fun onBindingCreated(binding: FragmentHomeBinding) {
        context?.let { HomeChildItemAdapter.updatePosterSize(it) }
        (activity as? ComponentActivity)?.attachBackPressedCallback("HomeFragment_BackPress") {
            handleTvBackPress(this)
        }
        binding.apply {
            //homeChangeApiLoading.setOnClickListener(apiChangeClickListener)
            //homeChangeApiLoading.setOnClickListener(apiChangeClickListener)
            homeApiFab.setOnClickListener(apiChangeClickListener)
            homeApiFab.setOnLongClickListener {
                if (currentApiName == noneApi.name) return@setOnLongClickListener false
                homeViewModel.loadAndCancel(currentApiName, forceReload = true, fromUI = true)
                showToast(R.string.action_reload, Toast.LENGTH_SHORT)
                true
            }
            homeChangeApi.setOnClickListener(apiChangeClickListener)
            homeSwitchAccount.setOnClickListener {
                activity?.showAccountSelectLinear()
            }

            homeMasterAdapter = HomeParentItemAdapterPreview(
                homeViewModel, accountViewModel
            ).apply {
                onPluginSearchClick = {
                    openPluginSearch()
                }
                onCarouselItemChanged = { item ->
                    updateHomeBackdrop(item)
                }
            }
            homeMasterRecycler.setRecycledViewPool(ParentItemAdapter.sharedPool)
            if (isLayout(TV or EMULATOR)) {
                homeMasterRecycler.setItemViewCacheSize(4)
            }
            initTvBackdrop(binding.root)
            homeMasterRecycler.adapter = homeMasterAdapter
            setupTvPluginSearch(binding)

            if (isLayout(PHONE)) {
                ViewCompat.setOnApplyWindowInsetsListener(homePinnedTopBar) { v, windowInsets ->
                    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                    v.updatePadding(top = insets.top + 52.toPx, bottom = 12.toPx)
                    windowInsets
                }

                try {
                    val decorView = activity?.window?.decorView as? ViewGroup
                    val rootView = decorView?.findViewById<ViewGroup>(android.R.id.content) ?: binding.root
                    val windowBackground = decorView?.background
                    val blurAlgorithm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        eightbitlab.com.blurview.RenderEffectBlur()
                    } else {
                        @Suppress("DEPRECATION")
                        eightbitlab.com.blurview.RenderScriptBlur(requireContext())
                    }

                    fun setupPillBlur(blurView: eightbitlab.com.blurview.BlurView) {
                        blurView.apply {
                            outlineProvider = object : ViewOutlineProvider() {
                                override fun getOutline(view: View, outline: Outline) {
                                    outline.setRoundRect(0, 0, view.width, view.height, 19.toPx.toFloat())
                                }
                            }
                            clipToOutline = true
                            setupWith(rootView, blurAlgorithm)
                                .setFrameClearDrawable(windowBackground)
                                .setBlurRadius(16f)
                                .setOverlayColor(Color.TRANSPARENT)
                                .setBlurAutoUpdate(true)
                        }
                    }

                    setupPillBlur(homeChangeApiBlur)
                    setupPillBlur(homePluginSearchBlur)

                    binding.root.findViewById<eightbitlab.com.blurview.BlurView>(R.id.home_plugin_search_bar_blur)?.let {
                        it.outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, 22.toPx.toFloat())
                            }
                        }
                        it.clipToOutline = true
                        it.setupWith(rootView, blurAlgorithm)
                            .setFrameClearDrawable(windowBackground)
                            .setBlurRadius(16f)
                            .setOverlayColor(Color.TRANSPARENT)
                            .setBlurAutoUpdate(true)
                    }

                    binding.root.findViewById<eightbitlab.com.blurview.BlurView>(R.id.home_plugin_search_back_blur)?.let {
                        it.outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: Outline) {
                                outline.setOval(0, 0, view.width, view.height)
                            }
                        }
                        it.clipToOutline = true
                        it.setupWith(rootView, blurAlgorithm)
                            .setFrameClearDrawable(windowBackground)
                            .setBlurRadius(16f)
                            .setOverlayColor(Color.TRANSPARENT)
                            .setBlurAutoUpdate(true)
                    }
                } catch (t: Throwable) {
                    logError(t)
                }

                homePluginSearchBtn.setOnClickListener {
                    openPluginSearch()
                }

                val searchHeaderGroup = binding.root.findViewById<View>(R.id.home_plugin_search_header_group)
                if (!isLayout(TV or EMULATOR) && searchHeaderGroup != null) {
                    ViewCompat.setOnApplyWindowInsetsListener(searchHeaderGroup) { v, windowInsets ->
                        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                        v.updatePadding(top = insets.top + 16.toPx, bottom = 20.toPx)
                        windowInsets
                    }
                }

                homeHeadProfilePadding.setOnClickListener {
                    activity?.showAccountSelectLinear()
                }

                homeViewModel.currentAccount.observe(viewLifecycleOwner) { currentAccount ->
                    homeHeadProfilePic.loadImage(currentAccount?.image)
                }

                homeScrollToTop.setOnClickListener {
                    homeMasterRecycler.smoothScrollToPosition(0)
                }

                homeMasterRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        val lm = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                        val firstVisiblePos = lm?.findFirstVisibleItemPosition() ?: 0
                        val shouldShow = firstVisiblePos > 0
                        if (shouldShow && !homeScrollToTop.isVisible) {
                            homeScrollToTop.alpha = 0f
                            homeScrollToTop.isVisible = true
                            homeScrollToTop.animate().alpha(1f).setDuration(200).start()
                        } else if (!shouldShow && homeScrollToTop.isVisible) {
                            homeScrollToTop.animate().alpha(0f).setDuration(150).withEndAction {
                                homeScrollToTop.isVisible = false
                            }.start()
                        }
                    }
                })
            }

            homeApiFab.isVisible = false

            homePreviewReloadProvider.setOnClickListener {
                homeViewModel.loadAndCancel(
                    homeViewModel.apiName.value ?: noneApi.name,
                    forceReload = true,
                    fromUI = true
                )
                showToast(R.string.action_reload, Toast.LENGTH_SHORT)
            }

            homePreviewSearchButton.setOnClickListener { _ ->
                // Open blank screen.
                homeViewModel.queryTextSubmit("")
            }

            homePreviewSettingsButton.setOnClickListener { view ->
                val apiName = homeViewModel.apiName.value
                val plugin = APIHolder.getApiFromNameNull(apiName)
                    ?.sourcePlugin?.let { PluginManager.plugins[it] } as? Plugin
                val openSettings = plugin?.openSettings
                if (openSettings != null) {
                    try {
                        val activityContext = view.context.getActivity() ?: view.context
                        openSettings.invoke(activityContext)
                    } catch (e: Throwable) {
                        logError(e)
                    }
                }
            }

            // Load value for toggling Tv layout real time clock. Hide by default at startup
            // set visibility first, to apply a scroll effect later
            context?.let {
                if (isLayout(TV)) {
                    val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
                    val toggleClock =
                        settingsManager.getBoolean(
                            getString(R.string.tv_layout_clock_key),
                            false
                        )
                    binding.homeClock.isVisible = toggleClock
                } else {
                    binding.homeClock.isVisible = false
                }
            }


            homeMasterRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (isLayout(PHONE)) {
                        // Fab is only relevant to Phone
                        if (dy > 0) { //check for scroll down
                            homeApiFab.shrink() // hide
                            homeRandom.shrink()
                        } else if (dy < -5) {
                            if (isLayout(PHONE)) {
                                homeApiFab.extend() // show
                                homeRandom.extend()
                            }
                        }
                    } else {
                        // Header scrolling is only relevant to TV/Emulator
                        // Provider pill is inside fragment_home_head_tv, keep floating compatibility container hidden
                        binding.homeApiHolder.isVisible = false

                        // Move the TV layout real time clock out of the way if enabled
                        if (isLayout(TV) && binding.homeClock.isVisible) {
                            val view = recyclerView.findViewHolderForAdapterPosition(0)?.itemView
                            if (view != null) {
                                val rect = IntArray(2)
                                view.getLocationInWindow(rect)
                                binding.homeClock.isVisible = true
                                binding.homeClock.translationY = rect[1].toFloat() - 60.toPx
                            } else {
                                binding.homeClock.isVisible = false
                            }
                        }
                    }
                    super.onScrolled(recyclerView, dx, dy)
                }
            })

        }

        //Load value for toggling Random button. Hide at startup
        context?.let {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
            toggleRandomButton =
                settingsManager.getBoolean(
                    getString(R.string.random_button_key),
                    false
                )
            binding.homeRandom.visibility = View.GONE
            binding.homeRandomButtonTv.visibility = View.GONE
        }

        observe(homeViewModel.apiName) { apiName ->
            currentApiName = apiName
            lastBackdropUrl = null
            com.lagradost.cloudstream3.utils.CardMetadataManager.onPluginChanged(apiName)
            val displayApiName = context?.getDisplayName(apiName) ?: apiName
            binding.apply {
                homeApiFab.text = displayApiName
                homeChangeApi.text = "$displayApiName ▾"
                val isNone = (apiName == noneApi.name)
                homePreviewReloadProvider.isGone = isNone
                homePreviewSearchButton.isGone = isNone
                if (isNone) {
                    homeChangeApi.nextFocusRightId = R.id.home_preview_search_button
                    homePreviewSearchButton.nextFocusLeftId = R.id.home_change_api
                } else {
                    homeChangeApi.nextFocusRightId = R.id.home_preview_reload_provider
                    homePreviewSearchButton.nextFocusLeftId = R.id.home_preview_reload_provider
                }
            }
            homeMasterAdapter?.updateApiName(apiName)
            activity?.findViewById<TextView>(R.id.home_change_api)?.text = "$displayApiName ▾"
        }

        observe(homeViewModel.page) { data ->
            binding.apply {
                if (isLayout(TV or EMULATOR)) {
                    val plugin = APIHolder.getApiFromNameNull(homeViewModel.apiName.value)
                        ?.sourcePlugin?.let { PluginManager.plugins[it] } as? Plugin
                    homePreviewSettingsButton.isGone = plugin?.openSettings == null
                }

                when (data) {
                    is Resource.Success -> {
                        val d = data.value
                        (homeMasterRecycler.adapter as? ParentItemAdapter)?.submitList(d.values.map {
                            it.copy(
                                list = it.list.copy(list = it.list.list.toMutableList())
                            )
                        })

                        saveHomepageToTV(d)

                        homeLoading.isVisible = false
                        homeLoadingError.isVisible = false
                        homeMasterRecycler.isVisible = true
                        homeLoadingShimmer.stopShimmer()

                        //home_loaded?.isVisible = true
                        if (toggleRandomButton) {
                            val distinct = d.values
                                .flatMap { it.list.list }
                                .distinctBy { it.url }
                            val hasItems = distinct.isNotEmpty()
                            val isPhone = isLayout(PHONE)
                            val randomClickListener = View.OnClickListener {
                                distinct.randomOrNull()?.let { activity.loadSearchResult(it) }
                            }

                            homeRandom.isVisible = isPhone && hasItems
                            homeRandom.setOnClickListener(randomClickListener)
                            homeRandomButtonTv.isVisible = !isPhone && hasItems
                            homeRandomButtonTv.setOnClickListener(randomClickListener)
                        } else {
                            homeRandom.isGone = true
                            homeRandomButtonTv.isGone = true
                        }
                    }
                    //Open browser directly, without a menu.
                    is Resource.Failure -> {
                        homeLoadingShimmer.stopShimmer()
                        homeReloadConnectionerror.setOnClickListener(apiChangeClickListener)
                        homeReloadConnectionOpenInBrowser.setOnClickListener {
                            val currentApi = currentApiName?.let { getApiFromNameNull(it) }
                                ?: homeViewModel.apiName.value?.let { getApiFromNameNull(it) }
                            val mainUrl = currentApi?.mainUrl
                            if (!mainUrl.isNullOrBlank()) {
                                context?.openBrowser(mainUrl)
                            }
                        }

                        homeLoading.isVisible = false
                        homeLoadingError.isVisible = true
                        homeMasterRecycler.isInvisible = true

                        // Based on https://github.com/recloudstream/cloudstream/pull/1438
                        val hasNoNetworkConnection = context?.isNetworkAvailable() == false
                        val isNetworkError = data.isNetworkError

                        // Show the downloads button if we have any sort of network shenanigans
                        homeReloadConnectionGoToDownloads.isVisible =
                            hasNoNetworkConnection || isNetworkError

                        // Only hide the open in browser button if we know this is not network shenanigans related to cs3
                        homeReloadConnectionOpenInBrowser.isGone = hasNoNetworkConnection

                        resultErrorText.text = if (hasNoNetworkConnection) {
                            getString(R.string.no_internet_connection)
                        } else {
                            data.errorString
                        }

                        homeReloadConnectionGoToDownloads.setOnClickListener {
                            activity.navigate(R.id.navigation_downloads)
                        }

                        (homeMasterRecycler.adapter as? ParentItemAdapter)?.apply {
                            submitList(null)
                            clearState()
                        }
                    }

                    is Resource.Loading -> {
                        homeLoadingShimmer.startShimmer()
                        homeLoading.isVisible = true
                        homeLoadingError.isVisible = false
                        homeMasterRecycler.isInvisible = true
                        (homeMasterRecycler.adapter as? ParentItemAdapter)?.apply {
                            submitList(null)
                            clearState()
                        }
                        //home_loaded?.isVisible = false
                    }
                }
            }
        }

        observeNullable(homeViewModel.popup) { item ->
            if (item == null) {
                bottomSheetDialog?.dismissSafe()
                bottomSheetDialog = null
                return@observeNullable
            }

            // don't recreate
            if (bottomSheetDialog != null) {
                return@observeNullable
            }

            val (items, delete) = item

            bottomSheetDialog = activity?.loadHomepageList(items, expandCallback = {
                homeViewModel.expandAndReturn(it)
            }, dismissCallback = {
                homeViewModel.popup(null)
                bottomSheetDialog = null
            }, deleteCallback = delete)
        }

        homeViewModel.reloadStored()
        homeViewModel.loadAndCancel(DataStoreHelper.currentHomePage, false)
        //loadHomePage(false)

        // nice profile pic on homepage
        //home_profile_picture_holder?.isVisible = false
        // just in case

        //TODO READD THIS
        /*for (syncApi in OAuth2Apis) {
            val login = SyncAPI2.loginInfo()
            val pic = login?.profilePicture
            if (home_profile_picture?.setImage(
                    pic,
                    errorImageDrawable = errorProfilePic
                ) == true
            ) {
                home_profile_picture_holder?.isVisible = true
                break
            }
        }*/
    }

    private fun handleTvBackPress(helper: BackPressedCallbackHelper.CallbackHelper) {
        if (view?.findViewById<View>(R.id.home_plugin_search_overlay)?.isVisible == true) {
            if (isLayout(TV or EMULATOR) && isPluginSearchCardsFocused) {
                setPluginSearchCardsFocusedState(false)
                view?.findViewById<View>(R.id.home_plugin_search_input)?.requestFocus()
                return
            }
            closePluginSearch()
            return
        }

        // Only run for TV and EMULATOR for the rest of D-pad back hierarchy
        if (!isLayout(TV or EMULATOR)) {
            helper.runDefault()
            return
        }
        val currentFocus = activity?.currentFocus ?: run {
            helper.runDefault()
            return
        }
        // isInsideRecycler is true when focus is inside home_master_recycler
        var parent = currentFocus.parent
        var isInsideRecycler = false
        while (parent != null) {
            if (parent is View && parent.id == R.id.home_master_recycler) {
                isInsideRecycler = true
                break
            }
            parent = parent.parent
        }
        val changeApiButton = binding?.homeChangeApi
            ?: activity?.findViewById<View>(R.id.home_change_api)
        when {
            // Case 1: Focus is on plugin selector, search button, hero banner, or header buttons -> Move to home navigation rail/navbar
            currentFocus.id == R.id.home_change_api ||
            currentFocus.id == R.id.home_plugin_search_btn ||
            currentFocus.id == R.id.home_preview_play ||
            currentFocus.id == R.id.home_preview_bookmark ||
            currentFocus.id == R.id.home_preview_info ||
            currentFocus.id == R.id.home_preview_reload_provider ||
            currentFocus.id == R.id.home_preview_search_button ||
            currentFocus.id == R.id.home_preview_info_btt -> {
                val navHome = activity?.findViewById<View>(R.id.tv_nav_home)
                    ?: activity?.findViewById<View>(R.id.navigation_home)
                navHome?.requestFocus()
            }
            // Case 2: Focus is within content rows -> Move up to header / hero banner
            isInsideRecycler -> {
                (binding?.homeMasterRecycler?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                val targetFocus = activity?.findViewById<View>(R.id.home_preview_play)
                    ?: changeApiButton
                targetFocus?.post {
                    if (!targetFocus.requestFocus()) {
                        changeApiButton?.requestFocus()
                    }
                }
            }
            // Case 3: Any other location -> Use default back behavior
            else -> helper.runDefault()
        }
    }

    private class TvPluginSearchAdapter(
        private val clickCallback: (SearchClickCallback) -> Unit,
        private val onFocusChanged: (Boolean) -> Unit,
        var spanCount: Int = 5
    ) : NoStateAdapter<SearchResponse>(
        BaseDiffCallback(
            itemSame = { a, b ->
                if (a.id != null || b.id != null) {
                    a.id == b.id
                } else {
                    a.name == b.name
                }
            }
        )
    ) {
        override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemTvPluginSearchCardBinding.inflate(inflater, parent, false)
            return ViewHolderState(binding)
        }

        override fun onBindContent(holder: ViewHolderState<Any>, item: SearchResponse, position: Int) {
            val binding = holder.view as? ItemTvPluginSearchCardBinding ?: return
            val context = binding.root.context
            val isTv = isLayout(TV or EMULATOR)
            val dm = context.resources.displayMetrics
            val currentSpan = if (isTv) 4 else (if (spanCount > 0) spanCount else 3)
            val totalHorizontalPadding = if (isTv) {
                72.toPx + (4 * 16.toPx)
            } else {
                20.toPx + (currentSpan * 12.toPx)
            }
            val colWidth = (dm.widthPixels - totalHorizontalPadding) / currentSpan
            val posterHeight = (colWidth / 0.68f).toInt()
            binding.backgroundCard.apply {
                val lp = layoutParams ?: ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, posterHeight)
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = posterHeight
                layoutParams = lp
            }

            val posterUrl = item.posterUrl
            if (!posterUrl.isNullOrEmpty()) {
                binding.cardPoster.loadImage(posterUrl, item.posterHeaders) {
                    error { com.lagradost.cloudstream3.utils.getImageFromDrawable(context, R.drawable.default_cover) }
                }
            } else {
                binding.cardPoster.loadImage(R.drawable.default_cover)
            }

            com.lagradost.cloudstream3.utils.CardMetadataManager.registerCard(item)
            val meta = com.lagradost.cloudstream3.utils.CardMetadataManager.resolveFromCard(item)
            val cleanItemName = com.lagradost.cloudstream3.utils.CardMetadataManager.cleanTitle(item.name)
            val displayTitle = if (meta.title.isNotBlank()) meta.title else cleanItemName
            binding.cardUnfocusedTitle.text = displayTitle
            binding.cardFocusTitle.text = displayTitle
            val initialSub = com.lagradost.cloudstream3.utils.CardMetadataManager.formatSubtitle(meta.year, meta.score)
            binding.cardFocusSubtitle.text = initialSub
            binding.cardFocusSubtitle.isVisible = !initialSub.isNullOrEmpty()

            if (!isTv) {
                // Mobile Portrait Touch Mode: Exact parity with Home Page content cards (title & rating below card)
                (binding.backgroundCard.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = 0
                binding.backgroundCard.foreground = null
                binding.root.isFocusable = false
                binding.root.isFocusableInTouchMode = false

                // Hide TV-specific centered overlays & shadows (no play icon, no centered dimmer, no white outline)
                binding.cardBottomShadow.isVisible = false
                binding.cardUnfocusedTitle.isVisible = false
                binding.cardRating.isVisible = false
                binding.cardFocusDimmer.isVisible = false
                binding.cardFocusInfo.isVisible = false
                binding.cardPlayIcon.isVisible = false

                binding.root.animate().cancel()
                binding.root.scaleX = 1.0f
                binding.root.scaleY = 1.0f
                binding.root.translationZ = 0f
                binding.root.alpha = 1.0f

                // Show mobile title & subtitle below card
                binding.cardMobileTitle.isVisible = true
                binding.cardMobileTitle.text = displayTitle

                val sub = com.lagradost.cloudstream3.utils.CardMetadataManager.formatSubtitle(meta.year, meta.score)
                binding.cardMobileSubtitle.text = sub
                binding.cardMobileSubtitle.isVisible = !sub.isNullOrEmpty()

                if (meta.year == null || meta.score == null) {
                    com.lagradost.cloudstream3.utils.CardMetadataManager.fetchMetadataAsync(item) { updated ->
                        val updatedTitle = if (updated.title.isNotBlank()) updated.title else com.lagradost.cloudstream3.utils.CardMetadataManager.cleanTitle(item.name)
                        if (updatedTitle.isNotBlank()) {
                            binding.cardMobileTitle.text = updatedTitle
                        }
                        val updatedSub = com.lagradost.cloudstream3.utils.CardMetadataManager.formatSubtitle(updated.year, updated.score)
                        binding.cardMobileSubtitle.text = updatedSub
                        binding.cardMobileSubtitle.isVisible = !updatedSub.isNullOrEmpty()
                    }
                }

                binding.root.setOnClickListener {
                    clickCallback(
                        SearchClickCallback(
                            if (item is DataStoreHelper.ResumeWatchingResult) SEARCH_ACTION_PLAY_FILE else SEARCH_ACTION_LOAD,
                            it,
                            position,
                            item
                        )
                    )
                }
                binding.root.onFocusChangeListener = null
                binding.root.setOnKeyListener(null)
            } else {
                // TV D-pad Remote Focus Mode — always reset to unfocused state on bind.
                // The setOnFocusChangeListener drives the focused visual at runtime.
                // This prevents recycled ViewHolders from carrying over scale / dimmer state
                // from a previously focused card (which looked wrong when focus was on the search bar).
                (binding.backgroundCard.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                    topMargin = 10.toPx
                    bottomMargin = 24.toPx
                }
                binding.cardMobileTitle.isVisible = false
                binding.cardMobileSubtitle.isVisible = false
                binding.backgroundCard.foreground = null
                binding.root.animate().cancel()
                binding.cardFocusDimmer.animate().cancel()
                binding.cardFocusInfo.animate().cancel()
                binding.cardBottomShadow.animate().cancel()
                binding.cardUnfocusedTitle.animate().cancel()
                binding.cardRating.animate().cancel()
                binding.root.scaleX = 1.0f
                binding.root.scaleY = 1.0f
                binding.root.translationZ = 0f
                binding.root.alpha = 1.0f
                binding.cardFocusDimmer.isVisible = false
                binding.cardFocusDimmer.alpha = 0f
                binding.cardFocusInfo.isVisible = false
                binding.cardFocusInfo.alpha = 0f
                binding.cardFocusInfo.translationY = 0f
                binding.cardBottomShadow.isVisible = true
                binding.cardBottomShadow.alpha = 1.0f
                binding.cardUnfocusedTitle.isVisible = true
                binding.cardUnfocusedTitle.alpha = 1.0f
                val cleanScore = meta.score?.replace("★", "")?.trim()?.takeIf { it.isNotBlank() }
                val showRating = !cleanScore.isNullOrBlank()
                binding.cardRating.isVisible = showRating
                if (showRating) {
                    binding.cardRating.text = cleanScore
                    binding.cardRating.alpha = 1.0f
                }

                binding.root.setOnClickListener {
                    clickCallback(
                        SearchClickCallback(
                            if (item is DataStoreHelper.ResumeWatchingResult) SEARCH_ACTION_PLAY_FILE else SEARCH_ACTION_LOAD,
                            it,
                            position,
                            item
                        )
                    )
                }

                binding.root.setOnFocusChangeListener { view, hasFocus ->
                    // Cancel running animations to avoid race conditions during fast traversal
                    view.animate().cancel()
                    binding.cardFocusDimmer.animate().cancel()
                    binding.cardFocusInfo.animate().cancel()
                    binding.cardBottomShadow.animate().cancel()
                    binding.cardUnfocusedTitle.animate().cancel()
                    binding.cardRating.animate().cancel()

                    val duration = 200L
                    val interpolator = DecelerateInterpolator()
                    val targetScale = if (hasFocus) 1.10f else 1.0f
                    val targetZ = if (hasFocus) 12f else 0f

                    binding.backgroundCard.foreground = if (hasFocus) androidx.core.content.ContextCompat.getDrawable(context, R.drawable.outline) else null

                    if (hasFocus) {
                        com.lagradost.cloudstream3.utils.CardMetadataManager.onCardFocused(item)
                        val currentMeta = com.lagradost.cloudstream3.utils.CardMetadataManager.resolveFromCard(item)
                        val cleanCardName = com.lagradost.cloudstream3.utils.CardMetadataManager.cleanTitle(item.name)
                        val focusedTitle = if (currentMeta.title.isNotBlank()) currentMeta.title else cleanCardName
                        if (focusedTitle.isNotBlank()) {
                            binding.cardFocusTitle.text = focusedTitle
                            binding.cardUnfocusedTitle.text = focusedTitle
                        }
                        val focusedSub = com.lagradost.cloudstream3.utils.CardMetadataManager.formatSubtitle(currentMeta.year, currentMeta.score)
                        binding.cardFocusSubtitle.text = focusedSub
                        binding.cardFocusSubtitle.isVisible = !focusedSub.isNullOrEmpty()

                        if (focusedTitle.isBlank() || currentMeta.year == null || currentMeta.score == null) {
                            com.lagradost.cloudstream3.utils.CardMetadataManager.fetchMetadataAsync(item) { updated ->
                                if (binding.root.isFocused) {
                                    val updatedTitle = if (updated.title.isNotBlank()) updated.title else com.lagradost.cloudstream3.utils.CardMetadataManager.cleanTitle(item.name)
                                    if (updatedTitle.isNotBlank()) {
                                        binding.cardFocusTitle.text = updatedTitle
                                        binding.cardUnfocusedTitle.text = updatedTitle
                                    }
                                    val updatedSub = com.lagradost.cloudstream3.utils.CardMetadataManager.formatSubtitle(updated.year, updated.score)
                                    binding.cardFocusSubtitle.text = updatedSub
                                    binding.cardFocusSubtitle.isVisible = !updatedSub.isNullOrEmpty()
                                }
                            }
                        }

                        view.translationZ = 12f
                        view.animate()
                            .scaleX(targetScale)
                            .scaleY(targetScale)
                            .translationZ(targetZ)
                            .alpha(1.0f)
                            .setDuration(duration)
                            .setInterpolator(interpolator)
                            .start()

                        binding.cardFocusDimmer.visibility = View.VISIBLE
                        binding.cardFocusDimmer.alpha = 0f
                        binding.cardFocusDimmer.animate()
                            .alpha(1.0f)
                            .setDuration(duration)
                            .setInterpolator(interpolator)
                            .start()

                        binding.cardFocusInfo.visibility = View.VISIBLE
                        binding.cardFocusInfo.alpha = 0f
                        binding.cardFocusInfo.translationY = 14.toPx.toFloat()
                        binding.cardFocusInfo.animate()
                            .alpha(1.0f)
                            .translationY(0f)
                            .setDuration(duration)
                            .setInterpolator(interpolator)
                            .start()

                        binding.cardBottomShadow.animate().alpha(0f).setDuration(160).withEndAction { binding.cardBottomShadow.visibility = View.GONE }.start()
                        binding.cardUnfocusedTitle.animate().alpha(0f).setDuration(160).withEndAction { binding.cardUnfocusedTitle.visibility = View.GONE }.start()
                        binding.cardRating.animate().alpha(0f).setDuration(160).withEndAction { binding.cardRating.visibility = View.GONE }.start()

                        onFocusChanged(true)
                    } else {
                        view.alpha = 1.0f
                        view.animate()
                            .scaleX(targetScale)
                            .scaleY(targetScale)
                            .translationZ(targetZ)
                            .alpha(1.0f)
                            .setDuration(duration)
                            .setInterpolator(interpolator)
                            .withEndAction {
                                if (!view.isFocused) {
                                    view.translationZ = 0f
                                }
                            }
                            .start()

                        binding.cardFocusDimmer.animate()
                            .alpha(0f)
                            .setDuration(160)
                            .setInterpolator(interpolator)
                            .withEndAction { binding.cardFocusDimmer.visibility = View.GONE }
                            .start()

                        binding.cardFocusInfo.animate()
                            .alpha(0f)
                            .translationY(10.toPx.toFloat())
                            .setDuration(160)
                            .setInterpolator(interpolator)
                            .withEndAction { binding.cardFocusInfo.visibility = View.GONE }
                            .start()

                        binding.cardBottomShadow.visibility = View.VISIBLE
                        binding.cardBottomShadow.alpha = 0f
                        binding.cardBottomShadow.animate().alpha(1.0f).setDuration(duration).setInterpolator(interpolator).start()

                        binding.cardUnfocusedTitle.visibility = View.VISIBLE
                        binding.cardUnfocusedTitle.alpha = 0f
                        binding.cardUnfocusedTitle.animate().alpha(1.0f).setDuration(duration).setInterpolator(interpolator).start()

                        val currentMeta = com.lagradost.cloudstream3.utils.CardMetadataManager.resolveFromCard(item)
                        val cleanScore = currentMeta.score?.replace("★", "")?.trim()?.takeIf { it.isNotBlank() }
                        if (!cleanScore.isNullOrBlank()) {
                            binding.cardRating.text = cleanScore
                            binding.cardRating.visibility = View.VISIBLE
                            binding.cardRating.alpha = 0f
                            binding.cardRating.animate().alpha(1.0f).setDuration(duration).setInterpolator(interpolator).start()
                        }
                    }
                }

                binding.root.setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val span = 4
                        val totalCount = itemCount
                        val rv = (binding.root.parent as? RecyclerView)
                            ?: binding.root.rootView?.findViewById(R.id.home_plugin_search_recycler)
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                val targetPos = position + span
                                if (rv != null) {
                                    if (targetPos < totalCount) {
                                        val targetHolder = rv.findViewHolderForAdapterPosition(targetPos)
                                        if (targetHolder != null && targetHolder.itemView.isAttachedToWindow) {
                                            targetHolder.itemView.requestFocus()
                                        } else {
                                            rv.smoothScrollToPosition(targetPos)
                                            rv.postDelayed({
                                                val h = rv.findViewHolderForAdapterPosition(targetPos)
                                                if (h != null && h.itemView.isAttachedToWindow) {
                                                    h.itemView.requestFocus()
                                                } else {
                                                    rv.postDelayed({
                                                        rv.findViewHolderForAdapterPosition(targetPos)?.itemView?.requestFocus()
                                                    }, 80)
                                                }
                                            }, 220)
                                        }
                                        return@setOnKeyListener true
                                    } else if (position / span < (totalCount - 1) / span) {
                                        val lastPos = totalCount - 1
                                        val targetHolder = rv.findViewHolderForAdapterPosition(lastPos)
                                        if (targetHolder != null && targetHolder.itemView.isAttachedToWindow) {
                                            targetHolder.itemView.requestFocus()
                                        } else {
                                            rv.smoothScrollToPosition(lastPos)
                                            rv.postDelayed({
                                                rv.findViewHolderForAdapterPosition(lastPos)?.itemView?.requestFocus()
                                            }, 220)
                                        }
                                        return@setOnKeyListener true
                                    } else {
                                        return@setOnKeyListener true
                                    }
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (position in 0 until span) {
                                    onFocusChanged(false)
                                    rv?.smoothScrollToPosition(0)
                                    binding.root.rootView?.findViewById<View>(R.id.home_plugin_search_input)?.requestFocus()
                                    return@setOnKeyListener true
                                } else {
                                    val targetPos = position - span
                                    if (rv != null) {
                                        val targetHolder = rv.findViewHolderForAdapterPosition(targetPos)
                                        if (targetHolder != null && targetHolder.itemView.isAttachedToWindow) {
                                            targetHolder.itemView.requestFocus()
                                        } else {
                                            rv.scrollToPosition(targetPos)
                                            rv.post {
                                                val h = rv.findViewHolderForAdapterPosition(targetPos)
                                                if (h != null && h.itemView.isAttachedToWindow) {
                                                    h.itemView.requestFocus()
                                                } else {
                                                    rv.postDelayed({
                                                        rv.findViewHolderForAdapterPosition(targetPos)?.itemView?.requestFocus()
                                                    }, 30)
                                                }
                                            }
                                        }
                                        return@setOnKeyListener true
                                    }
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

    private fun setPluginSearchCardsFocusedState(focused: Boolean) {
        isPluginSearchCardsFocused = focused
    }

    private fun initTvPluginSearchVideo(rootView: View) {
        val textureView = rootView.findViewById<TextureView>(R.id.home_plugin_search_video) ?: return
        pluginSearchVideoHelper?.release()
        val autoPlay = rootView.findViewById<View>(R.id.home_plugin_search_overlay)?.isVisible == true
        val videoRes = R.raw.tv_search_bg
        pluginSearchVideoHelper = TvAmbientVideoHelper(rootView.context).apply {
            attach(textureView, videoRes, autoPlay = autoPlay)
        }
    }

    private fun setupTvPluginSearch(binding: FragmentHomeBinding) {
        val rootView = binding.root
        val isTv = isLayout(TV or EMULATOR)
        initTvPluginSearchVideo(rootView)

        val span = if (isTv) 4 else rootView.context.getSpanCount()

        pluginSearchAdapter = TvPluginSearchAdapter(
            clickCallback = { callback ->
                homeViewModel.click(callback)
            },
            onFocusChanged = { isCardFocused ->
                if (isCardFocused && isTv) {
                    setPluginSearchCardsFocusedState(true)
                }
            },
            spanCount = span
        )

        val searchRecycler = rootView.findViewById<RecyclerView>(R.id.home_plugin_search_recycler)
        val searchInput = rootView.findViewById<android.widget.EditText>(R.id.home_plugin_search_input)
        val searchBar = rootView.findViewById<android.view.View>(R.id.home_plugin_search_bar)
        val searchClear = rootView.findViewById<android.widget.ImageView>(R.id.home_plugin_search_clear)
        val searchBack = rootView.findViewById<android.view.View>(R.id.home_plugin_search_back)

        searchBack?.setOnClickListener {
            closePluginSearch()
        }
        searchBack?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        val navHome = activity?.findViewById<View>(R.id.tv_nav_home)
                            ?: activity?.findViewById<View>(R.id.tv_nav_search)
                        if (navHome != null) {
                            navHome.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        searchInput?.requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        searchRecycler?.apply {
            layoutManager = GridLayoutManager(context, span)
            adapter = pluginSearchAdapter
            clipChildren = true
            clipToPadding = false
        }

        searchInput?.apply {
            setOnFocusChangeListener { _, hasFocus ->
                searchBar?.isActivated = hasFocus
            }

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    val query = text?.toString()?.trim().orEmpty()
                    if (query.isNotEmpty()) {
                        performPluginSearch(query)
                    }
                    true
                } else false
            }

            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        closePluginSearch()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if ((pluginSearchAdapter?.itemCount ?: 0) > 0) {
                            val firstCard = searchRecycler?.findViewHolderForAdapterPosition(0)?.itemView
                            if (firstCard != null) {
                                firstCard.requestFocus()
                            } else {
                                searchRecycler?.scrollToPosition(0)
                                searchRecycler?.post {
                                    searchRecycler?.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                                        ?: searchRecycler?.requestFocus()
                                }
                            }
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.hideSoftInputFromWindow(windowToken, 0)
                        searchBack?.requestFocus()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (selectionStart == 0 || text.isNullOrEmpty()) {
                            searchBack?.requestFocus()
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (searchClear?.isVisible == true && (selectionEnd == (text?.length ?: 0) || text.isNullOrEmpty())) {
                            searchClear?.requestFocus()
                            true
                        } else false
                    }
                    else -> false
                }
            }

            doAfterTextChanged { text ->
                val query = text?.toString()?.trim().orEmpty()
                searchClear?.isVisible = query.isNotEmpty()
                pluginSearchJob?.cancel()
                if (query.isEmpty()) {
                    loadDefaultTrendingItems()
                } else {
                    pluginSearchJob = lifecycleScope.launch {
                        delay(350)
                        performPluginSearch(query)
                    }
                }
            }
        }

        searchClear?.apply {
            setOnClickListener {
                searchInput?.text?.clear()
                searchInput?.requestFocus()
            }
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            searchInput?.requestFocus()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if ((pluginSearchAdapter?.itemCount ?: 0) > 0) {
                                val firstCard = searchRecycler?.findViewHolderForAdapterPosition(0)?.itemView
                                if (firstCard != null) {
                                    firstCard.requestFocus()
                                } else {
                                    searchRecycler?.scrollToPosition(0)
                                    searchRecycler?.post {
                                        searchRecycler?.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                                            ?: searchRecycler?.requestFocus()
                                    }
                                }
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            searchBack?.requestFocus()
                            true
                        }
                        else -> false
                    }
                } else false
            }
        }
    }

    private fun loadDefaultTrendingItems() {
        val rootView = view ?: return
        val sectionTitle = rootView.findViewById<TextView>(R.id.home_plugin_search_section_title)
        val searchLoading = rootView.findViewById<android.widget.ProgressBar>(R.id.home_plugin_search_loading)
        val searchEmpty = rootView.findViewById<TextView>(R.id.home_plugin_search_empty)

        sectionTitle?.setText(R.string.tv_trending_today)
        searchLoading?.isVisible = false

        val pageData = (homeViewModel.page.value as? Resource.Success)?.value
        val defaultItems = pageData?.values?.firstOrNull()?.list?.list
        if (!defaultItems.isNullOrEmpty()) {
            pluginSearchAdapter?.submitList(defaultItems)
            searchEmpty?.isVisible = false
        } else {
            val apiName = homeViewModel.apiName.value ?: currentApiName
            val api = APIHolder.getApiFromNameNull(apiName)
            if (api != null && api.hasMainPage) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val repo = APIRepository(api)
                        val res = repo.getMainPage(1, null)
                        if (res is Resource.Success) {
                            val firstList = res.value.firstOrNull()?.items?.firstOrNull()?.list
                            withContext(Dispatchers.Main) {
                                if (!firstList.isNullOrEmpty()) {
                                    pluginSearchAdapter?.submitList(firstList)
                                    searchEmpty?.isVisible = false
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun openPluginSearch() {
        val rootView = view ?: return
        val overlay = rootView.findViewById<android.view.View>(R.id.home_plugin_search_overlay) ?: return
        val searchBar = rootView.findViewById<android.view.View>(R.id.home_plugin_search_bar)
        val searchBarBlur = rootView.findViewById<android.view.View>(R.id.home_plugin_search_bar_blur)
        val searchInput = rootView.findViewById<android.widget.EditText>(R.id.home_plugin_search_input)

        if (overlay.isVisible) return

        // Start ambient theme shader loop
        pluginSearchVideoHelper?.play()

        // Reset collapse state
        setPluginSearchCardsFocusedState(false)

        // Load trending items if input is empty
        val currentQuery = searchInput?.text?.toString()?.trim().orEmpty()
        if (currentQuery.isEmpty()) {
            loadDefaultTrendingItems()
        }

        val activeApi = APIHolder.getApiFromNameNull(homeViewModel.apiName.value ?: currentApiName)
        if (activeApi != null) {
            searchInput?.hint = "Search in ${activeApi.name}..."
        } else {
            searchInput?.setHint(R.string.tv_search_hint)
        }

        overlay.alpha = 0f
        overlay.isVisible = true
        overlay.animate()
            .alpha(1f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()

        val animTarget = searchBarBlur ?: searchBar
        animTarget?.also {
            it.scaleX = 0.5f
            it.scaleY = 0.8f
            it.alpha = 0f
            it.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        searchInput?.requestFocus()
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    fun closePluginSearch() {
        val rootView = view ?: return
        val overlay = rootView.findViewById<android.view.View>(R.id.home_plugin_search_overlay) ?: return
        val searchInput = rootView.findViewById<android.widget.EditText>(R.id.home_plugin_search_input)

        if (!overlay.isVisible) return

        // Pause background video
        pluginSearchVideoHelper?.pause()

        setPluginSearchCardsFocusedState(false)

        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchInput?.windowToken, 0)

        overlay.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                overlay.isVisible = false
                val searchBtn = binding?.homePluginSearchBtn
                    ?: binding?.homeMasterRecycler?.findViewById<View>(R.id.home_plugin_search_btn)
                    ?: binding?.homeMasterRecycler?.findViewById<View>(R.id.home_change_api)
                searchBtn?.requestFocus()
            }
            .start()
    }

    private fun performPluginSearch(query: String) {
        pluginSearchJob?.cancel()
        val apiName = homeViewModel.apiName.value ?: currentApiName
        val api = APIHolder.getApiFromNameNull(apiName)
        val rootView = view

        val searchEmpty = rootView?.findViewById<android.widget.TextView>(R.id.home_plugin_search_empty)
        val searchLoading = rootView?.findViewById<android.widget.ProgressBar>(R.id.home_plugin_search_loading)
        val sectionTitle = rootView?.findViewById<android.widget.TextView>(R.id.home_plugin_search_section_title)

        sectionTitle?.text = "${getString(R.string.search)}: \"$query\""

        if (api == null) {
            searchEmpty?.isVisible = true
            searchLoading?.isVisible = false
            return
        }

        searchLoading?.isVisible = true
        searchEmpty?.isVisible = false

        pluginSearchJob = lifecycleScope.launch(Dispatchers.IO) {
            val repo = APIRepository(api)
            val res = repo.search(query, 1)
            val items = if (res is Resource.Success) res.value.items else emptyList()
            withContext(Dispatchers.Main) {
                searchLoading?.isVisible = false
                pluginSearchAdapter?.submitList(items)
                searchEmpty?.isVisible = items.isEmpty()
            }
        }
    }

    private var isBackdropImageAActive = true
    private var lastBackdropUrl: String? = null

    private fun initTvBackdrop(rootView: View) {
        val imageA = rootView.findViewById<ImageView>(R.id.home_tv_backdrop_image_a) ?: return
        val imageB = rootView.findViewById<ImageView>(R.id.home_tv_backdrop_image_b) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurEffect = RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
            imageA.setRenderEffect(blurEffect)
            imageB.setRenderEffect(blurEffect)
        }
    }

    private fun updateHomeBackdrop(item: LoadResponse) {
        val rootView = view ?: return
        val posterUrl = if (isLayout(PHONE)) {
            item.posterUrl ?: item.backgroundPosterUrl ?: return
        } else {
            item.backgroundPosterUrl ?: item.posterUrl ?: return
        }
        if (posterUrl == lastBackdropUrl) return
        val isFirstLoad = (lastBackdropUrl == null)
        lastBackdropUrl = posterUrl

        val imageA = rootView.findViewById<ImageView>(R.id.home_tv_backdrop_image_a) ?: return
        val imageB = rootView.findViewById<ImageView>(R.id.home_tv_backdrop_image_b) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurEffect = RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP)
            imageA.setRenderEffect(blurEffect)
            imageB.setRenderEffect(blurEffect)
        }

        if (isFirstLoad) {
            imageA.alpha = 0f
            imageB.alpha = 0f
            imageA.loadImage(posterUrl, item.posterHeaders) {
                size(320, 180)
            }
            imageA.animate().alpha(1.0f).setDuration(800).start()
            isBackdropImageAActive = true
            return
        }

        val incomingView = if (isBackdropImageAActive) imageB else imageA
        val outgoingView = if (isBackdropImageAActive) imageA else imageB

        incomingView.alpha = 0f
        incomingView.loadImage(posterUrl, item.posterHeaders) {
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
}

