package com.lagradost.cloudstream3.ui.download

import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentChildDownloadsBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import android.view.TextureView
import com.lagradost.cloudstream3.ui.utils.TvAmbientVideoHelper
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.setAppBarNoScrollFlagsOnTV
import com.lagradost.cloudstream3.utils.UIHelper.toPx

class DownloadChildFragment : BaseFragment<FragmentChildDownloadsBinding>(
    BaseFragment.BindingCreator.Bind(FragmentChildDownloadsBinding::bind)
) {

    private val downloadViewModel: DownloadViewModel by activityViewModels()
    private var tvAmbientVideoHelper: TvAmbientVideoHelper? = null

    override fun pickLayout(): Int? =
        if (isLayout(TV or EMULATOR)) R.layout.fragment_child_downloads_tv else R.layout.fragment_child_downloads

    companion object {
        fun newInstance(headerName: String, folder: String): Bundle {
            return Bundle().apply {
                putString("folder", folder)
                putString("name", headerName)
            }
        }
    }

    override fun onDestroyView() {
        activity?.detachBackPressedCallback("Downloads")
        downloadViewModel.clearChildren()
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

    override fun onBindingCreated(binding: FragmentChildDownloadsBinding) {
        val folder = arguments?.getString("folder")
        val name = arguments?.getString("name")
        if (folder == null) {
            dispatchBackPressed()
            return
        }

        context?.let { downloadViewModel.updateChildList(it, folder) }

        val textureView = binding.root.findViewById<TextureView>(R.id.tv_child_downloads_video)
        if (textureView != null) {
            tvAmbientVideoHelper?.release()
            tvAmbientVideoHelper = TvAmbientVideoHelper(binding.root.context).apply {
                attach(textureView, R.raw.tv_search_bg, autoPlay = true)
            }
        }

        if (isLayout(TV or EMULATOR)) {
            binding.root.findViewById<View>(R.id.download_child_tv_back)?.setOnClickListener {
                dispatchBackPressed()
            }
        } else {
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
                val insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                val targetTop = insets.top + 6.toPx
                val topAppBar = binding.downloadChildToolbar.parent as? View
                if (topAppBar != null && topAppBar.paddingTop != targetTop) {
                    topAppBar.setPadding(0, targetTop, 0, 0)
                }

                val navBarClearance = 82.toPx + insets.bottom
                val targetListPadding = navBarClearance + 60.toPx
                if (binding.downloadChildList.paddingBottom != targetListPadding) {
                    binding.downloadChildList.setPadding(
                        binding.downloadChildList.paddingLeft,
                        binding.downloadChildList.paddingTop,
                        binding.downloadChildList.paddingRight,
                        targetListPadding
                    )
                }
                windowInsets
            }
        }

        binding.downloadChildToolbar.apply {
            title = name
            if (isLayout(PHONE or EMULATOR)) {
                setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
                setNavigationOnClickListener {
                    dispatchBackPressed()
                }
            }
            setAppBarNoScrollFlagsOnTV()
        }

        binding.downloadDeleteAppbar.setAppBarNoScrollFlagsOnTV()

        observe(downloadViewModel.childCards) { cards ->
            when (cards) {
                is Resource.Success -> {
                    if (cards.value.isEmpty()) {
                        dispatchBackPressed()
                    }
                    (binding.downloadChildList.adapter as? DownloadAdapter)?.submitList(cards.value)
                }

                else -> {
                    (binding.downloadChildList.adapter as? DownloadAdapter)?.submitList(null)
                }
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
                val allSelected = downloadViewModel.isAllChildrenSelected()
                if (allSelected) {
                    downloadViewModel.clearSelectedItems()
                } else {
                    downloadViewModel.selectAllChildren()
                }
            }
        }

        observeNullable(downloadViewModel.selectedItemIds) { selection ->
            val isMultiDeleteState = selection != null
            val adapter = binding.downloadChildList.adapter as? DownloadAdapter
            adapter?.setIsMultiDeleteState(isMultiDeleteState)
            binding.downloadDeleteAppbar.isVisible = isMultiDeleteState
            binding.downloadChildToolbar.isGone = isMultiDeleteState

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

            val allSelected = downloadViewModel.isAllChildrenSelected()
            if (allSelected) {
                binding.btnToggleAll.setText(R.string.deselect_all)
            } else binding.btnToggleAll.setText(R.string.select_all)
        }

        val adapter = DownloadAdapter(
            {},
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

        binding.downloadChildList.apply {
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            this.adapter = adapter
            if (isLayout(TV or EMULATOR)) {
                layoutManager = androidx.recyclerview.widget.GridLayoutManager(context, 4)
                clipChildren = false
                clipToPadding = false
            } else {
                setLinearListLayout(
                    isHorizontal = false,
                    nextRight = FOCUS_SELF,
                    nextDown = FOCUS_SELF,
                )
            }
        }
    }

    private fun updateDeleteButton(count: Int, selectedBytes: Long) {
        val formattedSize = formatShortFileSize(context, selectedBytes)
        binding?.btnDelete?.text =
            getString(R.string.delete_format).format(count, formattedSize)
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