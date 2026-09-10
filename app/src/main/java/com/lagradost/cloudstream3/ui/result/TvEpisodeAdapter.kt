package com.lagradost.cloudstream3.ui.result

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import coil3.dispose
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ItemTvEpisodeBinding
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.newSharedPool
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

class TvEpisodeAdapter(
    private val clickCallback: (EpisodeClickEvent) -> Unit,
) : NoStateAdapter<ResultEpisode>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
    a.id == b.id
}, contentSame = { a, b ->
    a == b
})) {

    companion object {
        val sharedPool = newSharedPool {
            setMaxRecycledViews(CONTENT, 20)
        }
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val binding = ItemTvEpisodeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolderState(binding)
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        when (val binding = holder.view) {
            is ItemTvEpisodeBinding -> {
                binding.episodePoster.dispose()
                binding.episodePoster.setImageDrawable(null)
            }
        }
        super.onClearView(holder)
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: ResultEpisode, position: Int) {
        val binding = holder.view as? ItemTvEpisodeBinding ?: return
        val itemView = binding.root

        binding.apply {
            // Episode Number Badge
            episodeNumberBadge.text = "E${item.episode}"

            // Episode Title
            val title = if (item.name.isNullOrBlank()) {
                "${root.context.getString(R.string.episode)} ${item.episode}"
            } else {
                item.name
            }
            episodeTitle.text = title

            // Episode Description
            episodeDescription.text = item.description ?: ""
            episodeDescription.isVisible = !item.description.isNullOrBlank()

            // Watched status badge (Always visible like Cinejoy, tinted when watched)
            val isWatched = item.videoWatchState == VideoWatchState.Watched
            episodeWatchedBadge.isVisible = true
            episodeWatchedBadge.imageTintList = android.content.res.ColorStateList.valueOf(
                if (isWatched) 0xFF818CF8.toInt() else 0x80FFFFFF.toInt()
            )

            // Duration badge
            val durationMin = item.runTime ?: if (item.duration > 0) (item.duration / 60000).toInt() else null
            if (durationMin != null && durationMin > 0) {
                episodeDurationBadge.text = "${durationMin}m"
                episodeDurationBadge.isVisible = true
            } else {
                episodeDurationBadge.isVisible = false
            }

            // Progress bar
            val displayPos = item.getDisplayPosition()
            if (displayPos > 0 && item.duration > 0 && !isWatched) {
                episodeProgress.max = (item.duration / 1000).toInt()
                episodeProgress.progress = (displayPos / 1000).toInt()
                episodeProgress.isVisible = true
            } else {
                episodeProgress.isVisible = false
            }

            // Poster thumbnail
            val posterUrl = item.poster?.takeIf { it.isNotBlank() }
            if (posterUrl != null) {
                episodePoster.loadImage(posterUrl)
            } else {
                episodePoster.setImageResource(R.drawable.example_poster)
            }

            // D-Pad Focus Behavior: Centered play icon & fluid 1.04x scale
            itemView.setOnFocusChangeListener { _, hasFocus ->
                episodePlayIcon.isVisible = hasFocus
                val targetScale = if (hasFocus) 1.04f else 1.0f
                episodeThumbnailCard.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .translationZ(if (hasFocus) 4f else 0f)
                    .setDuration(150)
                    .start()
            }

            itemView.setOnClickListener {
                clickCallback.invoke(
                    EpisodeClickEvent(position, ACTION_CLICK_DEFAULT, item)
                )
            }

            itemView.setOnLongClickListener {
                clickCallback.invoke(
                    EpisodeClickEvent(position, ACTION_SHOW_OPTIONS, item)
                )
                true
            }
        }
    }
}
