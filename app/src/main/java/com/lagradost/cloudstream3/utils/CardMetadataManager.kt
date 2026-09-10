package com.lagradost.cloudstream3.utils

import android.text.Html
import android.text.SpannableString
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object CardMetadataManager {
    data class Metadata(
        val title: String,
        val year: Int?,
        val score: String?
    )

    val cache = ConcurrentHashMap<String, Metadata>()
    private val pendingRequests = ConcurrentHashMap<String, Boolean>()

    // Linking directly to the plugin selected on the home page
    @Volatile
    var activePluginApiName: String? = null

    // Prefetching constraint: do NOT prefetch until user navigates to content cards
    @Volatile
    var hasUserNavigatedToCards: Boolean = false

    private const val MAX_PREFETCH_COUNT = 180
    private val registeredCards = CopyOnWriteArrayList<SearchResponse>()
    private val registeredUrls = ConcurrentHashMap.newKeySet<String>()

    fun formatSubtitle(year: Int?, score: String?): CharSequence? {
        val cleanScore = score?.replace("★", "")?.trim()?.takeIf { it.isNotBlank() }
        val yearStr = year?.toString()
        return when {
            yearStr != null && cleanScore != null -> {
                Html.fromHtml(
                    "$yearStr  <font color='#FFC107'>★</font>  $cleanScore",
                    Html.FROM_HTML_MODE_LEGACY
                )
            }
            cleanScore != null -> {
                Html.fromHtml(
                    "<font color='#FFC107'>★</font>  $cleanScore",
                    Html.FROM_HTML_MODE_LEGACY
                )
            }
            yearStr != null -> SpannableString(yearStr)
            else -> null
        }
    }

    fun cleanTitle(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        if (trimmed.equals("unknown", ignoreCase = true) || trimmed.equals("none", ignoreCase = true)) {
            return ""
        }
        return trimmed
            .replace(Regex("(?i)^NetMirror\\s*[-–:]?\\s*"), "")
            .replace(Regex("""\s*\((19\d\d|20\d\d)\)"""), "")
            .trim()
    }

    fun getApi(card: SearchResponse?): MainAPI? {
        val cardApi = card?.apiName?.takeIf { it.isNotBlank() && !it.equals("none", true) }
        val activeApi = activePluginApiName?.takeIf { it.isNotBlank() && !it.equals("none", true) }

        return (activeApi?.let { APIHolder.getApiFromNameNull(it) })
            ?: (cardApi?.let { APIHolder.getApiFromNameNull(it) })
            ?: (card?.url?.let { APIHolder.getApiFromUrlNull(it) })
    }

    fun registerCard(card: SearchResponse) {
        if (registeredCards.size < MAX_PREFETCH_COUNT && registeredUrls.add(card.url)) {
            registeredCards.add(card)
        }
    }

    fun registerCards(cards: List<SearchResponse>) {
        for (c in cards) {
            if (registeredCards.size >= MAX_PREFETCH_COUNT) break
            if (registeredUrls.add(c.url)) {
                registeredCards.add(c)
            }
        }
    }

    fun onPluginChanged(newApiName: String?) {
        activePluginApiName = newApiName
        hasUserNavigatedToCards = false
        registeredCards.clear()
        registeredUrls.clear()
        pendingRequests.clear()
    }

    fun onCardFocused(card: SearchResponse) {
        registerCard(card)
        if (!hasUserNavigatedToCards) {
            hasUserNavigatedToCards = true
            startPrefetchBatch()
        }
    }

    private fun startPrefetchBatch() {
        ioSafe {
            val api = getApi(null) ?: return@ioSafe
            val toFetch = registeredCards.take(MAX_PREFETCH_COUNT).filter { c ->
                !cache.containsKey(c.url)
            }
            if (toFetch.isEmpty()) return@ioSafe

            if (api is InternalStreamBase) {
                val ids = toFetch.mapNotNull { c ->
                    c.url.substringAfterLast("/").substringAfterLast("id=").takeIf { it.isNotBlank() }
                }.distinct()

                if (ids.isNotEmpty()) {
                    val cookie = InternalStreamCommon.getOrBypassCookie()
                    InternalStreamCommon.fetchTitlesParallel(ids, api.ottCode, cookie)
                    for (c in toFetch) {
                        val cardId = c.url.substringAfterLast("/").substringAfterLast("id=")
                        val title = InternalStreamCommon.titleCache[cardId]
                        val year = InternalStreamCommon.yearCache[cardId]
                        val scoreObj = InternalStreamCommon.scoreCache[cardId]
                        if (title != null || year != null || scoreObj != null) {
                            val meta = Metadata(cleanTitle(title ?: c.name), year, scoreObj?.toStringNull(0.1, 10, 1))
                            cache[c.url] = meta
                            if (cardId.isNotBlank()) cache[cardId] = meta
                        }
                    }
                }
            } else {
                val repo = APIRepository(api)
                coroutineScope {
                    for (chunk in toFetch.chunked(6)) {
                        chunk.map { item ->
                            async {
                                try {
                                    if (!cache.containsKey(item.url)) {
                                        val res = repo.load(item.url)
                                        if (res is Resource.Success) {
                                            val resp = res.value
                                            val title = cleanTitle(resp.name).takeIf { it.isNotBlank() } ?: cleanTitle(item.name)
                                            val year = resp.year
                                            val scoreStr = resp.score?.toStringNull(0.1, 10, 1)
                                            val meta = Metadata(title, year, scoreStr)
                                            cache[item.url] = meta
                                            val cardId = item.url.substringAfterLast("/").substringAfterLast("id=")
                                            if (cardId.isNotBlank()) cache[cardId] = meta
                                        }
                                    }
                                } catch (_: Throwable) {}
                            }
                        }.awaitAll()
                    }
                }
            }
        }
    }

    fun resolveFromCard(card: SearchResponse): Metadata {
        val cardId = card.url.substringAfterLast("/").substringAfterLast("id=")

        cache[card.url]?.let { return it }
        if (cardId.isNotBlank()) {
            cache[cardId]?.let { return it }
        }

        val rawTitle = card.name.takeIf { it.isNotBlank() }
            ?: InternalStreamCommon.titleCache[card.url]
            ?: InternalStreamCommon.titleCache[cardId]
            ?: ""
        val title = cleanTitle(rawTitle)

        val year = when (card) {
            is MovieSearchResponse -> card.year
            is TvSeriesSearchResponse -> card.year
            is AnimeSearchResponse -> card.year
            is SyncAPI.LibraryItem -> card.releaseDate?.let {
                val cal = Calendar.getInstance()
                cal.time = it
                cal.get(Calendar.YEAR)
            }
            else -> null
        } ?: InternalStreamCommon.yearCache[card.url]
          ?: InternalStreamCommon.yearCache[cardId]
          ?: Regex("""\b(19\d\d|20\d\d)\b""").findAll(rawTitle).lastOrNull()?.value?.toIntOrNull()

        val scoreStr = if (card is SyncAPI.LibraryItem) {
            card.personalRating?.toStringNull(0.1, 10, 1)
        } else {
            card.score?.toStringNull(0.1, 10, 1)
                ?: InternalStreamCommon.scoreCache[card.url]?.toStringNull(0.1, 10, 1)
                ?: InternalStreamCommon.scoreCache[cardId]?.toStringNull(0.1, 10, 1)
        }

        val meta = Metadata(title, year, scoreStr)
        if (title.isNotBlank() && (year != null || scoreStr != null)) {
            cache[card.url] = meta
            if (cardId.isNotBlank()) cache[cardId] = meta
        }
        return meta
    }

    fun cacheFromLoadResponse(resp: LoadResponse) {
        val clean = cleanTitle(resp.name)
        val scoreStr = resp.score?.toStringNull(0.1, 10, 1)
        val meta = Metadata(clean, resp.year, scoreStr)
        cache[resp.url] = meta
        val cardId = resp.url.substringAfterLast("/").substringAfterLast("id=")
        if (cardId.isNotBlank()) cache[cardId] = meta
    }

    fun fetchMetadataAsync(
        card: SearchResponse,
        onLoaded: (Metadata) -> Unit
    ) {
        val existing = resolveFromCard(card)
        if (existing.title.isNotBlank() && existing.year != null && existing.score != null) {
            onLoaded(existing)
            return
        }

        val cardUrl = card.url
        if (pendingRequests.putIfAbsent(cardUrl, true) != null) {
            return
        }

        ioSafe {
            try {
                val api = getApi(card)
                if (api == null) {
                    pendingRequests.remove(cardUrl)
                    return@ioSafe
                }

                val repo = APIRepository(api)
                val loadRes = repo.load(cardUrl)
                if (loadRes is Resource.Success) {
                    val resp = loadRes.value
                    val title = cleanTitle(resp.name).takeIf { it.isNotBlank() } ?: existing.title
                    val year = resp.year ?: existing.year
                    val scoreStr = resp.score?.toStringNull(0.1, 10, 1)
                        ?: existing.score

                    val meta = Metadata(title, year, scoreStr)
                    cache[cardUrl] = meta
                    val cardId = cardUrl.substringAfterLast("/").substringAfterLast("id=")
                    if (cardId.isNotBlank()) cache[cardId] = meta

                    if (title.isNotBlank()) {
                        InternalStreamCommon.titleCache[cardUrl] = title
                        if (cardId.isNotBlank()) InternalStreamCommon.titleCache[cardId] = title
                    }
                    year?.let {
                        InternalStreamCommon.yearCache[cardUrl] = it
                        if (cardId.isNotBlank()) InternalStreamCommon.yearCache[cardId] = it
                    }

                    main {
                        onLoaded(meta)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                pendingRequests.remove(cardUrl)
            }
        }
    }
}
