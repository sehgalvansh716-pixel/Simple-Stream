package com.lagradost.cloudstream3.ui.search

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import android.view.KeyEvent
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.getNameFull
import com.lagradost.cloudstream3.utils.AppContextUtils.getShortSeasonText
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.fixVisual
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.getImageFromDrawable
import com.lagradost.cloudstream3.utils.CardMetadataManager
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView

object SearchResultBuilder {
    private val showCache: MutableMap<String, Boolean> = mutableMapOf()

    fun updateCache(context: Context?) {
        if (context == null) return
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

        for (k in context.resources.getStringArray(R.array.poster_ui_options_values)) {
            showCache[k] = settingsManager.getBoolean(k, showCache[k] ?: true)
        }
    }

    @SuppressLint("StringFormatInvalid")
    fun bind(
        clickCallback: (SearchClickCallback) -> Unit,
        card: SearchResponse,
        position: Int,
        itemView: View,
        nextFocusUp: Int? = null,
        nextFocusDown: Int? = null,
        colorCallback: ((Palette) -> Unit)? = null
    ) {
        val cardView: ImageView = itemView.findViewById(R.id.imageView)
        val cardText: TextView? = itemView.findViewById(R.id.imageText)
        val cardSubText: TextView? = itemView.findViewById(R.id.imageSubText)
        val isTv = isLayout(TV or EMULATOR)

        val textIsDub: TextView? = itemView.findViewById(R.id.text_is_dub)
        val textIsSub: TextView? = itemView.findViewById(R.id.text_is_sub)
        val textFlag: TextView? = itemView.findViewById(R.id.text_flag)
        val rating: TextView? = itemView.findViewById(R.id.text_rating)

        val textQuality: TextView? = itemView.findViewById(R.id.text_quality)
        val shadow: View? = itemView.findViewById(R.id.title_shadow)

        val bg: CardView = itemView.findViewById(R.id.background_card)

        val bar: ProgressBar? = itemView.findViewById(R.id.watchProgress)
        val playImg: ImageView? = itemView.findViewById(R.id.search_item_download_play)
        val episodeText: TextView? = itemView.findViewById(R.id.episode_text)

        // Do logic

        bar?.isVisible = false
        playImg?.isVisible = false
        textIsDub?.isVisible = false
        textIsSub?.isVisible = false
        textFlag?.isVisible = false
        rating?.isVisible = false
        episodeText?.isVisible = false

        val showSub = showCache[textIsDub?.context?.getString(R.string.show_sub_key)] ?: false
        val showDub = showCache[textIsDub?.context?.getString(R.string.show_dub_key)] ?: false
        val showTitle = showCache[cardText?.context?.getString(R.string.show_title_key)] ?: false
        val showEpisodeText = showCache[cardText?.context?.getString(R.string.show_episode_text_key)] ?: false
        val showHd = showCache[textQuality?.context?.getString(R.string.show_hd_key)] ?: false
        val showRatingView =
            showCache[textQuality?.context?.getString(R.string.show_rating_key)] ?: false
        if (card is SyncAPI.LibraryItem) {
            val ratingText = card.personalRating?.toStringNull(0.1, 10, 1)
            val showRating = !ratingText.isNullOrBlank()
            rating?.isVisible = showRating
            if (showRating) {
                rating?.text = ratingText
            }
        } else if (showRatingView) {
            val ratingText = card.score?.toStringNull(0.1, 10, 1)
            val showRating = !ratingText.isNullOrBlank()
            rating?.isVisible = showRating
            if (showRating) {
                rating?.text = ratingText
            }
        }

        shadow?.isVisible = showTitle

        when (card.quality) {
            SearchQuality.BlueRay -> R.string.quality_blueray
            SearchQuality.Cam -> R.string.quality_cam
            SearchQuality.CamRip -> R.string.quality_cam_rip
            SearchQuality.DVD -> R.string.quality_dvd
            SearchQuality.HD -> R.string.quality_hd
            SearchQuality.HQ -> R.string.quality_hq
            SearchQuality.HdCam -> R.string.quality_cam_hd
            SearchQuality.Telecine -> R.string.quality_tc
            SearchQuality.Telesync -> R.string.quality_ts
            SearchQuality.WorkPrint -> R.string.quality_workprint
            SearchQuality.SD -> R.string.quality_sd
            SearchQuality.FourK -> R.string.quality_4k
            SearchQuality.UHD -> R.string.quality_uhd
            SearchQuality.SDR -> R.string.quality_sdr
            SearchQuality.HDR -> R.string.quality_hdr
            SearchQuality.WebRip -> R.string.quality_webrip
            null -> null
        }?.let { textRes ->
            textQuality?.setText(textRes)
            textQuality?.isVisible = showHd
        } ?: run {
            textQuality?.isVisible = false
        }

        val cleanName = CardMetadataManager.cleanTitle(card.name)
        cardText?.text = if (cleanName.isNotBlank()) cleanName else card.name
        cardText?.isVisible = showTitle

        if (!isTv && cardSubText != null) {
            val meta = CardMetadataManager.resolveFromCard(card)
            val sub = CardMetadataManager.formatSubtitle(meta.year, meta.score)
            cardSubText.text = sub
            cardSubText.isVisible = sub != null

            if (meta.year == null || meta.score == null) {
                CardMetadataManager.fetchMetadataAsync(card) { updated ->
                    val updatedSub = CardMetadataManager.formatSubtitle(updated.year, updated.score)
                    cardSubText.text = updatedSub
                    cardSubText.isVisible = updatedSub != null
                }
            }
        }

        cardView.isVisible = true
        if (!card.posterUrl.isNullOrEmpty()) {
            val url = card.posterUrl!!
            cardView.loadImage(url, card.posterHeaders) {
                error { getImageFromDrawable(itemView.context, R.drawable.default_cover) }
            }
        } else cardView.loadImage(R.drawable.default_cover)

        fun click(view: View?) {
            clickCallback.invoke(
                SearchClickCallback(
                    if (card is DataStoreHelper.ResumeWatchingResult) SEARCH_ACTION_PLAY_FILE else SEARCH_ACTION_LOAD,
                    view ?: return,
                    position,
                    card
                )
            )
        }

        fun longClick(view: View?) {
            clickCallback.invoke(
                SearchClickCallback(
                    SEARCH_ACTION_SHOW_METADATA,
                    view ?: return,
                    position,
                    card
                )
            )
        }

        fun focus(view: View?, focus: Boolean) {
            if (focus) {
                clickCallback.invoke(
                    SearchClickCallback(
                        SEARCH_ACTION_FOCUSED,
                        view ?: return,
                        position,
                        card
                    )
                )
            }
        }

        bg.isFocusable = false
        bg.isFocusableInTouchMode = false
        if (!isLayout(TV or EMULATOR)) {
            bg.foreground = null
            itemView.findViewById<View>(R.id.card_focus_dimmer)?.isVisible = false
            itemView.findViewById<View>(R.id.card_focus_info)?.isVisible = false
            itemView.findViewById<View>(R.id.card_play_icon)?.isVisible = false
            bg.setOnClickListener {
                click(it)
            }
            bg.setOnLongClickListener {
                longClick(it)
                return@setOnLongClickListener true
            }
        }
        //
        //
        //

        itemView.setOnClickListener {
            click(it)
        }
        if (nextFocusUp != null) {
            itemView.nextFocusUpId = nextFocusUp
            if (isLayout(TV or EMULATOR)) {
                itemView.setOnKeyListener { v, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        val target = v.rootView?.findViewById<View>(nextFocusUp)
                        if (target != null) {
                            val master = v.rootView?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.home_master_recycler)
                            (master?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                            target.post {
                                target.requestFocus()
                            }
                            return@setOnKeyListener true
                        }
                    }
                    false
                }
            }
        }

        if (nextFocusDown != null) {
            itemView.nextFocusDownId = nextFocusDown
        }

        /*when (nextFocusBehavior) {
            true -> itemView.nextFocusLeftId = bg.id
            false -> itemView.nextFocusRightId = bg.id
            null -> {
                bg.nextFocusRightId = -1
                bg.nextFocusLeftId = -1
            }
        }*/

        /*if (nextFocusUp != null) {
            bg.nextFocusUpId = nextFocusUp
        }

        if (nextFocusDown != null) {
            bg.nextFocusDownId = nextFocusDown
        }

        */

        if (isLayout(TV or EMULATOR)) {
            // bg.isFocusable = true
            // bg.isFocusableInTouchMode = true
            // bg.touchscreenBlocksFocus = false
            itemView.isFocusableInTouchMode = true
            itemView.isFocusable = true
        }

        /**/

        itemView.setOnLongClickListener {
            longClick(it)
            return@setOnLongClickListener true
        }

        /*bg.setOnFocusChangeListener { view, b ->
            focus(view, b)
        }*/

        val focusDimmer: View? = itemView.findViewById(R.id.card_focus_dimmer)
        val focusInfo: View? = itemView.findViewById(R.id.card_focus_info)
        val focusTitle: TextView? = itemView.findViewById(R.id.card_focus_title)
        val focusSubtitle: TextView? = itemView.findViewById(R.id.card_focus_subtitle)
        val badgeContainer: View? = (rating?.parent as? View) ?: (textQuality?.parent as? View)
        val hasQuality = card.quality != null

        CardMetadataManager.registerCard(card)

        if (isTv && focusInfo != null) {
            val meta = CardMetadataManager.resolveFromCard(card)
            val cleanCardName = CardMetadataManager.cleanTitle(card.name)
            val displayTitle = if (meta.title.isNotBlank()) meta.title else cleanCardName
            if (displayTitle.isNotBlank()) {
                cardText?.text = displayTitle
                focusTitle?.text = displayTitle
            }
            val sub = CardMetadataManager.formatSubtitle(meta.year, meta.score)
            focusSubtitle?.text = sub
            focusSubtitle?.isVisible = (sub != null)
        }

        // Always reset to unfocused state on bind — the setOnFocusChangeListener handles the
        // focused visual at runtime. This prevents recycled views carrying over scale / dimmer
        // state from a previously focused card (which made cards look wrong when focus moved
        // back to the search bar).
        bg.foreground = null
        if (isTv) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                itemView.outlineAmbientShadowColor = Color.argb(16, 0, 0, 0)
                itemView.outlineSpotShadowColor = Color.argb(24, 0, 0, 0)
            }
            // Cancel any in-flight animators before resetting values
            itemView.animate().cancel()
            focusDimmer?.animate()?.cancel()
            focusInfo?.animate()?.cancel()
            cardText?.animate()?.cancel()
            shadow?.animate()?.cancel()
            badgeContainer?.animate()?.cancel()
            textQuality?.animate()?.cancel()

            itemView.scaleX = 1.0f
            itemView.scaleY = 1.0f
            itemView.translationZ = 0f
            itemView.alpha = 1.0f
            focusDimmer?.isVisible = false
            focusDimmer?.alpha = 0f
            focusInfo?.isVisible = false
            focusInfo?.alpha = 0f
            focusInfo?.translationY = 0f
            cardText?.visibility = if (showTitle) View.VISIBLE else View.GONE
            cardText?.alpha = 1.0f
            shadow?.visibility = if (showTitle) View.VISIBLE else View.GONE
            shadow?.alpha = 1.0f
            badgeContainer?.visibility = View.VISIBLE
            badgeContainer?.alpha = 1.0f
            textQuality?.visibility = if (showHd && hasQuality) View.VISIBLE else View.GONE
            textQuality?.alpha = 1.0f
        }

        itemView.setOnFocusChangeListener { view, hasFocus ->
            bg.foreground = if (hasFocus) ContextCompat.getDrawable(view.context, R.drawable.outline) else null
            if (isTv) {
                // Cancel running animations on this card and its animated children to avoid race conditions during rapid D-pad remote traversal
                view.animate().cancel()
                focusDimmer?.animate()?.cancel()
                focusInfo?.animate()?.cancel()
                cardText?.animate()?.cancel()
                shadow?.animate()?.cancel()
                badgeContainer?.animate()?.cancel()
                textQuality?.animate()?.cancel()

                val duration = 200L
                val interpolator = DecelerateInterpolator()
                val targetScale = if (hasFocus) 1.10f else 1.0f
                val targetZ = if (hasFocus) 12f else 0f

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    view.outlineAmbientShadowColor = if (hasFocus) Color.argb(40, 0, 0, 0) else Color.argb(16, 0, 0, 0)
                    view.outlineSpotShadowColor = if (hasFocus) Color.argb(60, 0, 0, 0) else Color.argb(24, 0, 0, 0)
                }

                if (hasFocus) {
                    CardMetadataManager.onCardFocused(card)
                    val meta = CardMetadataManager.resolveFromCard(card)
                    val cleanCardName = CardMetadataManager.cleanTitle(card.name)
                    val displayTitle = if (meta.title.isNotBlank()) meta.title else cleanCardName
                    if (displayTitle.isNotBlank()) {
                        focusTitle?.text = displayTitle
                        cardText?.text = displayTitle
                    }
                    val sub = CardMetadataManager.formatSubtitle(meta.year, meta.score)
                    focusSubtitle?.text = sub
                    focusSubtitle?.isVisible = (sub != null)

                    if (displayTitle.isBlank() || meta.year == null || meta.score == null) {
                        CardMetadataManager.fetchMetadataAsync(card) { updated ->
                            if (itemView.isFocused) {
                                val updatedTitle = if (updated.title.isNotBlank()) updated.title else CardMetadataManager.cleanTitle(card.name)
                                if (updatedTitle.isNotBlank()) {
                                    focusTitle?.text = updatedTitle
                                    cardText?.text = updatedTitle
                                }
                                val updatedSub = CardMetadataManager.formatSubtitle(updated.year, updated.score)
                                focusSubtitle?.text = updatedSub
                                focusSubtitle?.isVisible = (updatedSub != null)
                            }
                        }
                    }

                    // Bring this card to front in z-order
                    view.translationZ = 12f
                    view.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .translationZ(targetZ)
                        .alpha(1.0f)
                        .setDuration(duration)
                        .setInterpolator(interpolator)
                        .start()

                    // Dimmer overlay fade in
                    focusDimmer?.let { dimmer ->
                        dimmer.visibility = View.VISIBLE
                        dimmer.alpha = 0f
                        dimmer.animate()
                            .alpha(1.0f)
                            .setDuration(duration)
                            .setInterpolator(interpolator)
                            .start()
                    }

                    // Focus info container fade in and slide up
                    focusInfo?.let { info ->
                        info.visibility = View.VISIBLE
                        info.alpha = 0f
                        info.translationY = 14.toPx.toFloat()
                        info.animate()
                            .alpha(1.0f)
                            .translationY(0f)
                            .setDuration(duration)
                            .setInterpolator(interpolator)
                            .start()
                    }

                    // Fade out unfocused elements
                    cardText?.animate()?.alpha(0f)?.setDuration(160)?.withEndAction { cardText.visibility = View.GONE }?.start()
                    shadow?.animate()?.alpha(0f)?.setDuration(160)?.withEndAction { shadow.visibility = View.GONE }?.start()
                    badgeContainer?.animate()?.alpha(0f)?.setDuration(160)?.withEndAction { badgeContainer.visibility = View.INVISIBLE }?.start()
                    textQuality?.animate()?.alpha(0f)?.setDuration(160)?.withEndAction { textQuality.visibility = View.INVISIBLE }?.start()
                } else {
                    // Outgoing card unfocusing
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

                    focusDimmer?.let { dimmer ->
                        dimmer.animate()
                            .alpha(0f)
                            .setDuration(160)
                            .setInterpolator(interpolator)
                            .withEndAction { dimmer.visibility = View.GONE }
                            .start()
                    }

                    focusInfo?.let { info ->
                        info.animate()
                            .alpha(0f)
                            .translationY(10.toPx.toFloat())
                            .setDuration(160)
                            .setInterpolator(interpolator)
                            .withEndAction { info.visibility = View.GONE }
                            .start()
                    }

                    // Restore unfocused elements
                    if (showTitle) {
                        cardText?.visibility = View.VISIBLE
                        cardText?.alpha = 0f
                        cardText?.animate()?.alpha(1.0f)?.setDuration(duration)?.setInterpolator(interpolator)?.start()

                        shadow?.visibility = View.VISIBLE
                        shadow?.alpha = 0f
                        shadow?.animate()?.alpha(1.0f)?.setDuration(duration)?.setInterpolator(interpolator)?.start()
                    }
                    badgeContainer?.visibility = View.VISIBLE
                    badgeContainer?.alpha = 0f
                    badgeContainer?.animate()?.alpha(1.0f)?.setDuration(duration)?.setInterpolator(interpolator)?.start()

                    if (showHd && hasQuality) {
                        textQuality?.visibility = View.VISIBLE
                        textQuality?.alpha = 0f
                        textQuality?.animate()?.alpha(1.0f)?.setDuration(duration)?.setInterpolator(interpolator)?.start()
                    }
                }
            }
            focus(view, hasFocus)
        }

        when (card) {
            is LiveSearchResponse -> {
                SubtitleHelper.getFlagFromIso(card.lang)?.let { flagEmoji ->
                    textFlag?.apply {
                        isVisible = true
                        text = flagEmoji
                    }
                }
            }

            is DataStoreHelper.ResumeWatchingResult -> {
                val pos = card.watchPos?.fixVisual()
                if (pos != null) {
                    bar?.max = (pos.duration / 1000).toInt()
                    bar?.progress = (pos.position / 1000).toInt()
                    bar?.visibility = View.VISIBLE
                }
                playImg?.visibility = View.VISIBLE
                if (card.type?.isMovieType() == false && showEpisodeText) {
                    episodeText?.context?.getShortSeasonText(card.episode, card.season)?.let {text->
                        episodeText.text = text
                        episodeText.isVisible = true
                    }
                }
            }

            is AnimeSearchResponse -> {
                val dubStatus = card.dubStatus
                if (!dubStatus.isNullOrEmpty()) {
                    if (dubStatus.contains(DubStatus.Dubbed)) {
                        textIsDub?.isVisible = showDub
                    }
                    if (dubStatus.contains(DubStatus.Subbed)) {
                        textIsSub?.isVisible = showSub
                    }
                }

                val dubEpisodes = card.episodes[DubStatus.Dubbed]
                val subEpisodes = card.episodes[DubStatus.Subbed]

                textIsDub?.apply {
                    val dubText = context.getString(R.string.app_dubbed_text)
                    text = if (dubEpisodes != null && dubEpisodes > 0) {
                        context.getString(R.string.app_dub_sub_episode_text_format)
                            .format(dubText, dubEpisodes)
                    } else {
                        dubText
                    }
                }

                textIsSub?.apply {
                    val subText = context.getString(R.string.app_subbed_text)
                    text = if (subEpisodes != null && subEpisodes > 0) {
                        context.getString(R.string.app_dub_sub_episode_text_format)
                            .format(subText, subEpisodes)
                    } else {
                        subText
                    }
                }
            }
        }

        // This is the logic for making the rounded corners more round on the top and bottom element
        // a bit dirty to do memory allocation, but it makes it more extensible and is easier to reason about
        // then a large if statement

        // Requires that the ordering here is the same as in the xml
        val boxes = arrayListOf<TextView>()
        for (view in arrayOf(textIsDub, textIsSub, rating)) {
            if (view?.isVisible == true) {
                boxes.add(view)
            }
        }
        if (boxes.size == 1) {
            boxes[0].setBackgroundResource(R.drawable.bg_color_both)
        } else if (boxes.size > 1) {
            boxes[0].setBackgroundResource(R.drawable.bg_color_top)
            for (i in 1 until boxes.size) {
                boxes[i].setBackgroundResource(R.drawable.bg_color_center)
            }
            boxes[boxes.size - 1].setBackgroundResource(R.drawable.bg_color_bottom)
        }
        textIsDub?.apply {
            backgroundTintList = ColorStateList.valueOf(context.colorFromAttribute(R.attr.textColor))
        }
    }
}
