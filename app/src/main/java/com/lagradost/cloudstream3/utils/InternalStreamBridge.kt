package com.lagradost.cloudstream3.utils

import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.MainActivity.Companion.afterRepositoryLoadedEvent
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Request
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Internal Stream Bridge Engine.
 * Provides internal fallback provider endpoints with persistent local unlocking.
 */
object InternalStreamBridge {
    const val UNLOCK_CODE = "1908"
    const val STORAGE_KEY = "internal_stream_bridge_unlocked"
    const val REPO_URL = "https://simple.stream/internal"

    private val providers by lazy {
        listOf(
            InternalStreamA(),
            InternalStreamB(),
            InternalStreamC()
        )
    }

    fun isUnlocked(): Boolean = getKey<Boolean>(STORAGE_KEY) == true

    fun init(context: Context? = null) {
        if (isUnlocked()) {
            registerProviders()
            if (DataStoreHelper.currentHomePage.isNullOrBlank() || DataStoreHelper.currentHomePage == "None") {
                DataStoreHelper.currentHomePage = "NetMirror - Netflix"
            }
        }
    }

    fun unlock(context: Context? = null): Boolean {
        setKey(STORAGE_KEY, true)
        registerProviders()

        if (DataStoreHelper.currentHomePage.isNullOrBlank() || DataStoreHelper.currentHomePage == "None") {
            DataStoreHelper.currentHomePage = "NetMirror - Netflix"
        }

        ioSafe {
            val repoData = RepositoryData(
                iconUrl = null,
                name = "NetMirror",
                url = REPO_URL
            )
            RepositoryManager.addRepository(repoData)

            main {
                afterPluginsLoadedEvent.invoke(false)
                afterRepositoryLoadedEvent.invoke(true)
            }
        }
        return true
    }

    fun lock() {
        setKey(STORAGE_KEY, false)
        unregisterProviders()
        main {
            afterPluginsLoadedEvent.invoke(false)
            afterRepositoryLoadedEvent.invoke(true)
        }
    }

    fun registerProviders() {
        for (provider in providers) {
            if (!APIHolder.apis.any { it.name == provider.name }) {
                APIHolder.addPluginMapping(provider)
            }
            allProviders.withLock {
                if (!allProviders.any { it.name == provider.name }) {
                    allProviders.add(provider)
                }
            }
        }
    }

    fun unregisterProviders() {
        for (provider in providers) {
            APIHolder.removePluginMapping(provider)
            allProviders.withLock {
                allProviders.removeIf { it.name == provider.name }
            }
        }
    }
}

// ── Base64 Disguised Scraper Config & Common Utilities ──────────────────────────────

private fun b64(s: String): String = String(Base64.getDecoder().decode(s))

internal object InternalStreamCommon {
    val MAIN_URL: String get() = b64("aHR0cHM6Ly9uZXQ1Mi5jYw==")
    val VERIFY_ORIGIN: String get() = b64("aHR0cHM6Ly9uZXQ3Ny5jYw==")
    val VERIFY_REFERER: String get() = b64("aHR0cHM6Ly9uZXQ3Ny5jYy92ZXJpZnky")
    val NET27_URL: String get() = b64("aHR0cHM6Ly9uZXQyNy5jYw==")
    val NET27_REFERER: String get() = b64("aHR0cHM6Ly92aWRlb2Rvd25sb2FkZXIuc2l0ZS8=")
    val TMDB_API_KEY: String get() = b64("ZTYzMzNiMzI0MDllMDJhNGE2ZWJhNmZiN2ZmODY2YmI=")
    val CDN_URL: String get() = b64("aHR0cHM6Ly9pbWdjZG4ua2lt")
    val TV_CDN_URL: String get() = b64("aHR0cHM6Ly90di5pbWdjZG4ua2lt")

    val browserHeaders = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "en-IN,en-US;q=0.9,en;q=0.8",
        "Cache-Control" to "max-age=0",
        "Connection" to "keep-alive",
        "Upgrade-Insecure-Requests" to "1",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Pixel 5 Build/TQ3A.230901.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/144.0.7559.132 Safari/537.36 /OS.Gatu v3.0",
        "X-Requested-With" to "XMLHttpRequest"
    )

    val net27Headers = mapOf(
        "Accept" to "application/json",
        "Referer" to b64("aHR0cHM6Ly92aWRlb2Rvd25sb2FkZXIuc2l0ZS8="),
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"
    )

    val newTvBaseHeaders = mapOf(
        "Cache-Control" to "no-cache, no-store, must-revalidate",
        "Pragma" to "no-cache",
        "Expires" to "0",
        "X-Requested-With" to "NetmirrorNewTV v1.0",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0",
        "Accept" to "application/json, text/plain, */*"
    )

    private val failoverDomains = listOf(
        "aHR0cHM6Ly9tb2JpbGVkZXRlY3RzLmNvbQ==",
        "aHR0cHM6Ly9tb2JpbGVkZXRlY3QuYXBw",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmFydA==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmNj",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmNsaWNr",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0Lmluaw==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmxpdmU=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnBybw==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNob3A=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNpdGU=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNwYWNl",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnN0b3Jl",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnZpcA==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0Lndpa2k=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0Lnh5eg==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5hcnQ=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5jYw==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5pbmZv",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5pams=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5saXZl",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5wcm8=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5zdG9yZQ==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy50b3A=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy54eXo="
    ).map { b64(it) }

    private val cookieLock = ReentrantLock()
    private var cachedCookie: String = ""
    private var cookieTimestamp: Long = 0L

    private val apiLock = ReentrantLock()
    private var cachedApiUrl: String = ""
    private var apiTimestamp: Long = 0L

    private val tmdbCache = ConcurrentHashMap<String, String>()
    val titleCache = ConcurrentHashMap<String, String>()
    private val fetchExecutor = Executors.newFixedThreadPool(32)

    fun fetchTitlesParallel(ids: List<String>, ottCode: String, cookie: String) {
        val toFetch = ids.filter { !titleCache.containsKey(it) && it.isNotBlank() }
        if (toFetch.isEmpty()) return

        val endpoint = when (ottCode) {
            "pv" -> "$MAIN_URL/mobile/pv/post.php"
            "hs" -> "$MAIN_URL/mobile/hs/post.php"
            else -> "$MAIN_URL/mobile/post.php"
        }

        val cookieHeader = buildString {
            append("ott=$ottCode; hd=on")
            if (cookie.isNotBlank()) append("; t_hash_t=$cookie")
        }

        val client = app.baseClient.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()

        val batch = toFetch.take(250)
        val futures = batch.map { id ->
            fetchExecutor.submit {
                try {
                    val ts = System.currentTimeMillis() / 1000
                    val req = Request.Builder()
                        .url("$endpoint?id=$id&t=$ts")
                        .header("Cookie", cookieHeader)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) /OS.Gatu v3.0")
                        .header("Referer", "$MAIN_URL/mobile/home?app=1")
                        .build()

                    client.newCall(req).execute().use { resp ->
                        val body = resp.body.string()
                        if (!body.isNullOrBlank()) {
                            val post = tryParseJson<PostData>(body)
                            val title = post?.title?.trim()?.takeIf { it.isNotBlank() }
                            if (title != null) {
                                titleCache[id] = title
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        for (f in futures) {
            try {
                f.get(4000, TimeUnit.MILLISECONDS)
            } catch (_: Throwable) {}
        }
    }

    fun getOrBypassCookie(): String {
        cookieLock.lock()
        try {
            val now = System.currentTimeMillis()
            if (cachedCookie.isNotBlank() && (now - cookieTimestamp) < 86_400_000L) {
                return cachedCookie
            }

            try {
                val formBody = FormBody.Builder()
                    .add("g-recaptcha-response", UUID.randomUUID().toString())
                    .build()

                val request = Request.Builder()
                    .url("$MAIN_URL/verify.php")
                    .post(formBody)
                    .header("Origin", VERIFY_ORIGIN)
                    .header("Referer", VERIFY_REFERER)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build()

                val client = app.baseClient.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()

                client.newCall(request).execute().use { response ->
                    val cookieStr = response.headers.values("Set-Cookie")
                        .firstOrNull { it.startsWith("t_hash_t=") }
                        ?.substringAfter("t_hash_t=")
                        ?.substringBefore(";")
                        .orEmpty()

                    if (cookieStr.isNotBlank()) {
                        cachedCookie = cookieStr
                        cookieTimestamp = now
                    }
                }
            } catch (e: Throwable) {
                logError(e)
            }

            return cachedCookie
        } finally {
            cookieLock.unlock()
        }
    }

    suspend fun resolveApiUrl(): String {
        val now = System.currentTimeMillis()
        if (cachedApiUrl.isNotBlank() && (now - apiTimestamp) < 21_600_000L) {
            return cachedApiUrl
        }

        for (domain in failoverDomains) {
            try {
                val res = app.get(
                    "$domain/checknewtv.php",
                    headers = newTvBaseHeaders,
                    timeout = 5
                ).text

                val parsed = tryParseJson<NewTvTokenResponse>(res)
                val tokenHash = parsed?.token_hash
                if (!tokenHash.isNullOrBlank()) {
                    val decoded = String(Base64.getDecoder().decode(tokenHash)).trimEnd('/')
                    if (decoded.startsWith("http")) {
                        apiLock.lock()
                        try {
                            cachedApiUrl = decoded
                            apiTimestamp = now
                        } finally {
                            apiLock.unlock()
                        }
                        return cachedApiUrl
                    }
                }
            } catch (_: Throwable) {}
        }

        if (cachedApiUrl.isBlank()) {
            cachedApiUrl = TV_CDN_URL
        }
        return cachedApiUrl
    }

    suspend fun resolveTmdbId(title: String, year: Int?, isMovie: Boolean): String? {
        if (title.isBlank() || title.startsWith("NetMirror #")) return null
        val cacheKey = "$title|$year|$isMovie"
        tmdbCache[cacheKey]?.let { return it }

        val type = if (isMovie) "movie" else "tv"
        val cleanTitle = title.replace(Regex("""\(.*?\)|\[.*?\]"""), "").trim()
        val encoded = URLEncoder.encode(cleanTitle, "UTF-8")

        try {
            val yearParam = if (year != null && year > 1900) {
                if (isMovie) "&year=$year" else "&first_air_date_year=$year"
            } else ""
            val tmdbBase = b64("aHR0cHM6Ly9hcGkudG1kYi5vcmcvMw==")
            val tmdbUrl = "$tmdbBase/search/$type?api_key=$TMDB_API_KEY&query=$encoded$yearParam"
            val res = app.get(tmdbUrl, timeout = 6).text
            val parsed = tryParseJson<TmdbSearchResponse>(res)
            val firstId = parsed?.results?.firstOrNull()?.id
            if (firstId != null) {
                val resultStr = firstId.toString()
                tmdbCache[cacheKey] = resultStr
                return resultStr
            }
        } catch (_: Throwable) {}

        try {
            val cinemetaType = if (isMovie) "movie" else "series"
            val cinemetaBase = b64("aHR0cHM6Ly92My1jaW5lbWV0YS5zdHJlbS5pbw==")
            val cinemetaUrl = "$cinemetaBase/catalog/$cinemetaType/top/search=$encoded.json"
            val res = app.get(cinemetaUrl, timeout = 6).text
            val parsed = tryParseJson<CinemetaSearchResponse>(res)
            val imdbId = parsed?.metas?.firstOrNull()?.imdb_id
            if (!imdbId.isNullOrBlank()) {
                val tmdbBase = b64("aHR0cHM6Ly9hcGkudG1kYi5vcmcvMw==")
                val findUrl = "$tmdbBase/find/$imdbId?api_key=$TMDB_API_KEY&external_source=imdb_id"
                val findRes = app.get(findUrl, timeout = 6).text
                val findParsed = tryParseJson<TmdbFindResponse>(findRes)
                val foundId = if (isMovie) {
                    findParsed?.movie_results?.firstOrNull()?.id
                } else {
                    findParsed?.tv_results?.firstOrNull()?.id
                }
                if (foundId != null) {
                    val resultStr = foundId.toString()
                    tmdbCache[cacheKey] = resultStr
                    return resultStr
                }
            }
        } catch (_: Throwable) {}

        return null
    }

    @Serializable
    data class SearchData(
        @JsonProperty("status") @SerialName("status") val status: String? = null,
        @JsonProperty("searchResult") @SerialName("searchResult") val searchResult: List<SearchResultItem>? = null,
        @JsonProperty("error") @SerialName("error") val error: String? = null
    )

    @Serializable
    data class SearchResultItem(
        @JsonProperty("id") @SerialName("id") val id: String? = null,
        @JsonProperty("t") @SerialName("t") val t: String? = null
    )

    @Serializable
    data class PostData(
        @JsonProperty("title") @SerialName("title") val title: String? = null,
        @JsonProperty("year") @SerialName("year") val year: String? = null,
        @JsonProperty("desc") @SerialName("desc") val desc: String? = null,
        @JsonProperty("cast") @SerialName("cast") val cast: String? = null,
        @JsonProperty("genre") @SerialName("genre") val genre: String? = null,
        @JsonProperty("hs_genre") @SerialName("hs_genre") val hs_genre: String? = null,
        @JsonProperty("type") @SerialName("type") val type: String? = null,
        @JsonProperty("runtime") @SerialName("runtime") val runtime: String? = null,
        @JsonProperty("season") @SerialName("season") val season: List<SeasonItem>? = null,
        @JsonProperty("episodes") @SerialName("episodes") val episodes: List<EpisodeItem?>? = null,
        @JsonProperty("error") @SerialName("error") val error: String? = null
    )

    @Serializable
    data class SeasonItem(
        @JsonProperty("s") @SerialName("s") val s: String? = null,
        @JsonProperty("id") @SerialName("id") val id: String? = null,
        @JsonProperty("ep") @SerialName("ep") val ep: String? = null
    )

    @Serializable
    data class EpisodeItem(
        @JsonProperty("id") @SerialName("id") val id: String? = null,
        @JsonProperty("t") @SerialName("t") val t: String? = null,
        @JsonProperty("s") @SerialName("s") val s: String? = null,
        @JsonProperty("ep") @SerialName("ep") val ep: String? = null,
        @JsonProperty("time") @SerialName("time") val time: String? = null
    )

    @Serializable
    data class NewTvTokenResponse(
        @JsonProperty("token_hash") @SerialName("token_hash") val token_hash: String? = null
    )

    @Serializable
    data class NewTvPlayerResponse(
        @JsonProperty("status") @SerialName("status") val status: String? = null,
        @JsonProperty("video_link") @SerialName("video_link") val video_link: String? = null,
        @JsonProperty("referer") @SerialName("referer") val referer: String? = null,
        @JsonProperty("title") @SerialName("title") val title: String? = null
    )

    @Serializable
    data class TmdbSearchResponse(
        @JsonProperty("results") @SerialName("results") val results: List<TmdbSearchResultItem>? = null
    )

    @Serializable
    data class TmdbSearchResultItem(
        @JsonProperty("id") @SerialName("id") val id: Int? = null
    )

    @Serializable
    data class CinemetaSearchResponse(
        @JsonProperty("metas") @SerialName("metas") val metas: List<CinemetaMetaItem>? = null
    )

    @Serializable
    data class CinemetaMetaItem(
        @JsonProperty("imdb_id") @SerialName("imdb_id") val imdb_id: String? = null
    )

    @Serializable
    data class TmdbFindResponse(
        @JsonProperty("movie_results") @SerialName("movie_results") val movie_results: List<TmdbSearchResultItem>? = null,
        @JsonProperty("tv_results") @SerialName("tv_results") val tv_results: List<TmdbSearchResultItem>? = null
    )

    @Serializable
    data class Net27Response(
        @JsonProperty("ok") @SerialName("ok") val ok: Boolean? = null,
        @JsonProperty("mp4") @SerialName("mp4") val mp4: String? = null,
        @JsonProperty("streams") @SerialName("streams") val streams: List<Net27Stream>? = null,
        @JsonProperty("captions") @SerialName("captions") val captions: List<Net27Caption>? = null,
        @JsonProperty("error") @SerialName("error") val error: String? = null
    )

    @Serializable
    data class Net27Stream(
        @JsonProperty("url") @SerialName("url") val url: String? = null,
        @JsonProperty("resolution") @SerialName("resolution") val resolution: Int? = null,
        @JsonProperty("size") @SerialName("size") val size: Long? = null
    )

    @Serializable
    data class Net27Caption(
        @JsonProperty("lang") @SerialName("lang") val lang: String? = null,
        @JsonProperty("name") @SerialName("name") val name: String? = null,
        @JsonProperty("url") @SerialName("url") val url: String? = null
    )
}

// ── Internal Stream Provider Implementations ─────────────────────────────────

abstract class InternalStreamBase(val ottCode: String) : MainAPI() {
    override var mainUrl = InternalStreamCommon.MAIN_URL
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true
    override val hasQuickSearch = true

    private val postEndpoint: String
        get() = when (ottCode) {
            "pv" -> "$mainUrl/mobile/pv/post.php"
            "hs" -> "$mainUrl/mobile/hs/post.php"
            else -> "$mainUrl/mobile/post.php"
        }

    private val searchEndpoint: String
        get() = when (ottCode) {
            "pv" -> "$mainUrl/mobile/pv/search.php"
            "hs" -> "$mainUrl/mobile/hs/search.php"
            else -> "$mainUrl/mobile/search.php"
        }

    private fun extractId(url: String): String {
        if (url.contains("id=")) {
            return url.substringAfter("id=").substringBefore("&").substringBefore("|")
        }
        val clean = url.substringBefore("?").trimEnd('/')
        val lastSegment = clean.substringAfterLast('/')
        return lastSegment.substringBefore("|").substringBefore(":")
    }

    private fun getHighResPoster(id: String, rawSrc: String?): String {
        if (id.isBlank()) return rawSrc.orEmpty()
        val cdn = InternalStreamCommon.CDN_URL
        return when (ottCode) {
            "pv" -> "$cdn/pv/700/$id.jpg"
            "hs" -> "$cdn/hs/v/700/$id.jpg"
            else -> "$cdn/poster/v/$id.jpg"
        }
    }

    private fun getHighResBanner(id: String): String {
        if (id.isBlank()) return ""
        val cdn = InternalStreamCommon.CDN_URL
        return when (ottCode) {
            "pv" -> "$cdn/pv/h/$id.jpg"
            "hs" -> "$cdn/hs/h/1920/$id.jpg"
            else -> "$cdn/poster/h/$id.jpg"
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val id = attr("data-post").takeIf { it.isNotBlank() }
            ?: selectFirst("[data-post]")?.attr("data-post")?.takeIf { it.isNotBlank() }
            ?: return null

        val imgEl = selectFirst(".top10-img img, .card-img-container img, img:not(.top10-svg)")
            ?: selectFirst("img")

        val rawSrc = imgEl?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: imgEl?.attr("src")?.takeIf { it.isNotBlank() }

        val poster = getHighResPoster(id, rawSrc)

        var title = InternalStreamCommon.titleCache[id]
        if (title.isNullOrBlank()) {
            title = selectFirst(".card-img-container img")?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
                ?: selectFirst("a.post-data")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: ""
        }

        if (title.startsWith("NetMirror", true)) {
            title = ""
        }

        return newMovieSearchResponse(title, id, TvType.Movie, fix = false) {
            this.posterUrl = poster
            this.posterHeaders = mapOf("Referer" to "$mainUrl/home")
        }
    }

    private fun Element.toHomePageList(): HomePageList? {
        val name = selectFirst("h2, .tray-title, span")?.text()?.trim() ?: return null
        if (name.isBlank() || name.contains("ERROR", true) || name.contains("Advertisement", true)) {
            return null
        }

        val items = select("article, .top10-post, .post-data").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }

        if (items.isEmpty()) return null
        val isHorizontal = (ottCode == "pv")
        return HomePageList(name, items, isHorizontalImages = isHorizontal)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val cookie = InternalStreamCommon.getOrBypassCookie()
        val cookies = mutableMapOf("ott" to ottCode, "hd" to "on")
        if (cookie.isNotBlank()) cookies["t_hash_t"] = cookie

        val res = app.get(
            "$mainUrl/mobile/home?app=1",
            cookies = cookies,
            headers = InternalStreamCommon.browserHeaders,
            referer = "$mainUrl/mobile/home?app=1"
        )
        val doc = res.document

        val allIds = doc.select(".top10-post, article a.post-data, a.post-data").mapNotNull {
            it.attr("data-post").takeIf { id -> id.isNotBlank() }
        }.distinct()

        if (allIds.isNotEmpty()) {
            InternalStreamCommon.fetchTitlesParallel(allIds, ottCode, cookie)
        }

        val trays = doc.select(".tray-container, div#top10").mapNotNull {
            it.toHomePageList()
        }

        if (trays.isEmpty()) return null
        return newHomePageResponse(trays, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val cookie = InternalStreamCommon.getOrBypassCookie()
        val cookies = mutableMapOf("ott" to ottCode, "hd" to "on")
        if (cookie.isNotBlank()) cookies["t_hash_t"] = cookie

        val encoded = URLEncoder.encode(query, "UTF-8")
        val res = app.get(
            "$searchEndpoint?s=$encoded&t=$unixTime",
            cookies = cookies,
            headers = InternalStreamCommon.browserHeaders,
            referer = "$mainUrl/home"
        ).text

        val data = tryParseJson<InternalStreamCommon.SearchData>(res) ?: return emptyList()
        return data.searchResult?.mapNotNull { item ->
            val id = item.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = item.t?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            InternalStreamCommon.titleCache[id] = title
            val poster = getHighResPoster(id, null)
            newMovieSearchResponse(title, id, TvType.Movie, fix = false) {
                this.posterUrl = poster
                this.posterHeaders = mapOf("Referer" to "$mainUrl/home")
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val id = extractId(url)

        val cookie = InternalStreamCommon.getOrBypassCookie()
        val cookies = mutableMapOf("ott" to ottCode, "hd" to "on")
        if (cookie.isNotBlank()) cookies["t_hash_t"] = cookie

        val res = app.get(
            "$postEndpoint?id=$id&t=$unixTime",
            cookies = cookies,
            headers = InternalStreamCommon.browserHeaders,
            referer = "$mainUrl/home"
        ).text

        val post = tryParseJson<InternalStreamCommon.PostData>(res)
            ?: throw ErrorLoadingException("Failed to parse metadata for $id")

        if (post.error != null) {
            throw ErrorLoadingException(post.error)
        }

        val title = post.title?.takeIf { it.isNotBlank() } ?: "Content #$id"
        InternalStreamCommon.titleCache[id] = title

        val year = post.year?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
        val plot = post.desc
        val tags = (post.genre ?: post.hs_genre)?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
        val actors = post.cast?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
        val poster = getHighResPoster(id, null)

        val validEpisodes = post.episodes?.filterNotNull()?.filter { !it.id.isNullOrBlank() }.orEmpty()
        val isTv = post.type == "t" && validEpisodes.isNotEmpty()

        val tmdbId = InternalStreamCommon.resolveTmdbId(title, year, !isTv)

        if (isTv) {
            val episodesList = mutableListOf<Episode>()
            validEpisodes.forEach { ep ->
                val epId = ep.id ?: return@forEach
                val seasonNum = ep.s?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 1
                val epNum = ep.ep?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 1
                val payload = "$epId|${tmdbId.orEmpty()}|$seasonNum|$epNum|$ottCode"
                episodesList.add(newEpisode(payload) {
                    this.name = ep.t
                    this.season = seasonNum
                    this.episode = epNum
                    this.posterUrl = poster
                })
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesList) {
                this.posterUrl = poster
                this.backgroundPosterUrl = getHighResBanner(id)
                this.posterHeaders = mapOf("Referer" to "$mainUrl/home")
                this.year = year
                this.plot = plot
                this.tags = tags
                this.actors = actors?.map { ActorData(Actor(it)) }
            }
        } else {
            val payload = "$id|${tmdbId.orEmpty()}|||$ottCode"
            return newMovieLoadResponse(title, url, TvType.Movie, payload) {
                this.posterUrl = poster
                this.backgroundPosterUrl = getHighResBanner(id)
                this.posterHeaders = mapOf("Referer" to "$mainUrl/home")
                this.year = year
                this.plot = plot
                this.tags = tags
                this.actors = actors?.map { ActorData(Actor(it)) }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("|")
        val contentId = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: data
        val tmdbId = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
        val season = parts.getOrNull(2)?.toIntOrNull()
        val episode = parts.getOrNull(3)?.toIntOrNull()
        val ott = parts.getOrNull(4)?.takeIf { it.isNotBlank() } ?: ottCode

        var foundStream = false

        if (!tmdbId.isNullOrBlank()) {
            val net27Base = InternalStreamCommon.NET27_URL
            val embedUrl = if (season != null && episode != null) {
                "$net27Base/api/embed-tmdb/$tmdbId?type=tv&s=$season&e=$episode"
            } else {
                "$net27Base/api/embed-tmdb/$tmdbId"
            }

            try {
                val net27Res = app.get(
                    embedUrl,
                    headers = InternalStreamCommon.net27Headers,
                    timeout = 10
                ).text

                val parsed = tryParseJson<InternalStreamCommon.Net27Response>(net27Res)
                if (parsed?.ok == true) {
                    parsed.streams?.sortedByDescending { it.resolution }?.forEach { stream ->
                        val streamUrl = stream.url ?: return@forEach
                        val resLabel = stream.resolution?.let { "${it}p" } ?: "HD"
                        callback.invoke(
                            newExtractorLink(
                                source = this.name,
                                name = "${this.name} [$resLabel]",
                                url = streamUrl,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.referer = InternalStreamCommon.NET27_REFERER
                                this.quality = stream.resolution ?: 1080
                            }
                        )
                        foundStream = true
                    }

                    parsed.captions?.forEach { cap ->
                        val capUrl = cap.url ?: return@forEach
                        val finalSubUrl = if (capUrl.startsWith("/"))
                            "${InternalStreamCommon.NET27_URL}$capUrl"
                        else capUrl
                        subtitleCallback.invoke(
                            newSubtitleFile(cap.name ?: cap.lang ?: "Subtitle", finalSubUrl)
                        )
                    }
                }
            } catch (e: Throwable) {
                logError(e)
            }
        }

        if (!foundStream) {
            try {
                val apiBase = InternalStreamCommon.resolveApiUrl()
                val playerHeaders = InternalStreamCommon.newTvBaseHeaders.toMutableMap().apply {
                    put("Ott", ott)
                }

                val res = app.get(
                    "$apiBase/newtv/player.php?id=$contentId",
                    headers = playerHeaders,
                    timeout = 8
                ).text

                val player = tryParseJson<InternalStreamCommon.NewTvPlayerResponse>(res)
                val streamUrl = player?.video_link

                if (!streamUrl.isNullOrBlank()) {
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = "${this.name} [HLS]",
                            url = streamUrl.replace("\\/", "/"),
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = InternalStreamCommon.MAIN_URL
                        }
                    )
                    foundStream = true
                }
            } catch (e: Throwable) {
                logError(e)
            }
        }

        return foundStream
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return Interceptor { chain ->
            val original = chain.request()
            val ref = if (extractorLink.url.contains("hakunaymatata", true))
                InternalStreamCommon.NET27_REFERER
            else InternalStreamCommon.MAIN_URL

            chain.proceed(
                original.newBuilder()
                    .removeHeader("Referer").removeHeader("referer")
                    .removeHeader("Origin").removeHeader("origin")
                    .removeHeader("User-Agent").removeHeader("user-agent")
                    .header("Referer", ref)
                    .header("Origin", ref.trimEnd('/'))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
            )
        }
    }
}

class InternalStreamA : InternalStreamBase("nf") {
    override var name = "NetMirror - Netflix"
}

class InternalStreamB : InternalStreamBase("pv") {
    override var name = "NetMirror - Prime Video"
}

class InternalStreamC : InternalStreamBase("hs") {
    override var name = "NetMirror - Disney+ Hotstar"
}
