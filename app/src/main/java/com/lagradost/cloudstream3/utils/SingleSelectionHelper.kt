package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.app.Dialog
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.BottomInputDialogBinding
import com.lagradost.cloudstream3.databinding.BottomSelectionDialogBinding
import com.lagradost.cloudstream3.databinding.BottomTextDialogBinding
import com.lagradost.cloudstream3.databinding.OptionsPopupTvBinding
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import com.lagradost.cloudstream3.utils.UIHelper.toPx

object SingleSelectionHelper {
    fun BottomSheetDialog.setupLiquidGlass(bindingRoot: View) {
        bindingRoot.background = ContextCompat.getDrawable(context, R.drawable.bg_mobile_sheet_glass)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
            setDimAmount(0.45f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes?.blurBehindRadius = 32
            }
        }
    }
    fun Activity?.showOptionSelectStringRes(
        view: View?,
        poster: String?,
        options: List<Int>,
        tvOptions: List<Int> = listOf(),
        callback: (Pair<Boolean, Int>) -> Unit
    ) {
        if (this == null) return

        this.showOptionSelect(
            view,
            poster,
            options.map { this.getString(it) },
            tvOptions.map { this.getString(it) },
            callback
        )
    }

    private fun Activity?.showOptionSelect(
        view: View?,
        poster: String?,
        options: List<String>,
        tvOptions: List<String>,
        callback: (Pair<Boolean, Int>) -> Unit
    ) {
        if (this == null) return

        // This was temporarily removed until better UI is made
        /*if (isLayout(TV or EMULATOR)) {
            val binding = OptionsPopupTvBinding.inflate(layoutInflater)
            val dialog = AlertDialog.Builder(this, R.style.AlertDialogCustom)
                .setView(binding.root)
                .create()

            dialog.show()

            binding.listview1.let { listView ->
                listView.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                listView.adapter =
                    ArrayAdapter<String>(this, R.layout.sort_bottom_single_choice_color).apply {
                        addAll(tvOptions)
                    }

                listView.setOnItemClickListener { _, _, i, _ ->
                    callback.invoke(Pair(true, i))
                    dialog.dismissSafe(this)
                }
            }

            binding.imageView.apply {
                isGone = poster.isNullOrEmpty()
                loadImage(poster)
            }
        } else {*/
        view?.popupMenuNoIconsAndNoStringRes(options.mapIndexed { index, s ->
            Pair(
                index,
                s
            )
        }) {
            callback(Pair(false, this.itemId))
        }
        //}
    }

    fun Activity?.showDialog(
        binding: BottomSelectionDialogBinding,
        dialog: Dialog,
        items: List<String>,
        selectedIndex: List<Int>,
        name: String,
        showApply: Boolean,
        isMultiSelect: Boolean,
        callback: (List<Int>) -> Unit,
        dismissCallback: () -> Unit,
        itemLayout: Int = R.layout.sort_bottom_single_choice
    ) {
        if (this == null) return

        val isTv = isLayout(TV or EMULATOR)
        val realShowApply = if (isTv && !isMultiSelect) false else (showApply || isMultiSelect)
        val listView = binding.listview1
        val textView = binding.text1
        val applyButton = binding.applyBtt
        val cancelButton = binding.cancelBtt
        val applyHolder = binding.applyBttHolder

        if (!isTv && isLayout(PHONE or EMULATOR) && dialog is BottomSheetDialog) {
            binding.dragHandle.isVisible = true
            listView.isNestedScrollingEnabled = true
            dialog.setupLiquidGlass(binding.root)
        } else if (!isTv) {
            binding.dragHandle.isVisible = false
            binding.root.background = ContextCompat.getDrawable(this, R.drawable.bg_mobile_dialog_glass)
            binding.root.setPadding(20.toPx, 18.toPx, 20.toPx, 18.toPx)
            listView.divider = null
            listView.dividerHeight = 0
            listView.setPadding(0, 4.toPx, 0, 4.toPx)
            listView.clipToPadding = false
            textView.textSize = 18f
            textView.setTextColor(0xFFF3F4F6.toInt())
            textView.setPadding(4.toPx, 4.toPx, 4.toPx, 8.toPx)
            (textView.layoutParams as? FrameLayout.LayoutParams)?.apply {
                topMargin = 0
            }
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.45f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    attributes?.blurBehindRadius = 32
                }
            }
        } else {
            binding.dragHandle.isVisible = false
        }

        if (isTv) {
            binding.root.background = ContextCompat.getDrawable(this, R.drawable.bg_tv_dialog_glass)
            binding.root.setPadding(28.toPx, 24.toPx, 28.toPx, 24.toPx)
            listView.divider = null
            listView.dividerHeight = 0
            listView.setPadding(0, 6.toPx, 0, 6.toPx)
            listView.clipToPadding = false
            listView.clipChildren = false
            listView.itemsCanFocus = true
            listView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            textView.textSize = 20f
            textView.setTextColor(0xFFFFFFFF.toInt())
            dialog.window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(580.toPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            if (realShowApply) {
                applyButton.background = ContextCompat.getDrawable(this, R.drawable.bg_tv_dialog_item_pill)
                cancelButton.background = ContextCompat.getDrawable(this, R.drawable.bg_tv_dialog_item_pill)
                applyButton.setTextColor(0xFFFFFFFF.toInt())
                cancelButton.setTextColor(0xB3FFFFFF.toInt())
            }
        } else if (realShowApply) {
            applyButton.background = ContextCompat.getDrawable(this, R.drawable.bg_mobile_dialog_item_pill)
            cancelButton.background = ContextCompat.getDrawable(this, R.drawable.bg_mobile_dialog_item_pill)
            applyButton.setTextColor(0xFFFFFFFF.toInt())
            cancelButton.setTextColor(0xB3FFFFFF.toInt())
        }

        applyHolder.isVisible = realShowApply
        if (!realShowApply) {
            val params = listView.layoutParams as LinearLayout.LayoutParams
            params.setMargins(listView.marginLeft, listView.marginTop, listView.marginRight, 0)
            listView.layoutParams = params
        }

        textView.text = name
        textView.isGone = name.isBlank()

        val resolvedItemLayout = if (isTv && itemLayout == R.layout.sort_bottom_single_choice) {
            R.layout.sort_bottom_tv_choice
        } else if (isTv && itemLayout == R.layout.sort_bottom_single_choice_no_checkmark) {
            R.layout.sort_bottom_tv_choice_no_checkmark
        } else {
            itemLayout
        }

        val arrayAdapter = object : ArrayAdapter<String>(this, resolvedItemLayout) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val rowView = super.getView(position, convertView, parent)
                if (isTv) {
                    rowView.isFocusable = true
                    rowView.setOnClickListener {
                        if (realShowApply) {
                            if (!isMultiSelect) {
                                listView.setItemChecked(position, true)
                            }
                        } else {
                            callback.invoke(listOf(position))
                            dialog.dismissSafe(this@showDialog)
                        }
                    }
                }
                return rowView
            }
        }
        arrayAdapter.addAll(items)

        listView.adapter = arrayAdapter
        if (isMultiSelect) {
            listView.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE
        } else {
            listView.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        }

        for (select in selectedIndex) {
            listView.setItemChecked(select, true)
        }

        selectedIndex.minOrNull()?.let {
            listView.setSelection(it)
        }

        listView.post {
            if (isTv) {
                val initialPos = selectedIndex.firstOrNull() ?: 0
                val targetChild = listView.getChildAt(initialPos - listView.firstVisiblePosition)
                targetChild?.requestFocus() ?: listView.requestFocus()
            }
        }

        dialog.setOnDismissListener {
            dismissCallback.invoke()
        }

        listView.setOnItemClickListener { _, _, which, _ ->
            if (realShowApply) {
                if (!isMultiSelect) {
                    listView.setItemChecked(which, true)
                }
            } else {
                callback.invoke(listOf(which))
                dialog.dismissSafe(this)
            }
        }
        if (realShowApply) {
            applyButton.setOnClickListener {
                val list = ArrayList<Int>()
                for (index in 0 until listView.count) {
                    if (listView.checkedItemPositions[index])
                        list.add(index)
                }
                callback.invoke(list)
                dialog.dismissSafe(this)
            }
            cancelButton.setOnClickListener {
                dialog.dismissSafe(this)
            }
        }
    }

    private fun Activity?.showInputDialog(
        binding: BottomInputDialogBinding,
        dialog: Dialog,
        value: String,
        name: String,
        textInputType: Int?,
        callback: (String) -> Unit,
        dismissCallback: () -> Unit
    ) {
        if (this == null) return

        val inputView = binding.nginxTextInput
        val textView = binding.text1
        val applyButton = binding.applyBtt
        val cancelButton = binding.cancelBtt
        val applyHolder = binding.applyBttHolder

        applyHolder.isVisible = true
        textView.text = name

        if (textInputType != null) {
            inputView.inputType = textInputType // 16 for website url input type
        }
        inputView.setText(value, TextView.BufferType.EDITABLE)


        applyButton.setOnClickListener {
            callback.invoke(inputView.text.toString())  // try to save the setting, using callback
            dialog.dismissSafe(this)
        }

        cancelButton.setOnClickListener {  // just dismiss
            dialog.dismissSafe(this)
        }

        dialog.setOnDismissListener {
            dismissCallback.invoke()
        }

    }

    fun Activity?.showMultiDialog(
        items: List<String>,
        selectedIndex: List<Int>,
        name: String,
        dismissCallback: () -> Unit,
        callback: (List<Int>) -> Unit,
    ) {
        if (this == null) return

        val binding: BottomSelectionDialogBinding = BottomSelectionDialogBinding.inflate(
            LayoutInflater.from(this)
        )
        val isTv = isLayout(TV or EMULATOR)
        val themeRes = if (isTv) R.style.AlertDialogCustomTransparent else R.style.AlertDialogCustom
        val builder =
            AlertDialog.Builder(this, themeRes)
                .setView(binding.root)

        val dialog = builder.create()
        dialog.show()
        showDialog(
            binding,
            dialog,
            items,
            selectedIndex,
            name,
            showApply = true,
            isMultiSelect = true,
            callback,
            dismissCallback,
            itemLayout = if (isTv) R.layout.sort_bottom_tv_choice else R.layout.sort_bottom_single_choice
        )
    }

    fun Activity?.showDialog(
        items: List<String>,
        selectedIndex: Int,
        name: String,
        showApply: Boolean,
        dismissCallback: () -> Unit,
        callback: (Int) -> Unit,
    ) {
        if (this == null) return

        val binding: BottomSelectionDialogBinding = BottomSelectionDialogBinding.inflate(
            LayoutInflater.from(this)
        )
        val isTv = isLayout(TV or EMULATOR)
        val themeRes = if (isTv) R.style.AlertDialogCustomTransparent else R.style.AlertDialogCustom
        val builder =
            AlertDialog.Builder(this, themeRes)
                .setView(binding.root)

        val dialog = builder.create()
        dialog.show()


        showDialog(
            binding,
            dialog,
            items,
            listOf(selectedIndex),
            name,
            showApply,
            false,
            { if (it.isNotEmpty()) callback.invoke(it.first()) },
            dismissCallback,
            itemLayout = if (isTv) R.layout.sort_bottom_tv_choice else R.layout.sort_bottom_single_choice
        )
    }

    /** Only for a low amount of items */
    fun Activity?.showBottomDialog(
        items: List<String>,
        selectedIndex: Int,
        name: String,
        showApply: Boolean,
        dismissCallback: () -> Unit,
        callback: (Int) -> Unit,
    ) {
        if (this == null) return

        val binding: BottomSelectionDialogBinding = BottomSelectionDialogBinding.inflate(
            LayoutInflater.from(this)
        )

        val isTv = isLayout(TV or EMULATOR)
        val dialog: Dialog = if (isTv) {
            AlertDialog.Builder(this, R.style.AlertDialogCustomTransparent)
                .setView(binding.root)
                .create()
        } else {
            BottomSheetDialog(this).apply {
                setContentView(binding.root)
            }
        }

        dialog.show()
        showDialog(
            binding,
            dialog,
            items,
            listOf(selectedIndex),
            name,
            showApply,
            false,
            { if (it.isNotEmpty()) callback.invoke(it.first()) },
            dismissCallback,
            itemLayout = if (isTv) R.layout.sort_bottom_tv_choice else R.layout.sort_bottom_single_choice
        )
    }

    fun Activity.showBottomDialogInstant(
        items: List<String>,
        name: String,
        dismissCallback: () -> Unit,
        callback: (Int) -> Unit,
    ): Dialog {
        val binding: BottomSelectionDialogBinding = BottomSelectionDialogBinding.inflate(
            LayoutInflater.from(this)
        )

        val isTv = isLayout(TV or EMULATOR)
        val dialog: Dialog = if (isTv) {
            AlertDialog.Builder(this, R.style.AlertDialogCustomTransparent)
                .setView(binding.root)
                .create()
        } else {
            BottomSheetDialog(this).apply {
                setContentView(binding.root)
            }
        }

        dialog.show()
        showDialog(
            binding,
            dialog,
            items,
            emptyList(),
            name,
            showApply = false,
            isMultiSelect = false,
            callback = { if (it.isNotEmpty()) callback.invoke(it.first()) },
            dismissCallback = dismissCallback,
            itemLayout = if (isTv) R.layout.sort_bottom_tv_choice_no_checkmark else R.layout.sort_bottom_single_choice_no_checkmark
        )
        return dialog
    }

    fun Activity.showNginxTextInputDialog(
        name: String,
        value: String,
        textInputType: Int?,
        dismissCallback: () -> Unit,
        callback: (String) -> Unit,
    ) {
        val binding: BottomInputDialogBinding = BottomInputDialogBinding.inflate(
            LayoutInflater.from(this)
        )
        val builder = BottomSheetDialog(this).apply {
            setContentView(binding.root)
            setupLiquidGlass(binding.root)
        }

        builder.show()
        showInputDialog(
            binding,
            builder,
            value,
            name,
            textInputType,  // type is a uri
            callback,
            dismissCallback
        )
    }

    fun Activity.showBottomDialogText(
        title: String,
        text: Spanned,
        dismissCallback: () -> Unit
    ) {
        val binding = BottomTextDialogBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this).apply {
            setContentView(binding.root)
            setupLiquidGlass(binding.root)
        }

        binding.dialogTitle.text = title
        binding.dialogText.text = text

        dialog.setOnDismissListener {
            dismissCallback.invoke()
        }

        dialog.show()
    }
}
