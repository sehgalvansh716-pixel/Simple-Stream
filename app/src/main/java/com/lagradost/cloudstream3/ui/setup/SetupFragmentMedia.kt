package com.lagradost.cloudstream3.ui.setup

import android.view.KeyEvent
import android.view.View
import android.widget.AbsListView
import android.widget.ArrayAdapter
import androidx.core.content.edit
import androidx.core.util.forEach
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.FragmentSetupMediaBinding
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding

class SetupFragmentMedia : BaseFragment<FragmentSetupMediaBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentSetupMediaBinding::inflate)
) {

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(view)
    }

    override fun onBindingCreated(binding: FragmentSetupMediaBinding) {
        safe {
            val ctx = context ?: return@safe
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)

            val arrayAdapter =
                ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

            val names = enumValues<TvType>().sorted().map { it.name }
            val selected = mutableListOf<Int>()

            arrayAdapter.addAll(names)
            binding.apply {
                listview1.let {
                    it.adapter = arrayAdapter
                    it.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE

                    it.setOnItemClickListener { _, _, _, _ ->
                        it.checkedItemPositions?.forEach { key, value ->
                            if (value) {
                                selected.add(key)
                            } else {
                                selected.remove(key)
                            }
                        }
                        val prefValues = selected.mapNotNull { pos ->
                            val item =
                                it.getItemAtPosition(pos)?.toString() ?: return@mapNotNull null
                            val itemVal = TvType.valueOf(item)
                            itemVal.ordinal.toString()
                        }.toSet()
                        settingsManager.edit {
                            putStringSet(getString(R.string.prefer_media_type_key), prefValues)
                        }

                        // Regenerate set homepage
                        DataStoreHelper.currentHomePage = null
                    }
                }

                nextBtt.setOnClickListener {
                    findNavController().navigate(R.id.navigation_setup_media_to_navigation_setup_layout)
                }

                prevBtt.setOnClickListener {
                    findNavController().popBackStack()
                }

                if (isLayout(TV)) {
                    listview1.post {
                        listview1.requestFocus()
                    }

                    listview1.setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    nextBtt.requestFocus()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (listview1.selectedItemPosition >= arrayAdapter.count - 1) {
                                        nextBtt.requestFocus()
                                        true
                                    } else false
                                }
                                else -> false
                            }
                        } else false
                    }

                    nextBtt.setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
                            listview1.requestFocus()
                            true
                        } else false
                    }

                    prevBtt.setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            listview1.requestFocus()
                            true
                        } else false
                    }
                }
            }
        }
    }
}
