package com.tvapp.livetv.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val group: String = "General",
    val logoUrl: String? = null
)

data class PlaylistSource(
    val id: String,
    val label: String,
    val url: String
)

object M3uRepository {
    val PRESET_SOURCES = listOf(
        PlaylistSource("in", "India (All)", "https://iptv-org.github.io/iptv/countries/in.m3u"),
        PlaylistSource("hin", "Hindi", "https://iptv-org.github.io/iptv/languages/hin.m3u"),
        PlaylistSource("news", "News", "https://iptv-org.github.io/iptv/categories/news.m3u"),
        PlaylistSource("ent", "Entertainment", "https://iptv-org.github.io/iptv/categories/entertainment.m3u"),
        PlaylistSource("mov", "Movies", "https://iptv-org.github.io/iptv/categories/movies.m3u"),
        PlaylistSource("mus", "Music", "https://iptv-org.github.io/iptv/categories/music.m3u"),
        PlaylistSource("spt", "Sports", "https://iptv-org.github.io/iptv/categories/sports.m3u"),
        PlaylistSource("kid", "Kids", "https://iptv-org.github.io/iptv/categories/kids.m3u")
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun fetchPlaylist(url: String = PRESET_SOURCES.first().url): List<Channel> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Playlist fetch failed: HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                parseM3u(body)
            }
        }

    fun parseM3u(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val seenUrls = HashSet<String>()
        val lines = content.lines()
        var currentName = ""
        var currentGroup = "General"
        var currentLogo: String? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF")) {
                val rawGroup = extractAttr(line, "group-title")
                    ?.split(";", ",")
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim()
                currentGroup = when {
                    rawGroup.isNullOrBlank() -> "General"
                    rawGroup.equals("Undefined", ignoreCase = true) -> "General"
                    else -> rawGroup.replaceFirstChar { it.uppercase() }
                }
                currentLogo = extractAttr(line, "tvg-logo")?.takeIf { it.startsWith("http") }
                currentName = line.substringAfterLast(",", "").trim()
                    .replace(Regex("\\s*\\(\\d+p\\)"), "")
                    .replace(Regex("\\s*\\[.*?\\]"), "")
                    .trim()
                    .ifBlank { "Channel ${channels.size + 1}" }
            } else if (!line.startsWith("#")) {
                if ((line.startsWith("http://") || line.startsWith("https://")) && seenUrls.add(line)) {
                    channels.add(
                        Channel(
                            id = "${line.hashCode()}",
                            name = currentName,
                            streamUrl = line,
                            group = currentGroup,
                            logoUrl = currentLogo
                        )
                    )
                }
                currentName = ""
                currentGroup = "General"
                currentLogo = null
            }
        }
        return channels.sortedBy { it.name.lowercase() }
    }

    private fun extractAttr(line: String, key: String): String? {
        val regex = Regex("$key=\"([^\"]*)\"")
        return regex.find(line)?.groupValues?.getOrNull(1)
    }
}

object UserPrefs {
    private const val PREFS = "tarang_tv_prefs"
    private const val KEY_FAVS = "favorites"
    private const val KEY_RECENT = "recent"

    fun getFavorites(context: Context): Set<String> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp.getStringSet(KEY_FAVS, emptySet())?.toSet() ?: emptySet()
    }

    fun toggleFavorite(context: Context, channelId: String): Set<String> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = getFavorites(context).toMutableSet()
        if (!updated.add(channelId)) updated.remove(channelId)
        sp.edit().putStringSet(KEY_FAVS, updated).apply()
        return updated
    }

    fun getRecentIds(context: Context): List<String> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_RECENT, "").orEmpty()
        return if (raw.isBlank()) emptyList() else raw.split(",")
    }

    fun addRecent(context: Context, channelId: String): List<String> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = getRecentIds(context).toMutableList()
        list.remove(channelId)
        list.add(0, channelId)
        val trimmed = list.take(20)
        sp.edit().putString(KEY_RECENT, trimmed.joinToString(",")).apply()
        return trimmed
    }
}
