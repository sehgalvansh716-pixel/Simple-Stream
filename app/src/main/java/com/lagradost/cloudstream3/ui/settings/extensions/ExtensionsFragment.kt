package com.lagradost.cloudstream3.ui.settings.extensions

import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity.Companion.afterRepositoryLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.AddRepoInputBinding
import com.lagradost.cloudstream3.databinding.FragmentExtensionsBinding
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setSystemBarsPadding
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.ui.utils.TvAmbientVideoHelper
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import android.view.ViewGroup
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.AppContextUtils.addRepositoryDialog
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideProgress
import com.lagradost.cloudstream3.utils.UIHelper.showProgress
import com.lagradost.cloudstream3.utils.setText

class ExtensionsFragment : BaseFragment<FragmentExtensionsBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentExtensionsBinding::inflate)
) {

    private val extensionViewModel: ExtensionsViewModel by activityViewModels()
    private val pluginViewModel: PluginsViewModel by activityViewModels()
    private var ambientVideoHelper: TvAmbientVideoHelper? = null

    private fun View.setLayoutWidth(weight: Int) {
        val param = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            weight.toFloat()
        )
        this.layoutParams = param
    }

    override fun onResume() {
        super.onResume()
        afterRepositoryLoadedEvent += ::reloadRepositories
        ambientVideoHelper?.play()
    }

    override fun onPause() {
        super.onPause()
        ambientVideoHelper?.pause()
    }

    override fun onStop() {
        super.onStop()
        afterRepositoryLoadedEvent -= ::reloadRepositories
        ambientVideoHelper?.pause()
    }

    override fun onDestroyView() {
        try {
            ambientVideoHelper?.release()
            ambientVideoHelper = null
        } catch (_: Exception) {}
        super.onDestroyView()
    }

    private fun reloadRepositories(success: Boolean = true) {
        extensionViewModel.loadStats()
        extensionViewModel.loadRepositories()
    }

    override fun fixLayout(view: View) {
        setSystemBarsPadding()
    }

    override fun onBindingCreated(binding: FragmentExtensionsBinding) {
        val isTv = isLayout(TV or EMULATOR)

        binding.root.setBackgroundColor(0x00000000)
        binding.tvExtensionsVideo.isVisible = true
        binding.tvExtensionsVideoOverlay.isVisible = true
        ambientVideoHelper?.release()
        ambientVideoHelper = TvAmbientVideoHelper(binding.root.context).apply {
            attach(binding.tvExtensionsVideo, R.raw.tv_search_bg, autoPlay = true)
        }

        if (isTv) {
            binding.settingsToolbar.apply {
                setBackgroundColor(0x00000000)
                setPadding(44.toPx, paddingTop, 44.toPx, paddingBottom)
            }

            binding.repoRecyclerView.apply {
                background = ContextCompat.getDrawable(context, R.drawable.bg_tv_settings_card)
                clipToOutline = true
                updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    leftMargin = 44.toPx
                    rightMargin = 44.toPx
                    topMargin = 12.toPx
                    bottomMargin = 96.toPx
                }
                setPadding(20.toPx, 16.toPx, 20.toPx, 16.toPx)
                clipToPadding = false
            }

            binding.pluginRecyclerView.apply {
                background = ContextCompat.getDrawable(context, R.drawable.bg_tv_settings_card)
                clipToOutline = true
                updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    leftMargin = 44.toPx
                    rightMargin = 44.toPx
                    topMargin = 12.toPx
                    bottomMargin = 96.toPx
                }
                setPadding(20.toPx, 16.toPx, 20.toPx, 16.toPx)
                clipToPadding = false
            }

            binding.pluginStorageAppbar.apply {
                background = ContextCompat.getDrawable(context, R.drawable.bg_tv_settings_category_card)
                clipToOutline = true
                updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    leftMargin = 44.toPx
                    rightMargin = 44.toPx
                    bottomMargin = 14.toPx
                    height = 72.toPx
                }
                elevation = 8.toPx.toFloat()
                setPadding(20.toPx, 8.toPx, 20.toPx, 8.toPx)
                isFocusable = true
                isFocusableInTouchMode = false
                nextFocusRightId = R.id.add_repo_button_imageview
                nextFocusUpId = R.id.repo_recycler_view
                nextFocusDownId = id
            }

            binding.addRepoButtonImageview.apply {
                background = ContextCompat.getDrawable(context, R.drawable.bg_tv_card_action_btn)
                setPadding(10.toPx, 10.toPx, 10.toPx, 10.toPx)
                isFocusable = true
                isFocusableInTouchMode = false
                nextFocusLeftId = R.id.plugin_storage_appbar
                nextFocusUpId = R.id.repo_recycler_view
                nextFocusDownId = id
            }
        } else {
            binding.root.setBackgroundColor(0x00000000)
            binding.settingsToolbar.apply {
                setBackgroundColor(0x00000000)
            }
            binding.blankRepoScreen.apply {
                setBackgroundColor(0x00000000)
            }
            binding.pluginStorageAppbar.apply {
                background = ContextCompat.getDrawable(context, R.drawable.bg_mobile_glass_card)
                updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    leftMargin = 14.toPx
                    rightMargin = 14.toPx
                    bottomMargin = 108.toPx
                    height = 72.toPx
                }
                elevation = 6.toPx.toFloat()
                setPadding(16.toPx, 8.toPx, 16.toPx, 8.toPx)
            }
            binding.addRepoButton.translationY = -196.toPx.toFloat()
            binding.repoRecyclerView.apply {
                setBackgroundColor(0x00000000)
                setPadding(0, 8.toPx, 0, 260.toPx)
                clipToPadding = false
            }
            binding.pluginRecyclerView.apply {
                setBackgroundColor(0x00000000)
                setPadding(0, 8.toPx, 0, 260.toPx)
                clipToPadding = false
            }
            binding.addRepoButtonImageview.apply {
                background = ContextCompat.getDrawable(context, R.drawable.bg_mobile_search_circle_glass)
                imageTintList = android.content.res.ColorStateList.valueOf(0xFF818CF8.toInt())
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
                val insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                val navBarClearance = 82.toPx + insets.bottom
                val storageCardBottom = navBarClearance + 14.toPx
                val storageLp = binding.pluginStorageAppbar.layoutParams as? ViewGroup.MarginLayoutParams
                if (storageLp != null && storageLp.bottomMargin != storageCardBottom) {
                    storageLp.bottomMargin = storageCardBottom
                    binding.pluginStorageAppbar.layoutParams = storageLp
                }
                val fabBottomOffset = storageCardBottom + 72.toPx + 16.toPx
                val targetTransY = -(fabBottomOffset - 16.toPx).toFloat()
                if (binding.addRepoButton.translationY != targetTransY) {
                    binding.addRepoButton.translationY = targetTransY
                }
                val targetPaddingBottom = fabBottomOffset + 60.toPx
                if (binding.repoRecyclerView.paddingBottom != targetPaddingBottom) {
                    binding.repoRecyclerView.setPadding(0, 8.toPx, 0, targetPaddingBottom)
                }
                if (binding.pluginRecyclerView.paddingBottom != targetPaddingBottom) {
                    binding.pluginRecyclerView.setPadding(0, 8.toPx, 0, targetPaddingBottom)
                }
                windowInsets
            }
        }

        binding.repoRecyclerView.apply {
            setLinearListLayout(
                isHorizontal = false,
                nextUp = R.id.settings_toolbar, // FOCUS_SELF, // back has no id so we cant :pensive:
                nextDown = R.id.plugin_storage_appbar,
                nextRight = FOCUS_SELF,
                nextLeft = if (isLayout(TV or EMULATOR)) FOCUS_SELF else R.id.nav_rail_view
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    val dy = scrollY - oldScrollY
                    if (dy > 0) { // check for scroll down
                        binding.addRepoButton.shrink() // hide
                    } else if (dy < -5) {
                        binding.addRepoButton.extend() // show
                    }
                }
            }
            adapter = RepoAdapter(false, {
                findNavController().navigate(
                    R.id.navigation_settings_extensions_to_navigation_settings_plugins,
                    PluginsFragment.newInstance(it)
                )
            }, { repo ->
                // Prompt user before deleting repo
                main {
                    val uiContext = context ?: binding.root.context
                    val builder = AlertDialog.Builder(uiContext)
                    val dialogClickListener =
                        DialogInterface.OnClickListener { _, which ->
                            when (which) {
                                DialogInterface.BUTTON_POSITIVE -> {
                                    ioSafe {
                                        RepositoryManager.removeRepository(
                                            uiContext.applicationContext,
                                            repo
                                        )
                                        extensionViewModel.loadStats()
                                        extensionViewModel.loadRepositories()
                                    }
                                }

                                DialogInterface.BUTTON_NEGATIVE -> {}
                            }
                        }

                    builder.setTitle(R.string.delete_repository)
                        .setMessage(uiContext.getString(R.string.delete_repository_plugins))
                        .setPositiveButton(R.string.delete, dialogClickListener)
                        .setNegativeButton(R.string.cancel, dialogClickListener)
                        .show().setDefaultFocus()
                }
            })
        }

        observe(extensionViewModel.repositories) { repos ->
            binding.repoRecyclerView.isVisible = repos.isNotEmpty()
            binding.blankRepoScreen.isVisible = repos.isEmpty()
            (binding.repoRecyclerView.adapter as? RepoAdapter)?.submitList(repos.toList())
            pluginViewModel.updatePluginList(binding.root.context, repos.toList())

            if (isTv) {
                binding.repoRecyclerView.post {
                    if (activity?.currentFocus == null || activity?.currentFocus == view) {
                        binding.repoRecyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    }
                }
            }
        }

        observeNullable(extensionViewModel.pluginStats) { value ->
            binding.apply {
                if (value == null) {
                    pluginStorageAppbar.isVisible = false
                    return@observeNullable
                }

                pluginStorageAppbar.isVisible = true
                if (value.total == 0) {
                    pluginDownload.setLayoutWidth(1)
                    pluginDisabled.setLayoutWidth(0)
                    pluginNotDownloaded.setLayoutWidth(0)
                } else {
                    pluginDownload.setLayoutWidth(value.downloaded)
                    pluginDisabled.setLayoutWidth(value.disabled)
                    pluginNotDownloaded.setLayoutWidth(value.notDownloaded)
                }
                pluginNotDownloadedTxt.setText(value.notDownloadedText)
                pluginDisabledTxt.setText(value.disabledText)
                pluginDownloadTxt.setText(value.downloadedText)
            }
        }

        binding.pluginStorageAppbar.setOnClickListener {
            findNavController().navigate(
                R.id.navigation_settings_extensions_to_navigation_settings_plugins,
                PluginsFragment.newLocalInstance(
                    getString(R.string.extensions),
                )
            )
        }

        binding.pluginRecyclerView.apply {
            setLinearListLayout(
                isHorizontal = false,
                nextDown = FOCUS_SELF,
                nextRight = FOCUS_SELF,
            )
            setRecycledViewPool(PluginAdapter.sharedPool)
            adapter =
                PluginAdapter(true) {
                    val urls = extensionViewModel.repositories.value?.toList() ?: emptyList()
                    pluginViewModel.handlePluginAction(activity, urls, it, false)
                }
        }

        observe(pluginViewModel.filteredPlugins) { (scrollToTop, list) ->
            (binding.pluginRecyclerView.adapter as? PluginAdapter)?.submitList(list)
            if (scrollToTop) {
                binding.pluginRecyclerView.scrollToPosition(0)
            }
        }

        binding.settingsToolbar.apply {
            val searchItem = menu?.findItem(R.id.search_button)
            val searchView = searchItem?.actionView as? SearchView

            searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionCollapse(p0: MenuItem): Boolean {
                    binding.pluginRecyclerView.isVisible = false
                    binding.repoRecyclerView.isVisible = true
                    return true

                }

                override fun onMenuItemActionExpand(p0: MenuItem): Boolean {
                    binding.pluginRecyclerView.isVisible = true
                    binding.repoRecyclerView.isVisible = false
                    return true
                }
            })

            // Don't go back if active query
            setNavigationOnClickListener {
                if (searchView?.isIconified == false) {
                    searchView.isIconified = true
                } else {
                    dispatchBackPressed()
                }
            }

            searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    pluginViewModel.search(query)
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    pluginViewModel.search(newText)
                    return true
                }
            })
        }


        val addRepositoryClick = View.OnClickListener {
            val ctx = context ?: return@OnClickListener
            val binding = AddRepoInputBinding.inflate(LayoutInflater.from(ctx), null, false)
            val builder =
                AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                    .setView(binding.root)

            val dialog = builder.create()
            dialog.show()
            (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip?.getItemAt(
                0
            )?.text?.toString()?.let { copiedText ->
                if (copiedText.contains(RepoAdapter.SHAREABLE_REPO_SEPARATOR)) {
                    // text is of format <repository name> : <repository url>
                    val (name, url) = copiedText.split(
                        RepoAdapter.SHAREABLE_REPO_SEPARATOR,
                        limit = 2
                    )
                    binding.repoUrlInput.setText(url.trim())
                    binding.repoNameInput.setText(name.trim())
                } else {
                    binding.repoUrlInput.setText(copiedText)
                }
            }

            binding.applyBtt.setOnClickListener secondListener@{
                val name = binding.repoNameInput.text?.toString()?.trim()
                val urlInput = binding.repoUrlInput.text?.toString()?.trim()

                // Special unlock code — return early before any URL validation or regex checks
                if (urlInput == "1908" || name == "1908") {
                    com.lagradost.cloudstream3.utils.InternalStreamBridge.unlock(ctx)
                    dialog.dismissSafe(activity)
                    showToast("NetMirror activated successfully!", Toast.LENGTH_LONG)
                    extensionViewModel.loadStats()
                    extensionViewModel.loadRepositories()
                    return@secondListener
                }

                if (urlInput.isNullOrEmpty()) {
                    showToast(R.string.error_invalid_url, Toast.LENGTH_SHORT)
                    return@secondListener
                }
                binding.applyBtt.showProgress()
                ioSafe {
                    try {
                        val url = RepositoryManager.parseRepoUrl(urlInput)
                        if (url.isNullOrBlank()) {
                            showToast(R.string.error_invalid_data, Toast.LENGTH_SHORT)
                            return@ioSafe
                        }
                        val repository = RepositoryManager.parseRepository(url)

                        // Exit if wrong repository
                        if (repository == null) {
                            showToast(R.string.no_repository_found_error, Toast.LENGTH_LONG)
                            return@ioSafe
                        }

                        val fixedName = if (!name.isNullOrBlank()) name
                        else repository.name
                        val newRepo = RepositoryData(repository.iconUrl, fixedName, url)
                        RepositoryManager.addRepository(newRepo)
                        extensionViewModel.loadStats()
                        extensionViewModel.loadRepositories()

                        dialog.dismissSafe(activity) // Only dismiss if the repo was added

                        val plugins = RepositoryManager.getRepoPlugins(newRepo)
                        if (plugins.isNullOrEmpty()) {
                            showToast(R.string.no_plugins_found_error, Toast.LENGTH_LONG)
                            return@ioSafe
                        }

                        this@ExtensionsFragment.activity?.addRepositoryDialog(
                            newRepo
                        )
                    } finally {
                        binding.applyBtt.hideProgress()
                    }
                }
            }
            binding.cancelBtt.setOnClickListener {
                dialog.dismissSafe(activity)
            }
        }


        binding.apply {
            addRepoButton.isGone = isTv
            addRepoButtonImageviewHolder.isVisible = isTv

            pluginStorageAppbar.isFocusable = isTv
            pluginStorageAppbar.isFocusableInTouchMode = false
            addRepoButtonImageview.isFocusable = isTv
            addRepoButtonImageview.isFocusableInTouchMode = false

            addRepoButton.setOnClickListener(addRepositoryClick)
            addRepoButtonImageview.setOnClickListener(addRepositoryClick)
        }
        reloadRepositories()
    }
}
