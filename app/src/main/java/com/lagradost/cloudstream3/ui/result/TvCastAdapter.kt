package com.lagradost.cloudstream3.ui.result

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import coil3.dispose
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ItemTvCastBinding
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.newSharedPool
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

class TvCastAdapter(
    private val focusCallback: () -> Unit = {}
) : NoStateAdapter<ActorData>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
    a.actor.name == b.actor.name
}, contentSame = { a, b ->
    a == b
})) {

    companion object {
        val sharedPool = newSharedPool {
            setMaxRecycledViews(CONTENT, 15)
        }
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val binding = ItemTvCastBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolderState(binding)
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        when (val binding = holder.view) {
            is ItemTvCastBinding -> {
                binding.castImage.dispose()
                binding.castImage.setImageDrawable(null)
            }
        }
        super.onClearView(holder)
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: ActorData, position: Int) {
        val binding = holder.view as? ItemTvCastBinding ?: return
        val itemView = binding.root

        binding.apply {
            castName.text = item.actor.name

            val role = item.roleString ?: item.role?.name
            if (!role.isNullOrBlank()) {
                castRole.text = role
                castRole.isVisible = true
            } else {
                castRole.isVisible = false
            }

            val imgUrl = item.actor.image?.takeIf { it.isNotBlank() }
            if (imgUrl != null) {
                castImage.loadImage(imgUrl)
            } else {
                castImage.setImageResource(R.drawable.profile_bg_dark_blue)
            }

            // TV focus animation
            itemView.setOnFocusChangeListener { _, hasFocus ->
                val targetScale = if (hasFocus) 1.08f else 1.0f
                castAvatarCard.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .translationZ(if (hasFocus) 4f else 0f)
                    .setDuration(150)
                    .start()
                castName.isVisible = hasFocus

                if (hasFocus) {
                    focusCallback.invoke()
                }
            }
        }
    }
}
