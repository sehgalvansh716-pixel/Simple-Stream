package com.lagradost.cloudstream3.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setSystemBarsPadding
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import com.lagradost.cloudstream3.ui.utils.TvThemeBackgroundHelper
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.txt

/**
 * A base Fragment class that simplifies ViewBinding usage and handles view inflation safely.
 *
 * This class allows two modes of creating ViewBinding:
 * 1. Inflate: Using the standard `inflate()` method provided by generated ViewBinding classes.
 * 2. Bind: Using `bind()` on an existing root view.
 *
 * It also provides hooks for:
 * - Safe initialization of the binding (`onBindingCreated`)
 * - Automatic padding adjustment for system bars (`fixPadding`)
 * - Optional layout resource selection via `pickLayout()`
 *
 * @param T The type of ViewBinding for this Fragment.
 * @param bindingCreator The strategy used to create the binding instance.
 */
private interface BaseFragmentHelper<T : ViewBinding> {
    val bindingCreator: BaseFragment.BindingCreator<T>

    var _binding: T?
    val binding: T? get() = _binding

    fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layoutId = pickLayout()
        val root: View? = layoutId?.let { inflater.inflate(it, container, false) }
        _binding = try {
            when (val creator = bindingCreator) {
                is BaseFragment.BindingCreator.Inflate -> creator.fn(inflater, container, false)
                is BaseFragment.BindingCreator.Bind -> {
                    if (root != null) creator.fn(root)
                    else throw IllegalStateException("Root view is null for bind()")
                }
            }
        } catch (t: Throwable) {
            showToast(
                txt(R.string.unable_to_inflate, t.message ?: ""),
                Toast.LENGTH_LONG
            )
            logError(t)
            null
        }

        return _binding?.root ?: root
    }

    /**
     * Called after the fragment's view has been created.
     *
     * This method is `final` to ensure that the binding is properly initialized and
     * system bar padding adjustments are applied before any subclass logic runs.
     * Subclasses should use [onBindingCreated] instead of overriding this method directly.
     */
    fun onViewReady(view: View, savedInstanceState: Bundle?) {
        fixLayout(view)
        binding?.let { onBindingCreated(it, savedInstanceState) }
    }

    /**
     * Called when the binding is safely created and view is ready.
     * Can be overridden to provide fragment-specific initialization.
     *
     * @param binding The safely created ViewBinding.
     * @param savedInstanceState Saved state bundle or null.
     */
    fun onBindingCreated(binding: T, savedInstanceState: Bundle?) {
        onBindingCreated(binding)
    }

    /**
     * Called when the binding is safely created and view is ready.
     * Overload without savedInstanceState for convenience.
     *
     * @param binding The safely created ViewBinding.
     */
    fun onBindingCreated(binding: T) {}

    /**
     * Pick a layout resource ID for the fragment.
     *
     * Return `null` by default. Override to provide a layout resource when using
     * `BindingCreator.Bind`. Not needed if using `BindingCreator.Inflate`.
     *
     * @return Layout resource ID or null.
     */
    @LayoutRes
    fun pickLayout(): Int? = null

    /**
     * Ensures the layout of the root view is correctly adjusted for the current configuration.
     *
     * This may include applying padding for system bars, adjusting insets, or performing other
     * layout updates. `fixLayout` should remain idempotent, as it can be called multiple
     * times on the same view, such as during configuration changes (e.g. device rotation) or when
     * the view is recreated.
     *
     * @param view The root view to adjust.
     */
    fun fixLayout(view: View)
}

abstract class BaseFragment<T : ViewBinding>(
    override val bindingCreator: BindingCreator<T>
) : Fragment(), BaseFragmentHelper<T> {
    override var _binding: T? = null

    /** Safer activity?.onBackPressedDispatcher?.onBackPressed() with fallback behavior instead of app crash */
    fun dispatchBackPressed() {
        try {
            activity?.onBackPressedDispatcher?.onBackPressed()
        } catch (_: IllegalStateException) {
            // FragmentManager is already executing transactions, so try again
            delayedDispatchBackPressed(5)
        } catch (t: Throwable) {
            logError(t)
        }
    }

    /** Recursive back press when available */
    private fun delayedDispatchBackPressed(remaining: Int) {
        if (remaining <= 0) return
        binding?.root?.postDelayed({
            try {
                activity?.onBackPressedDispatcher?.onBackPressed()
            } catch (_: IllegalStateException) {
                // FragmentManager is already executing transactions, so try again
                delayedDispatchBackPressed(remaining - 1)
            } catch (t: Throwable) {
                logError(t)
            }
        }, 200)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = createBinding(inflater, container, savedInstanceState)

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onViewReady(view, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        view?.let { v ->
            if (v.alpha < 1.0f) {
                v.clearAnimation()
                v.alpha = 1.0f
            }
            if (v.visibility != View.VISIBLE) {
                v.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Called when the device configuration changes (e.g., orientation).
     * Re-applies system bar padding fixes to the root view to ensure it
     * readjusts for orientation changes.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        view?.let { fixLayout(it) }
    }

    /** Cleans up the binding reference when the view is destroyed to avoid memory leaks. */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Sealed class representing the two strategies for creating a ViewBinding instance.
     */
    sealed class BindingCreator<T : ViewBinding> {

        /**
         * Use the standard inflate() method for creating the binding.
         *
         * @param fn Lambda that inflates the binding.
         */
        class Inflate<T : ViewBinding>(
            val fn: (LayoutInflater, ViewGroup?, Boolean) -> T
        ) : BindingCreator<T>()

        /**
         * Use bind() on an existing root view to create the binding. This should
         * be used if you are differing per device layouts, such as different
         * layouts for TV and Phone.
         *
         * @param fn Lambda that binds the root view.
         */
        class Bind<T : ViewBinding>(
            val fn: (View) -> T
        ) : BindingCreator<T>()
    }
}

abstract class BaseDialogFragment<T : ViewBinding>(
    override val bindingCreator: BaseFragment.BindingCreator<T>
) : DialogFragment(), BaseFragmentHelper<T> {
    override var _binding: T? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = createBinding(inflater, container, savedInstanceState)

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onViewReady(view, savedInstanceState)
    }

    /** @see [BaseFragment.onConfigurationChanged] for documentation. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        view?.let { fixLayout(it) }
    }

    /** Cleans up the binding reference when the view is destroyed to avoid memory leaks. */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

abstract class BaseBottomSheetDialogFragment<T : ViewBinding>(
    override val bindingCreator: BaseFragment.BindingCreator<T>
) : BottomSheetDialogFragment(), BaseFragmentHelper<T> {
    override var _binding: T? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = createBinding(inflater, container, savedInstanceState)

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onViewReady(view, savedInstanceState)
    }

    /** @see [BaseFragment.onConfigurationChanged] for documentation. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        view?.let { fixLayout(it) }
    }

    /** Cleans up the binding reference when the view is destroyed to avoid memory leaks. */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

abstract class BasePreferenceFragmentCompat() : PreferenceFragmentCompat() {
    private var themeBackgroundHelper: TvThemeBackgroundHelper? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSystemBarsPadding()

        val rootContainer = (view as? ViewGroup) ?: view.findViewById<ViewGroup>(R.id.settings_top_root)
        if (rootContainer != null) {
            view.setBackgroundColor(0x00000000)
            rootContainer.clipChildren = false
            rootContainer.clipToPadding = false
            themeBackgroundHelper?.release()
            themeBackgroundHelper = TvThemeBackgroundHelper(requireContext()).apply {
                attachToContainer(rootContainer, 0)
            }
        } else {
            view.setBackgroundColor(0xFF0B0E14.toInt())
        }

        val isTv = isLayout(TV or EMULATOR)
        if (isTv) {

            view.findViewById<View>(R.id.settings_toolbar)?.apply {
                setBackgroundColor(0x00000000)
                setPadding(44.toPx, paddingTop, 44.toPx, paddingBottom)
            }
            view.findViewById<View>(android.R.id.list_container)?.apply {
                background = ContextCompat.getDrawable(context, R.drawable.bg_tv_settings_card)
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                (this as? ViewGroup)?.clipChildren = true
                (this as? ViewGroup)?.clipToPadding = true
                updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    leftMargin = 44.toPx
                    rightMargin = 44.toPx
                    topMargin = 8.toPx
                    bottomMargin = 24.toPx
                }
            }
            listView?.apply {
                setPadding(16.toPx, 12.toPx, 16.toPx, 32.toPx)
                clipToPadding = true
                clipChildren = true
            }
        } else {
            view.setBackgroundColor(0x00000000)
            view.findViewById<View>(R.id.settings_toolbar)?.apply {
                setBackgroundColor(0x00000000)
            }
            view.findViewById<View>(android.R.id.list_container)?.apply {
                background = ContextCompat.getDrawable(context, R.drawable.bg_mobile_settings_card)
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                (this as? ViewGroup)?.clipChildren = true
                (this as? ViewGroup)?.clipToPadding = true
                updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    leftMargin = 14.toPx
                    rightMargin = 14.toPx
                    topMargin = 6.toPx
                    bottomMargin = 14.toPx
                }
            }
            listView?.apply {
                setPadding(12.toPx, 10.toPx, 12.toPx, 88.toPx)
                clipToPadding = false
                clipChildren = false
            }
        }

        listView?.post {
            if (isTv) {
                if (activity?.currentFocus == null || activity?.currentFocus == view) {
                    listView?.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let { v ->
            if (v.alpha < 1.0f) {
                v.clearAnimation()
                v.alpha = 1.0f
            }
            if (v.visibility != View.VISIBLE) {
                v.visibility = View.VISIBLE
            }
        }
        themeBackgroundHelper?.resume()
    }

    override fun onPause() {
        themeBackgroundHelper?.pause()
        super.onPause()
    }

    override fun onDestroyView() {
        themeBackgroundHelper?.release()
        themeBackgroundHelper = null
        super.onDestroyView()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setSystemBarsPadding()
    }
}
