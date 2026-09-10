package com.lagradost.cloudstream3.ui.settings

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.MainSettingsBinding
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.AuthRepo
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.account.AccountHelper.showAccountSelectLinear
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.errorProfilePic
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.GitInfo.currentCommitHash
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.clipboardHelper
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.getImageFromDrawable
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.ui.utils.TvAmbientVideoHelper
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SettingsFragment : BaseFragment<MainSettingsBinding>(
    BaseFragment.BindingCreator.Bind(MainSettingsBinding::bind)
) {
    override fun pickLayout(): Int? =
        if (isLayout(TV or EMULATOR)) R.layout.fragment_settings_tv else R.layout.main_settings

    private var ambientVideoHelper: TvAmbientVideoHelper? = null

    private val onAccountReload = { _: Boolean ->
        activity?.runOnUiThread {
            binding?.let { updateProfileDisplay(it) }
        }
        Unit
    }

    override fun onResume() {
        super.onResume()
        binding?.root?.apply {
            clearAnimation()
            alpha = 1.0f
            scaleX = 1.0f
            scaleY = 1.0f
            translationX = 0f
            translationY = 0f
            visibility = View.VISIBLE
        }
        binding?.let { updateProfileDisplay(it) }
        ambientVideoHelper?.play()
    }

    override fun onPause() {
        super.onPause()
        ambientVideoHelper?.pause()
    }

    override fun onDestroyView() {
        MainActivity.reloadAccountEvent -= onAccountReload
        try {
            ambientVideoHelper?.release()
            ambientVideoHelper = null
        } catch (_: Exception) {}
        super.onDestroyView()
    }

    private fun hasProfilePictureFromAccountManagers(accountManagers: Array<AuthRepo>, binding: MainSettingsBinding): Boolean {
        for (syncApi in accountManagers) {
            val login = syncApi.authUser()
            val pic = login?.profilePicture ?: continue

            binding.settingsProfilePic.let { imageView ->
                imageView.loadImage(pic) {
                    error { getImageFromDrawable(context ?: return@error null, errorProfilePic) }
                }
            }
            binding.settingsProfileText.text = login.name
            return true
        }
        return false
    }

    private fun updateProfileDisplay(binding: MainSettingsBinding) {
        if (!hasProfilePictureFromAccountManagers(AccountManager.allApis, binding)) {
            val act = activity ?: return
            val currentAccount = try {
                DataStoreHelper.accounts.firstOrNull {
                    it.keyIndex == DataStoreHelper.selectedKeyIndex
                } ?: DataStoreHelper.getDefaultAccount(act)
            } catch (t: Throwable) {
                null
            }

            binding.settingsProfilePic.loadImage(currentAccount?.image)
            binding.settingsProfileText.text = currentAccount?.name ?: getString(R.string.account)
        }
    }

    private fun initTvSettingsVideo(rootView: View) {
        val textureView = rootView.findViewById<TextureView>(R.id.tv_settings_video) ?: return
        ambientVideoHelper?.release()
        ambientVideoHelper = TvAmbientVideoHelper(rootView.context).apply {
            attach(textureView, R.raw.tv_search_bg, autoPlay = true)
        }
    }
    companion object {
        fun PreferenceFragmentCompat?.getPref(id: Int): Preference? {
            if (this == null) return null
            return try {
                findPreference(getString(id))
            } catch (e: Exception) {
                logError(e)
                null
            }
        }

        /**
         * Hide many Preferences on selected layouts.
         **/
        fun PreferenceFragmentCompat?.hidePrefs(ids: List<Int>, layoutFlags: Int) {
            if (this == null) return

            try {
                ids.forEach {
                    getPref(it)?.isVisible = !isLayout(layoutFlags)
                }
            } catch (e: Exception) {
                logError(e)
            }
        }

        /**
         * Hide the [Preference] on selected layouts.
         * @return [Preference] if visible otherwise null.
         *
         * [hideOn] is usually followed by some actions on the preference which are mostly
         * unnecessary when the preference is disabled for the said layout thus returning null.
         **/
        fun Preference?.hideOn(layoutFlags: Int): Preference? {
            if (this == null) return null
            this.isVisible = !isLayout(layoutFlags)
            return if(this.isVisible) this else null
        }

        /**
         * On TV you cannot properly scroll to the bottom of settings, this fixes that.
         * */
        fun PreferenceFragmentCompat.setPaddingBottom() {
            if (isLayout(TV or EMULATOR)) {
                listView?.setPadding(16.toPx, 12.toPx, 16.toPx, 32.toPx)
                listView?.clipToPadding = true
                listView?.clipChildren = true
            }
        }

        fun PreferenceFragmentCompat.setToolBarScrollFlags() {
            if (isLayout(TV or EMULATOR)) {
                val settingsAppbar = view?.findViewById<MaterialToolbar>(R.id.settings_toolbar)

                settingsAppbar?.updateLayoutParams<AppBarLayout.LayoutParams> {
                    scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
                }
            }
        }

        fun Fragment?.setToolBarScrollFlags() {
            if (isLayout(TV or EMULATOR)) {
                val settingsAppbar = this?.view?.findViewById<MaterialToolbar>(R.id.settings_toolbar)

                settingsAppbar?.updateLayoutParams<AppBarLayout.LayoutParams> {
                    scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
                }
            }
        }

        fun Fragment?.setUpToolbar(title: String) {
            if (this == null) return
            val settingsToolbar = view?.findViewById<MaterialToolbar>(R.id.settings_toolbar) ?: return

            settingsToolbar.apply {
                setTitle(title)
                if (isLayout(PHONE or EMULATOR)) {
                    setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
                    setNavigationOnClickListener {
                        activity?.onBackPressedDispatcher?.onBackPressed()
                    }
                }
            }
        }

        fun Fragment?.setUpToolbar(@StringRes title: Int) {
            if (this == null) return
            val settingsToolbar = view?.findViewById<MaterialToolbar>(R.id.settings_toolbar) ?: return

            settingsToolbar.apply {
                setTitle(title)
                if (isLayout(PHONE or EMULATOR)) {
                    setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
                    children.firstOrNull { it is ImageView }?.tag = getString(R.string.tv_no_focus_tag)
                    setNavigationOnClickListener {
                        safe { activity?.onBackPressedDispatcher?.onBackPressed() }
                    }
                }
            }
        }

        fun Fragment.setSystemBarsPadding() {
            view?.let {
                fixSystemBarsPadding(
                    it,
                    padLeft = isLayout(TV or EMULATOR),
                    padBottom = isLandscape()
                )
            }
        }

        fun getFolderSize(dir: File): Long {
            var size: Long = 0
            dir.listFiles()?.let {
                for (file in it) {
                    size += if (file.isFile) {
                        // System.out.println(file.getName() + " " + file.length());
                        file.length()
                    } else getFolderSize(file)
                }
            }

            return size
        }
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padBottom = isLandscape(),
            padLeft = isLayout(TV or EMULATOR)
        )
    }

    override fun onBindingCreated(binding: MainSettingsBinding) {
        fun navigate(id: Int) {
            activity?.navigate(id, Bundle())
        }

        /** used to debug leaks
        showToast(activity,"${VideoDownloadManager.downloadStatusEvent.size} :
        ${VideoDownloadManager.downloadProgressEvent.size}") **/

        MainActivity.reloadAccountEvent += onAccountReload
        updateProfileDisplay(binding)

        binding.root.apply {
            clearAnimation()
            alpha = 1.0f
            scaleX = 1.0f
            scaleY = 1.0f
            translationX = 0f
            translationY = 0f
            visibility = View.VISIBLE
        }

        val isTv = isLayout(TV or EMULATOR)
        initTvSettingsVideo(binding.root)
        if (isTv) {
            // Back button
            binding.root.findViewById<View?>(R.id.tv_settings_back)?.setOnClickListener {
                activity?.onBackPressedDispatcher?.onBackPressed()
            }
        }

        binding.apply {
            settingsProfile.apply {
                setOnClickListener {
                    activity?.showAccountSelectLinear()
                }
                isFocusable = isTv
                isFocusableInTouchMode = false
            }

            listOf(
                settingsGeneral to R.id.action_navigation_global_to_navigation_settings_general,
                settingsPlayer to R.id.action_navigation_global_to_navigation_settings_player,
                settingsCredits to R.id.action_navigation_global_to_navigation_settings_account,
                settingsUi to R.id.action_navigation_global_to_navigation_settings_ui,
                settingsProviders to R.id.action_navigation_global_to_navigation_settings_providers,
                settingsUpdates to R.id.action_navigation_global_to_navigation_settings_updates,
                settingsExtensions to R.id.action_navigation_global_to_navigation_settings_extensions,
            ).forEach { (view, navigationId) ->
                view.apply {
                    setOnClickListener {
                        navigate(navigationId)
                    }
                    isFocusable = isTv
                    isFocusableInTouchMode = false
                }
            }

            // Default focus on TV to settings_profile only if navigation rail / top bar is not currently focused
            if (isTv) {
                if (activity?.findViewById<View>(R.id.nav_rail_view)?.hasFocus() != true &&
                    activity?.findViewById<View>(R.id.tv_top_bar)?.hasFocus() != true
                ) {
                    settingsProfile.requestFocus()
                }
            } else if (activity?.findViewById<View>(R.id.nav_rail_view)?.hasFocus() != true) {
                settingsGeneral.requestFocus()
            }
        }

        val appVersion = BuildConfig.VERSION_NAME
        val commitHash = activity?.currentCommitHash() ?: ""
        val buildTimestamp = SimpleDateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM,
            Locale.getDefault()
        ).apply { timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(BuildConfig.BUILD_DATE)).replace("UTC", "")

        binding.appVersion.text = appVersion
        binding.buildDate.text = buildTimestamp
        binding.commitHash.text = commitHash
        binding.appVersionInfo.setOnLongClickListener {
            clipboardHelper(txt(R.string.extension_version), "$appVersion $commitHash $buildTimestamp")
            true
        }
    }
}
