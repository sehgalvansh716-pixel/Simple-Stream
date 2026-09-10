package com.lagradost.cloudstream3.ui.download

import android.app.Activity
import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.os.Build
import android.text.format.Formatter.formatShortFileSize
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentDownloadsBinding
import com.lagradost.cloudstream3.databinding.StreamInputBinding
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.download.queue.DownloadQueueViewModel
import com.lagradost.cloudstream3.ui.player.BasicLink
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.LinkGenerator
import com.lagradost.cloudstream3.ui.player.OfflinePlaybackHelper.playUri
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.loadResult
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DataStore.getFolderName
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.setAppBarNoScrollFlagsOnTV
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import android.view.TextureView
import android.view.ViewGroup
import com.lagradost.cloudstream3.services.DownloadQueueService
import com.lagradost.cloudstream3.ui.utils.TvAmbientVideoHelper
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import java.net.URI

const val DOWNLOAD_NAVIGATE_TO = "downloadpage"

class DownloadFragment : BaseFragment<FragmentDownloadsBinding>(
    BaseFragment.BindingCreator.Bind(FragmentDownloadsBinding::bind)
) {

    private val downloadViewModel: DownloadViewModel by activityViewModels()
    private val downloadQueueViewModel: DownloadQueueViewModel by activityViewModels()
    private var tvAmbientVideoHelper: TvAmbientVideoHelper? = null

    override fun pickLayout(): Int? =
        if (isLayout(TV or EMULATOR)) R.layout.fragment_downloads_tv else R.layout.fragment_downloads

    private fun View.setLayoutWidth(weight: Long) {
        val param = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            maxOf((weight / 1000000000f), 0.1f) // 100mb
        )
        this.layoutParams = param
    }

    override fun onDestroyView() {
        activity?.detachBackPressedCallback("Downloads")
        tvAmbientVideoHelper?.release()
        tvAmbientVideoHelper = null
        super.onDestroyView()
    }

    override fun fixLayout(view: View) {
        if (isLayout(TV or EMULATOR)) {
            fixSystemBarsPadding(
                view,
                padBottom = isLandscape(),
                padLeft = true
            )
        }
    }

    override fun onBindingCreated(binding: FragmentDownloadsBinding) {
        hideKeyboard()
        binding.downloadAppbar.setAppBarNoScrollFlagsOnTV()
        binding.downloadDeleteAppbar.setAppBarNoScrollFlagsOnTV()

        val textureView = binding.root.findViewById<TextureView>(R.id.tv_downloads_video)
        if (textureView != null) {
            tvAmbientVideoHelper?.release()
            tvAmbientVideoHelper = TvAmbientVideoHelper(binding.root.context).apply {
                attach(textureView, R.raw.tv_search_bg, autoPlay = true)
            }
        }

        if (isLayout(TV or EMULATOR)) {
            binding.root.findViewById<View>(R.id.download_tv_back)?.setOnClickListener {
                activity?.onBackPressedDispatcher?.onBackPressed()
            }
        } else {
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
                val insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                val targetTop = insets.top + 6.toPx
                val topAppBar = binding.downloadAppbar.parent as? View
                if (topAppBar != null && topAppBar.paddingTop != targetTop) {
                    topAppBar.setPadding(0, targetTop, 0, 0)
                }

                val navBarClearance = 82.toPx + insets.bottom
                val fabBottomMargin = navBarClearance + 16.toPx
                val parentFab = binding.downloadStreamButton.parent as? ViewGroup
                val lp = parentFab?.layoutParams as? ViewGroup.MarginLayoutParams
                if (lp != null && lp.bottomMargin != fabBottomMargin) {
                    lp.bottomMargin = fabBottomMargin
                    parentFab.layoutParams = lp
                }

                val targetListPadding = navBarClearance + 60.toPx
                if (binding.downloadList.paddingBottom != targetListPadding) {
                    binding.downloadList.setPadding(0, 0, 0, targetListPadding)
                }
                windowInsets
            }
        }

        binding.root.findViewById<View>(R.id.btn_tv_pause_all)?.setOnClickListener {
            val instances = DownloadQueueService.downloadInstances.value
            instances.forEach { instance ->
                val id = instance.downloadQueueWrapper.id
                VideoDownloadManager.downloadEvent.invoke(
                    Pair(id, VideoDownloadManager.DownloadActionType.Pause)
                )
            }
            showToast(R.string.pause, Toast.LENGTH_SHORT)
        }

        binding.root.findViewById<View>(R.id.btn_tv_resume_all)?.setOnClickListener {
            val instances = DownloadQueueService.downloadInstances.value
            instances.forEach { instance ->
                val id = instance.downloadQueueWrapper.id
                VideoDownloadManager.downloadEvent.invoke(
                    Pair(id, VideoDownloadManager.DownloadActionType.Resume)
                )
            }
            showToast(R.string.resume, Toast.LENGTH_SHORT)
        }

        binding.root.findViewById<View>(R.id.btn_tv_clear_completed)?.setOnClickListener {
            DownloadQueueManager.removeAllFromQueue()
            showToast("Queue Cleared", Toast.LENGTH_SHORT)
        }

        observe(downloadViewModel.headerCards) { cards ->
            when (cards) {
                is Resource.Success -> {
                    val isEmpty = cards.value.isEmpty()
                    (binding.downloadList.adapter as? DownloadAdapter)?.submitList(cards.value)
                    binding.textNoDownloads.isVisible = isEmpty
                    view?.findViewById<View>(R.id.tv_empty_downloads_card)?.isVisible = isEmpty
                    binding.downloadLoading.isVisible = false
                    binding.downloadList.isVisible = true

                    val targetUp = if (isEmpty) R.id.download_stream_button_tv else R.id.download_list
                    val targetDown = if (isEmpty) R.id.download_queue_button else R.id.download_list
                    binding.downloadQueueButton.nextFocusUpId = targetUp
                    binding.downloadStreamButtonTv.nextFocusDownId = targetDown
                    binding.downloadAppbar.nextFocusDownId = targetDown
                }

                is Resource.Loading -> {
                    binding.downloadList.isVisible = false
                    binding.downloadLoading.isVisible = true
                    binding.textNoDownloads.isVisible = false
                    view?.findViewById<View>(R.id.tv_empty_downloads_card)?.isVisible = false
                }

                is Resource.Failure -> {
                    binding.downloadList.isVisible = true
                    binding.downloadLoading.isVisible = false
                }
            }
        }

        observe(downloadViewModel.availableBytes) {
            updateStorageInfo(
                binding.root.context,
                it,
                R.string.free_storage,
                binding.downloadFreeTxt,
                binding.downloadFree
            )
        }
        observe(downloadViewModel.usedBytes) {
            updateStorageInfo(
                binding.root.context,
                it,
                R.string.used_storage,
                binding.downloadUsedTxt,
                binding.downloadUsed
            )

            val hasBytes = it > 0
            if (hasBytes) {
                binding.downloadLoadingBytes.stopShimmer()
            } else binding.downloadLoadingBytes.startShimmer()

            binding.downloadBytesBar.isVisible = hasBytes
            binding.downloadLoadingBytes.isGone = hasBytes
        }
        observe(downloadViewModel.downloadBytes) {
            updateStorageInfo(
                binding.root.context,
                it,
                R.string.app_storage,
                binding.downloadAppTxt,
                binding.downloadApp
            )
        }
        observe(downloadQueueViewModel.childCards) { cards ->
            val size = cards.currentDownloads.size + cards.queue.size
            val context = binding.root.context
            val baseText = context.getString(R.string.download_queue)
            binding.downloadQueueText.text = if (size > 0) {
                "$baseText (${cards.currentDownloads.size}/$size)"
            } else {
                baseText
            }
        }

        observe(downloadViewModel.selectedBytes) {
            updateDeleteButton(downloadViewModel.selectedItemIds.value?.count() ?: 0, it)
        }

        binding.apply {
            btnDelete.setOnClickListener { view ->
                downloadViewModel.handleMultiDelete(view.context ?: return@setOnClickListener)
            }

            btnCancel.setOnClickListener {
                downloadViewModel.cancelSelection()
            }

            btnToggleAll.setOnClickListener {
                val allSelected = downloadViewModel.isAllHeadersSelected()
                if (allSelected) {
                    downloadViewModel.clearSelectedItems()
                } else {
                    downloadViewModel.selectAllHeaders()
                }
            }
        }

        observeNullable(downloadViewModel.selectedItemIds) { selection ->
            val isMultiDeleteState = selection != null
            val adapter = binding.downloadList.adapter as? DownloadAdapter
            adapter?.setIsMultiDeleteState(isMultiDeleteState)
            binding.downloadDeleteAppbar.isVisible = isMultiDeleteState
            binding.downloadAppbar.isGone = isMultiDeleteState

            if (selection == null) {
                activity?.detachBackPressedCallback("Downloads")
                return@observeNullable
            }
            activity?.attachBackPressedCallback("Downloads") {
                downloadViewModel.cancelSelection()
            }
            updateDeleteButton(selection.count(), downloadViewModel.selectedBytes.value ?: 0L)

            binding.btnDelete.isVisible = selection.isNotEmpty()
            binding.selectItemsText.isVisible = selection.isEmpty()

            val allSelected = downloadViewModel.isAllHeadersSelected()
            if (allSelected) {
                binding.btnToggleAll.setText(R.string.deselect_all)
            } else binding.btnToggleAll.setText(R.string.select_all)
        }

        val adapter = DownloadAdapter(
            { click -> handleItemClick(click) },
            { click ->
                if (click.action == DOWNLOAD_ACTION_DELETE_FILE) {
                    context?.let { ctx ->
                        downloadViewModel.handleSingleDelete(ctx, click.data.id)
                    }
                } else handleDownloadClick(click)
            },
            { itemId, isChecked ->
                if (isChecked) {
                    downloadViewModel.addSelected(itemId)
                } else downloadViewModel.removeSelected(itemId)
            }
        )

        binding.downloadList.apply {
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            this.adapter = adapter
            if (isLayout(TV or EMULATOR)) {
                layoutManager = androidx.recyclerview.widget.GridLayoutManager(context, 5)
                clipChildren = false
                clipToPadding = false
            } else {
                setLinearListLayout(
                    isHorizontal = false,
                    nextRight = FOCUS_SELF,
                    nextDown = R.id.download_queue_button,
                )
            }
        }

        binding.apply {
            openLocalVideoButton.apply {
                isGone = isLayout(TV or EMULATOR)
                setOnClickListener { openLocalVideo() }
            }
            downloadStreamButton.apply {
                isGone = isLayout(TV or EMULATOR)
                setOnClickListener { showStreamInputDialog(it.context) }
            }

            downloadQueueButton.setOnClickListener {
                activity?.navigate(R.id.action_navigation_global_to_navigation_download_queue)
            }

            downloadStreamButtonTv.isFocusable = true
            downloadStreamButtonTv.isFocusableInTouchMode = true
            downloadAppbar.isFocusable = true
            downloadAppbar.isFocusableInTouchMode = true
            downloadQueueButton.isFocusable = true
            downloadQueueButton.isFocusableInTouchMode = true

            downloadStreamButtonTv.setOnClickListener { showStreamInputDialog(it.context) }
            steamImageviewHolder.isVisible = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.downloadList.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                handleScroll(scrollY - oldScrollY)
            }
        }

        context?.let { downloadViewModel.updateHeaderList(it) }
    }

    private fun handleItemClick(click: DownloadHeaderClickEvent) {
        when (click.action) {
            DOWNLOAD_ACTION_GO_TO_CHILD -> {
                if (click.data.type.isEpisodeBased()) {
                    val folder =
                        getFolderName(DOWNLOAD_EPISODE_CACHE, click.data.id.toString())
                    activity?.navigate(
                        R.id.action_navigation_downloads_to_navigation_download_child,
                        DownloadChildFragment.newInstance(click.data.name, folder)
                    )
                }
            }

            DOWNLOAD_ACTION_LOAD_RESULT -> {
                activity?.loadResult(click.data.url, click.data.apiName, click.data.name)
            }
        }
    }

    private fun updateDeleteButton(count: Int, selectedBytes: Long) {
        val formattedSize = formatShortFileSize(context, selectedBytes)
        binding?.btnDelete?.text =
            getString(R.string.delete_format).format(count, formattedSize)
    }

    private fun updateStorageInfo(
        context: Context,
        bytes: Long,
        @StringRes stringRes: Int,
        textView: TextView?,
        view: View?
    ) {
        textView?.text = getString(R.string.storage_size_format).format(
            getString(stringRes),
            formatShortFileSize(context, bytes)
        )
        view?.setLayoutWidth(bytes)
    }

    private fun openLocalVideo() {
        val intent = Intent()
            .setAction(Intent.ACTION_GET_CONTENT)
            .setType("video/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .addFlags(FLAG_GRANT_READ_URI_PERMISSION) // Request temporary access
        safe {
            videoResultLauncher.launch(
                Intent.createChooser(
                    intent,
                    getString(R.string.open_local_video)
                )
            )
        }
    }

    private fun showStreamInputDialog(context: Context) {
        val dialog = Dialog(context, R.style.AlertDialogCustom)
        val binding = StreamInputBinding.inflate(dialog.layoutInflater)
        dialog.setContentView(binding.root)
        dialog.show()

        var preventAutoSwitching = false
        binding.hlsSwitch.setOnClickListener { preventAutoSwitching = true }

        binding.streamReferer.doOnTextChanged { text, _, _, _ ->
            if (!preventAutoSwitching) activateSwitchOnHls(text?.toString(), binding)
        }

        (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip?.getItemAt(
            0
        )?.text?.toString()?.let { copy ->
            val fixedText = copy.trim()
            binding.streamUrl.setText(fixedText)
            activateSwitchOnHls(fixedText, binding)
        }

        binding.applyBtt.setOnClickListener {
            val url = binding.streamUrl.text?.toString()
            if (url.isNullOrEmpty()) {
                showToast(R.string.error_invalid_url, Toast.LENGTH_SHORT)
            } else {
                val referer = binding.streamReferer.text?.toString()
                activity?.navigate(
                    R.id.global_to_navigation_player,
                    GeneratorPlayer.newInstance(
                        LinkGenerator(
                            listOf(BasicLink(url)),
                            extract = true,
                            refererUrl = referer,
                            id = url.hashCode()
                        ), 0
                    )
                )
                dialog.dismissSafe(activity)
            }
        }

        binding.cancelBtt.setOnClickListener {
            dialog.dismissSafe(activity)
        }
    }

    private fun activateSwitchOnHls(text: String?, binding: StreamInputBinding) {
        binding.hlsSwitch.isChecked = safe {
            URI(text).path?.substringAfterLast(".")?.contains("m3u")
        } == true
    }

    private fun handleScroll(dy: Int) {
        if (dy > 0) {
            binding?.downloadStreamButton?.shrink()
        } else if (dy < -5) {
            binding?.downloadStreamButton?.extend()
        }
    }

    // Open local video from files using content provider x safeFile
    private val videoResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val selectedVideoUri = result.data?.data ?: return@registerForActivityResult
        playUri(activity ?: return@registerForActivityResult, selectedVideoUri)
    }

    override fun onResume() {
        super.onResume()
        tvAmbientVideoHelper?.play()
    }

    override fun onPause() {
        tvAmbientVideoHelper?.pause()
        super.onPause()
    }
}